package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.domain.model.*
import com.reals.app.ui.profile.ownerProfilePreview
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

data class OwnerProfilePreviewState(
    val ownerSession: ProvisionedSession? = null,
    val open: Boolean = false,
    val loading: Boolean = false,
    val profile: VisualProfile? = null,
    val draft: Boolean = false,
    val error: ApiError? = null,
)

internal class OwnerProfilePreviewHandler(
    private val state: MutableStateFlow<RealsRootUiState>,
    private val scope: CoroutineScope,
    private val loadProfile: suspend (BackendUser) -> ApiResult<ProvisionedSession>,
    private val loadPhotos: suspend () -> ApiResult<List<ProfilePhoto>>,
    private val loadCatalog: suspend () -> ApiResult<ProfileQuestionCatalog>,
    private val loadAnswers: suspend () -> ApiResult<List<ProfileQuestionAnswer>>,
) {
    private var generation = 0L
    private var job: Job? = null
    private var ownerSession: ProvisionedSession? = null

    fun cancelForSessionReplacement(session: ProvisionedSession?) {
        if (ownerSession != null && ownerSession !== session) {
            generation++
            job?.cancel()
            ownerSession = null
            val ready = state.value as? RealsRootUiState.Ready
            if (ready != null && ready.ownerPreview.ownerSession !== ready.session) {
                state.value = ready.copy(ownerPreview = OwnerProfilePreviewState())
            }
        }
    }

    fun close() {
        generation++
        job?.cancel()
        val ready = state.value as? RealsRootUiState.Ready ?: return
        state.value = ready.copy(ownerPreview = OwnerProfilePreviewState())
    }

    fun open() {
        val ready = state.value as? RealsRootUiState.Ready ?: return
        val owner = (ready.session.profileSnapshot as? ProfileSnapshot.Found)?.profile ?: return
        if (owner.status !in listOf(ProfileStatus.Active, ProfileStatus.Draft)) return
        val request = ++generation
        ownerSession = ready.session
        job?.cancel()
        val retained = ready.ownerPreview.takeIf { it.ownerSession === ready.session }
        state.value = ready.copy(ownerPreview = (retained ?: OwnerProfilePreviewState(ownerSession = ready.session))
            .copy(open = true, loading = true, error = null))
        job = scope.launch {
            val result = try {
                coroutineScope {
                    val profile = async { loadProfile(ready.session.user).valueOrThrow() }
                    val photos = async { loadPhotos().valueOrThrow() }
                    val catalog = async { loadCatalog().valueOrThrow() }
                    val answers = async { loadAnswers().valueOrThrow() }
                    val confirmed = (profile.await().profileSnapshot as? ProfileSnapshot.Found)?.profile
                        ?: throw PreviewFailure(ApiError.Unexpected("Perfil no disponible"))
                    if (confirmed.id != owner.id || confirmed.userId != ready.session.user.id ||
                        confirmed.status !in listOf(ProfileStatus.Active, ProfileStatus.Draft)) {
                        throw PreviewFailure(ApiError.Unexpected("Perfil no disponible"))
                    }
                    OwnerProfilePreviewState(ownerSession = ready.session, open = true, profile = ownerProfilePreview(
                        confirmed, photos.await(), catalog.await(), answers.await(),
                    ), draft = confirmed.status == ProfileStatus.Draft)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: PreviewFailure) {
                (retained ?: OwnerProfilePreviewState(ownerSession = ready.session))
                    .copy(open = true, loading = false, error = failure.error)
            } catch (failure: Exception) {
                (retained ?: OwnerProfilePreviewState(ownerSession = ready.session))
                    .copy(open = true, loading = false, error = ApiError.Unexpected("No pudimos cargar el perfil."))
            }
            val latest = state.value as? RealsRootUiState.Ready ?: return@launch
            if (request != generation || !latest.ownerPreview.open || latest.session !== ready.session) return@launch
            state.value = latest.copy(ownerPreview = result)
        }
    }
}

private class PreviewFailure(val error: ApiError) : Exception()
private fun <T> ApiResult<T>.valueOrThrow(): T = when (this) {
    is ApiResult.Success -> value
    is ApiResult.Failure -> throw PreviewFailure(error)
}
