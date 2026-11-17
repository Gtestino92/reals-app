package com.reals.app.ui.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.reals.app.domain.model.VisualProfile
import com.reals.app.ui.chat.ProfilePresentationMode
import com.reals.app.ui.chat.VisualProfileCard
import org.junit.Rule
import org.junit.Test

class PublicProfilePresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun declaredLocationAppearsInReviewAndBrowse() {
        val mode = mutableStateOf(ProfilePresentationMode.Review)
        compose.setContent { MaterialTheme { VisualProfileCard(profile(), presentationMode = mode.value) } }
        compose.onNodeWithText("Buenos Aires, Argentina").assertIsDisplayed()
        compose.runOnIdle { mode.value = ProfilePresentationMode.Browse }
        compose.onNodeWithText("Buenos Aires, Argentina").assertIsDisplayed()
    }

    @Test fun legacyLocationIsOmitted() {
        compose.setContent { MaterialTheme { VisualProfileCard(profile().copy(city = null, countryCode = null)) } }
        compose.onNodeWithText("Buenos Aires, Argentina").assertDoesNotExist()
    }

    private fun profile() = VisualProfile(
        "owner", "Ana", 25, null, emptyList(), null, false, false, false, false,
        city = "Buenos Aires", countryCode = "AR",
    )
}
