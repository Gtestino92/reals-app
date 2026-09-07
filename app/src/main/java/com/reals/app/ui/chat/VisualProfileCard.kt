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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import com.reals.app.core.security.TextSafety
import com.reals.app.domain.model.ProfilePhoto
import com.reals.app.domain.model.PublicProfileQuestion
import com.reals.app.domain.model.VisualProfile
import com.reals.app.domain.model.isApprovedForExternalDisplay
import com.reals.app.ui.common.RealsSectionLabel
import com.reals.app.ui.common.RealsThinDivider
import com.reals.app.ui.profile.ProfilePhotoImageVariant
import com.reals.app.ui.profile.ProfilePhotoPresentationAspectRatio
import com.reals.app.ui.profile.profilePhotoImageRequest
import com.reals.app.ui.profile.stableProfilePhotoCacheKey
import com.reals.app.ui.theme.RealsRadii
import com.reals.app.ui.theme.RealsType
import kotlinx.coroutines.delay

@Composable
fun VisualProfileCard(
    profile: VisualProfile,
    showHeader: Boolean = true,
    presentationMode: ProfilePresentationMode = ProfilePresentationMode.Review,
) {
    val photos = remember(profile.photos) { visualProfilePhotosForDisplay(profile.photos) }
    val photoCacheIdentity = remember(photos) {
        photos.joinToString(separator = "|") { it.stableProfilePhotoCacheKey() }
    }
    val context = LocalContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    var loadedPhotoCount by rememberSaveable(profile.profileId, presentationMode.name, photoCacheIdentity) {
        mutableIntStateOf(initialVisualProfileLoadedPhotoCount(photos.size))
    }

    LaunchedEffect(profile.profileId, presentationMode, photoCacheIdentity) {
        loadedPhotoCount = initialVisualProfileLoadedPhotoCount(photos.size)
        while (loadedPhotoCount < photos.size) {
            delay(VisualProfileProgressivePhotoDelayMillis)
            loadedPhotoCount = nextVisualProfileLoadedPhotoCount(
                currentCount = loadedPhotoCount,
                totalPhotos = photos.size,
            )
        }
    }

    VisualProfilePhotoPrefetcher(
        photos = photos,
        selectedPhotoId = photos.firstOrNull()?.id,
        imageLoader = imageLoader,
        enabled = presentationMode == ProfilePresentationMode.Review && photos.isNotEmpty(),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (presentationMode) {
                ProfilePresentationMode.Review -> ReviewProfileContent(
                    profile = profile,
                    showHeader = showHeader,
                    loadedPhotoCount = loadedPhotoCount,
                    imageLoader = imageLoader,
                )

                ProfilePresentationMode.Browse -> BrowseProfileContent(
                    profile = profile,
                    showHeader = showHeader,
                    loadedPhotoCount = loadedPhotoCount,
                    imageLoader = imageLoader,
                )
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
    val photos = visualProfilePhotosForDisplay(profile.photos)
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
            add(VisualProfileContentBlock.Identity)
            photos.getOrNull(0)?.let { add(VisualProfileContentBlock.Photo(it, index = 0, total = photos.size)) }
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

internal data class BrowseProfilePhotoSelection(
    val selectedPhoto: ProfilePhoto,
    val selectedIndex: Int,
    val photos: List<ProfilePhoto>,
)

internal fun visualProfilePhotosForDisplay(
    photos: List<ProfilePhoto>,
): List<ProfilePhoto> = photos
    .filter { it.isApprovedForExternalDisplay() }
    .sortedBy { it.position }

internal fun browseProfilePhotoSelection(
    photos: List<ProfilePhoto>,
    selectedPhotoId: String?,
): BrowseProfilePhotoSelection? {
    val orderedPhotos = visualProfilePhotosForDisplay(photos)
    val selectedIndex = orderedPhotos.indexOfFirst { it.id == selectedPhotoId }
        .takeIf { it >= 0 }
        ?: 0
    val selectedPhoto = orderedPhotos.getOrNull(selectedIndex) ?: return null
    return BrowseProfilePhotoSelection(
        selectedPhoto = selectedPhoto,
        selectedIndex = selectedIndex,
        photos = orderedPhotos,
    )
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

internal fun visualProfileThumbnailContentDescription(
    profile: VisualProfile,
    photoIndex: Int,
    totalPhotos: Int,
): String {
    val name = profile.displayName
        .takeIf { it.isNotBlank() }
        ?.let { TextSafety.safeDisplay(it, maxLength = 100) }
    val ordinal = photoIndex + 1
    return if (name == null) {
        "Mostrar foto $ordinal de $totalPhotos del perfil"
    } else {
        "Mostrar foto $ordinal de $totalPhotos de $name"
    }
}

@Composable
private fun ReviewProfileContent(
    profile: VisualProfile,
    showHeader: Boolean,
    loadedPhotoCount: Int,
    imageLoader: ImageLoader,
) {
    visualProfileContentBlocks(profile, ProfilePresentationMode.Review).forEach { block ->
        when (block) {
            VisualProfileContentBlock.Affinities -> VisualProfileAffinitySection(profile)

            VisualProfileContentBlock.Bio -> VisualProfileBioSection(profile)
            VisualProfileContentBlock.CompactPhotos -> Unit
            VisualProfileContentBlock.Identity -> if (showHeader) VisualProfileIdentity(profile)
            is VisualProfileContentBlock.Photo -> VisualProfilePhotoFrame(
                profile = profile,
                photo = block.photo,
                photoIndex = block.index,
                totalPhotos = block.total,
                loadImage = block.index < loadedPhotoCount,
                imageLoader = imageLoader,
            )

            VisualProfileContentBlock.Questions -> VisualProfileQuestionsSection(profile.profileQuestions)
        }
    }
}

@Composable
private fun BrowseProfileContent(
    profile: VisualProfile,
    showHeader: Boolean,
    loadedPhotoCount: Int,
    imageLoader: ImageLoader,
) {
    visualProfileContentBlocks(profile, ProfilePresentationMode.Browse).forEach { block ->
        when (block) {
            VisualProfileContentBlock.Affinities -> VisualProfileAffinitySection(profile)

            VisualProfileContentBlock.Bio -> VisualProfileBioSection(profile)
            VisualProfileContentBlock.CompactPhotos -> CompactProfilePhotos(
                profile = profile,
                loadedPhotoCount = loadedPhotoCount,
                imageLoader = imageLoader,
            )
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
    VisualProfileInsertedSectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RealsSectionLabel("Bio")
            Text(
                text = TextSafety.safeDisplay(bio, maxLength = 1_000),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun VisualProfileAffinitySection(profile: VisualProfile) {
    val indicators = affinityIndicatorsForDisplay(profile.affinityIndicators)
    if (indicators.isEmpty()) return
    VisualProfileInsertedSectionCard {
        VisualAffinityIndicatorsContent(indicators = indicators)
    }
}

@Composable
private fun VisualProfileInsertedSectionCard(
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RealsRadii.Row),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun VisualProfilePhotoFrame(
    profile: VisualProfile,
    photo: ProfilePhoto,
    photoIndex: Int,
    totalPhotos: Int,
    loadImage: Boolean,
    imageLoader: ImageLoader,
) {
    val context = LocalContext.current
    val imageRequest = remember(context, photo.id, photo.url, loadImage) {
        if (loadImage) {
            profilePhotoImageRequest(
                context = context,
                photo = photo,
                variant = ProfilePhotoImageVariant.Full,
            )
        } else {
            null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            AsyncImage(
                model = imageRequest,
                contentDescription = visualProfilePhotoContentDescription(profile, photoIndex, totalPhotos),
                imageLoader = imageLoader,
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
private fun CompactProfilePhotos(
    profile: VisualProfile,
    loadedPhotoCount: Int,
    imageLoader: ImageLoader,
) {
    val context = LocalContext.current
    var selectedPhotoId by rememberSaveable(profile.profileId) { mutableStateOf<String?>(null) }
    val selection = browseProfilePhotoSelection(
        photos = profile.photos,
        selectedPhotoId = selectedPhotoId,
    ) ?: return
    VisualProfilePhotoPrefetcher(
        photos = selection.photos,
        selectedPhotoId = selection.selectedPhoto.id,
        imageLoader = imageLoader,
        enabled = true,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VisualProfilePhotoFrame(
            profile = profile,
            photo = selection.selectedPhoto,
            photoIndex = selection.selectedIndex,
            totalPhotos = selection.photos.size,
            loadImage = true,
            imageLoader = imageLoader,
        )
        if (selection.photos.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(selection.photos) { photoIndex, photo ->
                    val selected = photo.id == selection.selectedPhoto.id
                    Card(
                        onClick = { selectedPhotoId = photo.id },
                        shape = RoundedCornerShape(RealsRadii.Row),
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.semantics {
                            contentDescription = visualProfileThumbnailContentDescription(
                                profile = profile,
                                photoIndex = photoIndex,
                                totalPhotos = selection.photos.size,
                            )
                            stateDescription = if (selected) "Seleccionada" else "No seleccionada"
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .width(VisualProfileThumbnailWidth)
                                .height(136.dp)
                                .padding(4.dp),
                        ) {
                            val density = LocalDensity.current
                            val thumbnailWidthPx = remember(density) {
                                with(density) { VisualProfileThumbnailWidth.roundToPx() }
                            }
                            val thumbnailHeightPx = remember(density) {
                                with(density) { VisualProfileThumbnailHeight.roundToPx() }
                            }
                            val loadThumbnail = selected || photoIndex < loadedPhotoCount
                            val thumbnailRequest = remember(
                                context,
                                photo.id,
                                photo.url,
                                loadThumbnail,
                                thumbnailWidthPx,
                                thumbnailHeightPx,
                            ) {
                                if (loadThumbnail) {
                                    profilePhotoImageRequest(
                                        context = context,
                                        photo = photo,
                                        variant = ProfilePhotoImageVariant.Thumbnail,
                                        widthPx = thumbnailWidthPx,
                                        heightPx = thumbnailHeightPx,
                                    )
                                } else {
                                    null
                                }
                            }
                            AsyncImage(
                                model = thumbnailRequest,
                                contentDescription = null,
                                imageLoader = imageLoader,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(VisualProfileThumbnailHeight)
                                    .clip(RoundedCornerShape(RealsRadii.Row))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            PhotoCounterPill(
                                label = "${photoIndex + 1}/${selection.photos.size}",
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
}

@Composable
private fun VisualProfilePhotoPrefetcher(
    photos: List<ProfilePhoto>,
    selectedPhotoId: String?,
    imageLoader: ImageLoader,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val candidates = remember(photos, selectedPhotoId, enabled) {
        if (enabled) {
            visualProfilePrefetchCandidates(photos, selectedPhotoId)
        } else {
            emptyList()
        }
    }
    val prefetchIdentity = remember(candidates) {
        candidates.joinToString(separator = "|") { it.stableProfilePhotoCacheKey() }
    }

    LaunchedEffect(imageLoader, prefetchIdentity) {
        if (candidates.isEmpty()) return@LaunchedEffect
        delay(VisualProfilePrefetchStartDelayMillis)
        candidates.forEach { photo ->
            imageLoader.execute(
                profilePhotoImageRequest(
                    context = context,
                    photo = photo,
                    variant = ProfilePhotoImageVariant.Full,
                )
            )
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

    VisualProfileInsertedSectionCard {
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
}

private val VisualProfileThumbnailWidth = 108.dp
private val VisualProfileThumbnailHeight = 128.dp
