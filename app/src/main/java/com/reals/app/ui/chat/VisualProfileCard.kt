package com.reals.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.VisualProfile

@Composable
fun VisualProfileCard(profile: VisualProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = TextSafety.safeDisplay(profile.displayName, maxLength = 100),
                style = MaterialTheme.typography.titleLarge,
            )
            Text("Edad: ${profile.age}")
            profile.bio?.takeIf { it.isNotBlank() }?.let {
                Text(TextSafety.safeDisplay(it, maxLength = 1_000))
            }
            if (profile.photos.isEmpty()) {
                Text("No hay fotos para revisar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                profile.photos.forEach { photo ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Foto ${photo.position}")
                            AsyncImage(
                                model = photo.url,
                                contentDescription = "Foto ${photo.position}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
