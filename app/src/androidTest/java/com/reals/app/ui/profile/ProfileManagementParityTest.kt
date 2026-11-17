package com.reals.app.ui.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.reals.app.domain.model.*
import com.reals.app.ui.root.OwnerProfilePreviewState
import com.reals.app.ui.root.ProfileNavigationRestoration
import com.reals.app.ui.root.RealsRootUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileManagementParityTest {
    @get:Rule val compose = createComposeRule()
    private val profile = Profile("p", "u", "Ana", "1998-01-01", 28, true, "VERIFIED", "FEMALE",
        setOf("MALE"), "DATE", "Buenos Aires", "AR", "Bio", 20, 40, 10, ProfileStatus.Active, 0, "t", "t")
    private val session = ProvisionedSession(BackendUser("u", "u@example.test", BackendUserStatus.Active, null, null, true, "t"), ProfileSnapshot.Found(profile))

    @Test fun restoresPreviewRouteAfterProcessStateIsRebuilt() {
        val ready = mutableStateOf(RealsRootUiState.Ready(session,
            ownerPreview = OwnerProfilePreviewState(ownerSession = session, open = true)))
        val restoration = StateRestorationTester(compose)
        var restored = false
        var rebuilding = false
        restoration.setContent {
            ProfileNavigationRestoration(if (rebuilding) RealsRootUiState.Ready(session) else ready.value,
                {}, {}, { restored = true }, {}, {})
        }
        compose.waitForIdle()
        // Reset the ViewModel snapshot during disposal, preserving only the UI saved state.
        compose.runOnIdle { rebuilding = true }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertTrue(restored) }
    }

    @Test fun normalizedUnchangedDetailsCannotSaveAndCountryPrecedesCity() {
        compose.setContent { MaterialTheme { content() } }
        compose.onNodeWithText("Guardar cambios").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Nombre visible").performScrollTo().performTextReplacement(" Ana ")
        compose.onNodeWithText("Guardar cambios").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Nombre visible").performScrollTo().performTextReplacement("Ana María")
        compose.onNodeWithText("Guardar cambios").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("País").performScrollTo()
        assertTrue(compose.onNodeWithText("País").getUnclippedBoundsInRoot().top < compose.onNodeWithText("Ciudad").getUnclippedBoundsInRoot().top)
        compose.onNodeWithText("Bio (opcional)").assertExists()
    }

    @Test fun previewReturnAndRecreationPreserveUnsavedDetails() {
        val preview = mutableStateOf(OwnerProfilePreviewState())
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { content(preview.value,
            open = { preview.value = OwnerProfilePreviewState(ownerSession = session, open = true,
                profile = VisualProfile("p", "Ana", 28, "Bio", emptyList(), null, false, false, false, false, city = "Buenos Aires", countryCode = "AR")) },
            close = { preview.value = OwnerProfilePreviewState() }) } }
        compose.onNodeWithText("Nombre visible").performScrollTo().performTextReplacement("Borrador privado")
        compose.onNodeWithText("Vista previa de mi perfil").performScrollTo().performClick()
        compose.onNodeWithText("Buenos Aires, Argentina").assertExists()
        compose.onNodeWithText("Volver", substring = false).performClick()
        compose.onNodeWithText("Vista previa de mi perfil").assertIsFocused()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Nombre visible").performScrollTo().assertTextContains("Borrador privado")
    }

    @Composable
    private fun content(preview: OwnerProfilePreviewState = OwnerProfilePreviewState(), open: () -> Unit = {}, close: () -> Unit = {}) {
        ProfileStatusScreen(
            session = session, profileUpdateLoading = false, profileUpdateError = null, profileUpdateMessage = null,
            countriesLoading = false, countries = listOf(CountryReference("AR", "Argentina")), countriesError = null,
            matchFiltersLoading = false, matchFiltersError = null, matchFiltersMessage = null,
            photosLoading = false, photos = emptyList(), photosError = null,
            photoActionLoading = false, photoActionError = null, photoActionMessage = null,
            photoReorderLoading = false, photoReorderError = null, photoReorderMessage = null,
            activationLoading = false, activationError = null, emailVerificationSending = false,
            emailVerificationChecking = false, emailVerificationMessage = null, emailVerificationError = null,
            emailVerificationRequired = false, emailVerificationLocallyVerified = true,
            resendEmailVerificationAvailableAtMillis = null, checkEmailVerificationAvailableAtMillis = null,
            accountDeleteLoading = false, accountDeleteError = null, managementSurface = ProfileManagementSurface.Profile,
            onUpdateProfile = {}, onLoadCountries = {}, onUpdateMatchFilters = {}, onLoadPhotos = {},
            onAddPhotoFile = { _, _ -> }, onReplacePhotoFile = { _, _, _ -> }, onDeletePhoto = { _, _ -> },
            onMovePhoto = { _, _ -> }, onActivateProfile = {}, onResendEmailVerification = {},
            onCheckEmailVerification = {}, onOpenProfileQuestions = {}, onRefresh = {}, onSignOut = {},
            onDeleteAccount = {}, ownerPreview = preview, onOpenOwnerPreview = open, onCloseOwnerPreview = close,
        )
    }
}
