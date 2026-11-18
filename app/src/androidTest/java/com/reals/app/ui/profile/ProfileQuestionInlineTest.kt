package com.reals.app.ui.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.reals.app.domain.model.*
import com.reals.app.ui.root.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileQuestionInlineTest {
    @get:Rule val compose = createComposeRule()
    private val question = ProfileQuestion("a", 1, 1, "Mi domingo ideal", 1)
    private val answer = ProfileQuestionAnswer("a", 1, "Café", 1, true, "t", "t")
    private fun state() = ProfileQuestionUiState(open = true, profileId = "p",
        catalog = ProfileQuestionCatalog("1", listOf(question, question.copy(id = "b", prompt = "Una alegría", displayOrder = 2))),
        answers = listOf(answer), selectionDraftQuestionIds = listOf("a"))

    @Test fun inlineCancelRestoresConfirmedAnswerWithoutWrite() {
        var writes = 0
        compose.setContent { MaterialTheme { ProfileQuestionScreen(state(), {}, {}, {}, {}, {}, {}, {}, { _, _ -> writes++ }, {}) } }
        compose.onNodeWithText("Editar respuesta").performScrollTo().performClick()
        compose.onNodeWithText("Tu respuesta").performTextReplacement("Borrador")
        compose.onNodeWithText("Pública · Posición 1").performScrollTo().assertExists()
        compose.onNodeWithText("Eliminar").assertDoesNotExist()
        compose.onNodeWithText("Quitar", substring = false).assertDoesNotExist()
        compose.onNodeWithText("Cancelar edición").performScrollTo().performClick()
        compose.onNodeWithText("Café").assertExists()
        compose.onNodeWithText("Editar respuesta").assertIsFocused()
        assertEquals(0, writes)
    }

    @Test fun draftSurvivesRestorationAndUncertainSave() {
        val ui = mutableStateOf(state())
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { ProfileQuestionScreen(ui.value, {}, {}, {}, {}, {}, {}, {}, { _, _ -> ui.value = ui.value.copy(uncertain = true) }, {}) } }
        compose.onNodeWithText("Editar respuesta").performScrollTo().performClick()
        compose.onNodeWithText("Tu respuesta").performTextReplacement("Mi borrador")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Tu respuesta").assertTextContains("Mi borrador")
        compose.onNodeWithText("Guardar", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Guardar", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("Tu respuesta").assertTextContains("Mi borrador")
        compose.onNodeWithText("Cancelar edición").assertIsEnabled()
    }

    @Test fun successfulSaveInstallsAuthorityAndClosesEditor() {
        val ui = mutableStateOf(state())
        compose.setContent { MaterialTheme { ProfileQuestionScreen(ui.value, {}, {}, {}, {}, {}, {}, {}, { id, _ ->
            ui.value = ui.value.copy(answers = listOf(answer.copy(answer = "Servidor")),
                feedback = ProfileQuestionFeedback(ProfileQuestionDestination.Overview, id, "Respuesta guardada"))
        }, {}) } }
        compose.onNodeWithText("Editar respuesta").performScrollTo().performClick()
        compose.onNodeWithText("Tu respuesta").performTextReplacement("Local")
        compose.onNodeWithText("Guardar", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Servidor").assertExists()
        compose.onNodeWithText("Cancelar edición").assertDoesNotExist()
        compose.onNodeWithText("Editar respuesta").assertIsFocused()
    }

    @Test fun additionalQuestionsExcludeAnsweredCards() {
        compose.setContent { MaterialTheme { ProfileQuestionScreen(state().copy(destination = ProfileQuestionDestination.Questions), {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("Una alegría").assertExists()
        compose.onNodeWithText("Mi domingo ideal").assertDoesNotExist()
    }
}
