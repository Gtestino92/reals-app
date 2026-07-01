package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.isAccountDeleted
import com.reals.app.data.repository.AuthOperationResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.PasswordResetResult
import com.reals.app.data.repository.isLocallyValidEmail
import com.reals.app.di.AccountFeatureDependencies
import com.reals.app.di.SessionFeatureDependencies
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.BackendUserStatus
import com.reals.app.domain.model.ProvisionedSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the root session and account bootstrap flow.
 *
 * The coordinator writes session/account loading states directly into the shared
 * root UI state, then delegates active provisioned sessions back to the root
 * ViewModel so existing Home/Profile routing remains unchanged.
 */
internal class SessionCoordinator(
    private val uiState: MutableStateFlow<RealsRootUiState>,
    private val dependencies: SessionFeatureDependencies,
    private val accountDependencies: AccountFeatureDependencies,
    private val scope: CoroutineScope,
    private val onActiveSessionLoaded: suspend (ProvisionedSession) -> Unit,
    private val onReactivatedSessionLoaded: suspend (ProvisionedSession) -> Unit,
) {
    private val authRepository = dependencies.authRepository
    private val provisionAndLoadProfile = dependencies.provisionAndLoadProfile
    private val getMeUseCase = dependencies.getMe
    private val pushTokenRegistrationService = dependencies.pushTokenRegistrationService
    private val reactivateAccountUseCase = accountDependencies.reactivateAccount
    private val deleteAccountUseCase = accountDependencies.deleteAccount

    fun refreshSession() {
        if (!authRepository.isConfigured()) {
            uiState.value =
                RealsRootUiState.MissingFirebase(FirebaseAuthRepository.firebaseMissingMessage)
            return
        }
        if (!authRepository.hasSignedInUser()) {
            uiState.value = RealsRootUiState.Login()
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
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            uiState.value = RealsRootUiState.Login(error = "Email y password son requeridos.")
            return
        }
        scope.launch {
            uiState.value = RealsRootUiState.Login(loading = true)
            when (val result = authRepository.signUp(cleanEmail, password)) {
                AuthOperationResult.Success -> {
                    authRepository.sendEmailVerificationEmail()
                    loadBackendSession()
                }

                is AuthOperationResult.Failure -> uiState.value =
                    RealsRootUiState.Login(error = result.message)
            }
        }
    }

    fun requestPasswordReset(email: String) {
        val current = uiState.value as? RealsRootUiState.Login ?: return
        if (current.loading || current.passwordResetLoading) return

        val cleanEmail = email.trim()
        if (!isLocallyValidEmail(cleanEmail)) {
            uiState.value = current.copy(
                error = invalidPasswordResetEmailMessage,
                passwordResetLoading = false,
                passwordResetMessage = null,
            )
            return
        }

        scope.launch {
            uiState.value = current.copy(
                error = null,
                passwordResetLoading = true,
                passwordResetMessage = null,
            )
            when (authRepository.sendPasswordResetEmail(cleanEmail)) {
                PasswordResetResult.SentOrHandledGenerically -> uiState.value =
                    RealsRootUiState.Login(
                        passwordResetMessage = genericPasswordResetMessage,
                    )

                PasswordResetResult.InvalidEmailFormat -> uiState.value =
                    RealsRootUiState.Login(error = invalidPasswordResetEmailMessage)

                PasswordResetResult.SilentFailure -> uiState.value = RealsRootUiState.Login()
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
        uiState.value = RealsRootUiState.Login()
    }

    fun deleteAccount() {
        val current = uiState.value as? RealsRootUiState.Ready ?: return

        scope.launch {
            uiState.value = current.copy(
                account = current.account.copy(
                    deletingAccount = true,
                    accountDeleteError = null,
                ),
            )

            when (val result = deleteAccountUseCase()) {
                is ApiResult.Success -> {
                    uiState.value = RealsRootUiState.AccountDeletionScheduled(
                        deletionFinalizesAt = result.value.deletionFinalizesAt,
                    )
                }

                is ApiResult.Failure -> {
                    uiState.value = current.copy(
                        account = current.account.copy(
                            deletingAccount = false,
                            accountDeleteError = result.error,
                        ),
                    )
                }
            }
        }
    }

    fun reactivateAccount() {
        val current = uiState.value as? RealsRootUiState.AccountDeletionPending ?: return

        scope.launch {
            uiState.value = current.copy(reactivating = true, error = null)
            when (val result = reactivateAccountUseCase()) {
                is ApiResult.Success -> {
                    val session = loadProvisionedSessionForActiveUser(result.value) ?: return@launch
                    onReactivatedSessionLoaded(session)
                }

                is ApiResult.Failure -> uiState.value = current.copy(
                    reactivating = false,
                    error = result.error,
                )
            }
        }
    }

    fun loadBackendSession() {
        scope.launch {
            uiState.value = RealsRootUiState.LoadingSession(authRepository.currentUserEmail())
            when (val userResult = getMeUseCase()) {
                is ApiResult.Success -> when (userResult.value.status) {
                    BackendUserStatus.Active -> loadBackendSessionForActiveUser(userResult.value)
                    BackendUserStatus.Deleted -> uiState.value =
                        RealsRootUiState.AccountDeletionPending(
                            user = userResult.value,
                        )

                    is BackendUserStatus.Unknown -> uiState.value = RealsRootUiState.Failure(
                        ApiError.Unexpected("No pudimos leer el estado de tu cuenta.")
                    )
                }

                is ApiResult.Failure -> {
                    val backend = userResult.error as? ApiError.Backend
                    if (backend.shouldProvisionAfterGetMeFailure()) {
                        provisionAndLoadBackendSession()
                    } else {
                        handleSessionLoadFailure(userResult.error)
                    }
                }
            }
        }
    }

    suspend fun loadBackendSessionForActiveUser(user: BackendUser) {
        loadProvisionedSessionForActiveUser(user)?.let { session ->
            registerPushTokenBestEffort()
            onActiveSessionLoaded(session)
        }
    }

    suspend fun showAccountDeletionPendingFromBackend() {
        when (val userResult = getMeUseCase()) {
            is ApiResult.Success -> when (userResult.value.status) {
                BackendUserStatus.Deleted -> uiState.value =
                    RealsRootUiState.AccountDeletionPending(userResult.value)

                BackendUserStatus.Active -> loadBackendSessionForActiveUser(userResult.value)
                is BackendUserStatus.Unknown -> uiState.value = RealsRootUiState.Failure(
                    ApiError.Unexpected("No pudimos leer el estado de tu cuenta.")
                )
            }

            is ApiResult.Failure -> {
                authRepository.signOut()
                uiState.value = RealsRootUiState.Login(
                    error = "La cuenta esta pendiente de eliminacion. Volve a iniciar sesion para recuperarla."
                )
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
            uiState.value = RealsRootUiState.Login(error = "Email y password son requeridos.")
            return
        }
        scope.launch {
            uiState.value = RealsRootUiState.Login(loading = true)
            when (val result = action(cleanEmail, password)) {
                AuthOperationResult.Success -> loadBackendSession()
                is AuthOperationResult.Failure -> uiState.value =
                    RealsRootUiState.Login(error = result.message)
            }
        }
    }

    private suspend fun provisionAndLoadBackendSession() {
        when (val result = provisionAndLoadProfile()) {
            is ApiResult.Success -> {
                registerPushTokenBestEffort()
                onActiveSessionLoaded(result.value)
            }

            is ApiResult.Failure -> handleSessionLoadFailure(result.error)
        }
    }

    private suspend fun loadProvisionedSessionForActiveUser(user: BackendUser): ProvisionedSession? {
        return when (val result = provisionAndLoadProfile.loadProfileFor(user)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> {
                handleSessionLoadFailure(result.error)
                null
            }
        }
    }

    private suspend fun handleSessionLoadFailure(error: ApiError) {
        if (error.isAccountDeleted()) {
            showAccountDeletionPendingFromBackend()
            return
        }

        uiState.value = RealsRootUiState.Failure(error)
    }

    private fun ApiError.Backend?.shouldProvisionAfterGetMeFailure(): Boolean {
        if (this == null) return false
        return statusCode == 404 || statusCode == 403
    }

    private fun registerPushTokenBestEffort() {
        scope.launch {
            pushTokenRegistrationService.registerCurrentTokenIfPossible()
        }
    }

    private companion object {
        const val invalidPasswordResetEmailMessage = "Ingresá un email válido."
        const val genericPasswordResetMessage =
            "Si existe una cuenta con ese email, te enviamos instrucciones para recuperar la contraseña."
    }
}
