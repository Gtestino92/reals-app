package com.reals.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.reals.app.core.network.ApiError
import com.reals.app.core.network.ErrorContext
import com.reals.app.domain.model.AffinityQuestion
import com.reals.app.domain.model.AffinityQuestionCatalog
import com.reals.app.ui.common.ApiErrorFeedbackCard
import com.reals.app.ui.root.AffinityQuestionSource
import com.reals.app.ui.root.AffinityQuestionnaireDestination
import com.reals.app.ui.root.AffinityQuestionnaireUiState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AffinityQuestionnaireScreen(
    state: AffinityQuestionnaireUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStartContinue: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenCategory: (categoryId: String) -> Unit,
    onOpenReviewedAnswer: (questionId: String) -> Unit,
    onSkipQuestion: () -> Unit,
    onNextQuestion: () -> Unit,
    onSelectAnswer: (questionId: String, answerCode: String) -> Unit,
    onDeleteAnswer: (questionId: String) -> Unit,
) {
    val catalog = state.catalog
    if (catalog == null && state.loading) {
        AffinityQuestionnaireLoading(onBack)
        return
    }
    if (catalog == null && state.error != null) {
        AffinityQuestionnaireInitialFailure(
            state = state,
            onRetry = onRetry,
            onBack = onBack,
        )
        return
    }
    if (catalog == null) {
        AffinityQuestionnaireLoading(onBack)
        return
    }

    val destination = state.destination
    when (destination) {
        AffinityQuestionnaireDestination.Overview -> AffinityQuestionnaireOverview(
            state = state,
            catalog = catalog,
            onBack = onBack,
            onRetry = onRetry,
            onStartContinue = onStartContinue,
            onOpenCategories = onOpenCategories,
            onOpenReview = onOpenReview,
        )

        AffinityQuestionnaireDestination.Categories -> AffinityQuestionnaireCategories(
            state = state,
            catalog = catalog,
            onBack = onBack,
            onRetry = onRetry,
            onOpenCategory = onOpenCategory,
        )

        AffinityQuestionnaireDestination.Review -> AffinityQuestionnaireReview(
            state = state,
            catalog = catalog,
            onBack = onBack,
            onRetry = onRetry,
            onStartContinue = onStartContinue,
            onOpenReviewedAnswer = onOpenReviewedAnswer,
        )

        is AffinityQuestionnaireDestination.Question -> AffinitySingleQuestionScreen(
            state = state,
            catalog = catalog,
            destination = destination,
            onBack = onBack,
            onRetry = onRetry,
            onSkipQuestion = onSkipQuestion,
            onNextQuestion = onNextQuestion,
            onSelectAnswer = onSelectAnswer,
            onDeleteAnswer = onDeleteAnswer,
        )
    }
}

