package com.reals.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reals.app.domain.model.PublicProfileQuestion
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.VisualProfile
import com.reals.app.ui.profile.ProfilePhotoPresentationAspectRatio

@Composable
fun VisualProfileCard(
    profile: VisualProfile,
    showHeader: Boolean = true,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showHeader) {
                Text(
                    text = TextSafety.safeDisplay(profile.displayName, maxLength = 100),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("Edad: ${profile.age}")
            }
            profile.bio?.takeIf { it.isNotBlank() }?.let {
                Text(TextSafety.safeDisplay(it, maxLength = 1_000))
            }
            VisualProfileQuestionsSection(profile.profileQuestions)
            if (profile.photos.isEmpty()) {
                Text("No hay fotos para revisar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                profile.photos.forEach { photo ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AsyncImage(
                                model = photo.url,
                                contentDescription = "Foto ${photo.position}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(ProfilePhotoPresentationAspectRatio)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun publicProfileQuestionsForDisplay(
    questions: List<PublicProfileQuestion>,
): List<PublicProfileQuestion> =
    questions
        .filter { it.questionId.isNotBlank() && it.prompt.isNotBlank() && it.answer.isNotBlank() }
        .sortedBy { it.position }

@Composable
private fun VisualProfileQuestionsSection(
    questions: List<PublicProfileQuestion>,
) {
    val visibleQuestions = publicProfileQuestionsForDisplay(questions)
    if (visibleQuestions.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Preguntas del perfil", style = MaterialTheme.typography.titleMedium)
        visibleQuestions.forEach { question ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = TextSafety.safeDisplay(question.prompt, maxLength = 180),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = TextSafety.safeDisplay(question.answer, maxLength = 160),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
