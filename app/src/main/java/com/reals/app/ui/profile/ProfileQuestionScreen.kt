package com.reals.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.root.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun ProfileQuestionScreen(
    state: ProfileQuestionUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenQuestions: () -> Unit,
    onOpenEditor: (questionId: String) -> Unit,
    onOpenSelection: () -> Unit,
    onSelectionDraftChange: (questionIds: List<String>) -> Unit,
    onSaveSelection: () -> Unit,
    onSaveAnswer: (questionId: String, answer: String) -> Unit,
    onDeleteAnswer: (questionId: String) -> Unit,
    onResume: () -> Unit = {},
) {
    var drafts by rememberSaveable(state.profileId) { mutableStateOf(hashMapOf<String, String>()) }
    var editing by rememberSaveable(state.profileId) { mutableStateOf<String?>(null) }
    var submittedId by rememberSaveable(state.profileId) { mutableStateOf<String?>(null) }
    var submittedText by rememberSaveable(state.profileId) { mutableStateOf<String?>(null) }
    var focusEdit by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resume by rememberUpdatedState(onResume)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resume() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val busy = state.loading || state.refreshing || state.mutation != null
    val locked = busy || state.uncertain
    val rows = state.catalog?.rows(state.answers).orEmpty()
    var savedSelection by rememberSaveable(state.profileId) { mutableStateOf<ArrayList<String>?>(null) }
    val selection = savedSelection ?: state.selectionDraftQuestionIds
    val changeSelection: (List<String>) -> Unit = { ids ->
        savedSelection = ArrayList(ids)
        onSelectionDraftChange(ids)
    }
    LaunchedEffect(state.catalog, state.uncertain, state.mutation) {
        if (state.catalog != null && !locked && state.feedback?.message !in listOf("Selección guardada", "Respuesta eliminada")) {
            savedSelection?.let { onSelectionDraftChange(it) }
        }
    }
    val holder = rememberSaveableStateHolder()
    val additional = state.destination == ProfileQuestionDestination.Questions
    val headingFocus = remember { FocusRequester() }
    var focusHeading by remember { mutableStateOf(false) }
    val back = { keyboard?.hide(); if (editing != null) editing = null else onBack() }
    BackHandler { back() }
    LaunchedEffect(state.feedback) {
        val feedback = state.feedback ?: return@LaunchedEffect
        if (feedback.message == "Selección guardada") savedSelection = null
        val id = feedback.questionId ?: return@LaunchedEffect
        if (id == submittedId && feedback.message == "Respuesta guardada") {
            if (drafts[id] == submittedText) drafts = HashMap(drafts - id)
            if (editing == id) { editing = null; focusEdit = id; keyboard?.hide() }
            if (additional) focusHeading = true
            submittedId = null
        }
        if (feedback.message == "Respuesta eliminada") {
            drafts = HashMap(drafts - id)
            savedSelection = savedSelection?.let { ArrayList(it - id) }
        }
    }
    holder.SaveableStateProvider(if (additional) "additional" else "overview") {
        val scroll = rememberLazyListState()
        LaunchedEffect(focusHeading) {
            if (focusHeading) {
                scroll.scrollToItem(0)
                withFrameNanos { }
                headingFocus.requestFocus()
                focusHeading = false
            }
        }
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp),
            state = scroll, verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Text(if (additional) "Responder preguntas" else "Preguntas del perfil", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.focusRequester(headingFocus).focusable())
                TextButton(onClick = back) { Text("Volver") }
                if (busy) Text(if (state.catalog == null) "Cargando preguntas…" else "Actualizando preguntas…")
                state.error?.let { ApiErrorFeedbackCard(it, ErrorContext.ProfileQuestions) }
                state.mutationError?.let { ApiErrorFeedbackCard(it, ErrorContext.ProfileQuestions) }
                if (state.error != null || state.uncertain) {
                    if (state.uncertain) Text("No pudimos confirmar el cambio. Conservamos tu borrador. Consultá antes de volver a guardar.")
                    OutlinedButton(onClick = onRetry, enabled = !busy) { Text("Reintentar consulta") }
                }
                state.feedback?.let { Text(it.message) }
            }
            if (state.catalog != null) {
                if (!additional) item {
                    Text("${rows.count { it.answer != null }} de ${rows.size} respondidas")
                    Button(onClick = { keyboard?.hide(); onOpenQuestions() }, enabled = !busy && rows.any { it.answer == null }) { Text("Responder nuevas preguntas") }
                    Text("Preguntas públicas", style = MaterialTheme.typography.titleLarge)
                    Text("Elegí hasta tres respuestas para mostrar en tu perfil. Podés cambiar su orden.")
                    Text("${selection.size} de 3 elegidas para mostrar")
                }
                val eligible = rows.filter { it.selectable }.map { it.question.id }.toSet()
                if (!additional) items(selection.filter { it !in eligible }, key = { "invalid-$it" }) { id ->
                    Text("Una pregunta de la selección dejó de estar disponible. Quitala o actualizá su respuesta antes de guardar.")
                    OutlinedButton(onClick = { changeSelection(selection - id) }, enabled = !locked) { Text("Quitar pregunta no disponible") }
                }
                val visible = if (additional) rows.filter { it.answer == null } else rows.filter { it.answer != null }
                    .sortedBy { selection.indexOf(it.question.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                if (visible.isEmpty()) item { Text(if (additional) "No hay preguntas nuevas por responder." else "Respondé alguna pregunta actual para poder mostrarla en tu perfil.") }
                items(visible, key = { it.question.id }) { row ->
                    val id = row.question.id
                    val selectedIndex = selection.indexOf(id)
                    val text = drafts[id] ?: row.answer?.answer.orEmpty()
                    val editFocus = remember { FocusRequester() }
                    val inputFocus = remember { FocusRequester() }
                    val isEditing = editing == id || additional
                    LaunchedEffect(isEditing) {
                        if (isEditing && !additional) inputFocus.requestFocus()
                        else if (focusEdit == id) { editFocus.requestFocus(); focusEdit = null }
                    }
                    Card(Modifier.fillMaxWidth().testTag("profile-question-$id").semantics {
                        stateDescription = if (row.selectable && selectedIndex >= 0) "Respuesta pública, posición ${selectedIndex + 1}" else "Respuesta no pública"
                    }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(TextSafety.safeDisplay(row.question.prompt, maxLength = 180), style = MaterialTheme.typography.titleMedium)
                            val current = row.answer?.isCurrentFor(row.question) == true
                            if (row.answer != null && !current) Text("Versión anterior. Guardá la respuesta para actualizarla.")
                            if (isEditing) {
                                val validation = validateProfileQuestionAnswer(text)
                                OutlinedTextField(text, { drafts = HashMap(drafts + (id to it)) },
                                    label = { Text("Tu respuesta") }, supportingText = { Text("${validation.characterCount}/160") },
                                    isError = drafts.containsKey(id) && !validation.valid, enabled = !busy,
                                    modifier = Modifier.fillMaxWidth().focusRequester(inputFocus))
                                if (drafts.containsKey(id)) validation.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                Button(onClick = { submittedId = id; submittedText = text; onSaveAnswer(id, text) },
                                    enabled = !locked && canSaveProfileQuestionAnswer(text, row.answer?.copy(current = current), false)) { Text("Guardar") }
                                if (!additional) OutlinedButton(onClick = { drafts = HashMap(drafts - id); editing = null; focusEdit = id; keyboard?.hide() }, enabled = !busy) { Text("Cancelar edición") }
                            } else {
                                Text(TextSafety.safeDisplay(row.answer?.answer.orEmpty(), maxLength = 160))
                                OutlinedButton(onClick = { editing = id }, enabled = !busy, modifier = Modifier.focusRequester(editFocus)) { Text(if (current) "Editar respuesta" else "Actualizar respuesta") }
                                OutlinedButton(onClick = { onDeleteAnswer(id) }, enabled = !locked) { Text("Eliminar") }
                                if (row.selectable) {
                                    OutlinedButton(onClick = { changeSelection(if (selectedIndex >= 0) selection - id else selection + id) }, enabled = !locked && (selectedIndex >= 0 || selection.size < 3)) { Text(if (selectedIndex >= 0) "Quitar" else "Agregar") }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { changeSelection(selection.move(selectedIndex, selectedIndex - 1)) }, enabled = !locked && selectedIndex > 0) { Text("Subir") }
                                        OutlinedButton(onClick = { changeSelection(selection.move(selectedIndex, selectedIndex + 1)) }, enabled = !locked && selectedIndex >= 0 && selectedIndex < selection.lastIndex) { Text("Bajar") }
                                    }
                                }
                            }
                            if (row.selectable && selectedIndex >= 0) Text("Posición ${selectedIndex + 1}")
                        }
                    }
                }
                if (!additional) item {
                    OutlinedButton(onClick = { changeSelection(emptyList()) }, enabled = !locked && selection.isNotEmpty()) { Text("Limpiar selección") }
                    Button(onClick = onSaveSelection, enabled = !locked && selection != selectedProfileQuestionIds(state.answers) && profileQuestionSelectionDraftIsValid(selection, state.catalog.selectionRows(state.answers))) { Text("Guardar selección") }
                }
            }
        }
    }
}

private fun List<String>.move(from: Int, to: Int): List<String> {
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
