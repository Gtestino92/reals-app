package com.reals.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.PublicProfileQuestion
import com.reals.app.domain.model.VisualProfile
import com.reals.app.ui.common.RealsSectionLabel
import com.reals.app.ui.common.RealsThinDivider
import com.reals.app.ui.profile.ProfilePhotoPresentationAspectRatio
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType

@Composable
fun VisualProfileCard(
    profile: VisualProfile,
    showHeader: Boolean = true,
    presentationMode: ProfilePresentationMode = ProfilePresentationMode.Review,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (presentationMode) {
                ProfilePresentationMode.Review -> ReviewProfileContent(profile, showHeader)
                ProfilePresentationMode.Browse -> BrowseProfileContent(profile, showHeader)
            }
        }
    }
}

enum class ProfilePresentationMode {
    Review,
    Browse,
}

internal sealed interface VisualProfileContentBlock {
    data class Photo(val photo: ProfilePhoto, val index: Int, val total: Int) : VisualProfileContentBlock
    data object CompactPhotos : VisualProfileContentBlock
    data object Identity : VisualProfileContentBlock
    data object Bio : VisualProfileContentBlock
    data object Affinities : VisualProfileContentBlock
    data object Questions : VisualProfileContentBlock
}

internal fun visualProfileContentBlocks(
    profile: VisualProfile,
    presentationMode: ProfilePresentationMode,
): List<VisualProfileContentBlock> {
    val photos = profile.photos.sortedBy { it.position }
    val hasBio = profile.bio?.isNotBlank() == true
    val hasAffinities = affinityIndicatorsForDisplay(profile.affinityIndicators).isNotEmpty()
    val hasQuestions = publicProfileQuestionsForDisplay(profile.profileQuestions).isNotEmpty()

    return when (presentationMode) {
        ProfilePresentationMode.Browse -> buildList {
            if (photos.isNotEmpty()) add(VisualProfileContentBlock.CompactPhotos)
            add(VisualProfileContentBlock.Identity)
            if (hasBio) add(VisualProfileContentBlock.Bio)
            if (hasAffinities) add(VisualProfileContentBlock.Affinities)
            if (hasQuestions) add(VisualProfileContentBlock.Questions)
        }

        ProfilePresentationMode.Review -> buildList {
            photos.getOrNull(0)?.let { add(VisualProfileContentBlock.Photo(it, index = 0, total = photos.size)) }
            add(VisualProfileContentBlock.Identity)
            if (hasBio) add(VisualProfileContentBlock.Bio)
            photos.getOrNull(1)?.let { add(VisualProfileContentBlock.Photo(it, index = 1, total = photos.size)) }
            if (hasAffinities) add(VisualProfileContentBlock.Affinities)
            photos.getOrNull(2)?.let { add(VisualProfileContentBlock.Photo(it, index = 2, total = photos.size)) }
            if (hasQuestions) add(VisualProfileContentBlock.Questions)
            photos.drop(3).forEachIndexed { offset, photo ->
                add(VisualProfileContentBlock.Photo(photo, index = offset + 3, total = photos.size))
            }
        }
    }
}

internal fun visualProfilePhotoContentDescription(
    profile: VisualProfile,
    photoIndex: Int,
    totalPhotos: Int,
): String {
    val name = profile.displayName
        .takeIf { it.isNotBlank() }
        ?.let { TextSafety.safeDisplay(it, maxLength = 100) }
    val ordinal = photoIndex + 1
    return if (name == null) {
        "Foto $ordinal de $totalPhotos del perfil"
    } else {
        "Foto $ordinal de $totalPhotos de $name"
    }
}

@Composable
private fun ReviewProfileContent(
    profile: VisualProfile,
    showHeader: Boolean,
) {
    visualProfileContentBlocks(profile, ProfilePresentationMode.Review).forEach { block ->
        when (block) {
            VisualProfileContentBlock.Affinities -> VisualAffinityIndicatorsContent(
                indicators = affinityIndicatorsForDisplay(profile.affinityIndicators),
            )

            VisualProfileContentBlock.Bio -> VisualProfileBioSection(profile)
            VisualProfileContentBlock.CompactPhotos -> Unit
            VisualProfileContentBlock.Identity -> if (showHeader) VisualProfileIdentity(profile)
            is VisualProfileContentBlock.Photo -> VisualProfilePhotoFrame(
                profile = profile,
                photo = block.photo,
                photoIndex = block.index,
                totalPhotos = block.total,
            )

            VisualProfileContentBlock.Questions -> VisualProfileQuestionsSection(profile.profileQuestions)
        }
    }
}

@Composable
private fun BrowseProfileContent(
    profile: VisualProfile,
    showHeader: Boolean,
) {
    visualProfileContentBlocks(profile, ProfilePresentationMode.Browse).forEach { block ->
        when (block) {
            VisualProfileContentBlock.Affinities -> VisualAffinityIndicatorsContent(
                indicators = affinityIndicatorsForDisplay(profile.affinityIndicators),
            )

            VisualProfileContentBlock.Bio -> VisualProfileBioSection(profile)
            VisualProfileContentBlock.CompactPhotos -> CompactProfilePhotos(profile)
            VisualProfileContentBlock.Identity -> if (showHeader) VisualProfileIdentity(profile)
            is VisualProfileContentBlock.Photo -> Unit
            VisualProfileContentBlock.Questions -> VisualProfileQuestionsSection(profile.profileQuestions)
        }
    }
}

@Composable
private fun VisualProfileIdentity(profile: VisualProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = TextSafety.safeDisplay(profile.displayName, maxLength = 100),
            style = RealsType.SectionTitle,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "${profile.age} años",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VisualProfileBioSection(profile: VisualProfile) {
    val bio = profile.bio?.takeIf { it.isNotBlank() } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RealsSectionLabel("Bio")
        Text(
            text = TextSafety.safeDisplay(bio, maxLength = 1_000),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun VisualProfilePhotoFrame(
    profile: VisualProfile,
    photo: ProfilePhoto,
    photoIndex: Int,
    totalPhotos: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            AsyncImage(
                model = photo.url,
                contentDescription = visualProfilePhotoContentDescription(profile, photoIndex, totalPhotos),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ProfilePhotoPresentationAspectRatio)
                    .clip(RoundedCornerShape(RealsRadii.Row))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            PhotoCounterPill(
                label = "Foto ${photoIndex + 1} de $totalPhotos",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun CompactProfilePhotos(profile: VisualProfile) {
    val photos = profile.photos.sortedBy { it.position }
    val hero = photos.firstOrNull() ?: return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VisualProfilePhotoFrame(
            profile = profile,
            photo = hero,
            photoIndex = 0,
            totalPhotos = photos.size,
        )
        if (photos.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(photos.drop(1)) { offset, photo ->
                    val photoIndex = offset + 1
                    Box(
                        modifier = Modifier
                            .width(108.dp)
                            .height(136.dp),
                    ) {
                        AsyncImage(
                            model = photo.url,
                            contentDescription = visualProfilePhotoContentDescription(
                                profile = profile,
                                photoIndex = photoIndex,
                                totalPhotos = photos.size,
                            ),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(136.dp)
                                .clip(RoundedCornerShape(RealsRadii.Row))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        PhotoCounterPill(
                            label = "${photoIndex + 1}/${photos.size}",
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCounterPill(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
        )
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
        RealsSectionLabel("Preguntas del perfil")
        RealsThinDivider()
        visibleQuestions.forEach { question ->
            Card(
                shape = RoundedCornerShape(RealsRadii.Row),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
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
