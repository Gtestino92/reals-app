package com.reals.app.core.media

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.reals.app.ui.profile.ProfilePhotoOutputHeightPx
import com.reals.app.ui.profile.ProfilePhotoOutputWidthPx
import com.reals.app.ui.profile.ProfilePhotoSourceCropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

internal class ProfilePhotoCropProcessor(
    private val context: Context,
) {
    suspend fun decodeUprightBitmap(uri: Uri): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    decodeWithImageDecoder(uri)
                } else {
                    decodeWithBitmapFactory(uri)
                }
            }.mapCatching { bitmap ->
                require(bitmap.width > 0 && bitmap.height > 0) { "Decoded bitmap is empty." }
                bitmap
            }
        }

    suspend fun exportCroppedJpeg(bitmap: Bitmap, cropRect: ProfilePhotoSourceCropRect): Result<Uri> =
        withContext(Dispatchers.Default) {
            runCatching {
                val outputBitmap = Bitmap.createBitmap(
                    ProfilePhotoOutputWidthPx,
                    ProfilePhotoOutputHeightPx,
                    Bitmap.Config.ARGB_8888,
                )
                Canvas(outputBitmap).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(
                        bitmap,
                        Rect(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom),
                        Rect(0, 0, ProfilePhotoOutputWidthPx, ProfilePhotoOutputHeightPx),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
                    )
                }
                outputBitmap
                val outputFile = File(
                    profilePhotoCropCacheDirectory(context),
                    "profile-photo-crop-${UUID.randomUUID()}.jpg",
                )
                try {
                    outputFile.outputStream().use { output ->
                        check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, JpegQuality, output)) {
                            "JPEG compression failed."
                        }
                    }
                } finally {
                    outputBitmap.recycle()
                }
                outputFile.toUri()
            }
        }

    @TargetApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val sample = calculateSampleSize(info.size.width, info.size.height)
            if (sample > 1) {
                decoder.setTargetSize(info.size.width / sample, info.size.height / sample)
            }
        }
    }

    private fun decodeWithBitmapFactory(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image bounds unavailable." }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: error("Image decode failed.")
        return applyExifOrientation(uri, decoded)
    }

    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it != bitmap) bitmap.recycle()
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        val longEdge = max(width, height)
        if (longEdge <= DecodeLongEdgeCeilingPx) return 1
        return ceil(longEdge / DecodeLongEdgeCeilingPx.toFloat()).toInt().coerceAtLeast(1)
    }
}

private const val DecodeLongEdgeCeilingPx = 4096
private const val JpegQuality = 90
