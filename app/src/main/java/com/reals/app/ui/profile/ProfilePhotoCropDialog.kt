package com.reals.app.ui.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reals.app.core.media.ProfilePhotoCropProcessor
import kotlin.math.roundToInt

@Composable
internal fun ProfilePhotoCropDialog(
    request: ProfilePhotoCropRequest,
    onCancel: () -> Unit,
    onCropped: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val processor = remember(context) { ProfilePhotoCropProcessor(context.applicationContext) }
    var bitmap by remember(request.sourceUri) { mutableStateOf<Bitmap?>(null) }
    var transform by remember(request.sourceUri) { mutableStateOf<ProfilePhotoCropTransform?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var loading by remember(request.sourceUri) { mutableStateOf(true) }
    var processing by remember(request.sourceUri) { mutableStateOf(false) }
    var errorText by remember(request.sourceUri) { mutableStateOf<String?>(null) }
    var exportRequest by remember(request.sourceUri) { mutableStateOf<ProfilePhotoSourceCropRect?>(null) }

    LaunchedEffect(request.sourceUri) {
        loading = true
        errorText = null
        processor.decodeUprightBitmap(request.sourceUri)
            .onSuccess { decoded ->
                bitmap = decoded
                transform = if (viewportSize.width > 0 && viewportSize.height > 0) {
                    centeredCropTransform(
                        sourceWidth = decoded.width,
                        sourceHeight = decoded.height,
                        viewportWidth = viewportSize.width.toFloat(),
                        viewportHeight = viewportSize.height.toFloat(),
                    )
                } else {
                    null
                }
            }
            .onFailure {
                errorText = "No pudimos abrir ésta imagen.\nElegí otra foto o volvé a intentarlo."
            }
        loading = false
    }

    DisposableEffect(bitmap) {
        val displayedBitmap = bitmap
        onDispose {
            displayedBitmap?.recycle()
        }
    }

    BackHandler(enabled = !processing) {
        onCancel()
    }

    LaunchedEffect(exportRequest) {
        val cropRect = exportRequest ?: return@LaunchedEffect
        val currentBitmap = bitmap ?: return@LaunchedEffect
        processing = true
        errorText = null
        processor.exportCroppedJpeg(currentBitmap, cropRect)
            .onSuccess(onCropped)
            .onFailure {
                errorText = "No pudimos preparar la foto.\nVolvé a intentarlo."
            }
        processing = false
        exportRequest = null
    }

    Dialog(
        onDismissRequest = {
            if (!processing) onCancel()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
    ) {
        ProfilePhotoCropDialogContent(
            bitmap = bitmap,
            transform = transform,
            loading = loading,
            processing = processing,
            errorText = errorText,
            onCancel = onCancel,
            onReset = { transform = transform?.reset() },
            onConfirm = { exportRequest = transform?.sourceCropRect() },
            onViewportSizeChanged = { newSize ->
                viewportSize = newSize
                val currentBitmap = bitmap
                if (currentBitmap != null && newSize.width > 0 && newSize.height > 0) {
                    transform = transform
                        ?.resized(newSize.width.toFloat(), newSize.height.toFloat())
                        ?: centeredCropTransform(
                            sourceWidth = currentBitmap.width,
                            sourceHeight = currentBitmap.height,
                            viewportWidth = newSize.width.toFloat(),
                            viewportHeight = newSize.height.toFloat(),
                        )
                }
            },
            onGestureTransform = { pan, zoom ->
                transform = transform
                    ?.zoomBy(zoom)
                    ?.panBy(pan.x, pan.y)
            },
        )
    }
}

@Composable
internal fun ProfilePhotoCropDialogContent(
    bitmap: Bitmap?,
    transform: ProfilePhotoCropTransform?,
    loading: Boolean,
    processing: Boolean,
    errorText: String?,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    onViewportSizeChanged: (IntSize) -> Unit,
    onGestureTransform: (pan: Offset, zoom: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding(),
    ) {
        val density = LocalDensity.current
        val layoutSpec = remember(maxWidth, maxHeight, density.fontScale, errorText) {
            profilePhotoCropLayoutSpec(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                fontScale = density.fontScale,
                hasError = errorText != null,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(layoutSpec.outerPadding),
            verticalArrangement = Arrangement.spacedBy(layoutSpec.verticalSpacing),
        ) {
            Text(
                text = "Ajustar foto",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Mové y ampliá la imagen para elegir qué se va a mostrar.",
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium,
            )
            CropViewport(
                bitmap = bitmap,
                transform = transform,
                loading = loading,
                processing = processing,
                minViewportHeight = layoutSpec.minViewportHeight,
                onViewportSizeChanged = onViewportSizeChanged,
                onGestureTransform = onGestureTransform,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            errorText?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ProfilePhotoCropActions(
                actionLayout = layoutSpec.actionLayout,
                processing = processing,
                canReset = !processing && bitmap != null,
                canConfirm = !processing && !loading && bitmap != null && transform != null,
                onCancel = onCancel,
                onReset = onReset,
                onConfirm = onConfirm,
            )
            Spacer(Modifier.height(layoutSpec.bottomSpacerHeight))
        }
    }
}

@Composable
private fun CropViewport(
    bitmap: Bitmap?,
    transform: ProfilePhotoCropTransform?,
    loading: Boolean,
    processing: Boolean,
    minViewportHeight: Dp,
    onViewportSizeChanged: (IntSize) -> Unit,
    onGestureTransform: (pan: Offset, zoom: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val maxViewportWidth = maxWidth
        val maxViewportHeight = maxHeight
        val viewportModifier = if (maxViewportWidth / ProfilePhotoPresentationAspectRatio <= maxViewportHeight) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.height(maxViewportHeight)
        }
        Box(
            modifier = viewportModifier
                .heightIn(min = minOf(minViewportHeight, maxViewportHeight))
                .aspectRatio(ProfilePhotoPresentationAspectRatio)
                .testTag(ProfilePhotoCropViewportTag)
                .clipToBounds()
                .background(Color.DarkGray)
                .border(2.dp, Color.White.copy(alpha = 0.86f))
                .onSizeChanged(onViewportSizeChanged)
                .pointerInput(bitmap, processing) {
                    if (bitmap == null || processing) return@pointerInput
                    awaitEachGesture {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedPointers = event.changes.count { it.pressed }
                            if (pressedPointers == 0) break
                            val pan = event.calculatePan()
                            val zoom = if (pressedPointers >= 2) event.calculateZoom() else 1f
                            if (pan != Offset.Zero || zoom != 1f) {
                                onGestureTransform(pan, zoom)
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null && transform != null) {
                CropPreviewImage(bitmap, transform)
            }
            if (loading || processing) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun ProfilePhotoCropActions(
    actionLayout: ProfilePhotoCropActionLayout,
    processing: Boolean,
    canReset: Boolean,
    canConfirm: Boolean,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
) {
    when (actionLayout) {
        ProfilePhotoCropActionLayout.Normal -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfilePhotoCropActionsNormalTag),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CancelAction(onCancel = onCancel, enabled = !processing)
                ResetAction(onReset = onReset, enabled = canReset)
                ConfirmAction(onConfirm = onConfirm, enabled = canConfirm)
            }
        }

        ProfilePhotoCropActionLayout.ConstrainedRow -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfilePhotoCropActionsConstrainedTag),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConfirmAction(
                    onConfirm = onConfirm,
                    enabled = canConfirm,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CancelAction(
                        onCancel = onCancel,
                        enabled = !processing,
                        modifier = Modifier.weight(1f),
                    )
                    ResetAction(
                        onReset = onReset,
                        enabled = canReset,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        ProfilePhotoCropActionLayout.ConstrainedStacked -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfilePhotoCropActionsConstrainedTag),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConfirmAction(
                    onConfirm = onConfirm,
                    enabled = canConfirm,
                    modifier = Modifier.fillMaxWidth(),
                )
                CancelAction(
                    onCancel = onCancel,
                    enabled = !processing,
                    modifier = Modifier.fillMaxWidth(),
                )
                ResetAction(
                    onReset = onReset,
                    enabled = canReset,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CancelAction(
    onCancel: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onCancel,
        enabled = enabled,
        modifier = modifier
            .widthIn(min = ProfilePhotoCropSecondaryActionMinWidth)
            .testTag(ProfilePhotoCropCancelActionTag),
    ) {
        Text("Cancelar", color = Color.White)
    }
}

@Composable
private fun ResetAction(
    onReset: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onReset,
        enabled = enabled,
        modifier = modifier
            .widthIn(min = ProfilePhotoCropSecondaryActionMinWidth)
            .testTag(ProfilePhotoCropResetActionTag),
    ) {
        Text("Restablecer")
    }
}

@Composable
private fun ConfirmAction(
    onConfirm: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onConfirm,
        enabled = enabled,
        modifier = modifier
            .sizeIn(minWidth = ProfilePhotoCropPrimaryActionMinWidth)
            .testTag(ProfilePhotoCropConfirmActionTag),
    ) {
        Text("Usar foto")
    }
}

@Composable
private fun CropPreviewImage(bitmap: Bitmap, transform: ProfilePhotoCropTransform) {
    val density = LocalDensity.current
    val imageWidth = with(density) { (bitmap.width * transform.scale).toDp() }
    val imageHeight = with(density) { (bitmap.height * transform.scale).toDp() }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier
            .requiredSize(imageWidth, imageHeight)
            .offset {
                IntOffset(
                    x = transform.offsetX.roundToInt(),
                    y = transform.offsetY.roundToInt(),
                )
            },
    )
}

internal enum class ProfilePhotoCropActionLayout {
    Normal,
    ConstrainedRow,
    ConstrainedStacked,
}

internal data class ProfilePhotoCropLayoutSpec(
    val actionLayout: ProfilePhotoCropActionLayout,
    val outerPadding: Dp,
    val verticalSpacing: Dp,
    val minViewportHeight: Dp,
    val bottomSpacerHeight: Dp,
)

internal fun profilePhotoCropLayoutSpec(
    maxWidth: Dp,
    maxHeight: Dp,
    fontScale: Float,
    hasError: Boolean,
): ProfilePhotoCropLayoutSpec {
    val compactHeight = maxHeight < 560.dp
    val narrowWidth = maxWidth < 340.dp
    val largeText = fontScale >= 1.45f
    val errorConsumesVerticalSpace = hasError && maxHeight < 640.dp
    val constrained = narrowWidth || compactHeight || largeText || errorConsumesVerticalSpace
    val stackSecondaryActions = maxWidth < 340.dp || fontScale >= 1.9f || maxHeight < 500.dp
    return if (!constrained) {
        ProfilePhotoCropLayoutSpec(
            actionLayout = ProfilePhotoCropActionLayout.Normal,
            outerPadding = 20.dp,
            verticalSpacing = 16.dp,
            minViewportHeight = 240.dp,
            bottomSpacerHeight = 2.dp,
        )
    } else {
        ProfilePhotoCropLayoutSpec(
            actionLayout = if (stackSecondaryActions) {
                ProfilePhotoCropActionLayout.ConstrainedStacked
            } else {
                ProfilePhotoCropActionLayout.ConstrainedRow
            },
            outerPadding = if (maxHeight < 500.dp || maxWidth < 340.dp) 12.dp else 16.dp,
            verticalSpacing = if (maxHeight < 500.dp) 8.dp else 10.dp,
            minViewportHeight = if (maxHeight < 500.dp) 128.dp else 160.dp,
            bottomSpacerHeight = 0.dp,
        )
    }
}

internal const val ProfilePhotoCropRootTag = "profile_photo_crop_root"
internal const val ProfilePhotoCropViewportTag = "profile_photo_crop_viewport"
internal const val ProfilePhotoCropActionsNormalTag = "profile_photo_crop_actions_normal"
internal const val ProfilePhotoCropActionsConstrainedTag = "profile_photo_crop_actions_constrained"
internal const val ProfilePhotoCropCancelActionTag = "profile_photo_crop_cancel"
internal const val ProfilePhotoCropResetActionTag = "profile_photo_crop_reset"
internal const val ProfilePhotoCropConfirmActionTag = "profile_photo_crop_confirm"

private val ProfilePhotoCropPrimaryActionMinWidth = 112.dp
private val ProfilePhotoCropSecondaryActionMinWidth = 88.dp
