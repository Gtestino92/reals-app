package com.reals.app.ui.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reals.app.ui.common.SignOutCancelButtonTag
import com.reals.app.ui.common.SignOutConfirmationBody
import com.reals.app.ui.common.SignOutConfirmButtonTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeleteAccountSectionSignOutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signOutButtonOpensConfirmationWithoutSigningOut() {
        var signOutCount = 0
        setDeleteAccountSection(onSignOut = { signOutCount++ })

        composeRule.onNodeWithText("Cerrar sesión").performClick()

        composeRule.onNodeWithText(SignOutConfirmationBody).assertIsDisplayed()
        assertEquals(0, signOutCount)
    }

    @Test
    fun cancellingSignOutConfirmationDoesNotSignOut() {
        var signOutCount = 0
        setDeleteAccountSection(onSignOut = { signOutCount++ })

        composeRule.onNodeWithText("Cerrar sesión").performClick()
        composeRule.onNodeWithTag(SignOutCancelButtonTag).performClick()

        composeRule.onAllNodesWithText(SignOutConfirmationBody).assertCountEquals(0)
        assertEquals(0, signOutCount)
    }

    @Test
    fun confirmingSignOutConfirmationSignsOutOnce() {
        var signOutCount = 0
        setDeleteAccountSection(onSignOut = { signOutCount++ })

        composeRule.onNodeWithText("Cerrar sesión").performClick()
        composeRule.onNodeWithTag(SignOutConfirmButtonTag).performClick()

        composeRule.onAllNodesWithText(SignOutConfirmationBody).assertCountEquals(0)
        assertEquals(1, signOutCount)
    }

    private fun setDeleteAccountSection(
        busy: Boolean = false,
        loading: Boolean = false,
        expanded: Boolean = true,
        onSignOut: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                DeleteAccountSection(
                    busy = busy,
                    loading = loading,
                    error = null,
                    expanded = expanded,
                    onExpandedChange = {},
                    onSignOut = onSignOut,
                    onDeleteAccount = {},
                )
            }
        }
    }
}
