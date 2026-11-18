package com.reals.app.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.ProfileQuestion
import com.reals.app.domain.model.ProfileQuestionAnswer
import com.reals.app.domain.model.ProfileQuestionCatalog
import com.reals.app.ui.root.ProfileQuestionUiState
import com.reals.app.ui.theme.RealsAppTheme
import com.reals.app.ui.theme.RealsColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileQuestionScreenTest {
    @get:Rule val compose = createComposeRule()
    private val ui = mutableStateOf(ProfileQuestionUiState(open = true, profileId = "p", loading = true))
    private val questions = ('a'..'e').mapIndexed { index, id ->
        ProfileQuestion(id.toString(), 1, 1, "Pregunta ${index + 1}", index)
    }
    private val answers = questions.take(4).mapIndexed { index, question ->
        ProfileQuestionAnswer(question.id, 1, "Respuesta ${index + 1}", (index + 1).takeIf { it <= 2 }, true, "t", "t")
    }
    private val loaded = ui.value.copy(
        loading = false,
        catalog = ProfileQuestionCatalog("1", questions),
        answers = answers,
        selectionDraftQuestionIds = listOf("a", "b"),
    )

    @Test fun fastLoadNeverShowsProgressAndKeepsTheHeader() {
        compose.mainClock.autoAdvance = false
        showScreen()
        val header = compose.onNodeWithTag("profile-question-header")
        val bounds = header.getUnclippedBoundsInRoot()
        compose.onNodeWithText("Volver").assertIsDisplayed()
        compose.onNodeWithText("Cargando preguntas…").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { ui.value = loaded }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Cargando preguntas…").assertDoesNotExist()
        compose.onNodeWithText("4 de 5 respondidas").assertIsDisplayed()
        assertEquals(bounds, header.getUnclippedBoundsInRoot())
        assertEquals(RealsColors.Ivory.toArgb(), listBackground())
    }

    @Test fun slowLoadKeepsHeaderThroughFailureAndRetryToEmptyCatalog() {
        var retries = 0
        compose.mainClock.autoAdvance = false
        showScreen(onRetry = {
            retries++
            ui.value = ui.value.copy(loading = true, error = null)
        })
        val header = compose.onNodeWithTag("profile-question-header")
        val bounds = header.getUnclippedBoundsInRoot()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Cargando preguntas…").assertIsDisplayed()
        assertEquals(bounds, header.getUnclippedBoundsInRoot())

        compose.runOnIdle { ui.value = ui.value.copy(loading = false, error = ApiError.Unexpected("test")) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("Cargando preguntas…").assertDoesNotExist()
        compose.onNodeWithText("Reintentar consulta").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertEquals(1, retries)
            ui.value = ui.value.copy(loading = false, catalog = ProfileQuestionCatalog("1", emptyList()))
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Cargando preguntas…").assertDoesNotExist()
        compose.onNodeWithText("No hay preguntas disponibles por ahora.").assertIsDisplayed()
        compose.onNodeWithText("Responder nuevas preguntas").assertIsNotEnabled()
        assertEquals(bounds, header.getUnclippedBoundsInRoot())
    }

    @Test fun publicActionsFollowDraftOrderAndKeepConfirmedPositionsUntilSave() {
        ui.value = loaded.copy(revalidating = true)
        var submitted: List<String>? = null
        showScreen(onSaveSelection = { submitted = ui.value.selectionDraftQuestionIds })

        compose.onNodeWithText("Actualizando preguntas…").assertDoesNotExist()
        compose.onNodeWithText("Guardar selección").assertIsNotEnabled()
        cardAction("a", "Subir").assertIsNotEnabled()
        cardAction("a", "Bajar").assertIsEnabled().performClick()
        cardAction("a", "Pública · Posición 1, pasará a 2").assertIsDisplayed()
        cardAction("a", "Bajar").assertIsNotEnabled()
        cardAction("c", "Agregar").performClick()
        cardAction("d", "Agregar").assertIsNotEnabled()
        cardAction("c", "Quitar").performClick()

        scrollTo("Guardar selección").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("b", "a"), submitted) }
        compose.onNodeWithText("Limpiar selección").performClick()
        compose.runOnIdle { assertTrue(ui.value.selectionDraftQuestionIds.isEmpty()) }
        compose.onNodeWithText("Limpiar selección").assertIsNotEnabled()
        compose.onNodeWithText("Guardar selección").assertIsEnabled()
    }

    @Test fun narrowDarkLayoutWrapsActionsAndAndroidBackKeepsTheInlineDraft() {
        ui.value = loaded
        var backs = 0
        val list = compose.onNodeWithTag("profile-question-list")
        showScreen(narrowAndDark = true, onBack = { backs++ })

        assertEquals(RealsColors.DarkInk.toArgb(), listBackground())
        compose.onNodeWithText("Volver").assertIsDisplayed()
        val listBounds = list.getUnclippedBoundsInRoot()
        val edit = cardAction("a", "Editar respuesta").assertHasClickAction()
        val delete = cardAction("a", "Eliminar").assertHasClickAction()
        for (action in listOf(edit, delete)) {
            val bounds = action.getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= listBounds.left && bounds.right <= listBounds.right)
        }

        edit.performScrollTo().performClick()
        compose.onNodeWithText("Tu respuesta").performTextReplacement("Un borrador")
        closeSoftKeyboard()
        pressBack()
        compose.onNodeWithText("Tu respuesta").assertDoesNotExist()
        cardAction("a", "Editar respuesta").assertIsFocused().performClick()
        compose.onNodeWithText("Tu respuesta").assertIsFocused()
        compose.onNodeWithText("Un borrador").assertExists()
        scrollTo("Cancelar edición").performClick()
        compose.runOnIdle { assertEquals(0, backs) }
        pressBack()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    private fun showScreen(
        narrowAndDark: Boolean = false,
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
        onSaveSelection: () -> Unit = {},
    ) {
        compose.setContent {
            RealsAppTheme(darkTheme = narrowAndDark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides if (narrowAndDark) Density(density.density, fontScale = 1.75f) else density,
                ) {
                    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        Box(if (narrowAndDark) Modifier.width(272.dp) else Modifier) {
                            ProfileQuestionScreen(
                                state = ui.value,
                                onBack = onBack,
                                onRetry = onRetry,
                                onOpenQuestions = {},
                                onOpenEditor = {},
                                onOpenSelection = {},
                                onSelectionDraftChange = { ui.value = ui.value.copy(selectionDraftQuestionIds = it) },
                                onSaveSelection = onSaveSelection,
                                onSaveAnswer = { _, _ -> },
                                onDeleteAnswer = {},
                            )
                        }
                    }
                }
            }
        }
    }

    private fun listBackground(): Int =
        compose.onNodeWithTag("profile-question-list").captureToImage().toPixelMap()[0, 0].toArgb()

    private fun scrollTo(text: String) = compose.onNodeWithText(text).also {
        compose.onNodeWithTag("profile-question-list").performScrollToNode(hasText(text))
    }

    private fun cardAction(id: String, text: String) =
        (hasAnyAncestor(hasTestTag("profile-question-$id")) and hasText(text)).let { matcher ->
            compose.onNodeWithTag("profile-question-list").performScrollToNode(matcher)
            compose.onNode(matcher)
        }
}
