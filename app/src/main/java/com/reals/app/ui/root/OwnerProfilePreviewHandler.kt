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
    val revalidating: Boolean = false,
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
        if (ownerSession != null && (!ownerSession!!.sameProfileOwner(session) || session?.supportsOwnerProfileContent() != true)) {
            generation++
            job?.cancel()
            ownerSession = null
            val ready = state.value as? RealsRootUiState.Ready
            if (ready != null && (!ready.session.sameProfileOwner(ready.ownerPreview.ownerSession) || !ready.session.supportsOwnerProfileContent())) {
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

    fun open() = load(background = false)

    fun onResume() = load(background = true)

    private fun load(background: Boolean) {
        val ready = state.value as? RealsRootUiState.Ready ?: return
        if (background && !ready.ownerPreview.open) return
        val owner = (ready.session.profileSnapshot as? ProfileSnapshot.Found)?.profile ?: return
        if (!ready.session.supportsOwnerProfileContent()) { close(); return }
        if (background && (ready.ownerPreview.loading || ready.ownerPreview.revalidating)) return
        val request = ++generation
        ownerSession = ready.session
        job?.cancel()
        val retained = ready.ownerPreview.takeIf { ready.session.sameProfileOwner(it.ownerSession) }
        state.value = ready.copy(ownerPreview = (retained ?: OwnerProfilePreviewState(ownerSession = ready.session))
            .copy(open = true, loading = !background || retained?.profile == null,
                revalidating = background && retained?.profile != null, error = null))
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
                    .copy(open = true, loading = false, revalidating = false, error = failure.error)
            } catch (failure: Exception) {
                (retained ?: OwnerProfilePreviewState(ownerSession = ready.session))
                    .copy(open = true, loading = false, revalidating = false, error = ApiError.Unexpected("No pudimos cargar el perfil."))
            }
            val latest = state.value as? RealsRootUiState.Ready ?: return@launch
            if (request != generation || !latest.ownerPreview.open || !latest.session.sameProfileOwner(ready.session)) return@launch
            if (result.error == null && latest.session.profileSnapshot != ready.session.profileSnapshot) {
                state.value = latest.copy(ownerPreview = latest.ownerPreview.copy(loading = false, revalidating = false))
                onResume()
                return@launch
            }
            state.value = latest.copy(ownerPreview = result.copy(ownerSession = latest.session,
                profile = if (result.error?.disallowsProfileSnapshot() == true) null else result.profile))
        }
    }
}

private class PreviewFailure(val error: ApiError) : Exception()
private fun <T> ApiResult<T>.valueOrThrow(): T = when (this) {
    is ApiResult.Success -> value
    is ApiResult.Failure -> throw PreviewFailure(error)
}