@Composable
private fun AffinityQuestionnaireOverview(
    state: AffinityQuestionnaireUiState,
    catalog: AffinityQuestionCatalog,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStartContinue: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenReview: () -> Unit,
) {
    val progress = catalog.progress(state.answers)
    val reviewRows = catalog.reviewRows(state.answers)
    val actionPolicy = progress.overviewActionPolicy(hasReviewRows = reviewRows.isNotEmpty())
    val actionsEnabled = state.mutation == null && !state.loading && !state.refreshing
    val primaryLabel = when (actionPolicy.primaryAction) {
        AffinityOverviewPrimaryAction.Start -> "Empezar a responder"
        AffinityOverviewPrimaryAction.Continue -> "Continuar respondiendo"
        AffinityOverviewPrimaryAction.Review -> "Revisar mis respuestas"
        null -> null
    }

    AffinityQuestionnaireLazySurface(
        title = "Preguntas de afinidad",
        onBack = onBack,
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Tus respuestas son opcionales y privadas. Otras personas no ven tus respuestas concretas. Reals puede usarlas para mejorar afinidades, proponer temas de conversación y mostrar categorías generales compartidas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AffinityQuestionnaireStatus(
                    state = state,
                    onRetry = onRetry,
                    showMutationStatus = false,
                )
                Text(
                    text = "${progress.answeredCount} de ${progress.totalQuestionCount} respondidas",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (primaryLabel != null) {
                    Button(
                        onClick = if (actionPolicy.primaryAction == AffinityOverviewPrimaryAction.Review) {
                            onOpenReview
                        } else {
                            onStartContinue
                        },
                        enabled = actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(primaryLabel)
                    }
                } else {
                    Text(
                        text = "No hay preguntas disponibles por ahora.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (actionPolicy.showExploreCategories) {
                    OutlinedButton(
                        onClick = onOpenCategories,
                        enabled = actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Explorar por categoría")
                    }
                }
                if (actionPolicy.showEmptyReviewText) {
                    Text(
                        text = "Todavía no hay respuestas para revisar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (actionPolicy.showSecondaryReview) {
                    OutlinedButton(
                        onClick = onOpenReview,
                        enabled = actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Revisar mis respuestas")
                    }
                }
            }
        }
    }
}

@Composable
private fun AffinityQuestionnaireCategories(
    state: AffinityQuestionnaireUiState,
    catalog: AffinityQuestionCatalog,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenCategory: (categoryId: String) -> Unit,
) {
    val groups = catalog.groupQuestionsForPresentation(state.answers)
    val actionsEnabled = state.mutation == null
    AffinityQuestionnaireLazySurface(
        title = "Explorar por categoría",
        onBack = onBack,
    ) {
        item {
            AffinityQuestionnaireStatus(state, onRetry)
        }
        if (groups.isEmpty()) {
            item {
                Text(
                    text = "No hay categorías disponibles por ahora.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(groups, key = { it.category.id }) { group ->
                AffinityCategoryRow(
                    group = group,
                    actionsEnabled = actionsEnabled,
                    onOpenCategory = onOpenCategory,
                )
            }
        }
    }
}

@Composable
private fun AffinityCategoryRow(
    group: AffinityQuestionCategoryPresentation,
    actionsEnabled: Boolean,
    onOpenCategory: (categoryId: String) -> Unit,
) {
    val actionLabel = when (group.validAnsweredCount) {
        0 -> "Empezar"
        group.totalQuestionCount -> "Revisar"
        else -> "Continuar"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = group.category.title,
                style = MaterialTheme.typography.titleLarge,
            )
            group.category.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${group.validAnsweredCount} de ${group.totalQuestionCount} respondidas",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onOpenCategory(group.category.id) },
                enabled = actionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun AffinityQuestionnaireReview(
    state: AffinityQuestionnaireUiState,
    catalog: AffinityQuestionCatalog,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStartContinue: () -> Unit,
    onOpenReviewedAnswer: (questionId: String) -> Unit,
) {
    val reviewGroups = catalog.reviewRows(state.answers)
    val progress = catalog.progress(state.answers)
    val actionsEnabled = state.mutation == null
    AffinityQuestionnaireLazySurface(
        title = "Revisar mis respuestas",
        onBack = onBack,
    ) {
        item {
            AffinityQuestionnaireStatus(state, onRetry)
        }
        if (reviewGroups.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Todavía no hay respuestas para revisar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (progress.totalQuestionCount > 0) {
                        Button(
                            onClick = onStartContinue,
                            enabled = actionsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Empezar a responder")
                        }
                    }
                }
            }
        } else {
            reviewGroups.forEach { group ->
                item(key = "category-${group.category.id}") {
                    Text(
                        text = group.category.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(group.rows, key = { it.question.id }) { row ->
                    AffinityReviewRow(
                        row = row,
                        enabled = actionsEnabled,
                        onOpenReviewedAnswer = onOpenReviewedAnswer,
                    )
                }
            }
        }
    }
}

@Composable
private fun AffinityReviewRow(
    row: AffinityQuestionReviewRowPresentation,
    enabled: Boolean,
    onOpenReviewedAnswer: (questionId: String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onOpenReviewedAnswer(row.question.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = row.question.prompt,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = row.selectedOptionLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AffinitySingleQuestionScreen(
    state: AffinityQuestionnaireUiState,
    catalog: AffinityQuestionCatalog,
    destination: AffinityQuestionnaireDestination.Question,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSkipQuestion: () -> Unit,
    onNextQuestion: () -> Unit,
    onSelectAnswer: (questionId: String, answerCode: String) -> Unit,
    onDeleteAnswer: (questionId: String) -> Unit,
) {
    val question = catalog.findAnswerableQuestion(destination.questionId)
    if (question == null) {
        AffinityQuestionnaireOverview(
            state = state,
            catalog = catalog,
            onBack = onBack,
            onRetry = onRetry,
            onStartContinue = {},
            onOpenCategories = {},
            onOpenReview = {},
        )
        return
    }
    val category = catalog.categoryFor(question)
    val currentAnswer = question.currentValidAnswer(state.answers)
    val selectedCode = when {
        state.draftQuestionId == question.id ->
            state.draftAnswerCode

        else ->
            currentAnswer?.answerCode
    }
    val mutationActive = state.mutation != null
    val mutationsEnabled = !mutationActive && !state.loading && !state.refreshing
    val questionSaving = state.mutation?.questionId == question.id
    val positionLabel = catalog.questionPositionLabel(question.id, destination.source)
    val progress = catalog.progress(state.answers)
    val isReviewQuestion = destination.source == AffinityQuestionSource.Review
    val draftChanged =
        selectedCode != null &&
                selectedCode != currentAnswer?.answerCode

    AffinityQuestionnaireLazySurface(
        title = category?.title ?: "Preguntas de afinidad",
        onBack = onBack,
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AffinityQuestionnaireStatus(
                    state = state,
                    onRetry = onRetry,
                    showMutationStatus = false,
                )
                positionLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } ?: Text(
                    text = "${progress.answeredCount} de ${progress.totalQuestionCount} respondidas",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AffinityQuestionCard(
                    question = question,
                    selectedCode = selectedCode,
                    currentAnswerExists = currentAnswer != null,
                    mutationsEnabled = mutationsEnabled,
                    questionSaving = questionSaving,
                    mutationError = state.mutationError
                        ?.takeIf { state.mutationFeedbackQuestionId == question.id },
                    message = state.message
                        ?.takeIf { state.mutationFeedbackQuestionId == question.id },
                    onSelectAnswer = onSelectAnswer,
                    onDeleteAnswer = onDeleteAnswer,
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isReviewQuestion) {
                        Button(
                            onClick = if (draftChanged) onNextQuestion else onBack,
                            enabled = !mutationActive,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    mutationActive -> "Guardando..."
                                    draftChanged -> "Guardar cambios"
                                    else -> "Volver a mis respuestas"
                                }
                            )
                        }
                    } else {
                        if (selectedCode == null) {
                            OutlinedButton(
                                onClick = onSkipQuestion,
                                enabled = !mutationActive,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Omitir")
                            }
                        }
                        Button(
                            onClick = onNextQuestion,
                            enabled = currentAnswer != null && !mutationActive,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Siguiente")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AffinityQuestionCard(
    question: AffinityQuestion,
    selectedCode: String?,
    currentAnswerExists: Boolean,
    mutationsEnabled: Boolean,
    questionSaving: Boolean,
    mutationError: ApiError?,
    message: String?,
    onSelectAnswer: (questionId: String, answerCode: String) -> Unit,
    onDeleteAnswer: (questionId: String) -> Unit,
) {
    var showSavingIndicator by remember(question.id) { mutableStateOf(false) }
    LaunchedEffect(questionSaving) {
        if (questionSaving) {
            delay(AffinityQuestionSavingIndicatorDelayMillis.milliseconds)
            showSavingIndicator = true
        } else {
            showSavingIndicator = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.titleLarge,
            )
            question.options.forEach { option ->
                val selected = selectedCode == option.code
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            enabled = mutationsEnabled,
                            role = Role.RadioButton,
                            onClick = { onSelectAnswer(question.id, option.code) },
                        )
                        .semantics {
                            stateDescription = if (selected) "Seleccionada" else "No seleccionada"
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    RadioButton(
                        selected = selected,
                        enabled = mutationsEnabled,
                        onClick = null,
                    )
                    Text(
                        text = option.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (currentAnswerExists) {
                TextButton(
                    onClick = { onDeleteAnswer(question.id) },
                    enabled = mutationsEnabled,
                ) {
                    Text("Quitar respuesta")
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                when {
                    questionSaving && showSavingIndicator -> Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text(
                            text = "Guardando...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    mutationError != null -> ApiErrorFeedbackCard(mutationError, ErrorContext.AffinityQuestions)

                    message != null -> Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private const val AffinityQuestionSavingIndicatorDelayMillis = 400L

@Composable
private fun AffinityQuestionnaireLazySurface(
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
                TextButton(onClick = onBack) {
                    Text("Volver")
                }
            }
        }
        content()
    }
}

@Composable
private fun AffinityQuestionnaireStatus(
    state: AffinityQuestionnaireUiState,
    onRetry: () -> Unit,
    showMutationStatus: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.refreshing) {
            Text(
                text = "Actualizando...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (shouldShowAffinityParentMutationStatus(showMutationStatus, state.mutation)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Guardando respuesta...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.error?.let {
            ApiErrorFeedbackCard(it, ErrorContext.AffinityQuestions)
            OutlinedButton(
                onClick = onRetry,
                enabled = !state.loading && !state.refreshing && state.mutation == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun AffinityQuestionnaireLoading(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Preguntas de afinidad",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        CircularProgressIndicator()
        Text(
            text = "Cargando preguntas...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onBack) {
            Text("Volver")
        }
    }
}

@Composable
private fun AffinityQuestionnaireInitialFailure(
    state: AffinityQuestionnaireUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Preguntas de afinidad",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        state.error?.let {
            ApiErrorFeedbackCard(it, ErrorContext.AffinityQuestions)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRetry,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reintentar")
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Volver")
            }
        }
    }
}
