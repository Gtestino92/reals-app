package com.reals.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ErrorContext
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.ProfileQuestionAnswer
import com.reals.app.domain.model.ProfileQuestionCatalog
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.root.ProfileQuestionDestination
import com.reals.app.ui.root.ProfileQuestionMutationKind
import com.reals.app.ui.root.ProfileQuestionUiState
import kotlinx.coroutines.delay

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
) {
    val catalog = state.catalog
    when {
        state.loading && catalog == null -> ProfileQuestionLoading(onBack)
        catalog == null && state.error != null -> ProfileQuestionInitialFailure(state, onRetry, onBack)
        catalog == null -> ProfileQuestionLoading(onBack)
        else -> when (val destination = state.destination) {
            ProfileQuestionDestination.Overview -> ProfileQuestionOverview(
                state = state,
                catalog = catalog,
                onBack = onBack,
                onRetry = onRetry,
                onOpenQuestions = onOpenQuestions,
                onOpenSelection = onOpenSelection,
            )
            ProfileQuestionDestination.Questions -> ProfileQuestionList(
                state = state,
                catalog = catalog,
                onBack = onBack,
                onRetry = onRetry,
                onOpenEditor = onOpenEditor,
            )
            is ProfileQuestionDestination.Editor -> ProfileQuestionEditor(
                state = state,
                catalog = catalog,
                questionId = destination.questionId,
                onBack = onBack,
                onRetry = onRetry,
                onSaveAnswer = onSaveAnswer,
                onDeleteAnswer = onDeleteAnswer,
            )
            ProfileQuestionDestination.Selection -> ProfileQuestionSelectionEditor(
                state = state,
                catalog = catalog,
                onBack = onBack,
                onRetry = onRetry,
                onSelectionDraftChange = onSelectionDraftChange,
                onSaveSelection = onSaveSelection,
            )
        }
    }
}

