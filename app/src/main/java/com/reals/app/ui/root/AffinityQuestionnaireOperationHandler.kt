package com.reals.app.ui.root

import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.isLegalActionRequired
import com.reals.app.core.network.isTerminalAuthFailure
import com.reals.app.di.AffinityFeatureDependencies
import com.reals.app.domain.model.AffinityAnswer
import com.reals.app.domain.model.AffinityQuestionCatalog
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus
import com.reals.app.ui.profile.canSelectAnswerCode
import com.reals.app.ui.profile.currentValidAnswer
import com.reals.app.ui.profile.findAnswerableQuestion
import com.reals.app.ui.profile.firstCategoryQuestion
import com.reals.app.ui.profile.firstUnansweredQuestion
import com.reals.app.ui.profile.groupQuestionsForPresentation
import com.reals.app.ui.profile.nextCategoryQuestion
import com.reals.app.ui.profile.nextContinueQuestion
import com.reals.app.ui.profile.reconciledDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class AffinityQuestionnaireOperationHandler(
    private val uiState: kotlinx.coroutines.flow.MutableStateFlow<RealsRootUiState>,
    private val dependencies: AffinityFeatureDependencies,
    private val scope: CoroutineScope,
) {
    private var loadRequestId = 0L
    private var homeSummaryLoadRequestId = 0L
    private var mutationRequestId = 0L
    private var mutationJob: Job? = null
    private var activeMutation: ActiveAffinityAnswerMutation? = null

    fun loadHomeSummaryIfNeeded() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val profile = (current.session.profileSnapshot as? ProfileSnapshot.Found)?.profile ?: return
        if (profile.status != ProfileStatus.Draft && profile.status != ProfileStatus.Active) return

        val questionnaire = current.affinityQuestionnaire
            .takeIf { it.profileId == profile.id && it.catalog != null }
        if (questionnaire != null) {
            uiState.value = current.copy(
                affinityHomeSummary = current.affinityHomeSummary.copy(
                    profileId = profile.id,
                    catalog = questionnaire.catalog,
                    answers = questionnaire.answers,
                    loading = false,
                    loadAttempted = true,
                ),
            )
            return
        }

        val summary = current.affinityHomeSummary
        if (
            summary.profileId == profile.id &&
            (summary.catalog != null || summary.loading || summary.loadAttempted)
        ) {
            return
        }

        val requestId = ++homeSummaryLoadRequestId
        uiState.value = current.copy(
            affinityHomeSummary = AffinityHomeSummaryUiState(
                profileId = profile.id,
                loading = true,
                loadAttempted = true,
            ),
        )
        scope.launch {
            installHomeSummaryLoadResult(
                requestId = requestId,
                profileId = profile.id,
                userId = current.session.user.id,
                result = loadSnapshot(),
            )
        }
    }

    fun open() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val profile = (current.session.profileSnapshot as? ProfileSnapshot.Found)?.profile ?: return
        if (profile.status != ProfileStatus.Draft && profile.status != ProfileStatus.Active) return
        if (current.affinityQuestionnaire.mutation != null) return
        if (current.affinityQuestionnaire.loading || current.affinityQuestionnaire.refreshing) return

        val retainedQuestionnaire = current.affinityQuestionnaire
            .takeIf { it.profileId == profile.id && it.catalog != null }
        val retainedSummary = current.affinityHomeSummary
            .takeIf { it.profileId == profile.id && it.catalog != null }
        val retainedCatalog = retainedQuestionnaire?.catalog ?: retainedSummary?.catalog
        val retainedAnswers = retainedQuestionnaire?.answers ?: retainedSummary?.answers.orEmpty()
        val activeMutationForProfile = activeMutation
            ?.takeIf {
                mutationJob?.isActive == true &&
                        it.profileId == profile.id &&
                        it.userId == current.session.user.id
            }
        if (activeMutationForProfile != null) {
            uiState.value = current.copy(
                affinityQuestionnaire = AffinityQuestionnaireUiState(
                    open = true,
                    profileId = profile.id,
                    destination = AffinityQuestionnaireDestination.Overview,
                    catalog = retainedCatalog,
                    answers = retainedAnswers,
                    loading = retainedCatalog == null,
                    refreshing = retainedCatalog != null,
                    mutation = activeMutationForProfile.mutation,
                ),
            )
            return
        }
        invalidateHomeSummaryLoadRequests()
        val requestId = ++loadRequestId
        val opening = current.copy(
            affinityQuestionnaire = AffinityQuestionnaireUiState(
                open = true,
                profileId = profile.id,
                destination = AffinityQuestionnaireDestination.Overview,
                catalog = retainedCatalog,
                answers = retainedAnswers,
                loading = retainedCatalog == null,
                refreshing = retainedCatalog != null,
            ),
        ).withStoppedHomeSummaryLoad(profile.id)
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
                destination = AffinityQuestionnaireDestination.Overview,
                loading = false,
                refreshing = false,
                mutation = null,
                error = null,
                mutationError = null,
                mutationFeedbackQuestionId = null,
                message = null,
            ),
        )
    }

    fun refresh() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = current.affinityQuestionnaire
        val profileId = questionnaire.profileId ?: return
        if (!questionnaire.open || questionnaire.loading || questionnaire.refreshing || questionnaire.mutation != null) return
        invalidateHomeSummaryLoadRequests()
        val requestId = ++loadRequestId
        uiState.value = current.copy(
            affinityQuestionnaire = questionnaire.copy(
                loading = questionnaire.catalog == null,
                refreshing = questionnaire.catalog != null,
                error = null,
                mutationError = null,
                mutationFeedbackQuestionId = null,
                message = null,
            ),
        ).withStoppedHomeSummaryLoad(profileId)
        scope.launch {
            installLoadResult(
                requestId = requestId,
                profileId = profileId,
                userId = current.session.user.id,
                result = loadSnapshot(),
            )
        }
    }

    fun openContinue() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = current.affinityQuestionnaire
        val catalog = questionnaire.catalog ?: return
        if (!questionnaire.open) return
        val question = catalog.firstUnansweredQuestion(questionnaire.answers) ?: return
        setDestination(
            current = current,
            destination = AffinityQuestionnaireDestination.Question(
                questionId = question.id,
                source = AffinityQuestionSource.Continue,
            ),
        )
    }

    fun openCategories() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        if (!current.affinityQuestionnaire.open) return
        setDestination(current, AffinityQuestionnaireDestination.Categories)
    }

    fun openReview() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        if (!current.affinityQuestionnaire.open) return
        setDestination(current, AffinityQuestionnaireDestination.Review)
    }

    fun openCategory(categoryId: String) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = current.affinityQuestionnaire
        val catalog = questionnaire.catalog ?: return
        if (!questionnaire.open) return
        val group = catalog.groupQuestionsForPresentation(questionnaire.answers)
            .firstOrNull { it.category.id == categoryId }
            ?: return
        val reviewAll = group.validAnsweredCount == group.totalQuestionCount
        val question = catalog.firstCategoryQuestion(
            categoryId = categoryId,
            answers = questionnaire.answers,
            reviewAll = reviewAll,
        ) ?: return
        setDestination(
            current = current,
            destination = AffinityQuestionnaireDestination.Question(
                questionId = question.id,
                source = AffinityQuestionSource.Category(
                    categoryId = categoryId,
                    reviewAll = reviewAll,
                ),
            ),
        )
    }

    fun openReviewedAnswer(questionId: String) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = current.affinityQuestionnaire
        val catalog = questionnaire.catalog ?: return
        if (!questionnaire.open) return
        val question = catalog.findAnswerableQuestion(questionId) ?: return
        if (question.currentValidAnswer(questionnaire.answers) == null) return
        setDestination(
            current = current,
            destination = AffinityQuestionnaireDestination.Question(
                questionId = question.id,
                source = AffinityQuestionSource.Review,
            ),
        )
    }

    fun skipQuestion() {
        moveFromCurrentQuestion(requireConfirmedAnswer = false)
    }

    fun nextQuestion() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = current.affinityQuestionnaire
        val catalog = questionnaire.catalog ?: return
        val destination =
            questionnaire.destination as? AffinityQuestionnaireDestination.Question
                ?: return

        if (questionnaire.mutation != null) return

        val question = catalog.findAnswerableQuestion(destination.questionId) ?: return
        val draftAnswerCode = questionnaire.draftAnswerCode
            ?.takeIf { questionnaire.draftQuestionId == question.id }
            ?: return

        if (!question.canSelectAnswerCode(draftAnswerCode)) return

        val confirmedAnswerCode =
            question.currentValidAnswer(questionnaire.answers)?.answerCode

        if (draftAnswerCode == confirmedAnswerCode) {
            moveFromCurrentQuestion(requireConfirmedAnswer = true)
            return
        }

        saveAnswerAndAdvance(
            current = current,
            questionId = question.id,
            answerCode = draftAnswerCode,
        )
    }

    private fun saveAnswerAndAdvance(
        current: RealsRootUiState.Ready,
        questionId: String,
        answerCode: String,
    ) {
        val questionnaire = current.affinityQuestionnaire
        val profileId = questionnaire.profileId ?: return
        val userId = current.session.user.id
        val requestId = ++mutationRequestId

        val mutation = AffinityAnswerMutationUiState(
            questionId = questionId,
            pendingAnswerCode = answerCode,
            requestId = requestId,
        )

        activeMutation = ActiveAffinityAnswerMutation(
            requestId = requestId,
            profileId = profileId,
            userId = userId,
            mutation = mutation,
        )

        uiState.value = current.copy(
            affinityQuestionnaire = questionnaire.copy(
                mutation = mutation,
                mutationError = null,
                mutationFeedbackQuestionId = null,
                message = null,
            ),
        )

        mutationJob = scope.launch {
            val completion = installMutationResult(
                profileId = profileId,
                userId = userId,
                mutation = mutation,
                result = dependencies.patchAnswer(questionId, answerCode),
            )

            clearActiveMutationIfCurrent(requestId)

            if (completion.shouldReload) {
                startLoadForOpenQuestionnaireAfterMutation(
                    profileId = profileId,
                    userId = userId,
                )
            }

            if (completion.successful) {
                val latest =
                    uiState.value as? RealsRootUiState.Ready

                val latestQuestion =
                    latest
                        ?.affinityQuestionnaire
                        ?.destination as? AffinityQuestionnaireDestination.Question

                /*
                 * Advance only if the user is still looking at the same question.
                 * A late completion must not move them after they navigated Back.
                 */
                if (
                    latest?.affinityQuestionnaire?.open == true &&
                    latest.affinityQuestionnaire.mutation == null &&
                    latestQuestion?.questionId == questionId
                ) {
                    moveFromCurrentQuestion(requireConfirmedAnswer = true)
                }
            }
        }
    }

    fun navigateBack() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        if (!current.affinityQuestionnaire.open) return
        when (val destination = current.affinityQuestionnaire.destination) {
            AffinityQuestionnaireDestination.Overview -> close()
            AffinityQuestionnaireDestination.Categories,
            AffinityQuestionnaireDestination.Review -> {
                setDestination(current, AffinityQuestionnaireDestination.Overview)
            }

            is AffinityQuestionnaireDestination.Question -> {
                val parent = when (destination.source) {
                    AffinityQuestionSource.Continue -> AffinityQuestionnaireDestination.Overview
                    is AffinityQuestionSource.Category -> AffinityQuestionnaireDestination.Categories
                    AffinityQuestionSource.Review -> AffinityQuestionnaireDestination.Review
                }
                setDestination(current, parent)
            }
        }
    }

    private fun moveFromCurrentQuestion(requireConfirmedAnswer: Boolean) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = current.affinityQuestionnaire
        val catalog = questionnaire.catalog ?: return
        val destination =
            questionnaire.destination as? AffinityQuestionnaireDestination.Question ?: return
        val question = catalog.findAnswerableQuestion(destination.questionId) ?: run {
            setDestination(current, destination.fallbackParent())
            return
        }
        if (requireConfirmedAnswer && question.currentValidAnswer(questionnaire.answers) == null) return
        if (questionnaire.mutation != null) return

        val next = when (val source = destination.source) {
            AffinityQuestionSource.Continue -> {
                catalog.nextContinueQuestion(question.id, questionnaire.answers)
                    ?.let { nextQuestion ->
                        AffinityQuestionnaireDestination.Question(
                            nextQuestion.id,
                            AffinityQuestionSource.Continue
                        )
                    } ?: AffinityQuestionnaireDestination.Overview
            }

            is AffinityQuestionSource.Category -> {
                catalog.nextCategoryQuestion(
                    categoryId = source.categoryId,
                    currentQuestionId = question.id,
                    answers = questionnaire.answers,
                    reviewAll = source.reviewAll,
                )?.let { nextQuestion ->
                    AffinityQuestionnaireDestination.Question(nextQuestion.id, source)
                } ?: AffinityQuestionnaireDestination.Categories
            }

            AffinityQuestionSource.Review -> AffinityQuestionnaireDestination.Review
        }
        setDestination(current, next)
    }

    private fun setDestination(
        current: RealsRootUiState.Ready,
        destination: AffinityQuestionnaireDestination,
    ) {
        val latest = uiState.value as? RealsRootUiState.Ready ?: return
        if (!latest.affinityQuestionnaire.open) return
        val retainedMutationError = latest.affinityQuestionnaire.mutationError
            ?.takeIf { error ->
                error.isLegalActionRequired() ||
                        error.isTerminalAuthFailure()
            }
        val destinationQuestion =
            destination as? AffinityQuestionnaireDestination.Question

        val draftAnswerCode = destinationQuestion
            ?.let { questionDestination ->
                latest.affinityQuestionnaire.catalog
                    ?.findAnswerableQuestion(questionDestination.questionId)
                    ?.currentValidAnswer(latest.affinityQuestionnaire.answers)
                    ?.answerCode
            }
        uiState.value = latest.copy(
            affinityQuestionnaire = latest.affinityQuestionnaire.copy(
                destination = destination,
                error = null,
                mutationError = retainedMutationError,
                mutationFeedbackQuestionId = null,
                message = null,
                draftQuestionId = destinationQuestion?.questionId,
                draftAnswerCode = draftAnswerCode,
            ),
        )
    }

    fun selectAnswer(questionId: String, answerCode: String) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = current.affinityQuestionnaire
        val catalog = questionnaire.catalog ?: return

        if (
            !questionnaire.open ||
            questionnaire.mutation != null ||
            questionnaire.loading ||
            questionnaire.refreshing ||
            mutationJob?.isActive == true
        ) {
            return
        }

        val question = catalog.questions
            .firstOrNull { it.id == questionId }
            ?: return

        if (!question.canSelectAnswerCode(answerCode)) return

        val presentedAnswerCode =
            questionnaire.draftAnswerCode
                .takeIf { questionnaire.draftQuestionId == question.id }
                ?: question.currentValidAnswer(questionnaire.answers)?.answerCode

        if (presentedAnswerCode == answerCode) return

        uiState.value = current.copy(
            affinityQuestionnaire = questionnaire.copy(
                draftQuestionId = question.id,
                draftAnswerCode = answerCode,
                mutationError = null,
                mutationFeedbackQuestionId = null,
                message = null,
            ),
        )
    }

    fun deleteAnswer(questionId: String) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = current.affinityQuestionnaire
        val catalog = questionnaire.catalog ?: return
        if (
            !questionnaire.open ||
            questionnaire.mutation != null ||
            questionnaire.loading ||
            questionnaire.refreshing ||
            mutationJob?.isActive == true
        ) {
            return
        }
        val question = catalog.questions.firstOrNull { it.id == questionId } ?: return
        if (question.currentValidAnswer(questionnaire.answers) == null) return

        val requestId = ++mutationRequestId

        val profileId = questionnaire.profileId ?: return
        val userId = current.session.user.id

        val mutation = AffinityAnswerMutationUiState(
            questionId = question.id,
            pendingAnswerCode = null,
            requestId = requestId,
        )

        activeMutation = ActiveAffinityAnswerMutation(
            requestId = requestId,
            profileId = profileId,
            userId = userId,
            mutation = mutation,
        )

        uiState.value = current.copy(
            affinityQuestionnaire = questionnaire.copy(
                mutation = mutation,
                mutationError = null,
                mutationFeedbackQuestionId = null,
                message = null,
            ),
        )

        mutationJob = scope.launch {
            val completion = installMutationResult(
                profileId = profileId,
                userId = userId,
                mutation = mutation,
                result = dependencies.deleteAnswer(question.id),
            )

            clearActiveMutationIfCurrent(requestId)

            if (completion.shouldReload) {
                startLoadForOpenQuestionnaireAfterMutation(
                    profileId = profileId,
                    userId = userId,
                )
            }
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
            is AffinityQuestionnaireLoadResult.Success -> {
                invalidateHomeSummaryLoadRequests()
                latest.copy(
                    affinityQuestionnaire = questionnaire.copy(
                        catalog = result.catalog,
                        answers = result.answers,
                        destination = result.catalog.reconciledDestination(
                            destination = questionnaire.destination,
                            answers = result.answers,
                        ),
                        loading = false,
                        refreshing = false,
                        error = null,
                        mutationError = null,
                        mutationFeedbackQuestionId = null,
                    ),
                    affinityHomeSummary = AffinityHomeSummaryUiState(
                        profileId = profileId,
                        catalog = result.catalog,
                        answers = result.answers,
                        loading = false,
                        loadAttempted = true,
                    ),
                )
            }

            is AffinityQuestionnaireLoadResult.Failure -> latest.copy(
                affinityQuestionnaire = questionnaire.copy(
                    loading = false,
                    refreshing = false,
                    error = result.error,
                ),
            )
        }
    }

    private fun installHomeSummaryLoadResult(
        requestId: Long,
        profileId: String,
        userId: String,
        result: AffinityQuestionnaireLoadResult,
    ) {
        val latest = uiState.value as? RealsRootUiState.Ready ?: return
        val summary = latest.affinityHomeSummary
        if (
            requestId != homeSummaryLoadRequestId ||
            summary.profileId != profileId ||
            latest.session.user.id != userId ||
            (latest.session.profileSnapshot as? ProfileSnapshot.Found)?.profile?.id != profileId
        ) {
            return
        }

        uiState.value = when (result) {
            is AffinityQuestionnaireLoadResult.Success -> latest.copy(
                affinityHomeSummary = summary.copy(
                    catalog = result.catalog,
                    answers = result.answers,
                    loading = false,
                    loadAttempted = true,
                ),
            )

            is AffinityQuestionnaireLoadResult.Failure -> latest.copy(
                affinityHomeSummary = summary.copy(
                    loading = false,
                    loadAttempted = true,
                ),
            )
        }
    }

    private fun installMutationResult(
        profileId: String,
        userId: String,
        mutation: AffinityAnswerMutationUiState,
        result: ApiResult<List<AffinityAnswer>>,
    ): AffinityMutationCompletion {
        val ignored = AffinityMutationCompletion(
            successful = false,
            shouldReload = false,
        )

        val latest = uiState.value as? RealsRootUiState.Ready
            ?: return ignored

        val questionnaire = latest.affinityQuestionnaire

        if (
            questionnaire.profileId != profileId ||
            latest.session.user.id != userId ||
            (latest.session.profileSnapshot as? ProfileSnapshot.Found)
                ?.profile
                ?.id != profileId
        ) {
            return ignored
        }

        if (
            questionnaire.open &&
            questionnaire.mutation?.requestId != mutation.requestId
        ) {
            return ignored
        }

        val successful = result is ApiResult.Success

        val shouldReloadAfterMutation =
            questionnaire.open &&
                    (questionnaire.loading || questionnaire.refreshing) &&
                    successful

        val showQuestionFeedback =
            questionnaire.open &&
                    (questionnaire.destination as? AffinityQuestionnaireDestination.Question)
                        ?.questionId == mutation.questionId

        /*
         * pendingAnswerCode == null identifies DELETE.
         * Successful deletion must not display "Respuesta guardada".
         */
        val deletingAnswer = mutation.pendingAnswerCode == null

        val showSavedFeedback =
            showQuestionFeedback && !deletingAnswer

        /*
         * Clear the local draft only when the successful DELETE still owns
         * the draft for the deleted question. Do not overwrite a draft belonging
         * to another question if the user navigated while the request was active.
         */
        val shouldClearDeletedDraft =
            deletingAnswer &&
                    questionnaire.draftQuestionId == mutation.questionId

        if (successful) {
            invalidateHomeSummaryLoadRequests()
        }

        uiState.value = when (result) {
            is ApiResult.Success -> latest.copy(
                affinityQuestionnaire = questionnaire.copy(
                    answers = result.value,
                    loading = false,
                    refreshing = false,
                    mutation = null,
                    mutationError = null,
                    mutationFeedbackQuestionId =
                        if (showSavedFeedback) mutation.questionId else null,
                    message =
                        if (showSavedFeedback) "Respuesta guardada" else null,
                    draftQuestionId =
                        if (shouldClearDeletedDraft) {
                            mutation.questionId
                        } else {
                            questionnaire.draftQuestionId
                        },
                    draftAnswerCode =
                        if (shouldClearDeletedDraft) {
                            null
                        } else {
                            questionnaire.draftAnswerCode
                        },
                ),
                affinityHomeSummary =
                    if (latest.affinityHomeSummary.profileId == profileId) {
                        latest.affinityHomeSummary.copy(
                            catalog = questionnaire.catalog ?: latest.affinityHomeSummary.catalog,
                            answers = result.value,
                            loading = false,
                            loadAttempted = true,
                        )
                    } else {
                        latest.affinityHomeSummary
                    },
            )

            is ApiResult.Failure -> latest.copy(
                affinityQuestionnaire = questionnaire.copy(
                    loading = false,
                    refreshing = false,
                    mutation = null,
                    mutationError = result.error,
                    mutationFeedbackQuestionId =
                        if (showQuestionFeedback) mutation.questionId else null,
                    message = null,
                ),
            )
        }

        return AffinityMutationCompletion(
            successful = successful,
            shouldReload = shouldReloadAfterMutation,
        )
    }

    private fun clearActiveMutationIfCurrent(requestId: Long) {
        if (activeMutation?.requestId == requestId) {
            activeMutation = null
        }
    }

    private fun startLoadForOpenQuestionnaireAfterMutation(profileId: String, userId: String) {
        val latest = uiState.value as? RealsRootUiState.Ready ?: return
        val questionnaire = latest.affinityQuestionnaire
        if (
            !questionnaire.open ||
            questionnaire.profileId != profileId ||
            questionnaire.mutation != null ||
            latest.session.user.id != userId ||
            (latest.session.profileSnapshot as? ProfileSnapshot.Found)?.profile?.id != profileId
        ) {
            return
        }
        invalidateHomeSummaryLoadRequests()
        val requestId = ++loadRequestId
        uiState.value = latest.copy(
            affinityQuestionnaire = questionnaire.copy(
                loading = questionnaire.catalog == null,
                refreshing = questionnaire.catalog != null,
                error = null,
                mutationError = null,
                mutationFeedbackQuestionId = null,
                message = null,
            ),
        ).withStoppedHomeSummaryLoad(profileId)
        scope.launch {
            installLoadResult(
                requestId = requestId,
                profileId = profileId,
                userId = userId,
                result = loadSnapshot(),
            )
        }
    }

    private fun invalidateHomeSummaryLoadRequests() {
        homeSummaryLoadRequestId += 1
    }

    private fun RealsRootUiState.Ready.withStoppedHomeSummaryLoad(profileId: String): RealsRootUiState.Ready =
        if (affinityHomeSummary.profileId == profileId && affinityHomeSummary.loading) {
            copy(
                affinityHomeSummary = affinityHomeSummary.copy(
                    loading = false,
                    loadAttempted = true,
                ),
            )
        } else {
            this
        }
}

private fun AffinityQuestionnaireDestination.Question.fallbackParent(): AffinityQuestionnaireDestination =
    when (source) {
        AffinityQuestionSource.Continue -> AffinityQuestionnaireDestination.Overview
        is AffinityQuestionSource.Category -> AffinityQuestionnaireDestination.Categories
        AffinityQuestionSource.Review -> AffinityQuestionnaireDestination.Review
    }

private data class ActiveAffinityAnswerMutation(
    val requestId: Long,
    val profileId: String,
    val userId: String,
    val mutation: AffinityAnswerMutationUiState,
)

private data class AffinityMutationCompletion(
    val successful: Boolean,
    val shouldReload: Boolean,
)

private sealed interface AffinityQuestionnaireLoadResult {
    data class Success(
        val catalog: AffinityQuestionCatalog,
        val answers: List<AffinityAnswer>,
    ) : AffinityQuestionnaireLoadResult

    data class Failure(val error: com.reals.app.core.network.ApiError) :
        AffinityQuestionnaireLoadResult
}
