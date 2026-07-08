package com.reals.app.ui.root

import android.content.ContextWrapper
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.BackendErrorCode
import com.reals.app.core.network.backendErrorCode
import com.reals.app.data.repository.AuthOperationResult
import com.reals.app.data.repository.ChangePasswordResult
import com.reals.app.data.repository.ChatRepository
import com.reals.app.data.repository.EmailVerificationCheckResult
import com.reals.app.data.repository.EmailVerificationSendResult
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.LegalRepository
import com.reals.app.data.repository.MatchRepository
import com.reals.app.data.repository.MatchmakingRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.repository.PasswordResetResult
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.data.repository.SchedulingRepository
import com.reals.app.di.AccountFeatureDependencies
import com.reals.app.di.FirstChatFeatureDependencies
import com.reals.app.di.HomeFeatureDependencies
import com.reals.app.di.LegalFeatureDependencies
import com.reals.app.di.ProfileFeatureDependencies
import com.reals.app.di.RealsRootDependencies
import com.reals.app.di.SchedulingFeatureDependencies
import com.reals.app.di.SessionFeatureDependencies
import com.reals.app.di.VisualApprovalFeatureDependencies
import com.reals.app.domain.usecase.AcceptChatExitRequestUseCase
import com.reals.app.domain.usecase.AcceptSchedulingProposalUseCase
import com.reals.app.domain.usecase.ActivateProfileUseCase
import com.reals.app.domain.usecase.AddProfilePhotoFileUseCase
import com.reals.app.domain.usecase.CancelChatUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.DeleteAccountUseCase
import com.reals.app.domain.usecase.DeleteProfilePhotoUseCase
import com.reals.app.domain.usecase.DismissSecondChatForConnectionUseCase
import com.reals.app.domain.usecase.EnqueueMatchmakingUseCase
import com.reals.app.domain.usecase.GetChatExitRequestsUseCase
import com.reals.app.domain.usecase.GetChatMessagesUseCase
import com.reals.app.domain.usecase.GetChatUseCase
import com.reals.app.domain.usecase.GetCurrentLegalDocumentsUseCase
import com.reals.app.domain.usecase.GetFirstChatForMatchUseCase
import com.reals.app.domain.usecase.GetHomePendingUseCase
import com.reals.app.domain.usecase.GetHomeStatusUseCase
import com.reals.app.domain.usecase.GetHomeUseCase
import com.reals.app.domain.usecase.GetLegalStatusUseCase
import com.reals.app.domain.usecase.GetMatchUseCase
import com.reals.app.domain.usecase.GetMeUseCase
import com.reals.app.domain.usecase.GetPartnerPersonalMessageUseCase
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.domain.usecase.GetSchedulingNegotiationUseCase
import com.reals.app.domain.usecase.GetSchedulingProposalsUseCase
import com.reals.app.domain.usecase.GetSecondChatForConnectionUseCase
import com.reals.app.domain.usecase.GetVisualProfileUseCase
import com.reals.app.domain.usecase.LeaveQueueUseCase
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
import com.reals.app.domain.usecase.PutMyPersonalMessageUseCase
import com.reals.app.domain.usecase.ReactivateAccountUseCase
import com.reals.app.domain.usecase.RegisterPushTokenUseCase
import com.reals.app.domain.usecase.RejectChatExitRequestUseCase
import com.reals.app.domain.usecase.RejectSchedulingRoundUseCase
import com.reals.app.domain.usecase.ReorderProfilePhotosUseCase
import com.reals.app.domain.usecase.ReplaceProfilePhotoFileUseCase
import com.reals.app.domain.usecase.RequestMutualChatExitUseCase
import com.reals.app.domain.usecase.RequestNextFirstChatGuidanceQuestionUseCase
import com.reals.app.domain.usecase.RecordLegalDocumentActionUseCase
import com.reals.app.domain.usecase.SafetyCancelChatUseCase
import com.reals.app.domain.usecase.SendChatMessageUseCase
import com.reals.app.domain.usecase.SubmitChatDecisionUseCase
import com.reals.app.domain.usecase.SubmitSchedulingProposalsUseCase
import com.reals.app.domain.usecase.SubmitVisualDecisionUseCase
import com.reals.app.domain.usecase.TimeoutChatExitRequestUseCase
import com.reals.app.domain.usecase.UpdateMatchFiltersUseCase
import com.reals.app.domain.usecase.UpdateProfileUseCase
import com.reals.app.notifications.registration.PushTokenRegistrationService
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.TestDomain
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import com.reals.app.testutil.testJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealsRootViewModelPasswordResetTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank password reset email shows local validation error`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically)
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Login(passwordResetMessage = "stale"))

        viewModel.requestPasswordReset(" ")

        val state = viewModel.uiState.value as RealsRootUiState.Login
        assertEquals("Ingresá un email válido.", state.error)
        assertNull(state.passwordResetMessage)
        assertEquals(false, state.passwordResetLoading)
        assertNull(state.passwordResetAvailableAtMillis)
        assertEquals(emptyList<String>(), authRepository.resetRequests)
    }

    @Test
    fun `generic handled password reset result shows generic message`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically)
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Login(error = "stale"))

        viewModel.requestPasswordReset(" alex@example.com ")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Login
        assertNull(state.error)
        assertEquals(
            "Si el email está registrado, te enviamos instrucciones para recuperar el acceso.",
            state.passwordResetMessage,
        )
        assertEquals(false, state.passwordResetLoading)
        assertNotNull(state.passwordResetAvailableAtMillis)
        assertTrue(state.passwordResetAvailableAtMillis!! > System.currentTimeMillis())
        assertEquals(listOf("alex@example.com"), authRepository.resetRequests)
    }

    @Test
    fun `silent password reset failure stops loading without visible feedback`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(PasswordResetResult.SilentFailure)
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Login(error = "stale", passwordResetMessage = "stale"))

        viewModel.requestPasswordReset("alex@example.com")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Login
        assertNull(state.error)
        assertNull(state.passwordResetMessage)
        assertEquals(false, state.passwordResetLoading)
        assertNotNull(state.passwordResetAvailableAtMillis)
    }

    @Test
    fun `password reset during cooldown is ignored`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically)
        val viewModel = viewModel(authRepository)
        val availableAtMillis = System.currentTimeMillis() + 60_000L
        viewModel.setState(RealsRootUiState.Login(passwordResetAvailableAtMillis = availableAtMillis))

        viewModel.requestPasswordReset("alex@example.com")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Login
        assertEquals(emptyList<String>(), authRepository.resetRequests)
        assertEquals(availableAtMillis, state.passwordResetAvailableAtMillis)
    }

    @Test
    fun `sign in and sign up remain available during password reset cooldown`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            signInResult = AuthOperationResult.Failure("nope"),
            signUpResult = AuthOperationResult.Failure("nope"),
        )
        val viewModel = viewModel(authRepository)
        val availableAtMillis = System.currentTimeMillis() + 60_000L

        viewModel.setState(RealsRootUiState.Login(passwordResetAvailableAtMillis = availableAtMillis))
        viewModel.signIn("alex@example.com", "password")
        advanceUntilIdle()

        viewModel.setState(RealsRootUiState.Login(passwordResetAvailableAtMillis = availableAtMillis))
        viewModel.signUp("alex@example.com", "password")
        advanceUntilIdle()

        assertEquals(listOf("alex@example.com"), authRepository.signInRequests)
        assertEquals(listOf("alex@example.com"), authRepository.signUpRequests)
    }

    @Test
    fun `sign in and sign up validation clear stale password reset message`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically))
        viewModel.setState(RealsRootUiState.Login(passwordResetMessage = "stale"))

        viewModel.signIn("", "")

        var state = viewModel.uiState.value as RealsRootUiState.Login
        assertEquals("Email y password son requeridos.", state.error)
        assertNull(state.passwordResetMessage)

        viewModel.setState(RealsRootUiState.Login(passwordResetMessage = "stale"))

        viewModel.signUp("", "")

        state = viewModel.uiState.value as RealsRootUiState.Login
        assertEquals("Email y password son requeridos.", state.error)
        assertNull(state.passwordResetMessage)
    }

    @Test
    fun `sign up success sends verification email best effort and loads backend session`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            signUpResult = AuthOperationResult.Success,
            emailVerificationSendResult = EmailVerificationSendResult.Sent,
        )
        val viewModel = viewModel(authRepository, api)
        viewModel.setState(RealsRootUiState.Login())

        viewModel.signUp("alex@example.com", "password")
        advanceUntilIdle()

        assertEquals(listOf("alex@example.com"), authRepository.signUpRequests)
        assertEquals(1, authRepository.emailVerificationSendCalls)
        assertEquals(true, api.calls.contains("getMe"))
    }

    @Test
    fun `sign up still loads backend session when verification email send fails`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            signUpResult = AuthOperationResult.Success,
            emailVerificationSendResult = EmailVerificationSendResult.Failure,
        )
        val viewModel = viewModel(authRepository, api)
        viewModel.setState(RealsRootUiState.Login())

        viewModel.signUp("alex@example.com", "password")
        advanceUntilIdle()

        assertEquals(1, authRepository.emailVerificationSendCalls)
        assertEquals(true, api.calls.contains("getMe"))
    }

    @Test
    fun `sign in does not send verification email`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            signInResult = AuthOperationResult.Failure("nope"),
        )
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Login())

        viewModel.signIn("alex@example.com", "password")
        advanceUntilIdle()

        assertEquals(0, authRepository.emailVerificationSendCalls)
    }

    @Test
    fun `resend email verification shows success message`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            emailVerificationSendResult = EmailVerificationSendResult.Sent,
        )
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.resendEmailVerification()
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertFalse(state.sendingEmailVerification)
        assertEquals("Te enviamos un nuevo correo de verificación.", state.emailVerificationMessage)
        assertNull(state.emailVerificationError)
        assertNotNull(state.resendEmailVerificationAvailableAtMillis)
    }

    @Test
    fun `resend email verification shows failure error`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            emailVerificationSendResult = EmailVerificationSendResult.Failure,
        )
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.resendEmailVerification()
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertFalse(state.sendingEmailVerification)
        assertNull(state.emailVerificationMessage)
        assertEquals(
            "No pudimos enviar el correo de verificación. Intentá nuevamente.",
            state.emailVerificationError,
        )
    }

    @Test
    fun `check email verification shows verified message`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            emailVerificationCheckResult = EmailVerificationCheckResult.Verified,
        )
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.checkEmailVerification()
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertFalse(state.checkingEmailVerification)
        assertEquals("Email verificado. Ya podés activar tu perfil.", state.emailVerificationMessage)
        assertNull(state.emailVerificationError)
        assertEquals(false, state.emailVerificationRequired)
        assertEquals(true, state.emailVerificationLocallyVerified)
        assertEquals(1, authRepository.emailVerificationCheckCalls)
    }

    @Test
    fun `check email verification shows not verified error`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            emailVerificationCheckResult = EmailVerificationCheckResult.NotVerified,
        )
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.checkEmailVerification()
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertFalse(state.checkingEmailVerification)
        assertNull(state.emailVerificationMessage)
        assertEquals(true, state.emailVerificationRequired)
        assertEquals(false, state.emailVerificationLocallyVerified)
        assertNotNull(state.checkEmailVerificationAvailableAtMillis)
        assertEquals(
            "Todavía no vemos el email verificado. Abrí el link del correo y volvé a intentar.",
            state.emailVerificationError,
        )
    }

    @Test
    fun `check email verification during cooldown does not call Firebase`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically)
        val viewModel = viewModel(authRepository)
        viewModel.setState(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                profileOp = ProfileManagementState(
                    emailVerificationRequired = true,
                    checkEmailVerificationAvailableAtMillis = System.currentTimeMillis() + 10_000L,
                ),
            )
        )

        viewModel.checkEmailVerification()
        advanceUntilIdle()

        assertEquals(0, authRepository.emailVerificationCheckCalls)
    }

    @Test
    fun `resend email verification during cooldown does not call Firebase`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically)
        val viewModel = viewModel(authRepository)
        viewModel.setState(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                profileOp = ProfileManagementState(
                    emailVerificationRequired = true,
                    resendEmailVerificationAvailableAtMillis = System.currentTimeMillis() + 60_000L,
                ),
            )
        )

        viewModel.resendEmailVerification()
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals(0, authRepository.emailVerificationSendCalls)
        assertNotNull(state.emailVerificationMessage)
    }

    @Test
    fun `email verification duplicate calls are ignored while loading`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically)
        val viewModel = viewModel(authRepository)
        viewModel.setState(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                profileOp = ProfileManagementState(sendingEmailVerification = true),
            )
        )

        viewModel.resendEmailVerification()
        viewModel.checkEmailVerification()
        advanceUntilIdle()

        assertEquals(0, authRepository.emailVerificationSendCalls)
        assertEquals(0, authRepository.emailVerificationCheckCalls)
    }

    @Test
    fun `email not verified activation failure remains on profile state`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            profileResponse = backendErrorResponse(
                statusCode = 409,
                code = "EMAIL_NOT_VERIFIED",
                message = "Verificá tu email antes de activar el perfil.",
            )
        }
        val viewModel = viewModel(FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically), api)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.activateProfile()
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        val error = state.profileActivationError as ApiError.Backend
        assertEquals(BackendErrorCode.EmailNotVerified, error.backendErrorCode)
        assertEquals(true, state.emailVerificationRequired)
        assertEquals(false, state.emailVerificationLocallyVerified)
    }

    @Test
    fun `activation is blocked locally while email verification is required`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically), api)
        viewModel.setState(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                profileOp = ProfileManagementState(emailVerificationRequired = true),
            )
        )

        viewModel.activateProfile()
        advanceUntilIdle()

        assertEquals(0, api.calls.count { it == "activateMyProfile" })
    }

    @Test
    fun `non email verification activation failure does not set verification required`() = runTest(dispatcher) {
        val api = FakeRealsApi().apply {
            profileResponse = backendErrorResponse(
                statusCode = 409,
                code = "PROFILE_PHOTOS_REQUIRED",
                message = "Subi mas fotos para poder activar tu perfil.",
            )
        }
        val viewModel = viewModel(FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically), api)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.activateProfile()
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals(false, state.emailVerificationRequired)
    }

    @Test
    fun `system back closes active profile management`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically), api)
        viewModel.setState(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                editingActiveProfile = true,
            )
        )

        viewModel.onSystemBack()
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals(false, state.editingActiveProfile)
        assertEquals(0, api.calls.count { it == "reorderMyProfilePhotos" })
    }

    @Test
    fun `explicit profile management close does not autosave reorder`() = runTest(dispatcher) {
        val api = FakeRealsApi()
        val viewModel = viewModel(FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically), api)
        viewModel.setState(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                editingActiveProfile = true,
            )
        )

        viewModel.closeProfileManagement()
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals(false, state.editingActiveProfile)
        assertEquals(0, api.calls.count { it == "reorderMyProfilePhotos" })
    }

    @Test
    fun `change password success keeps user signed in and shows account message`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            changePasswordResult = ChangePasswordResult.Success,
        )
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.changePassword("current-password", "new-password")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertFalse(state.changingPassword)
        assertEquals("Contraseña actualizada.", state.changePasswordMessage)
        assertNull(state.changePasswordError)
        assertEquals(listOf("current-password" to "new-password"), authRepository.changePasswordRequests)
    }

    @Test
    fun `change password wrong current password shows safe account error`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            changePasswordResult = ChangePasswordResult.WrongCurrentPassword,
        )
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.changePassword("bad-current", "new-password")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertFalse(state.changingPassword)
        assertNull(state.changePasswordMessage)
        assertEquals("La contraseña actual no es correcta.", state.changePasswordError)
    }

    @Test
    fun `change password weak new password shows safe account error`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            changePasswordResult = ChangePasswordResult.WeakNewPassword,
        )
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.changePassword("current-password", "weak")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals("La nueva contraseña es demasiado débil.", state.changePasswordError)
    }

    @Test
    fun `change password invalid new password shows safe account error`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            changePasswordResult = ChangePasswordResult.InvalidNewPassword,
        )
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.changePassword("current-password", "invalid-password")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals("La nueva contraseña no tiene un formato válido.", state.changePasswordError)
    }

    @Test
    fun `change password missing session is handled safely`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            changePasswordResult = ChangePasswordResult.NotSignedIn,
        )
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.changePassword("current-password", "new-password")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals("Tu sesión necesita renovarse. Volvé a iniciar sesión.", state.changePasswordError)
    }

    @Test
    fun `change password missing email is handled safely`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            changePasswordResult = ChangePasswordResult.MissingEmail,
        )
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.changePassword("current-password", "new-password")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals("No pudimos confirmar tu email de sesión. Volvé a iniciar sesión.", state.changePasswordError)
    }

    @Test
    fun `change password provider unavailable is handled safely`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(
            passwordResetResult = PasswordResetResult.SentOrHandledGenerically,
            changePasswordResult = ChangePasswordResult.PasswordProviderUnavailable,
            canChangePassword = false,
        )
        val viewModel = viewModel(authRepository)
        viewModel.setState(RealsRootUiState.Ready(session = TestDomain.session()))

        viewModel.changePassword("current-password", "new-password")
        advanceUntilIdle()

        val state = viewModel.uiState.value as RealsRootUiState.Ready
        assertEquals(
            "El cambio de contraseña no está disponible para este método de inicio de sesión.",
            state.changePasswordError,
        )
        assertEquals(false, viewModel.currentUserHasPasswordProvider())
    }

    @Test
    fun `change password duplicate calls are ignored while loading`() = runTest(dispatcher) {
        val authRepository = FakeFirebaseAuthRepository(PasswordResetResult.SentOrHandledGenerically)
        val viewModel = viewModel(authRepository)
        viewModel.setState(
            RealsRootUiState.Ready(
                session = TestDomain.session(),
                account = AccountUiState(changingPassword = true),
            )
        )

        viewModel.changePassword("current-password", "new-password")
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, String>>(), authRepository.changePasswordRequests)
    }

    private fun viewModel(
        authRepository: FirebaseAuthRepository,
        api: FakeRealsApi = FakeRealsApi(),
    ): RealsRootViewModel =
        RealsRootViewModel(rootDependencies(authRepository, api), autoRefreshSession = false)

    private fun RealsRootViewModel.setState(state: RealsRootUiState) {
        val field = RealsRootViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(this) as MutableStateFlow<RealsRootUiState>
        stateFlow.value = state
    }

    private fun rootDependencies(
        authRepository: FirebaseAuthRepository,
        api: FakeRealsApi,
    ): RealsRootDependencies {
        val context = ContextWrapper(null)
        val tokenProvider = FakeAuthTokenProvider()
        val apiExecutor = testApiExecutor()
        val meRepository = MeRepository(api, tokenProvider, apiExecutor)
        val profileRepository = ProfileRepository(context, api, tokenProvider, apiExecutor)
        val matchmakingRepository = MatchmakingRepository(api, tokenProvider, apiExecutor)
        val matchRepository = MatchRepository(api, tokenProvider, apiExecutor)
        val chatRepository = ChatRepository(api, testJson, tokenProvider, apiExecutor)
        val schedulingRepository = SchedulingRepository(api, tokenProvider, apiExecutor)
        val legalRepository = LegalRepository(api, tokenProvider, apiExecutor)
        val registerPushTokenUseCase = RegisterPushTokenUseCase(meRepository)

        return RealsRootDependencies(
            session = SessionFeatureDependencies(
                authRepository = authRepository,
                provisionAndLoadProfile = ProvisionAndLoadProfileUseCase(meRepository, profileRepository),
                getMe = GetMeUseCase(meRepository),
                pushTokenRegistrationService = PushTokenRegistrationService(context, registerPushTokenUseCase),
            ),
            account = AccountFeatureDependencies(
                reactivateAccount = ReactivateAccountUseCase(meRepository),
                deleteAccount = DeleteAccountUseCase(meRepository, authRepository),
            ),
            legal = LegalFeatureDependencies(
                getCurrentDocuments = GetCurrentLegalDocumentsUseCase(legalRepository),
                getStatus = GetLegalStatusUseCase(legalRepository),
                recordAction = RecordLegalDocumentActionUseCase(legalRepository),
            ),
            profile = ProfileFeatureDependencies(
                createProfile = CreateProfileUseCase(profileRepository),
                updateProfile = UpdateProfileUseCase(profileRepository),
                updateMatchFilters = UpdateMatchFiltersUseCase(profileRepository),
                getProfilePhotos = GetProfilePhotosUseCase(profileRepository),
                addProfilePhotoFile = AddProfilePhotoFileUseCase(profileRepository),
                replaceProfilePhotoFile = ReplaceProfilePhotoFileUseCase(profileRepository),
                deleteProfilePhoto = DeleteProfilePhotoUseCase(profileRepository),
                reorderProfilePhotos = ReorderProfilePhotosUseCase(profileRepository),
                activateProfile = ActivateProfileUseCase(profileRepository),
            ),
            home = HomeFeatureDependencies(
                enqueueMatchmaking = EnqueueMatchmakingUseCase(matchmakingRepository),
                getHome = GetHomeUseCase(meRepository),
                getHomeStatus = GetHomeStatusUseCase(meRepository),
                getHomePending = GetHomePendingUseCase(meRepository),
                leaveQueue = LeaveQueueUseCase(matchmakingRepository),
                dismissSecondChat = DismissSecondChatForConnectionUseCase(chatRepository),
            ),
            firstChat = FirstChatFeatureDependencies(
                getMatch = GetMatchUseCase(matchRepository),
                getChat = GetChatUseCase(chatRepository),
                getFirstChatForMatch = GetFirstChatForMatchUseCase(matchRepository),
                getSecondChatForConnection = GetSecondChatForConnectionUseCase(chatRepository),
                submitChatDecision = SubmitChatDecisionUseCase(matchRepository),
                getChatMessages = GetChatMessagesUseCase(chatRepository),
                sendChatMessage = SendChatMessageUseCase(chatRepository),
                requestNextFirstChatGuidanceQuestion = RequestNextFirstChatGuidanceQuestionUseCase(chatRepository),
                getChatExitRequests = GetChatExitRequestsUseCase(chatRepository),
                requestMutualChatExit = RequestMutualChatExitUseCase(chatRepository),
                acceptChatExitRequest = AcceptChatExitRequestUseCase(chatRepository),
                rejectChatExitRequest = RejectChatExitRequestUseCase(chatRepository),
                timeoutChatExitRequest = TimeoutChatExitRequestUseCase(chatRepository),
                cancelChat = CancelChatUseCase(chatRepository),
                safetyCancelChat = SafetyCancelChatUseCase(chatRepository),
            ),
            visualApproval = VisualApprovalFeatureDependencies(
                getMatch = GetMatchUseCase(matchRepository),
                getVisualProfile = GetVisualProfileUseCase(matchRepository),
                submitVisualDecision = SubmitVisualDecisionUseCase(matchRepository),
                putMyPersonalMessage = PutMyPersonalMessageUseCase(matchRepository),
                getPartnerPersonalMessage = GetPartnerPersonalMessageUseCase(matchRepository),
            ),
            scheduling = SchedulingFeatureDependencies(
                getNegotiation = GetSchedulingNegotiationUseCase(schedulingRepository),
                getProposals = GetSchedulingProposalsUseCase(schedulingRepository),
                submitProposals = SubmitSchedulingProposalsUseCase(schedulingRepository),
                acceptProposal = AcceptSchedulingProposalUseCase(schedulingRepository),
                rejectRound = RejectSchedulingRoundUseCase(schedulingRepository),
            ),
        )
    }

    private class FakeFirebaseAuthRepository(
        private val passwordResetResult: PasswordResetResult,
        private val signInResult: AuthOperationResult = AuthOperationResult.Success,
        private val signUpResult: AuthOperationResult = AuthOperationResult.Success,
        private val emailVerificationSendResult: EmailVerificationSendResult =
            EmailVerificationSendResult.Sent,
        private val emailVerificationCheckResult: EmailVerificationCheckResult =
            EmailVerificationCheckResult.Verified,
        private val changePasswordResult: ChangePasswordResult = ChangePasswordResult.Success,
        private val canChangePassword: Boolean = true,
    ) : FirebaseAuthRepository(ContextWrapper(null)) {
        val resetRequests = mutableListOf<String>()
        val signInRequests = mutableListOf<String>()
        val signUpRequests = mutableListOf<String>()
        val changePasswordRequests = mutableListOf<Pair<String, String>>()
        var emailVerificationSendCalls = 0
            private set
        var emailVerificationCheckCalls = 0
            private set

        override suspend fun signIn(email: String, password: String): AuthOperationResult {
            signInRequests += email
            return signInResult
        }

        override suspend fun signUp(email: String, password: String): AuthOperationResult {
            signUpRequests += email
            return signUpResult
        }

        override suspend fun sendPasswordResetEmail(email: String): PasswordResetResult {
            resetRequests += email
            return passwordResetResult
        }

        override suspend fun sendEmailVerificationEmail(): EmailVerificationSendResult {
            emailVerificationSendCalls++
            return emailVerificationSendResult
        }

        override suspend fun reloadAndRefreshEmailVerification(): EmailVerificationCheckResult {
            emailVerificationCheckCalls++
            return emailVerificationCheckResult
        }

        override fun currentUserHasPasswordProvider(): Boolean = canChangePassword

        override suspend fun changePassword(
            currentPassword: String,
            newPassword: String,
        ): ChangePasswordResult {
            changePasswordRequests += currentPassword to newPassword
            return changePasswordResult
        }
    }
}
