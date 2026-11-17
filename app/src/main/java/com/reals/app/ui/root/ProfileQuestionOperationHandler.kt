package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.isLegalActionRequired
import com.reals.app.core.network.isTerminalAuthFailure
import com.reals.app.di.ProfileQuestionFeatureDependencies
import com.reals.app.domain.model.ProfileQuestionAnswer
import com.reals.app.domain.model.ProfileQuestionCatalog
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.sameProfileOwner
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.ui.profile.ProfileQuestionMaxPublicSelections
import com.reals.app.ui.profile.profileQuestionSelectionDraftIsValid
import com.reals.app.ui.profile.selectedProfileQuestionIds
import com.reals.app.ui.profile.selectionRows
import com.reals.app.ui.profile.validateProfileQuestionAnswer
import com.reals.app.ui.profile.canSaveProfileQuestionAnswer
import com.reals.app.ui.profile.isCurrentFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class ProfileQuestionOperationHandler(
    private val uiState: kotlinx.coroutines.flow.MutableStateFlow<RealsRootUiState>,
    private val dependencies: ProfileQuestionFeatureDependencies,
    private val scope: CoroutineScope,
) {
    private var loadRequestId = 0L
    private var mutationRequestId = 0L
    private var mutationJob: Job? = null
    private var loadJob: Job? = null
    private var activeMutation: ActiveProfileQuestionMutation? = null
    private var ownerSession: com.reals.app.domain.model.ProvisionedSession? = null

    fun cancelForSessionReplacement(session: com.reals.app.domain.model.ProvisionedSession?) {
        if (ownerSession != null && (!ownerSession!!.sameProfileOwner(session) || session?.supportsOwnerProfileContent() != true)) {
            ++loadRequestId
            loadJob?.cancel()
            mutationJob?.cancel()
            activeMutation = null
            ownerSession = null
            val ready = uiState.value as? RealsRootUiState.Ready
            if (ready != null) uiState.value = ready.copy(profileQuestions = ProfileQuestionUiState())
        }
    }

    fun open() {
        val candidate = uiState.value as? RealsRootUiState.Ready ?: return
        cancelForSessionReplacement(candidate.session)
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val profile = (current.session.profileSnapshot as? ProfileSnapshot.Found)?.profile ?: return
        if (profile.status != ProfileStatus.Draft && profile.status != ProfileStatus.Active) return
        ownerSession = current.session
        if (current.profileQuestions.mutation != null) return
        if (current.profileQuestions.loading || current.profileQuestions.refreshing) return

        val retained = current.profileQuestions
            .takeIf { it.profileId == profile.id && it.catalog != null }
        val activeMutationForProfile = activeMutation
            ?.takeIf {
                mutationJob?.isActive == true &&
                        it.profileId == profile.id &&
                        it.userId == current.session.user.id
            }
        if (activeMutationForProfile != null) {
            uiState.value = current.copy(
                profileQuestions = ProfileQuestionUiState(
                    open = true,
                    profileId = profile.id,
                    destination = ProfileQuestionDestination.Overview,
                    catalog = retained?.catalog,
                    answers = retained?.answers.orEmpty(),
                    loading = retained == null,
                    refreshing = retained != null,
                    mutation = activeMutationForProfile.mutation,
                    selectionDraftQuestionIds = retained?.selectionDraftQuestionIds.orEmpty(),
                    uncertain = retained?.uncertain ?: false,
                ),
            )
            return
        }

        val requestId = ++loadRequestId
        val opening = current.copy(
            profileQuestions = ProfileQuestionUiState(
                open = true,
                profileId = profile.id,
                destination = ProfileQuestionDestination.Overview,
                catalog = retained?.catalog,
                answers = retained?.answers.orEmpty(),
                loading = retained == null,
                refreshing = retained != null,
                selectionDraftQuestionIds = retained?.selectionDraftQuestionIds.orEmpty(),
                uncertain = retained?.uncertain ?: false,
            ),
        )
        uiState.value = opening
        loadJob?.cancel()
        loadJob = scope.launch {
            installLoadResult(
                requestId = requestId,
                profileId = profile.id,
                userId = opening.session.user.id,
                result = loadSnapshot(),
                session = opening.session,
            )
        }
    }

    fun close() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        if (!current.profileQuestions.open) return
        ++loadRequestId
        loadJob?.cancel()
        uiState.value = current.copy(
            profileQuestions = current.profileQuestions.copy(
                open = false,
                destination = ProfileQuestionDestination.Overview,
                loading = false,
                refreshing = false,
                revalidating = false,
                mutation = null,
                error = null,
                mutationError = null,
                feedback = null,
            ),
        )
    }

    fun onResume() = refresh(background = true)

    fun refresh(background: Boolean = false) {
        val candidate = uiState.value as? RealsRootUiState.Ready ?: return
        cancelForSessionReplacement(candidate.session)
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.profileQuestions
        val profileId = state.profileId ?: return
        if (!state.open || state.loading || state.refreshing || state.mutation != null) return
        if (background && (state.uncertain || state.revalidating)) return
        ownerSession = current.session
        val silent = background && state.catalog != null
        val requestId = ++loadRequestId
        uiState.value = current.copy(
            profileQuestions = state.copy(
                loading = !silent && state.catalog == null,
                refreshing = !silent && state.catalog != null,
                revalidating = silent,
                error = null,
                mutationError = null,
                feedback = if (silent) state.feedback else null,
            ),
        )
        loadJob?.cancel()
        loadJob = scope.launch {
            installLoadResult(requestId, profileId, current.session.user.id, loadSnapshot(), current.session)
        }
    }

    fun openOverview() = setDestination(ProfileQuestionDestination.Overview)

    fun openQuestions() = setDestination(ProfileQuestionDestination.Questions)

    fun openEditor(questionId: String) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.profileQuestions
        if (!state.open || state.mutation != null || state.catalog?.questions?.none { it.id == questionId } == true) return
        setDestination(ProfileQuestionDestination.Editor(questionId))
    }

    fun openSelection() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.profileQuestions
        if (!state.open || state.mutation != null) return
        setDestination(
            ProfileQuestionDestination.Selection,
            selectionDraftQuestionIds = selectedProfileQuestionIds(state.answers),
        )
    }

    fun updateSelectionDraft(questionIds: List<String>) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.profileQuestions
        val catalog = state.catalog ?: return
        if (!state.open || state.mutation != null || state.uncertain) return
        val normalized = questionIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalized.size > ProfileQuestionMaxPublicSelections) return
        uiState.value = current.copy(
            profileQuestions = state.copy(
                selectionDraftQuestionIds = normalized,
                mutationError = null,
                feedback = null,
            ),
        )
    }

    fun navigateBack() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        if (!current.profileQuestions.open) return
        when (current.profileQuestions.destination) {
            ProfileQuestionDestination.Overview -> close()
            ProfileQuestionDestination.Questions,
            ProfileQuestionDestination.Selection -> setDestination(ProfileQuestionDestination.Overview)

            is ProfileQuestionDestination.Editor -> setDestination(ProfileQuestionDestination.Questions)
        }
    }

    fun saveAnswer(questionId: String, answer: String) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.profileQuestions
        val catalog = state.catalog ?: return
        if (!state.canStartMutation() || catalog.questions.none { it.id == questionId }) return
        val question = catalog.questions.first { it.id == questionId }
        val saved = state.answers.firstOrNull { it.questionId == questionId }
        if (!canSaveProfileQuestionAnswer(answer, saved?.copy(current = saved.isCurrentFor(question)), false)) return
        val validation = validateProfileQuestionAnswer(answer)
        if (!validation.valid) return
        startMutation(
            current = current,
            mutation = ProfileQuestionMutationUiState(
                kind = ProfileQuestionMutationKind.Upsert,
                requestId = ++mutationRequestId,
                questionId = questionId,
            ),
        ) { dependencies.upsertAnswer(questionId, validation.normalizedAnswer) }
    }

    fun deleteAnswer(questionId: String) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.profileQuestions
        if (!state.canStartMutation() || state.answers.none { it.questionId == questionId }) return
        startMutation(
            current = current,
            mutation = ProfileQuestionMutationUiState(
                kind = ProfileQuestionMutationKind.Delete,
                requestId = ++mutationRequestId,
                questionId = questionId,
            ),
        ) { dependencies.deleteAnswer(questionId) }
    }

    fun saveSelection() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val state = current.profileQuestions
        val catalog = state.catalog ?: return
        if (!state.canStartMutation()) return
        val currentSelection = selectedProfileQuestionIds(state.answers)
        val draft = state.selectionDraftQuestionIds
        if (draft == currentSelection || draft.size > ProfileQuestionMaxPublicSelections) return
        if (!profileQuestionSelectionDraftIsValid(
                draft,
                catalog.selectionRows(state.answers)
            )
        ) return
        startMutation(
            current = current,
            mutation = ProfileQuestionMutationUiState(
                kind = ProfileQuestionMutationKind.Selection,
                requestId = ++mutationRequestId,
            ),
        ) { dependencies.replaceSelections(draft) }
    }

    private fun startMutation(
        current: RealsRootUiState.Ready,
        mutation: ProfileQuestionMutationUiState,
        call: suspend () -> ApiResult<List<ProfileQuestionAnswer>>,
    ) {
        if (!current.session.supportsOwnerProfileContent()) return
        val state = current.profileQuestions
        val profileId = state.profileId ?: return
        val userId = current.session.user.id
        ++loadRequestId
        loadJob?.cancel()
        ownerSession = current.session
        val originDestination = state.destination
        activeMutation = ActiveProfileQuestionMutation(
            requestId = mutation.requestId,
            profileId = profileId,
            userId = userId,
            mutation = mutation,
            originDestination = originDestination,
        )
        uiState.value = current.copy(
            profileQuestions = state.copy(
                mutation = mutation,
                revalidating = false,
                mutationError = null,
                feedback = null,
            ),
        )
        mutationJob = scope.launch {
            installMutationResult(
                profileId = profileId,
                userId = userId,
                mutation = mutation,
                originDestination = originDestination,
                result = call(),
                session = current.session,
            )
            clearActiveMutationIfCurrent(mutation.requestId)
        }
    }

    private suspend fun loadSnapshot(): ProfileQuestionLoadResult = coroutineScope {
        val catalog = async { dependencies.getCatalog() }
        val answers = async { dependencies.getMyAnswers() }
        when (val catalogResult = catalog.await()) {
            is ApiResult.Failure -> ProfileQuestionLoadResult.Failure(catalogResult.error)
            is ApiResult.Success -> when (val answersResult = answers.await()) {
                is ApiResult.Failure -> ProfileQuestionLoadResult.Failure(answersResult.error)
                is ApiResult.Success -> ProfileQuestionLoadResult.Success(
                    catalogResult.value,
                    answersResult.value
                )
            }
        }
    }

    private fun installLoadResult(
        requestId: Long,
        profileId: String,
        userId: String,
        result: ProfileQuestionLoadResult,
        session: com.reals.app.domain.model.ProvisionedSession,
    ) {
        val latest = uiState.value as? RealsRootUiState.Ready ?: return
        val state = latest.profileQuestions
        if (
            requestId != loadRequestId || !latest.session.sameProfileOwner(session) ||
            !state.open ||
            state.profileId != profileId ||
            latest.session.user.id != userId ||
            (latest.session.profileSnapshot as? ProfileSnapshot.Found)?.profile?.id != profileId
        ) {
            return
        }
        uiState.value = when (result) {
            is ProfileQuestionLoadResult.Success -> latest.copy(
                profileQuestions = state.copy(
                    catalog = result.catalog,
                    uncertain = false,
                    answers = result.answers,
                    destination = result.catalog.reconciledDestination(state.destination),
                    loading = false,
                    refreshing = false,
                    revalidating = false,
                    error = null,
                    mutationError = null,
                    feedback = if (state.revalidating) state.feedback else null,
                    selectionDraftQuestionIds = if (state.selectionDraftQuestionIds == selectedProfileQuestionIds(state.answers))
                        selectedProfileQuestionIds(result.answers) else state.selectionDraftQuestionIds,
                ),
            )

            is ProfileQuestionLoadResult.Failure -> latest.copy(
                profileQuestions = state.copy(
                    uncertain = true,
                    catalog = if (result.error.disallowsProfileSnapshot()) null else state.catalog,
                    answers = if (result.error.disallowsProfileSnapshot()) emptyList() else state.answers,
                    loading = false,
                    refreshing = false,
                    revalidating = false,
                    error = result.error,
                ),
            )
        }
    }

    private fun installMutationResult(
        profileId: String,
        userId: String,
        mutation: ProfileQuestionMutationUiState,
        originDestination: ProfileQuestionDestination,
        result: ApiResult<List<ProfileQuestionAnswer>>,
        session: com.reals.app.domain.model.ProvisionedSession,
    ) {
        val latest = uiState.value as? RealsRootUiState.Ready ?: return
        val state = latest.profileQuestions
        if (
            state.profileId != profileId || !latest.session.sameProfileOwner(session) ||
            latest.session.user.id != userId ||
            (latest.session.profileSnapshot as? ProfileSnapshot.Found)?.profile?.id != profileId
        ) {
            return
        }
        if (state.open && state.mutation?.requestId != mutation.requestId) return
        val localResultStillRelevant =
            state.open && state.destination == originDestination
        uiState.value = when (result) {
            is ApiResult.Success -> {
                val completedSelectionAtOrigin =
                    mutation.kind == ProfileQuestionMutationKind.Selection &&
                            localResultStillRelevant

                latest.copy(
                    profileQuestions = state.copy(
                        answers = result.value,
                        uncertain = false,
                        destination =
                            if (completedSelectionAtOrigin) {
                                ProfileQuestionDestination.Overview
                            } else {
                                state.destination
                            },
                        loading = false,
                        refreshing = false,
                        mutation = null,
                        mutationError = null,
                        feedback = when {
                            completedSelectionAtOrigin ->
                                ProfileQuestionFeedback(
                                    destination = ProfileQuestionDestination.Overview,
                                    questionId = null,
                                    message = "Selección guardada",
                                )

                            localResultStillRelevant ->
                                mutation.successFeedback(originDestination)

                            else ->
                                null
                        },
                        selectionDraftQuestionIds = if (mutation.kind == ProfileQuestionMutationKind.Selection ||
                            state.selectionDraftQuestionIds == selectedProfileQuestionIds(state.answers))
                            selectedProfileQuestionIds(result.value) else state.selectionDraftQuestionIds.filter { id ->
                                result.value.any { it.questionId == id }
                            },
                    ),
                )
            }

            is ApiResult.Failure -> {
                val mustRemainObservable =
                    result.error.isLegalActionRequired() ||
                            result.error.isTerminalAuthFailure()

                latest.copy(
                    profileQuestions = state.copy(
                        loading = false,
                        refreshing = false,
                        mutation = null,
                        uncertain = result.error.disallowsProfileSnapshot() || result.error !is com.reals.app.core.network.ApiError.Backend || result.error.statusCode !in 400..499,
                        catalog = if (result.error.disallowsProfileSnapshot()) null else state.catalog,
                        answers = if (result.error.disallowsProfileSnapshot()) emptyList() else state.answers,
                        error = if (result.error.disallowsProfileSnapshot()) result.error else state.error,
                        mutationError = when {
                            mustRemainObservable -> result.error
                            localResultStillRelevant -> result.error
                            else -> null
                        },
                        feedback = null,
                    ),
                )
            }
        }
    }

    private fun setDestination(
        destination: ProfileQuestionDestination,
        selectionDraftQuestionIds: List<String>? = null,
    ) {
        val latest = uiState.value as? RealsRootUiState.Ready ?: return
        val state = latest.profileQuestions
        if (!state.open) return
        val retainedMutationError = state.mutationError
            ?.takeIf { state.uncertain || it.isLegalActionRequired() || it.isTerminalAuthFailure() }
        uiState.value = latest.copy(
            profileQuestions = state.copy(
                destination = destination,
                error = null,
                mutationError = retainedMutationError,
                feedback = null,
                selectionDraftQuestionIds = selectionDraftQuestionIds
                    ?: state.selectionDraftQuestionIds,
            ),
        )
    }

    private fun clearActiveMutationIfCurrent(requestId: Long) {
        if (activeMutation?.requestId == requestId) {
            activeMutation = null
        }
    }
}

