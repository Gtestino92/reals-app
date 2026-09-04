package com.reals.app.ui.root

import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ApiResult
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.core.network.isAccountBanned
import com.reals.app.core.network.isAccountDeleted
import com.reals.app.core.network.isTerminalAuthFailure
import com.reals.app.data.repository.AuthOperationResult
import com.reals.app.data.repository.ChangePasswordResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.PasswordResetResult
import com.reals.app.data.repository.isLocallyValidEmail
import com.reals.app.di.AccountFeatureDependencies
import com.reals.app.di.SessionFeatureDependencies
import com.reals.app.domain.model.BackendUser
import com.reals.app.domain.model.BackendUserStatus
import com.reals.app.domain.model.PermanentBanAppealState
import com.reals.app.domain.model.PermanentBanAppealStatus
import com.reals.app.domain.model.ProvisionedSession
import com.reals.app.ui.auth.GoogleCredentialResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val getPermanentBanAppealUseCase = dependencies.getPermanentBanAppeal
    private val submitPermanentBanAppealUseCase = dependencies.submitPermanentBanAppeal
    private val pushTokenRegistrationService = dependencies.pushTokenRegistrationService
    private val localFirebaseEmailVerificationCoordinator =
        dependencies.localFirebaseEmailVerificationCoordinator
    private val requestPasswordResetUseCase = dependencies.requestPasswordReset
    private val clearLocalSessionUseCase = dependencies.clearLocalSession
    private val reactivateAccountUseCase = accountDependencies.reactivateAccount
    private val deleteAccountUseCase = accountDependencies.deleteAccount
    private val finalizeAccountDeletionUseCase = accountDependencies.finalizeAccountDeletion
    private var refreshSessionJob: Job? = null
    private var appealJob: Job? = null
    private var appealRequestSequence = 0L
    private var googleAttemptSequence = 0L
    private var passwordResetAttemptSequence = 0L

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
        if (refreshSessionJob?.isActive == true) return
        refreshSessionJob = loadBackendSession()
    }

    fun signIn(email: String, password: String) {
        authenticate(email, password) { cleanEmail, cleanPassword ->
            authRepository.signIn(cleanEmail, cleanPassword)
        }
    }

    fun signUp(email: String, password: String) {
        val cleanEmail = email.trim()
        val loginState = uiState.value as? RealsRootUiState.Login ?: return
        if (loginState.loading || loginState.googleLoading || loginState.passwordResetLoading) return
        if (cleanEmail.isBlank() || password.isBlank()) {
            uiState.value = loginState.copy(
                loading = false,
                googleLoading = false,
                googleAttemptId = null,
                error = "Email y password son requeridos.",
                passwordResetMessage = null,
            )
            return
        }
        scope.launch {
            uiState.value = loginState.copy(
                loading = true,
                googleLoading = false,
                googleAttemptId = null,
                error = null,
                passwordResetMessage = null,
            )
            when (val result = authRepository.signUp(cleanEmail, password)) {
                AuthOperationResult.Success -> {
                    if (!dependencies.localFirebaseEmailAutoVerificationEnabled) {
                        authRepository.sendEmailVerificationEmail()
                    }
                    loadBackendSession().join()
                }

                is AuthOperationResult.Failure -> uiState.value =
                    loginState.copy(
                        loading = false,
                        googleLoading = false,
                        googleAttemptId = null,
                        error = result.message,
                        passwordResetMessage = null,
                    )
            }
        }
    }

    fun requestPasswordReset(email: String) {
        val current = uiState.value as? RealsRootUiState.Login ?: return
        if (current.loading || current.googleLoading || current.passwordResetLoading) return
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

        val attemptId = ++passwordResetAttemptSequence
        uiState.value = current.copy(
            error = null,
            passwordResetLoading = true,
            passwordResetAttemptId = attemptId,
            passwordResetMessage = null,
            passwordResetAvailableAtMillis = nowMillis + PASSWORD_RESET_COOLDOWN_MILLIS,
        )
        scope.launch {
            when (requestPasswordResetUseCase(cleanEmail)) {
                PasswordResetResult.SentOrHandledGenerically ->
                    uiState.value.passwordResetStateFor(attemptId)?.let { latest ->
                        uiState.value = latest.copy(
                            passwordResetLoading = false,
                            passwordResetAttemptId = null,
                            passwordResetMessage = genericPasswordResetMessage,
                        )
                    }

                PasswordResetResult.InvalidEmailFormat ->
                    uiState.value.passwordResetStateFor(attemptId)?.let { latest ->
                        uiState.value = latest.copy(
                            error = invalidPasswordResetEmailMessage,
                            passwordResetLoading = false,
                            passwordResetAttemptId = null,
                            passwordResetMessage = null,
                        )
                    }

                PasswordResetResult.SilentFailure -> uiState.value.passwordResetStateFor(attemptId)?.let { latest ->
                    uiState.value = latest.copy(
                        passwordResetLoading = false,
                        passwordResetAttemptId = null,
                        passwordResetMessage = null,
                    )
                }
            }
        }
    }

    fun signOut() {
        scope.launch {
            clearLocalSessionAndShowLogin()
        }
    }

    fun invalidateTerminalSession() {
        scope.launch {
            clearLocalSessionAndShowLogin(
                error = "Tu sesión terminó. Volvé a iniciar sesión.",
            )
        }
    }

    fun invalidateAccountBannedSession(error: ApiError) {
        scope.launch {
            handleAccountBannedError(error)
        }
    }

    fun retryAccountSuspension() {
        val current = uiState.value as? RealsRootUiState.AccountSuspended ?: return
        if (current.retrying || current.suspension !is AccountSuspension.Temporary) return
        if (refreshSessionJob?.isActive == true) return
        refreshSessionJob = scope.launch {
            uiState.value = current.copy(retrying = true, retryError = null)
            loadBackendSessionFromCurrentAuth(
                showLoadingState = false,
                onFailure = { error ->
                    uiState.value = error.accountSuspendedState()
                        ?: current.copy(retrying = false, retryError = error)
                },
                clearAssociationConflict = false,
            )
        }
    }

    fun refreshPermanentBanAppeal() {
        val current = uiState.value as? RealsRootUiState.PermanentBanAppeal ?: return
        if (current.loading || current.submitting) return
        if (appealJob?.isActive == true) return
        appealJob = scope.launch {
            loadPermanentBanAppealFromBackend(current.copy(loading = true, error = null, normalBootstrapError = null))
        }
    }

    fun retryApprovedAppealBootstrap() {
        val current = uiState.value as? RealsRootUiState.PermanentBanAppeal ?: return
        if (current.loading || current.submitting) return
        if (current.appeal?.isApprovedInactive() != true) return
        if (refreshSessionJob?.isActive == true) return
        refreshSessionJob = scope.launch {
            uiState.value = current.copy(loading = true, error = null, normalBootstrapError = null)
            bootstrapAfterApprovedAppeal(current.appeal)
        }
    }

    fun submitPermanentBanAppeal(statement: String) {
        val current = uiState.value as? RealsRootUiState.PermanentBanAppeal ?: return
        val appeal = current.appeal ?: return
        if (current.loading || current.submitting || appealJob?.isActive == true) return
        if (appeal.status != PermanentBanAppealStatus.Available || !appeal.banActive) return
        val trimmedStatement = statement.trim()
        if (trimmedStatement.isBlank() || trimmedStatement.length > PERMANENT_BAN_APPEAL_MAX_LENGTH) return

        val requestId = ++appealRequestSequence
        appealJob = scope.launch {
            val pending = current.copy(
                submitting = true,
                error = null,
                normalBootstrapError = null,
                requestId = requestId,
            )
            uiState.value = pending
            when (val result = submitPermanentBanAppealUseCase(trimmedStatement)) {
                is ApiResult.Success -> reconcilePermanentBanAppealAfterSubmit(requestId)
                is ApiResult.Failure -> {
                    val backend = result.error as? ApiError.Backend
                    if (backend?.backendErrorCode == BackendErrorCode.PenaltyAppealAlreadySubmitted) {
                        reconcilePermanentBanAppealAfterSubmit(requestId)
                    } else {
                        uiState.value.permanentBanAppealStateFor(requestId)?.let { latest ->
                            uiState.value = latest.copy(submitting = false, error = result.error)
                        }
                    }
                }
            }
        }
    }

    fun beginGoogleSignIn(): Long? {
        val current = uiState.value as? RealsRootUiState.Login ?: return null
        if (current.loading || current.googleLoading || current.passwordResetLoading) return null
        val attemptId = ++googleAttemptSequence
        uiState.value = current.copy(
            googleLoading = true,
            googleAttemptId = attemptId,
            error = null,
            passwordResetMessage = null,
        )
        return attemptId
    }

    fun completeGoogleSignIn(attemptId: Long, result: GoogleCredentialResult) {
        val current = uiState.value.googleLoginStateFor(attemptId) ?: return
        when (result) {
            GoogleCredentialResult.Cancelled -> uiState.value = current.copy(
                googleLoading = false,
                googleAttemptId = null,
            )

            GoogleCredentialResult.NotConfigured -> uiState.value = current.copy(
                googleLoading = false,
                googleAttemptId = null,
                error = "Google Sign-In no está configurado para este entorno.",
            )

            GoogleCredentialResult.Failure -> uiState.value = current.copy(
                googleLoading = false,
                googleAttemptId = null,
                error = "No pudimos iniciar sesión con Google. Intentá nuevamente.",
            )

            is GoogleCredentialResult.Success -> signInWithGoogleIdToken(attemptId, result.idToken)
        }
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
                    clearLocalSessionUseCase()
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
                    clearLocalSessionUseCase()
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
        if (!current.session.user.passwordManagementAllowed) {
            uiState.value = current.copy(
                account = current.account.copy(
                    changePasswordError = "El cambio de contraseña no está disponible para este método de inicio de sesión.",
                    changePasswordMessage = null,
                ),
            )
            return
        }

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
            if (result == ChangePasswordResult.NotSignedIn) {
                clearLocalSessionAndShowLogin(
                    error = "Tu sesión terminó. Volvé a iniciar sesión.",
                )
                return@launch
            }
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
        if (current.reactivating || current.finalizingDeletion) return

        scope.launch {
            uiState.value = current.copy(reactivating = true, error = null)
            when (val result = reactivateAccountUseCase()) {
                is ApiResult.Success -> {
                    val session = loadProvisionedSessionForActiveUser(result.value) ?: return@launch
                    finalizeActiveSession(
                        session = session,
                        onLoaded = onReactivatedSessionLoaded,
                    )
                }

                is ApiResult.Failure -> uiState.value = current.copy(
                    reactivating = false,
                    error = result.error,
                )
            }
        }
    }

    fun finalizeAccountDeletion() {
        val current = uiState.value as? RealsRootUiState.AccountDeletionPending ?: return
        if (current.reactivating || current.finalizingDeletion) return

        scope.launch {
            uiState.value = current.copy(finalizingDeletion = true, error = null)
            when (val result = finalizeAccountDeletionUseCase()) {
                is ApiResult.Success -> {
                    clearLocalSessionAndShowLogin()
                }

                is ApiResult.Failure -> uiState.value = current.copy(
                    finalizingDeletion = false,
                    error = result.error,
                )
            }
        }
    }

    fun loadBackendSession(): Job {
        return scope.launch {
            loadBackendSessionFromCurrentAuth(
                showLoadingState = true,
                onFailure = ::handleSessionLoadFailure,
                clearAssociationConflict = true,
            )
        }
    }

    private suspend fun loadBackendSessionFromCurrentAuth(
        showLoadingState: Boolean,
        onFailure: suspend (ApiError) -> Unit,
        clearAssociationConflict: Boolean,
    ) {
        if (showLoadingState) {
            uiState.value = RealsRootUiState.LoadingSession(authRepository.currentUserEmail())
        }
        when (val userResult = getMeUseCase()) {
            is ApiResult.Success -> when (userResult.value.status) {
                BackendUserStatus.Active -> loadBackendSessionForActiveUser(userResult.value, onFailure)
                BackendUserStatus.Deleted -> uiState.value =
                    RealsRootUiState.AccountDeletionPending(
                        user = userResult.value,
                    )

                is BackendUserStatus.Unknown -> onFailure(
                    ApiError.Unexpected("No pudimos leer el estado de tu cuenta.")
                )
            }

            is ApiResult.Failure -> {
                val backend = userResult.error as? ApiError.Backend
                if (backend.shouldProvisionAfterGetMeFailure()) {
                    provisionAndLoadBackendSession(onFailure, clearAssociationConflict)
                } else {
                    onFailure(userResult.error)
                }
            }
        }
    }

    suspend fun loadBackendSessionForActiveUser(
        user: BackendUser,
        onFailure: suspend (ApiError) -> Unit = ::handleSessionLoadFailure,
    ) {
        loadProvisionedSessionForActiveUser(user, onFailure)?.let { session ->
            finalizeActiveSession(
                session = session,
                onLoaded = onActiveSessionLoaded,
                onFailure = onFailure,
            )
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
                if (userResult.error.isAccountBanned()) {
                    handleAccountBannedError(userResult.error)
                } else if (userResult.error.isTerminalAuthFailure()) {
                    invalidateTerminalSession()
                } else {
                    uiState.value = RealsRootUiState.Failure(userResult.error)
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
        val loginState = uiState.value as? RealsRootUiState.Login ?: return
        if (loginState.loading || loginState.googleLoading || loginState.passwordResetLoading) return
        if (cleanEmail.isBlank() || password.isBlank()) {
            uiState.value = loginState.copy(
                loading = false,
                googleLoading = false,
                googleAttemptId = null,
                error = "Email y password son requeridos.",
                passwordResetMessage = null,
            )
            return
        }
        scope.launch {
            uiState.value = loginState.copy(
                loading = true,
                googleLoading = false,
                googleAttemptId = null,
                error = null,
                passwordResetMessage = null,
            )
            when (val result = action(cleanEmail, password)) {
                AuthOperationResult.Success -> loadBackendSession().join()
                is AuthOperationResult.Failure -> uiState.value =
                    loginState.copy(
                        loading = false,
                        googleLoading = false,
                        googleAttemptId = null,
                        error = result.message,
                        passwordResetMessage = null,
                    )
            }
        }
    }

    private fun signInWithGoogleIdToken(attemptId: Long, idToken: String) {
        val current = uiState.value.googleLoginStateFor(attemptId) ?: return
        scope.launch {
            uiState.value = current.copy(
                loading = true,
                googleLoading = true,
                error = null,
                passwordResetMessage = null,
            )
            when (val result = authRepository.signInWithGoogleIdToken(idToken)) {
                AuthOperationResult.Success -> {
                    if (uiState.value.googleLoginStateFor(attemptId) != null) {
                        loadBackendSession().join()
                    }
                }

                is AuthOperationResult.Failure -> {
                    if (uiState.value.googleLoginStateFor(attemptId) != null) {
                        uiState.value = current.copy(
                            loading = false,
                            googleLoading = false,
                            googleAttemptId = null,
                            error = result.message,
                            passwordResetMessage = null,
                        )
                    }
                }
            }
        }
    }

    private suspend fun provisionAndLoadBackendSession(
        onFailure: suspend (ApiError) -> Unit = ::handleSessionLoadFailure,
        clearAssociationConflict: Boolean = true,
    ) {
        when (val result = provisionAndLoadProfile()) {
            is ApiResult.Success -> {
                finalizeActiveSession(
                    session = result.value,
                    onLoaded = onActiveSessionLoaded,
                    onFailure = onFailure,
                )
            }

            is ApiResult.Failure -> {
                if (clearAssociationConflict && result.error.isProvisioningAccountAssociationConflict()) {
                    clearLocalSessionAndShowLogin(
                        error = "Ya existe una cuenta asociada a ese email. Iniciá sesión con el método original.",
                    )
                } else {
                    onFailure(result.error)
                }
            }
        }
    }

    private suspend fun loadProvisionedSessionForActiveUser(
        user: BackendUser,
        onFailure: suspend (ApiError) -> Unit = ::handleSessionLoadFailure,
    ): ProvisionedSession? {
        return when (val result = provisionAndLoadProfile.loadProfileFor(user)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> {
                onFailure(result.error)
                null
            }
        }
    }

    private suspend fun finalizeActiveSession(
        session: ProvisionedSession,
        onLoaded: suspend (ProvisionedSession) -> Unit,
        onFailure: suspend (ApiError) -> Unit = { error -> uiState.value = RealsRootUiState.Failure(error) },
    ) {
        when (val verification = localFirebaseEmailVerificationCoordinator.ensureVerifiedForLocalBootstrap()) {
            LocalFirebaseEmailVerificationResult.Verified -> {
                registerPushTokenBestEffort()
                onLoaded(session)
            }

            LocalFirebaseEmailVerificationResult.NotSignedIn -> clearLocalSessionAndShowLogin(
                error = "Tu sesión terminó. Volvé a iniciar sesión.",
            )
            is LocalFirebaseEmailVerificationResult.Failure -> {
                onFailure(verification.error)
            }
        }
    }

    private suspend fun handleSessionLoadFailure(error: ApiError) {
        if (error.isAccountBanned()) {
            handleAccountBannedError(error)
            return
        }

        if (error.isAuthenticationMethodNotAllowed()) {
            clearLocalSessionAndShowLogin(
                error = "Ese método de inicio de sesión no está habilitado para esta cuenta.",
            )
            return
        }

        if (error.isTerminalAuthFailure()) {
            clearLocalSessionAndShowLogin(
                error = "Tu sesión terminó. Volvé a iniciar sesión.",
            )
            return
        }

        if (error.isAccountDeleted()) {
            showAccountDeletionPendingFromBackend()
            return
        }

        uiState.value = RealsRootUiState.Failure(error)
    }

    private fun ApiError.Backend?.shouldProvisionAfterGetMeFailure(): Boolean {
        if (this == null) return false
        return statusCode == 404 ||
            (statusCode == 403 && backendErrorCode == BackendErrorCode.AccessDenied)
    }

    private suspend fun clearLocalSessionAndShowLogin(error: String? = null) {
        clearLocalSessionUseCase()
        uiState.value = RealsRootUiState.Login(error = error)
    }

    private suspend fun handleAccountBannedError(error: ApiError) {
        val backend = error as? ApiError.Backend
        when (backend?.backendErrorCode) {
            BackendErrorCode.AccountTemporarilyBanned -> {
                uiState.value = RealsRootUiState.AccountSuspended(
                    suspension = AccountSuspension.Temporary(backend.expiresAt),
                )
            }

            BackendErrorCode.AccountPermanentlyBanned -> {
                if (appealJob?.isActive == true) return
                appealJob = scope.launch {
                    loadPermanentBanAppealFromBackend(
                        RealsRootUiState.PermanentBanAppeal(loading = true),
                    )
                }
                appealJob?.join()
            }

            else -> uiState.value = RealsRootUiState.Failure(error)
        }
    }

    private suspend fun loadPermanentBanAppealFromBackend(
        loadingState: RealsRootUiState.PermanentBanAppeal,
    ) {
        val requestId = ++appealRequestSequence
        uiState.value = loadingState.copy(loading = true, submitting = false, requestId = requestId)
        when (val result = getPermanentBanAppealUseCase()) {
            is ApiResult.Success -> installPermanentBanAppealResult(requestId, result.value)
            is ApiResult.Failure -> {
                if (result.error.isTerminalAuthFailure()) {
                    clearLocalSessionAndShowLogin(
                        error = "Tu sesión terminó. Volvé a iniciar sesión.",
                    )
                } else {
                    uiState.value.permanentBanAppealStateFor(requestId)?.let { latest ->
                        uiState.value = latest.copy(loading = false, submitting = false, error = result.error)
                    }
                }
            }
        }
    }

    private suspend fun reconcilePermanentBanAppealAfterSubmit(requestId: Long) {
        when (val result = getPermanentBanAppealUseCase()) {
            is ApiResult.Success -> installPermanentBanAppealResult(requestId, result.value)
            is ApiResult.Failure -> {
                if (result.error.isTerminalAuthFailure()) {
                    clearLocalSessionAndShowLogin(
                        error = "Tu sesión terminó. Volvé a iniciar sesión.",
                    )
                } else {
                    uiState.value.permanentBanAppealStateFor(requestId)?.let { latest ->
                        uiState.value = latest.copy(submitting = false, error = result.error)
                    }
                }
            }
        }
    }

    private suspend fun installPermanentBanAppealResult(
        requestId: Long,
        appeal: PermanentBanAppealState,
    ) {
        val latest = uiState.value.permanentBanAppealStateFor(requestId) ?: return
        if (!appeal.hasExpectedBanActivity()) {
            uiState.value = latest.copy(
                appeal = appeal,
                loading = false,
                submitting = false,
                error = ApiError.Unexpected("No pudimos confirmar el estado de tu suspensión."),
            )
            return
        }
        if (appeal.isApprovedInactive()) {
            uiState.value = latest.copy(
                appeal = appeal,
                loading = true,
                submitting = false,
                error = null,
                normalBootstrapError = null,
            )
            bootstrapAfterApprovedAppeal(appeal)
            return
        }
        uiState.value = latest.copy(
            appeal = appeal,
            loading = false,
            submitting = false,
            error = null,
            normalBootstrapError = null,
        )
    }

    private suspend fun bootstrapAfterApprovedAppeal(appeal: PermanentBanAppealState) {
        loadBackendSessionFromCurrentAuth(
            showLoadingState = false,
            onFailure = { error ->
                if (error.isTerminalAuthFailure()) {
                    clearLocalSessionAndShowLogin(
                        error = "Tu sesión terminó. Volvé a iniciar sesión.",
                    )
                } else if (error.isAccountBanned()) {
                    handleAccountBannedError(error)
                } else {
                    uiState.value = RealsRootUiState.PermanentBanAppeal(
                        appeal = appeal,
                        loading = false,
                        normalBootstrapError = error,
                    )
                }
            },
            clearAssociationConflict = false,
        )
    }

    private fun registerPushTokenBestEffort() {
        scope.launch {
            pushTokenRegistrationService.registerCurrentTokenIfPossible()
        }
    }

    private companion object {
        const val PASSWORD_RESET_COOLDOWN_MILLIS = 60_000L
        const val PERMANENT_BAN_APPEAL_MAX_LENGTH = 1000
        const val invalidPasswordResetEmailMessage = "Ingresá un email válido."
        const val genericPasswordResetMessage =
            "Si el email está registrado, te enviamos instrucciones para recuperar el acceso."
    }
}

private fun Long?.isInFuture(nowMillis: Long): Boolean = this != null && nowMillis < this

private fun RealsRootUiState.googleLoginStateFor(attemptId: Long): RealsRootUiState.Login? {
    val login = this as? RealsRootUiState.Login ?: return null
    return login.takeIf { it.googleLoading && it.googleAttemptId == attemptId }
}

private fun RealsRootUiState.passwordResetStateFor(attemptId: Long): RealsRootUiState.Login? {
    val login = this as? RealsRootUiState.Login ?: return null
    return login.takeIf { it.passwordResetLoading && it.passwordResetAttemptId == attemptId }
}

private fun RealsRootUiState.permanentBanAppealStateFor(requestId: Long): RealsRootUiState.PermanentBanAppeal? {
    val appeal = this as? RealsRootUiState.PermanentBanAppeal ?: return null
    return appeal.takeIf { it.requestId == requestId }
}

private fun PermanentBanAppealState.hasExpectedBanActivity(): Boolean = when (status) {
    PermanentBanAppealStatus.Available,
    PermanentBanAppealStatus.Pending,
    PermanentBanAppealStatus.Rejected -> banActive

    PermanentBanAppealStatus.Approved -> !banActive
    is PermanentBanAppealStatus.Unknown -> false
}

private fun PermanentBanAppealState.isApprovedInactive(): Boolean =
    status == PermanentBanAppealStatus.Approved && !banActive

private fun ApiError.isAuthenticationMethodNotAllowed(): Boolean {
    return this is ApiError.Backend && backendErrorCode == BackendErrorCode.AuthMethodNotAllowed
}

private fun ApiError.isProvisioningAccountAssociationConflict(): Boolean {
    val backend = this as? ApiError.Backend ?: return false
    return backend.backendErrorCode == BackendErrorCode.EmailAlreadyLinkedToDifferentFirebaseUser
}

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
