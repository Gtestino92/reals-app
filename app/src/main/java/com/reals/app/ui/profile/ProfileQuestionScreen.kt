package com.reals.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.common.RealsBrandDivider
import com.reals.app.ui.common.RealsEditorialSurface
import com.reals.app.ui.common.RealsPrimaryButton
import com.reals.app.ui.common.RealsSecondaryButton
import com.reals.app.ui.common.RealsThinDivider
import com.reals.app.ui.common.realsOutlinedTextFieldColors
import com.reals.app.ui.root.ProfileQuestionDestination
import com.reals.app.ui.root.ProfileQuestionMutationKind
import com.reals.app.ui.root.ProfileQuestionUiState
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType
import kotlinx.coroutines.delay

private const val ProfileQuestionProgressDelayMillis = 350L

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
    val progressLabel = when (state.mutation?.kind) {
        ProfileQuestionMutationKind.Selection -> "Guardando selección…"
        ProfileQuestionMutationKind.Upsert -> "Guardando respuesta…"
        ProfileQuestionMutationKind.Delete -> "Eliminando respuesta…"
        null -> when {
            state.loading -> "Cargando preguntas…"
            state.refreshing -> "Actualizando preguntas…"
            else -> null
        }
    }
    var showProgress by remember(progressLabel, state.mutation?.requestId) { mutableStateOf(false) }
    LaunchedEffect(progressLabel, state.mutation?.requestId) {
        if (progressLabel != null) {
            delay(ProfileQuestionProgressDelayMillis)
            showProgress = true
        }
    }
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
    val back: () -> Unit = {
        keyboard?.hide()
        val id = editing
        if (id != null) {
            editing = null
            focusEdit = id
        } else {
            onBack()
        }
    }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .testTag("profile-question-list"),
            state = scroll,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item(key = "header") {
                ProfileQuestionHeader(
                    title = if (additional) "Responder preguntas" else "Preguntas del perfil",
                    onBack = back,
                    titleModifier = Modifier.focusRequester(headingFocus).focusable(),
                )
            }
            if (showProgress || state.error != null || state.mutationError != null ||
                state.uncertain || state.feedback != null
            ) {
                item(key = "status") {
                    ProfileQuestionStatus(state, progressLabel.takeIf { showProgress }, busy, onRetry)
                }
            }
            if (state.catalog != null) {
                if (!additional) item(key = "summary") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${rows.count { it.answer != null }} de ${rows.size} respondidas",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        RealsPrimaryButton(
                            text = "Responder nuevas preguntas",
                            onClick = { keyboard?.hide(); onOpenQuestions() },
                            enabled = !busy && rows.any { it.answer == null },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (!additional) item(key = "public-selection") {
                    val changed = selection != selectedProfileQuestionIds(state.answers)
                    ProfileQuestionPublicSelection(
                        confirmedCount = rows.count { it.selectable && it.selectedPosition != null },
                        draftCount = selection.size.takeIf { changed },
                        canClear = !locked && selection.isNotEmpty(),
                        canSave = !locked && changed && profileQuestionSelectionDraftIsValid(
                            selection, state.catalog.selectionRows(state.answers),
                        ),
                        onClear = { changeSelection(emptyList()) },
                        onSave = onSaveSelection,
                    )
                }
                val eligible = rows.filter { it.selectable }.map { it.question.id }.toSet()
                if (!additional) items(selection.filter { it !in eligible }, key = { "invalid-$it" }) { id ->
                    RealsEditorialSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Una pregunta dejó de estar disponible. Quitala o actualizá su respuesta antes de guardar.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ProfileQuestionTextAction("Quitar pregunta no disponible", { changeSelection(selection - id) }, !locked)
                        }
                    }
                }
                val visible = if (additional) rows.filter { it.answer == null } else rows.filter { it.answer != null }
                    .sortedBy { selection.indexOf(it.question.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                if (visible.isEmpty()) item(key = "empty") {
                    Text(
                        text = when {
                            rows.isEmpty() -> "No hay preguntas disponibles por ahora."
                            additional -> "No hay preguntas nuevas por responder."
                            else -> "Todavía no respondiste ninguna pregunta."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(visible, key = { "question-${it.question.id}" }) { row ->
                    val id = row.question.id
                    val selectedIndex = selection.indexOf(id)
                    val text = drafts[id] ?: row.answer?.answer.orEmpty()
                    val editFocus = remember { FocusRequester() }
                    val inputFocus = remember { FocusRequester() }
                    val isEditing = editing == id || additional
                    LaunchedEffect(isEditing) {
                        if (isEditing && !additional) inputFocus.requestFocus()
                    }
                    if (!isEditing && !busy && focusEdit == id) {
                        LaunchedEffect(Unit) {
                            editFocus.requestFocus()
                            focusEdit = null
                        }
                    }
                    val current = row.answer?.isCurrentFor(row.question) == true
                    val confirmedPosition = row.selectedPosition.takeIf { row.selectable }
                    val draftPosition = (selectedIndex + 1).takeIf { row.selectable && selectedIndex >= 0 }
                    ProfileQuestionCard(row, current, confirmedPosition, draftPosition, isEditing) {
                        if (isEditing) {
                            ProfileQuestionInlineEditor(
                                text = text,
                                touched = drafts.containsKey(id),
                                enabled = !busy,
                                canSave = !locked && canSaveProfileQuestionAnswer(
                                    text, row.answer?.copy(current = current), false,
                                ),
                                inputFocus = inputFocus,
                                onChange = { drafts = HashMap(drafts + (id to it)) },
                                onSave = {
                                    submittedId = id
                                    submittedText = text
                                    onSaveAnswer(id, text)
                                },
                                onCancel = if (additional) null else ({
                                    drafts = HashMap(drafts - id)
                                    back()
                                }),
                            )
                        } else {
                            RealsThinDivider()
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                ProfileQuestionTextAction(
                                    text = if (current) "Editar respuesta" else "Actualizar respuesta",
                                    onClick = { editing = id },
                                    enabled = !busy,
                                    modifier = Modifier
                                        .focusRequester(editFocus)
                                        .focusProperties { canFocus = !busy },
                                )
                                if (row.selectable) {
                                    ProfileQuestionTextAction(
                                        text = if (selectedIndex >= 0) "Quitar" else "Agregar",
                                        onClick = { changeSelection(if (selectedIndex >= 0) selection - id else selection + id) },
                                        enabled = !locked && (selectedIndex >= 0 || selection.size < ProfileQuestionMaxPublicSelections),
                                    )
                                    if (selectedIndex >= 0) {
                                        ProfileQuestionTextAction("Subir", { changeSelection(selection.move(selectedIndex, selectedIndex - 1)) }, !locked && selectedIndex > 0)
                                        ProfileQuestionTextAction("Bajar", { changeSelection(selection.move(selectedIndex, selectedIndex + 1)) }, !locked && selectedIndex < selection.lastIndex)
                                    }
                                }
                                ProfileQuestionTextAction("Eliminar", { onDeleteAnswer(id) }, !locked, subdued = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun List<String>.move(from: Int, to: Int): List<String> {
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

@Composable
private fun ProfileQuestionHeader(
    title: String,
    onBack: () -> Unit,
    titleModifier: Modifier,
) {
    Column(Modifier.testTag("profile-question-header")) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth / LocalDensity.current.fontScale < 240.dp
            val heading: @Composable (Modifier) -> Unit = { modifier ->
                Text(
                    text = title,
                    style = RealsType.ScreenTitle,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = modifier.then(titleModifier).semantics { heading() },
                )
            }
            if (compact) {
                Column(Modifier.fillMaxWidth()) {
                    heading(Modifier.fillMaxWidth())
                    ProfileQuestionTextAction("Volver", onBack, modifier = Modifier.align(Alignment.End))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    heading(Modifier.weight(1f))
                    ProfileQuestionTextAction("Volver", onBack)
                }
            }
        }
        RealsBrandDivider(Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun ProfileQuestionStatus(
    state: ProfileQuestionUiState,
    progressLabel: String?,
    busy: Boolean,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progressLabel != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.error?.let { ApiErrorFeedbackCard(it, ErrorContext.ProfileQuestions) }
        state.mutationError?.takeUnless { it == state.error }?.let {
            ApiErrorFeedbackCard(it, ErrorContext.ProfileQuestions)
        }
        if (state.uncertain && state.error == null) {
            Text(
                "No pudimos confirmar el cambio. Consultá antes de volver a guardar.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.error != null || state.uncertain) {
            RealsSecondaryButton(text = "Reintentar consulta", onClick = onRetry, enabled = !busy)
        }
        state.feedback?.let {
            Text(
                text = it.message,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun ProfileQuestionPublicSelection(
    confirmedCount: Int,
    draftCount: Int?,
    canClear: Boolean,
    canSave: Boolean,
    onClear: () -> Unit,
    onSave: () -> Unit,
) {
    RealsEditorialSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Preguntas públicas",
                style = RealsType.SectionTitle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "$confirmedCount de $ProfileQuestionMaxPublicSelections en tu perfil",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (draftCount != null) {
                Text(
                    text = "$draftCount de $ProfileQuestionMaxPublicSelections elegidas · Cambios sin guardar",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RealsPrimaryButton(text = "Guardar selección", onClick = onSave, enabled = canSave)
                ProfileQuestionTextAction("Limpiar selección", onClear, canClear)
            }
        }
    }
}

@Composable
private fun ProfileQuestionCard(
    row: ProfileQuestionRowPresentation,
    current: Boolean,
    confirmedPosition: Int?,
    draftPosition: Int?,
    editing: Boolean,
    content: @Composable () -> Unit,
) {
    val visibility = when {
        confirmedPosition == null && draftPosition == null -> null
        confirmedPosition == null -> "Posición $draftPosition · Sin guardar"
        draftPosition == null -> "Pública · Posición $confirmedPosition · Pendiente de quitar"
        draftPosition != confirmedPosition -> "Pública · Posición $confirmedPosition, pasará a $draftPosition"
        else -> "Pública · Posición $confirmedPosition"
    }
    RealsEditorialSurface(
        Modifier.fillMaxWidth().testTag("profile-question-${row.question.id}").semantics {
            stateDescription = visibility ?: "Respuesta no pública"
        },
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = TextSafety.safeDisplay(row.question.prompt, maxLength = 180),
                style = RealsType.SectionTitle,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!editing && row.answer != null) {
                Text(
                    text = TextSafety.safeDisplay(row.answer.answer, maxLength = ProfileQuestionAnswerMaxLength),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (row.answer != null && !current) {
                Text(
                    text = "Versión anterior. Guardá la respuesta para actualizarla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            visibility?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun ProfileQuestionInlineEditor(
    text: String,
    touched: Boolean,
    enabled: Boolean,
    canSave: Boolean,
    inputFocus: FocusRequester,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    val validation = validateProfileQuestionAnswer(text)
    val error = validation.error.takeIf { touched }
    RealsThinDivider()
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text("Tu respuesta") },
        supportingText = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                error?.let { Text(it) }
                Text("${validation.characterCount}/$ProfileQuestionAnswerMaxLength")
            }
        },
        isError = error != null,
        enabled = enabled,
        maxLines = 4,
        shape = RoundedCornerShape(RealsRadii.Button),
        colors = realsOutlinedTextFieldColors(),
        modifier = Modifier.fillMaxWidth().focusRequester(inputFocus),
    )
    FlowRow(
        modifier = Modifier.padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RealsPrimaryButton(text = "Guardar", onClick = onSave, enabled = canSave)
        onCancel?.let { RealsSecondaryButton(text = "Cancelar edición", onClick = it, enabled = enabled) }
    }
}

@Composable
private fun ProfileQuestionTextAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    subdued: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
        shape = RoundedCornerShape(RealsRadii.Button),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (subdued) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(text)
    }
}