@Composable
private fun ProfileQuestionOverview(
    state: ProfileQuestionUiState,
    catalog: ProfileQuestionCatalog,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenQuestions: () -> Unit,
    onOpenSelection: () -> Unit,
) {
    val overview = catalog.overview(state.answers)
    val actionsEnabled = state.mutation == null && !state.loading && !state.refreshing
    ProfileQuestionLazySurface("Preguntas del perfil", onBack) {
        item { ProfileQuestionStatus(state, onRetry) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${overview.answeredCount} de ${overview.totalQuestionCount} respondidas")
                Text("${overview.selectedCount} de 3 elegidas para mostrar")
                if (overview.totalQuestionCount == 0) {
                    Text(
                        text = "No hay preguntas disponibles por ahora.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Button(
                onClick = onOpenQuestions,
                enabled = actionsEnabled && overview.totalQuestionCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Responder o editar preguntas")
            }
        }
        item {
            OutlinedButton(
                onClick = onOpenSelection,
                enabled = actionsEnabled && state.answers.any { it.current && it.answer.isNotBlank() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Elegir preguntas públicas")
            }
        }
    }
}

@Composable
private fun ProfileQuestionList(
    state: ProfileQuestionUiState,
    catalog: ProfileQuestionCatalog,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenEditor: (questionId: String) -> Unit,
) {
    val rows = catalog.rows(state.answers)
    val enabled = state.mutation == null
    ProfileQuestionLazySurface("Responder preguntas", onBack) {
        item { ProfileQuestionStatus(state, onRetry) }
        if (rows.isEmpty()) {
            item {
                Text("No hay preguntas disponibles por ahora.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(rows, key = { it.question.id }) { row ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onOpenEditor(row.question.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(TextSafety.safeDisplay(row.question.prompt, maxLength = 180))
                        row.answer?.let { answer ->
                            Text(
                                text = TextSafety.safeDisplay(answer.answer, maxLength = ProfileQuestionAnswerMaxLength),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (!answer.current) {
                                Text("Versión anterior", color = MaterialTheme.colorScheme.error)
                            } else if (row.selectedPosition != null) {
                                Text("Visible en posición ${row.selectedPosition}")
                            }
                        } ?: Text("Sin responder", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { onOpenEditor(row.question.id) }, enabled = enabled) {
                            Text("Abrir")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileQuestionEditor(
    state: ProfileQuestionUiState,
    catalog: ProfileQuestionCatalog,
    questionId: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSaveAnswer: (questionId: String, answer: String) -> Unit,
    onDeleteAnswer: (questionId: String) -> Unit,
) {
    val question = catalog.questions.firstOrNull { it.id == questionId } ?: return
    val savedAnswer = state.answers.firstOrNull { it.questionId == questionId }
    var text by rememberSaveable(questionId, savedAnswer?.updatedAt, state.mutation == null) {
        mutableStateOf(TextFieldValue(savedAnswer?.answer.orEmpty()))
    }
    val validation = validateProfileQuestionAnswer(text.text)
    val mutationActive = state.mutation != null
    val savingThisQuestion = state.mutation?.questionId == questionId
    val canSave = !mutationActive &&
        validation.valid &&
        validation.normalizedAnswer != savedAnswer?.answer.orEmpty().trim()
    ProfileQuestionLazySurface("Editar respuesta", onBack) {
        item { ProfileQuestionStatus(state, onRetry, showMutationStatus = false) }
        item {
            ProfileQuestionEditorCard(
                prompt = question.prompt,
                text = text,
                onTextChange = { value ->
                    text = value.copy(text = value.text.lineSequence().firstOrNull().orEmpty())
                },
                validation = validation,
                canSave = canSave,
                canDelete = !mutationActive && savedAnswer != null,
                saving = savingThisQuestion,
                feedbackMessage = state.feedback
                    ?.takeIf { it.questionId == questionId && it.destination == state.destination }
                    ?.message,
                onSave = { onSaveAnswer(questionId, text.text) },
                onDelete = { onDeleteAnswer(questionId) },
            )
        }
    }
}

@Composable
private fun ProfileQuestionEditorCard(
    prompt: String,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    validation: ProfileQuestionAnswerValidation,
    canSave: Boolean,
    canDelete: Boolean,
    saving: Boolean,
    feedbackMessage: String?,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var showSaving by remember { mutableStateOf(false) }
    LaunchedEffect(saving) {
        if (saving) {
            delay(ProfileQuestionSavingIndicatorDelayMillis)
            showSaving = true
        } else {
            showSaving = false
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(TextSafety.safeDisplay(prompt, maxLength = 180), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                label = { Text("Tu respuesta") },
                supportingText = {
                    Text("${validation.characterCount}/$ProfileQuestionAnswerMaxLength")
                },
                isError = validation.error != null,
                modifier = Modifier.fillMaxWidth(),
            )
            validation.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSave, enabled = canSave) { Text("Guardar") }
                OutlinedButton(onClick = onDelete, enabled = canDelete) { Text("Eliminar") }
            }
            Column(modifier = Modifier.heightIn(min = 32.dp), verticalArrangement = Arrangement.Center) {
                when {
                    saving && showSaving -> Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text("Guardando...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    feedbackMessage != null -> Text(feedbackMessage, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ProfileQuestionSelectionEditor(
    state: ProfileQuestionUiState,
    catalog: ProfileQuestionCatalog,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectionDraftChange: (questionIds: List<String>) -> Unit,
    onSaveSelection: () -> Unit,
) {
    val rows = catalog.selectionRows(state.answers)
    val currentSelection = selectedProfileQuestionIds(state.answers)
    val draft = state.selectionDraftQuestionIds
    val validDraft = profileQuestionSelectionDraftIsValid(draft, rows)
    val canSave = state.mutation == null && validDraft && draft != currentSelection
    ProfileQuestionLazySurface("Preguntas públicas", onBack) {
        item { ProfileQuestionStatus(state, onRetry) }
        item {
            Text("${draft.size} de 3 elegidas para mostrar")
        }
        if (rows.isEmpty()) {
            item {
                Text(
                    text = "Respondé alguna pregunta actual para poder mostrarla en tu perfil.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(rows, key = { it.question.id }) { row ->
                val selectedIndex = draft.indexOf(row.question.id)
                val selected = selectedIndex >= 0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(TextSafety.safeDisplay(row.question.prompt, maxLength = 180))
                        Text(TextSafety.safeDisplay(row.answer.answer, maxLength = ProfileQuestionAnswerMaxLength))
                        if (selected) Text("Posición ${selectedIndex + 1}", color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onSelectionDraftChange(
                                        if (selected) draft - row.question.id else draft + row.question.id,
                                    )
                                },
                                enabled = state.mutation == null &&
                                    (selected || draft.size < ProfileQuestionMaxPublicSelections),
                            ) {
                                Text(if (selected) "Quitar" else "Agregar")
                            }
                            OutlinedButton(
                                onClick = { onSelectionDraftChange(draft.move(selectedIndex, selectedIndex - 1)) },
                                enabled = state.mutation == null && selectedIndex > 0,
                            ) {
                                Text("Subir")
                            }
                            OutlinedButton(
                                onClick = { onSelectionDraftChange(draft.move(selectedIndex, selectedIndex + 1)) },
                                enabled = state.mutation == null && selected && selectedIndex < draft.lastIndex,
                            ) {
                                Text("Bajar")
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { onSelectionDraftChange(emptyList()) },
                enabled = state.mutation == null && draft.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Limpiar selección")
            }
        }
        item {
            Button(onClick = onSaveSelection, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar selección")
            }
        }
    }
}

@Composable
private fun ProfileQuestionLazySurface(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onBack) { Text("Volver") }
            }
        }
        content()
    }
}

@Composable
private fun ProfileQuestionStatus(
    state: ProfileQuestionUiState,
    onRetry: () -> Unit,
    showMutationStatus: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.refreshing) Text("Actualizando...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showMutationStatus && state.mutation != null) {
            Text(
                text = when (state.mutation.kind) {
                    ProfileQuestionMutationKind.Selection -> "Guardando selección..."
                    ProfileQuestionMutationKind.Upsert,
                    ProfileQuestionMutationKind.Delete -> "Guardando respuesta..."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.error?.let {
            ApiErrorFeedbackCard(it, ErrorContext.ProfileQuestions)
            OutlinedButton(
                onClick = onRetry,
                enabled = !state.loading && !state.refreshing && state.mutation == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reintentar")
            }
        }
        state.mutationError?.let { ApiErrorFeedbackCard(it, ErrorContext.ProfileQuestions) }
        state.feedback?.takeIf { it.destination == state.destination }?.let {
            Text(it.message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ProfileQuestionLoading(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Preguntas del perfil", style = MaterialTheme.typography.headlineMedium)
        CircularProgressIndicator()
        Text("Cargando preguntas...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onBack) { Text("Volver") }
    }
}

@Composable
private fun ProfileQuestionInitialFailure(
    state: ProfileQuestionUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Preguntas del perfil", style = MaterialTheme.typography.headlineMedium)
        state.error?.let { ApiErrorFeedbackCard(it, ErrorContext.ProfileQuestions) }
        Button(onClick = onRetry, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

private fun List<String>.move(fromIndex: Int, toIndex: Int): List<String> {
    if (fromIndex !in indices || toIndex !in indices) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}

private const val ProfileQuestionSavingIndicatorDelayMillis = 400L