private fun ProfileQuestionUiState.canStartMutation(): Boolean =
    open && mutation == null && !loading && !refreshing && !uncertain

private fun ProfileQuestionCatalog.reconciledDestination(
    destination: ProfileQuestionDestination,
): ProfileQuestionDestination = when (destination) {
    is ProfileQuestionDestination.Editor ->
        if (questions.any { it.id == destination.questionId }) destination else ProfileQuestionDestination.Questions

    ProfileQuestionDestination.Selection,
    ProfileQuestionDestination.Questions,
    ProfileQuestionDestination.Overview -> destination
}

private fun ProfileQuestionMutationUiState.successFeedback(
    destination: ProfileQuestionDestination,
): ProfileQuestionFeedback = ProfileQuestionFeedback(
    destination = destination,
    questionId = questionId,
    message = when (kind) {
        ProfileQuestionMutationKind.Upsert -> "Respuesta guardada"
        ProfileQuestionMutationKind.Delete -> "Respuesta eliminada"
        ProfileQuestionMutationKind.Selection -> "Selección guardada"
    },
)

private data class ActiveProfileQuestionMutation(
    val requestId: Long,
    val profileId: String,
    val userId: String,
    val mutation: ProfileQuestionMutationUiState,
    val originDestination: ProfileQuestionDestination,
)

private sealed interface ProfileQuestionLoadResult {
    data class Success(
        val catalog: ProfileQuestionCatalog,
        val answers: List<ProfileQuestionAnswer>,
    ) : ProfileQuestionLoadResult

    data class Failure(val error: com.reals.app.core.network.ApiError) : ProfileQuestionLoadResult
}
