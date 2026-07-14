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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
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
                errorText = "No pudimos abrir esta imagen.\nElegí otra foto o volvé a intentarlo."
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                        .aspectRatio(ProfilePhotoPresentationAspectRatio)
                        .clipToBounds()
                        .background(Color.DarkGray)
                        .border(2.dp, Color.White.copy(alpha = 0.86f))
                        .onSizeChanged { newSize ->
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
                        }
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
                                        transform = transform
                                            ?.zoomBy(zoom)
                                            ?.panBy(pan.x, pan.y)
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val currentBitmap = bitmap
                    val currentTransform = transform
                    if (currentBitmap != null && currentTransform != null) {
                        CropPreviewImage(currentBitmap, currentTransform)
                    }
                    if (loading || processing) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
            errorText?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel, enabled = !processing) {
                    Text("Cancelar", color = Color.White)
                }
                OutlinedButton(
                    onClick = { transform = transform?.reset() },
                    enabled = !processing && bitmap != null,
                ) {
                    Text("Restablecer")
                }
                OutlinedButton(
                    onClick = {
                        exportRequest = transform?.sourceCropRect()
                    },
                    enabled = !processing && !loading && bitmap != null && transform != null,
                ) {
                    Text("Usar foto")
                }
            }
            Spacer(Modifier.height(2.dp))
        }
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
