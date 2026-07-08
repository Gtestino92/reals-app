package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.di.LegalFeatureDependencies
import com.reals.app.domain.model.CurrentLegalDocument
import com.reals.app.domain.model.LegalDocumentAction
import com.reals.app.domain.model.LegalDocumentStatus
import com.reals.app.domain.model.ProvisionedSession

internal class LegalCoordinator(
    private val dependencies: LegalFeatureDependencies,
) {
    suspend fun load(
        session: ProvisionedSession,
        resumeContext: LegalResumeContext,
    ): LegalCoordinatorResult = loadInternal(session, resumeContext, keepAccountState = null)

    suspend fun recordRequiredAction(
        current: RealsRootUiState.LegalRequirements,
        requirement: LegalRequirementUiItem,
    ): LegalCoordinatorResult {
        val pending = current.copy(
            submittingDocumentType = requirement.type,
            error = null,
        )
        val recordResult = dependencies.recordAction(
            documentType = requirement.type,
            documentVersion = requirement.version,
            action = requirement.requiredAction,
        )
        if (recordResult is ApiResult.Failure) {
            val backend = recordResult.error as? ApiError.Backend
            return if (
                backend?.backendErrorCode == BackendErrorCode.LegalDocumentVersionNotCurrent ||
                    backend?.backendErrorCode == BackendErrorCode.LegalDocumentNotFound
            ) {
                loadInternal(
                    session = current.session,
                    resumeContext = current.resumeContext,
                    keepAccountState = current,
                )
            } else {
                LegalCoordinatorResult.Show(pending.copy(submittingDocumentType = null, error = recordResult.error))
            }
        }

        return loadInternal(
            session = current.session,
            resumeContext = current.resumeContext,
            keepAccountState = current,
        )
    }

    private suspend fun loadInternal(
        session: ProvisionedSession,
        resumeContext: LegalResumeContext,
        keepAccountState: RealsRootUiState.LegalRequirements?,
    ): LegalCoordinatorResult {
        val statusResult = dependencies.getStatus()
        if (statusResult is ApiResult.Failure) {
            return LegalCoordinatorResult.Show(
                RealsRootUiState.LegalRequirements(
                    session = session,
                    resumeContext = resumeContext,
                    loading = false,
                    error = statusResult.error,
                    deletingAccount = keepAccountState?.deletingAccount ?: false,
                    accountDeleteError = keepAccountState?.accountDeleteError,
                )
            )
        }

        val status = (statusResult as ApiResult.Success).value
        if (status.requirementsSatisfied) {
            return LegalCoordinatorResult.Satisfied(session, resumeContext)
        }

        val documentsResult = dependencies.getCurrentDocuments()
        if (documentsResult is ApiResult.Failure) {
            return LegalCoordinatorResult.Show(
                RealsRootUiState.LegalRequirements(
                    session = session,
                    resumeContext = resumeContext,
                    requirementsSatisfied = false,
                    documents = emptyList(),
                    loading = false,
                    error = documentsResult.error,
                    deletingAccount = keepAccountState?.deletingAccount ?: false,
                    accountDeleteError = keepAccountState?.accountDeleteError,
                )
            )
        }

        val currentDocuments = (documentsResult as ApiResult.Success).value
        val requirements = joinCurrentDocumentsWithStatus(
            currentDocuments = currentDocuments,
            statusDocuments = status.documents,
        ) ?: return LegalCoordinatorResult.Show(
            RealsRootUiState.LegalRequirements(
                session = session,
                resumeContext = resumeContext,
                requirementsSatisfied = false,
                loading = false,
                error = ApiError.Unexpected("El estado legal no coincide con los documentos vigentes."),
                deletingAccount = keepAccountState?.deletingAccount ?: false,
                accountDeleteError = keepAccountState?.accountDeleteError,
            )
        )

        return LegalCoordinatorResult.Show(
            RealsRootUiState.LegalRequirements(
                session = session,
                resumeContext = resumeContext,
                requirementsSatisfied = false,
                documents = requirements,
                loading = false,
                deletingAccount = keepAccountState?.deletingAccount ?: false,
                accountDeleteError = keepAccountState?.accountDeleteError,
            )
        )
    }

    private fun joinCurrentDocumentsWithStatus(
        currentDocuments: List<CurrentLegalDocument>,
        statusDocuments: List<LegalDocumentStatus>,
    ): List<LegalRequirementUiItem>? {
        val statusByKey = statusDocuments.associateBy { it.key() }
        return currentDocuments.map { current ->
            val status = statusByKey[current.key()] ?: return null
            if (status.requiredAction.rawValue != current.requiredAction.rawValue) return null
            LegalRequirementUiItem(
                type = current.type,
                version = current.version,
                url = current.url,
                requiredAction = current.requiredAction,
                recordedAction = status.recordedAction,
                actedAt = status.actedAt,
                satisfied = status.satisfied,
            )
        }
    }
}

internal sealed interface LegalCoordinatorResult {
    data class Satisfied(
        val session: ProvisionedSession,
        val resumeContext: LegalResumeContext,
    ) : LegalCoordinatorResult

    data class Show(
        val state: RealsRootUiState.LegalRequirements,
    ) : LegalCoordinatorResult
}

private fun CurrentLegalDocument.key(): String = "${type.rawValue}:$version"

private fun LegalDocumentStatus.key(): String = "${type.rawValue}:$version"

internal fun LegalDocumentAction.isKnownAction(): Boolean =
    this is LegalDocumentAction.Accepted || this is LegalDocumentAction.Acknowledged
