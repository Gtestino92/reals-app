package com.reals.app.ui.profile

import android.view.Window
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
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
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.reals.app.core.network.ApiError
import com.reals.app.domain.model.ProfileQuestion
import com.reals.app.domain.model.ProfileQuestionAnswer
import com.reals.app.domain.model.ProfileQuestionCatalog
import com.reals.app.ui.root.ProfileQuestionDestination
import com.reals.app.ui.root.ProfileQuestionFeedback
import com.reals.app.ui.root.ProfileQuestionUiState
import com.reals.app.ui.theme.RealsAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileQuestionScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var dialogBack: OnBackPressedDispatcher
    private lateinit var dialogWindow: Window
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
        assertEditorialBackground(darkTheme = false)
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
        showScreen(onSaveSelection = {
            val saved = ui.value.selectionDraftQuestionIds
            submitted = saved
            ui.value = ui.value.copy(
                answers = answers.map { answer ->
                    answer.copy(selectedPosition = (saved.indexOf(answer.questionId) + 1).takeIf { it > 0 })
                },
                feedback = ProfileQuestionFeedback(ProfileQuestionDestination.Overview, null, "Selección guardada"),
            )
        })

        compose.onNodeWithText("Actualizando preguntas…").assertDoesNotExist()
        assertItemOrder("summary", "public-heading", "question-a", "question-b", "public-actions",
            "other-heading", "question-c", "question-d", "open-questions")
        scrollTo("Guardar selección").assertIsNotEnabled()
        cardAction("a", "Subir").assertIsNotEnabled()
        cardAction("a", "Bajar").assertIsEnabled().performClick()
        cardAction("a", "Pública · Posición 1, pasará a 2").assertIsDisplayed()
        assertItemOrder("public-heading", "question-b", "question-a", "public-actions", "other-heading")
        cardAction("a", "Bajar").assertIsNotEnabled()
        cardAction("c", "Agregar").performClick()
        assertItemOrder("question-a", "question-c", "public-actions", "other-heading", "question-d")
        cardAction("d", "Agregar").assertIsNotEnabled()
        cardAction("c", "Quitar").performClick()

        scrollTo("Guardar selección").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("b", "a"), submitted) }
        scrollTo("Selección guardada").assertIsDisplayed()
        cardAction("a", "Pública · Posición 2").assertExists()
        scrollTo("Limpiar selección").performClick()
        compose.runOnIdle { assertTrue(ui.value.selectionDraftQuestionIds.isEmpty()) }
        assertItemOrder("public-heading", "question-b", "question-a", "public-actions", "other-heading")
        cardAction("a", "Pública · Posición 2 · Pendiente de quitar").assertExists()
        scrollTo("Limpiar selección").assertIsNotEnabled()
        scrollTo("Guardar selección").assertIsEnabled().performClick()
        assertItemOrder("public-heading", "public-actions", "other-heading", "question-a", "question-b")
        scrollTo("0 de 3 en tu perfil").assertIsDisplayed()
    }

    @Test fun savingAnAdditionalAnswerKeepsTheScrolledAnchorDuringConfirmationAndRevalidation() {
        val catalog = ProfileQuestionCatalog("1", (1..20).map { index ->
            ProfileQuestion("new-$index", 1, 1, "Pregunta nueva $index", index)
        })
        ui.value = loaded.copy(
            catalog = catalog,
            answers = emptyList(),
            selectionDraftQuestionIds = emptyList(),
            destination = ProfileQuestionDestination.Questions,
        )
        var submitted: Pair<String, String>? = null
        showScreen(onSaveAnswer = { id, text -> submitted = id to text })
        cardAction("new-11", "Tu respuesta").performTextReplacement("Otro borrador")
        cardAction("new-10", "Tu respuesta").performTextReplacement("Mi respuesta")
        closeSoftKeyboard()
        compose.onNodeWithTag("profile-question-list").performScrollToKey("question-new-10")

        val anchor = compose.onNodeWithTag("profile-question-new-10").getUnclippedBoundsInRoot().top
        cardAction("new-10", "Guardar").performClick()
        compose.runOnIdle {
            assertEquals("new-10" to "Mi respuesta", submitted)
            ui.value = ui.value.copy(
                answers = listOf(ProfileQuestionAnswer("new-10", 1, "Mi respuesta", null, true, "t", "t")),
                feedback = ProfileQuestionFeedback(ProfileQuestionDestination.Questions, "new-10", "Respuesta guardada"),
            )
        }

        val confirmation = compose.onNodeWithTag("profile-question-saved-new-10")
        confirmation.assertIsDisplayed()
        assertEquals(anchor, confirmation.getUnclippedBoundsInRoot().top)
        compose.onNodeWithTag("profile-question-header").assertIsNotDisplayed()
        compose.onNodeWithTag("profile-question-new-10").assertDoesNotExist()
        compose.runOnIdle { ui.value = ui.value.copy(revalidating = true) }
        assertEquals(anchor, confirmation.getUnclippedBoundsInRoot().top)
        cardAction("new-11", "Otro borrador").assertExists()
    }

    @Test fun narrowDarkLayoutWrapsActionsAndDialogBackKeepsTheInlineDraft() {
        ui.value = loaded
        var backs = 0
        val list = compose.onNodeWithTag("profile-question-list")
        showScreen(narrowAndDark = true, onBack = { backs++ })

        assertEditorialBackground(darkTheme = true)
        compose.onNodeWithText("Volver").assertIsDisplayed()
        val listBounds = list.getUnclippedBoundsInRoot()
        for (title in listOf("Preguntas públicas", "Otras respuestas")) {
            val heading = scrollTo(title).assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
            val bounds = heading.getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= listBounds.left && bounds.right <= listBounds.right)
        }
        val edit = cardAction("a", "Editar respuesta").assertHasClickAction()
        val delete = cardAction("a", "Eliminar").assertHasClickAction()
        for (action in listOf(edit, delete)) {
            val bounds = action.getUnclippedBoundsInRoot()
            assertTrue(bounds.left >= listBounds.left && bounds.right <= listBounds.right)
        }

        edit.performScrollTo().performClick()
        compose.onNodeWithText("Tu respuesta").performTextReplacement("Un borrador")
        // Exercise the dialog's Back callback independently of the IME's system Back handling.
        compose.runOnIdle { dialogBack.onBackPressed() }
        compose.onNodeWithText("Tu respuesta").assertDoesNotExist()
        cardAction("a", "Editar respuesta").assertIsFocused().performClick()
        compose.onNodeWithText("Tu respuesta").assertIsFocused()
        compose.onNodeWithText("Un borrador").assertExists()
        scrollTo("Cancelar edición").performClick()
        compose.runOnIdle { assertEquals(0, backs) }
        compose.runOnIdle {
            dialogBack.onBackPressed()
            assertEquals(1, backs)
        }
    }

    private fun showScreen(
        narrowAndDark: Boolean = false,
        onBack: () -> Unit = {},
        onRetry: () -> Unit = {},
        onSaveSelection: () -> Unit = {},
        onSaveAnswer: (String, String) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            RealsAppTheme(darkTheme = narrowAndDark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides if (narrowAndDark) Density(density.density, fontScale = 1.75f) else density,
                ) {
                    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        val back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                        val window = (LocalView.current.parent as DialogWindowProvider).window
                        SideEffect {
                            dialogBack = back
                            dialogWindow = window
                        }
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
                                onSaveAnswer = onSaveAnswer,
                                onDeleteAnswer = {},
                            )
                        }
                    }
                }
            }
        }
    }

    private fun assertEditorialBackground(darkTheme: Boolean) {
        val pixels = compose.onNodeWithTag("profile-question-list").captureToImage().toPixelMap()
        val samples = (0 until pixels.width step (pixels.width / 12).coerceAtLeast(1))
            .map { x -> pixels[x, 0].toArgb() }.toSet()
        assertTrue("Expected the editorial image behind the header", samples.size > 1)
        compose.runOnIdle {
            val insets = WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
            assertEquals(!darkTheme, insets.isAppearanceLightStatusBars)
            assertEquals(!darkTheme, insets.isAppearanceLightNavigationBars)
        }
    }

    private fun assertItemOrder(vararg keys: String) {
        val keyIndex = compose.onNodeWithTag("profile-question-list").fetchSemanticsNode().config[SemanticsProperties.IndexForKey]
        val indices = keys.map(keyIndex)
        assertTrue("Unexpected order of ${keys.toList()}: $indices", indices.all { it >= 0 } &&
            indices.zipWithNext().all { (first, second) -> first < second })
    }

    private fun scrollTo(text: String) = compose.onNodeWithText(text).also {
        compose.onNodeWithTag("profile-question-list").performScrollToNode(hasText(text))
    }

    private fun cardAction(id: String, text: String) =
        (hasAnyAncestor(hasTestTag("profile-question-$id")) and hasText(text)).let { matcher ->
            compose.onNodeWithTag("profile-question-list").performScrollToNode(matcher)
            compose.onNode(matcher)
        }
}
