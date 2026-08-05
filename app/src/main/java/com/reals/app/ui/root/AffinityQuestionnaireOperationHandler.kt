package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.di.AffinityFeatureDependencies
import com.reals.app.domain.model.AffinityAnswer
import com.reals.app.domain.model.AffinityQuestionCatalog
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.ui.profile.canSelectAnswerCode
import com.reals.app.ui.profile.currentValidAnswer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class AffinityQuestionnaireOperationHandler(
    private val uiState: kotlinx.coroutines.flow.MutableStateFlow<RealsRootUiState>,
    private val dependencies: AffinityFeatureDependencies,
    private val scope: CoroutineScope,
) {
    private var loadRequestId = 0L

    fun open() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val profile = (current.session.profileSnapshot as? ProfileSnapshot.Found)?.profile ?: return
        if (profile.status != ProfileStatus.Draft && profile.status != ProfileStatus.Active) return
        if (current.affinityQuestionnaire.mutation != null) return
        if (current.affinityQuestionnaire.loading || current.affinityQuestionnaire.refreshing) return

        val retained = current.affinityQuestionnaire
            .takeIf { it.profileId == profile.id && it.catalog != null }
        val requestId = ++loadRequestId
        val opening = current.copy(
            affinityQuestionnaire = AffinityQuestionnaireUiState(
                open = true,
                profileId = profile.id,
                catalog = retained?.catalog,
                answers = retained?.answers.orEmpty(),
                loading = retained == null,
                refreshing = retained != null,
            ),
        )
        uiState.value = opening
        scope.launch {
            installLoadResult(
                requestId = requestId,
                profileId = profile.id,
                userId = opening.session.user.id,
                result = loadSnapshot(),
            )
        }
    }

    fun close() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        if (!current.affinityQuestionnaire.open) return
        uiState.value = current.copy(
            affinityQuestionnaire = current.affinityQuestionnaire.copy(
                open = false,
                loading = false,
                refreshing = false,
                mutation = null,
                error = null,
                mutationError = null,
                message = null,
            ),
        )
    }

    fun refresh() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = current.affinityQuestionnaire
        val profileId = questionnaire.profileId ?: return
        if (!questionnaire.open || questionnaire.loading || questionnaire.refreshing || questionnaire.mutation != null) return
        val requestId = ++loadRequestId
        uiState.value = current.copy(
            affinityQuestionnaire = questionnaire.copy(
                loading = questionnaire.catalog == null,
                refreshing = questionnaire.catalog != null,
                error = null,
                mutationError = null,
                message = null,
            ),
        )
        scope.launch {
            installLoadResult(
                requestId = requestId,
                profileId = profileId,
                userId = current.session.user.id,
                result = loadSnapshot(),
            )
        }
    }

    fun selectAnswer(questionId: String, answerCode: String) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = current.affinityQuestionnaire
        val catalog = questionnaire.catalog ?: return
        if (!questionnaire.open || questionnaire.mutation != null || questionnaire.loading) return
        val question = catalog.questions.firstOrNull { it.id == questionId } ?: return
        if (!question.canSelectAnswerCode(answerCode)) return
        if (question.currentValidAnswer(questionnaire.answers)?.answerCode == answerCode) return

        val mutation = AffinityAnswerMutationUiState(questionId = question.id, pendingAnswerCode = answerCode)
        uiState.value = current.copy(
            affinityQuestionnaire = questionnaire.copy(
                mutation = mutation,
                mutationError = null,
                message = null,
            ),
        )
        scope.launch {
            installMutationResult(
                profileId = questionnaire.profileId ?: return@launch,
                userId = current.session.user.id,
                mutation = mutation,
                result = dependencies.patchAnswer(question.id, answerCode),
            )
        }
    }

    fun deleteAnswer(questionId: String) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = current.affinityQuestionnaire
        val catalog = questionnaire.catalog ?: return
        if (!questionnaire.open || questionnaire.mutation != null || questionnaire.loading) return
        val question = catalog.questions.firstOrNull { it.id == questionId } ?: return
        if (question.currentValidAnswer(questionnaire.answers) == null) return

        val mutation = AffinityAnswerMutationUiState(questionId = question.id, pendingAnswerCode = null)
        uiState.value = current.copy(
            affinityQuestionnaire = questionnaire.copy(
                mutation = mutation,
                mutationError = null,
                message = null,
            ),
        )
        scope.launch {
            installMutationResult(
                profileId = questionnaire.profileId ?: return@launch,
                userId = current.session.user.id,
                mutation = mutation,
                result = dependencies.deleteAnswer(question.id),
            )
        }
    }

    private suspend fun loadSnapshot(): AffinityQuestionnaireLoadResult = coroutineScope {
        val catalog = async { dependencies.getCatalog() }
        val answers = async { dependencies.getMyAnswers() }
        when (val catalogResult = catalog.await()) {
            is ApiResult.Failure -> AffinityQuestionnaireLoadResult.Failure(catalogResult.error)
            is ApiResult.Success -> when (val answersResult = answers.await()) {
                is ApiResult.Failure -> AffinityQuestionnaireLoadResult.Failure(answersResult.error)
                is ApiResult.Success -> AffinityQuestionnaireLoadResult.Success(
                    catalog = catalogResult.value,
                    answers = answersResult.value,
                )
            }
        }
    }

    private fun installLoadResult(
        requestId: Long,
        profileId: String,
        userId: String,
        result: AffinityQuestionnaireLoadResult,
    ) {
        val latest = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = latest.affinityQuestionnaire
        if (
            requestId != loadRequestId ||
            !questionnaire.open ||
            questionnaire.profileId != profileId ||
            latest.session.user.id != userId ||
            (latest.session.profileSnapshot as? ProfileSnapshot.Found)?.profile?.id != profileId
        ) {
            return
        }
        uiState.value = when (result) {
            is AffinityQuestionnaireLoadResult.Success -> latest.copy(
                affinityQuestionnaire = questionnaire.copy(
                    catalog = result.catalog,
                    answers = result.answers,
                    loading = false,
                    refreshing = false,
                    error = null,
                    mutationError = null,
                ),
            )

            is AffinityQuestionnaireLoadResult.Failure -> latest.copy(
                affinityQuestionnaire = questionnaire.copy(
                    loading = false,
                    refreshing = false,
                    error = result.error,
                ),
            )
        }
    }

    private fun installMutationResult(
        profileId: String,
        userId: String,
        mutation: AffinityAnswerMutationUiState,
        result: ApiResult<List<AffinityAnswer>>,
    ) {
        val latest = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = latest.affinityQuestionnaire
        if (
            !questionnaire.open ||
            questionnaire.profileId != profileId ||
            questionnaire.mutation != mutation ||
            latest.session.user.id != userId ||
            (latest.session.profileSnapshot as? ProfileSnapshot.Found)?.profile?.id != profileId
        ) {
            return
        }
        uiState.value = when (result) {
            is ApiResult.Success -> latest.copy(
                affinityQuestionnaire = questionnaire.copy(
                    answers = result.value,
                    mutation = null,
                    mutationError = null,
                    message = "Respuesta guardada.",
                ),
            )

            is ApiResult.Failure -> latest.copy(
                affinityQuestionnaire = questionnaire.copy(
                    mutation = null,
                    mutationError = result.error,
                    message = null,
                ),
            )
        }
    }
}

private sealed interface AffinityQuestionnaireLoadResult {
    data class Success(
        val catalog: AffinityQuestionCatalog,
        val answers: List<AffinityAnswer>,
    ) : AffinityQuestionnaireLoadResult

    data class Failure(val error: com.reals.app.core.network.ApiError) : AffinityQuestionnaireLoadResult
}
