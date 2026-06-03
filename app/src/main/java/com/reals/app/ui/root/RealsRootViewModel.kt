package com.reals.app.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.AuthOperationResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.di.AppContainer
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfileActivationResult
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.model.UpdateProfileInput
import com.reals.app.domain.usecase.ActivateProfileUseCase
import com.reals.app.domain.usecase.AddMockProfilePhotoUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
import com.reals.app.domain.usecase.UpdateProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RealsRootUiState {
    data object Checking : RealsRootUiState
    data class MissingFirebase(val message: String) : RealsRootUiState
    data class Login(val loading: Boolean = false, val error: String? = null) : RealsRootUiState
    data class LoadingSession(val email: String?) : RealsRootUiState
    data class Ready(
        val session: ProvisionedSession,
        val creatingProfile: Boolean = false,
        val profileCreateError: ApiError? = null,
        val updatingProfile: Boolean = false,
        val profileUpdateError: ApiError? = null,
        val profileUpdateMessage: String? = null,
        val addingPhoto: Boolean = false,
        val photoActionError: ApiError? = null,
        val photoActionMessage: String? = null,
        val activatingProfile: Boolean = false,
        val profileActivationError: ApiError? = null,
    ) : RealsRootUiState
    data class ActivationComplete(
        val session: ProvisionedSession,
        val result: ProfileActivationResult,
    ) : RealsRootUiState
    data class Failure(val error: ApiError) : RealsRootUiState
}

class RealsRootViewModel(
    private val authRepository: FirebaseAuthRepository,
    private val provisionAndLoadProfile: ProvisionAndLoadProfileUseCase,
    private val createProfileUseCase: CreateProfileUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val addMockProfilePhotoUseCase: AddMockProfilePhotoUseCase,
    private val activateProfileUseCase: ActivateProfileUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RealsRootUiState>(RealsRootUiState.Checking)
    val uiState: StateFlow<RealsRootUiState> = _uiState.asStateFlow()

    init {
        refreshSession()
    }

    fun refreshSession() {
        if (!authRepository.isConfigured()) {
            _uiState.value = RealsRootUiState.MissingFirebase(FirebaseAuthRepository.firebaseMissingMessage)
            return
        }
        if (!authRepository.hasSignedInUser()) {
            _uiState.value = RealsRootUiState.Login()
            return
        }
        loadBackendSession()
    }

    fun signIn(email: String, password: String) {
        authenticate(email, password) { cleanEmail, cleanPassword ->
            authRepository.signIn(cleanEmail, cleanPassword)
        }
    }

    fun signUp(email: String, password: String) {
        authenticate(email, password) { cleanEmail, cleanPassword ->
            authRepository.signUp(cleanEmail, cleanPassword)
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.value = RealsRootUiState.Login()
    }

    fun createProfile(input: CreateProfileInput) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(creatingProfile = true, profileCreateError = null)
            when (val result = createProfileUseCase.invoke(input)) {
                is ApiResult.Success -> {
                    _uiState.value = current.copy(
                        session = current.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        creatingProfile = false,
                        profileCreateError = null,
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = current.copy(
                        creatingProfile = false,
                        profileCreateError = result.error,
                    )
                }
            }
        }
    }

    fun updateProfile(input: UpdateProfileInput) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(
                updatingProfile = true,
                profileUpdateError = null,
                profileUpdateMessage = null,
            )
            when (val result = updateProfileUseCase.invoke(input)) {
                is ApiResult.Success -> {
                    _uiState.value = current.copy(
                        session = current.session.copy(
                            profileSnapshot = ProfileSnapshot.Found(result.value),
                        ),
                        updatingProfile = false,
                        profileUpdateError = null,
                        profileUpdateMessage = "Perfil actualizado.",
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = current.copy(
                        updatingProfile = false,
                        profileUpdateError = result.error,
                    )
                }
            }
        }
    }

    fun addMockProfilePhoto(
        profile: Profile,
        position: Int,
        isPersonPhoto: Boolean,
        isFullBody: Boolean,
    ) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(
                addingPhoto = true,
                photoActionError = null,
                photoActionMessage = null,
                profileActivationError = null,
            )
            when (
                val result = addMockProfilePhotoUseCase.invoke(
                    profile = profile,
                    position = position,
                    isPersonPhoto = isPersonPhoto,
                    isFullBody = isFullBody,
                )
            ) {
                is ApiResult.Success -> {
                    val refreshedSession = provisionAndLoadProfile()
                    when (refreshedSession) {
                        is ApiResult.Success -> _uiState.value = RealsRootUiState.Ready(
                            session = refreshedSession.value,
                            addingPhoto = false,
                            photoActionMessage = "Foto ${result.value.position} agregada.",
                        )

                        is ApiResult.Failure -> _uiState.value = current.copy(
                            addingPhoto = false,
                            photoActionError = refreshedSession.error,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    _uiState.value = current.copy(
                        addingPhoto = false,
                        photoActionError = result.error,
                    )
                }
            }
        }
    }

    fun activateProfile() {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(activatingProfile = true, profileActivationError = null)
            when (val result = activateProfileUseCase.invoke()) {
                is ApiResult.Success -> {
                    val updatedSession = current.session.copy(
                        profileSnapshot = ProfileSnapshot.Found(result.value.profile),
                    )
                    _uiState.value = RealsRootUiState.ActivationComplete(
                        session = updatedSession,
                        result = result.value,
                    )
                }

                is ApiResult.Failure -> {
                    _uiState.value = current.copy(
                        activatingProfile = false,
                        profileActivationError = result.error,
                    )
                }
            }
        }
    }

    private fun authenticate(
        email: String,
        password: String,
        action: suspend (email: String, password: String) -> AuthOperationResult,
    ) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            _uiState.value = RealsRootUiState.Login(error = "Email y password son requeridos.")
            return
        }
        viewModelScope.launch {
            _uiState.value = RealsRootUiState.Login(loading = true)
            when (val result = action(cleanEmail, password)) {
                AuthOperationResult.Success -> loadBackendSession()
                is AuthOperationResult.Failure -> _uiState.value = RealsRootUiState.Login(error = result.message)
            }
        }
    }

    private fun loadBackendSession() {
        viewModelScope.launch {
            _uiState.value = RealsRootUiState.LoadingSession(authRepository.currentUserEmail())
            when (val result = provisionAndLoadProfile()) {
                is ApiResult.Success -> _uiState.value = RealsRootUiState.Ready(result.value)
                is ApiResult.Failure -> _uiState.value = RealsRootUiState.Failure(result.error)
            }
        }
    }
}

class RealsRootViewModelFactory(
    private val appContainer: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RealsRootViewModel::class.java)) {
            return RealsRootViewModel(
                authRepository = appContainer.authRepository,
                provisionAndLoadProfile = appContainer.provisionAndLoadProfileUseCase,
                createProfileUseCase = appContainer.createProfileUseCase,
                updateProfileUseCase = appContainer.updateProfileUseCase,
                addMockProfilePhotoUseCase = appContainer.addMockProfilePhotoUseCase,
                activateProfileUseCase = appContainer.activateProfileUseCase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}
