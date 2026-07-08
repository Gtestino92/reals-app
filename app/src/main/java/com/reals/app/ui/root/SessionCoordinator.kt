package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.isAccountDeleted
import com.reals.app.data.repository.AuthOperationResult
import com.reals.app.data.repository.ChangePasswordResult
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
        val loginState = uiState.value as? RealsRootUiState.Login
        if (cleanEmail.isBlank() || password.isBlank()) {
            uiState.value = RealsRootUiState.Login(
                error = "Email y password son requeridos.",
                passwordResetAvailableAtMillis = loginState?.passwordResetAvailableAtMillis,
            )
            return
        }
        scope.launch {
            uiState.value = RealsRootUiState.Login(
                loading = true,
                passwordResetAvailableAtMillis = loginState?.passwordResetAvailableAtMillis,
            )
            when (val result = authRepository.signUp(cleanEmail, password)) {
                AuthOperationResult.Success -> {
                    authRepository.sendEmailVerificationEmail()
                    loadBackendSession()
                }

                is AuthOperationResult.Failure -> uiState.value =
                    RealsRootUiState.Login(
                        error = result.message,
                        passwordResetAvailableAtMillis = loginState?.passwordResetAvailableAtMillis,
                    )
            }
        }
    }

    fun requestPasswordReset(email: String) {
        val current = uiState.value as? RealsRootUiState.Login ?: return
        if (current.loading || current.passwordResetLoading) return
        val nowMillis = System.currentTimeMillis()
        if (current.passwordResetAvailableAtMillis.isInFuture(nowMillis)) return

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
            val pending = current.copy(
                error = null,
                passwordResetLoading = true,
                passwordResetMessage = null,
                passwordResetAvailableAtMillis = nowMillis + PASSWORD_RESET_COOLDOWN_MILLIS,
            )
            uiState.value = pending
            when (authRepository.sendPasswordResetEmail(cleanEmail)) {
                PasswordResetResult.SentOrHandledGenerically -> uiState.value =
                    pending.copy(
                        passwordResetLoading = false,
                        passwordResetMessage = genericPasswordResetMessage,
                    )

                PasswordResetResult.InvalidEmailFormat -> uiState.value =
                    pending.copy(
                        error = invalidPasswordResetEmailMessage,
                        passwordResetLoading = false,
                        passwordResetMessage = null,
                    )

                PasswordResetResult.SilentFailure -> uiState.value = pending.copy(
                    passwordResetLoading = false,
                    passwordResetMessage = null,
                )
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
        uiState.value = RealsRootUiState.Login()
    }

    fun deleteAccount() {
        when (val current = uiState.value) {
            is RealsRootUiState.Ready -> deleteAccountFromReady(current)
            is RealsRootUiState.LegalRequirements -> deleteAccountFromLegal(current)
            else -> return
        }
    }

    private fun deleteAccountFromReady(current: RealsRootUiState.Ready) {
        if (current.changingPassword) return

        scope.launch {
            uiState.value = current.copy(
                account = current.account.copy(
                    deletingAccount = true,
                    accountDeleteError = null,
                    changePasswordError = null,
                    changePasswordMessage = null,
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

    private fun deleteAccountFromLegal(current: RealsRootUiState.LegalRequirements) {
        if (current.loading || current.deletingAccount || current.submittingDocumentType != null) return

        scope.launch {
            uiState.value = current.copy(
                deletingAccount = true,
                accountDeleteError = null,
            )

            when (val result = deleteAccountUseCase()) {
                is ApiResult.Success -> {
                    uiState.value = RealsRootUiState.AccountDeletionScheduled(
                        deletionFinalizesAt = result.value.deletionFinalizesAt,
                    )
                }

                is ApiResult.Failure -> {
                    uiState.value = current.copy(
                        deletingAccount = false,
                        accountDeleteError = result.error,
                    )
                }
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        val current = uiState.value as? RealsRootUiState.Ready ?: return
        if (current.changingPassword || current.deletingAccount) return

        scope.launch {
            val pending = current.copy(
                account = current.account.copy(
                    changingPassword = true,
                    changePasswordError = null,
                    changePasswordMessage = null,
                    accountDeleteError = null,
                ),
            )
            uiState.value = pending

            val result = authRepository.changePassword(
                currentPassword = currentPassword,
                newPassword = newPassword,
            )
            uiState.value = pending.copy(
                account = pending.account.copy(
                    changingPassword = false,
                    changePasswordMessage = if (result == ChangePasswordResult.Success) {
                        "Contraseña actualizada."
                    } else {
                        null
                    },
                    changePasswordError = result.toChangePasswordMessageOrNull(),
                ),
            )
        }
    }

    fun reactivateAccount() {
        val current = uiState.value as? RealsRootUiState.AccountDeletionPending ?: return

        scope.launch {
            uiState.value = current.copy(reactivating = true, error = null)
            when (val result = reactivateAccountUseCase()) {
                is ApiResult.Success -> {
                    val session = loadProvisionedSessionForActiveUser(result.value) ?: return@launch
                    registerPushTokenBestEffort()
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
        val loginState = uiState.value as? RealsRootUiState.Login
        if (cleanEmail.isBlank() || password.isBlank()) {
            uiState.value = RealsRootUiState.Login(
                error = "Email y password son requeridos.",
                passwordResetAvailableAtMillis = loginState?.passwordResetAvailableAtMillis,
            )
            return
        }
        scope.launch {
            uiState.value = RealsRootUiState.Login(
                loading = true,
                passwordResetAvailableAtMillis = loginState?.passwordResetAvailableAtMillis,
            )
            when (val result = action(cleanEmail, password)) {
                AuthOperationResult.Success -> loadBackendSession()
                is AuthOperationResult.Failure -> uiState.value =
                    RealsRootUiState.Login(
                        error = result.message,
                        passwordResetAvailableAtMillis = loginState?.passwordResetAvailableAtMillis,
                    )
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
        const val PASSWORD_RESET_COOLDOWN_MILLIS = 60_000L
        const val invalidPasswordResetEmailMessage = "Ingresá un email válido."
        const val genericPasswordResetMessage =
            "Si el email está registrado, te enviamos instrucciones para recuperar el acceso."
    }
}

private fun Long?.isInFuture(nowMillis: Long): Boolean = this != null && nowMillis < this

private fun ChangePasswordResult.toChangePasswordMessageOrNull(): String? = when (this) {
    ChangePasswordResult.Success -> null
    ChangePasswordResult.PasswordProviderUnavailable ->
        "El cambio de contraseña no está disponible para este método de inicio de sesión."
    ChangePasswordResult.WrongCurrentPassword -> "La contraseña actual no es correcta."
    ChangePasswordResult.WeakNewPassword -> "La nueva contraseña es demasiado débil."
    ChangePasswordResult.InvalidNewPassword -> "La nueva contraseña no tiene un formato válido."
    ChangePasswordResult.NotSignedIn -> "Tu sesión necesita renovarse. Volvé a iniciar sesión."
    ChangePasswordResult.MissingEmail -> "No pudimos confirmar tu email de sesión. Volvé a iniciar sesión."
    ChangePasswordResult.Failure -> "No pudimos cambiar la contraseña. Intentá nuevamente."
}
