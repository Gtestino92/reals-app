package com.reals.app.ui.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reals.app.domain.model.*
import com.reals.app.ui.root.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileResumeLifecycleTest {
    @get:Rule val compose = createComposeRule()
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun questionResumeKeepsEditorDraftSelectionAndFocusWithoutBlockingSave() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        val ui = mutableStateOf(ProfileQuestionUiState(open = true, profileId = "p",
            catalog = ProfileQuestionCatalog("1", listOf(ProfileQuestion("a", 1, 1, "Mi domingo ideal", 1))),
            answers = listOf(ProfileQuestionAnswer("a", 1, "Café", 1, true, "t", "t")),
            selectionDraftQuestionIds = listOf("a")))
        var resumes = 0
        var retries = 0
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MaterialTheme {
                    ProfileQuestionScreen(ui.value, {}, { retries++ }, {}, {}, {}, {}, {}, { _, _ -> }, {},
                        onResume = { resumes++; ui.value = ui.value.copy(revalidating = true) })
                }
            }
        }
        compose.onNodeWithText("Editar respuesta").performScrollTo().performClick()
        compose.onNodeWithText("Tu respuesta").performTextReplacement("Borrador independiente")
        compose.runOnIdle {
            ui.value = ui.value.copy(revalidating = false)
            owner.registry.currentState = Lifecycle.State.CREATED
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Tu respuesta").assertTextContains("Borrador independiente").assertIsFocused()
        compose.onNodeWithText("Posición 1").assertExists()
        compose.onNodeWithText("Guardar", substring = false).assertIsEnabled()
        compose.onNodeWithText("Actualizando preguntas…").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, resumes); assertEquals(0, retries) }
    }

    @Test fun previewResumeUsesSilentCallbackAndPreservesSnapshotScroll() {
        val owner = Owner().apply { registry.currentState = Lifecycle.State.RESUMED }
        val profile = VisualProfile("p", "Ana", 28, "Mi presentación. ".repeat(100), emptyList(), null,
            false, false, false, false, city = "Rosario", countryCode = "AR")
        val ui = mutableStateOf(OwnerProfilePreviewState(open = true, profile = profile))
        var resumes = 0
        var retries = 0
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MaterialTheme { OwnerProfilePreviewDialog(ui.value, {}, { retries++ },
                    onResume = { resumes++; ui.value = ui.value.copy(revalidating = true) }) }
            }
        }
        compose.onNodeWithText("Bio", substring = false).performScrollTo()
        val before = compose.onNodeWithText("Bio", substring = false).getUnclippedBoundsInRoot()
        compose.runOnIdle {
            ui.value = ui.value.copy(revalidating = false)
            owner.registry.currentState = Lifecycle.State.CREATED
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("Cargando perfil…").assertDoesNotExist()
        assertEquals(before, compose.onNodeWithText("Bio", substring = false).getUnclippedBoundsInRoot())
        compose.runOnIdle { assertEquals(profile, ui.value.profile); assertEquals(2, resumes); assertEquals(0, retries) }
    }
}
