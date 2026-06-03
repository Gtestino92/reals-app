package com.reals.app.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.data.repository.AuthOperationResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.di.AppContainer
import com.reals.app.domain.model.Profile
import com.reals.app.domain.model.ProfileActivationResult
import com.reals.app.domain.model.CreateProfileInput
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.domain.usecase.CompleteAndActivateProfileUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
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
    private val completeAndActivateProfileUseCase: CompleteAndActivateProfileUseCase,
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

    fun completeAndActivateProfile(profile: Profile) {
        val current = _uiState.value as? RealsRootUiState.Ready ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(activatingProfile = true, profileActivationError = null)
            when (val result = completeAndActivateProfileUseCase.invoke(profile)) {
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
                completeAndActivateProfileUseCase = appContainer.completeAndActivateProfileUseCase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}
