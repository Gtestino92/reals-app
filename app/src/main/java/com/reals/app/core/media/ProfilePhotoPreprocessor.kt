package com.reals.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

interface ProfilePhotoUploadPreprocessor {
    suspend fun prepare(sourceUri: Uri?): Result<PreparedProfilePhotoUpload>
}

data class PreparedProfilePhotoUpload(
    val file: File,
    val mimeType: String,
    val filename: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val fileOwnership: PreparedUploadFileOwnership = PreparedUploadFileOwnership.RepositoryOwned,
    val usedTrustedCropFastPath: Boolean = false,
)

enum class PreparedUploadFileOwnership {
    RepositoryOwned,
    CallerOwned,
}

enum class ProfilePhotoPreprocessingFailure {
    UndecodableSource,
    SourceTooLarge,
    CacheWriteFailure,
    EncodingFailure,
}

class ProfilePhotoPreprocessingException(
    val failure: ProfilePhotoPreprocessingFailure,
) : Exception(failure.name)

class ProfilePhotoPreprocessor(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProfilePhotoUploadPreprocessor {
    private val appContext = context.applicationContext

    override suspend fun prepare(sourceUri: Uri?): Result<PreparedProfilePhotoUpload> =
        withContext(dispatcher) {
            var outputFile: File? = null
            try {
                if (sourceUri == null) {
                    throw ProfilePhotoPreprocessingException(ProfilePhotoPreprocessingFailure.UndecodableSource)
                }
                val bounds = decodeBounds(sourceUri)
                if (bounds.width.toLong() * bounds.height.toLong() > MaxSourcePixels) {
                    throw ProfilePhotoPreprocessingException(ProfilePhotoPreprocessingFailure.SourceTooLarge)
                }

                val decoded = decodeSampledBitmap(sourceUri, bounds)
                val oriented = try {
                    applyExifOrientation(sourceUri, decoded)
                } catch (exception: Exception) {
                    if (!decoded.isRecycled) decoded.recycle()
                    throw exception
                }
                val normalized = try {
                    renderNormalizedBitmap(oriented)
                } finally {
                    if (!oriented.isRecycled) oriented.recycle()
                }
                val outputWidth = normalized.width
                val outputHeight = normalized.height

                outputFile = createPreparedUploadFile(profilePhotoPreparedUploadCacheDirectory(appContext))
                try {
                    outputFile.outputStream().use { output ->
                        val encoded = normalized.compress(Bitmap.CompressFormat.JPEG, PreparedUploadJpegQuality, output)
                        if (!encoded) {
                            throw ProfilePhotoPreprocessingException(ProfilePhotoPreprocessingFailure.EncodingFailure)
                        }
                    }
                } catch (exception: ProfilePhotoPreprocessingException) {
                    throw exception
                } catch (exception: Exception) {
                    throw ProfilePhotoPreprocessingException(ProfilePhotoPreprocessingFailure.CacheWriteFailure)
                } finally {
                    normalized.recycle()
                }

                Result.success(
                    PreparedProfilePhotoUpload(
                        file = outputFile,
                        mimeType = PreparedUploadMimeType,
                        filename = outputFile.name,
                        width = outputWidth,
                        height = outputHeight,
                        fileSizeBytes = outputFile.length(),
                    ),
                )
            } catch (exception: ProfilePhotoPreprocessingException) {
                outputFile?.delete()
                Result.failure(exception)
            } catch (exception: CancellationException) {
                outputFile?.delete()
                throw exception
            } catch (error: OutOfMemoryError) {
                outputFile?.delete()
                Result.failure(ProfilePhotoPreprocessingException(ProfilePhotoPreprocessingFailure.SourceTooLarge))
            } catch (exception: Exception) {
                outputFile?.delete()
                Result.failure(ProfilePhotoPreprocessingException(ProfilePhotoPreprocessingFailure.UndecodableSource))
            }
        }

    private fun decodeBounds(sourceUri: Uri): ImageBounds {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val input = appContext.contentResolver.openInputStream(sourceUri)
            ?: throw ProfilePhotoPreprocessingException(ProfilePhotoPreprocessingFailure.UndecodableSource)
        input.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw ProfilePhotoPreprocessingException(ProfilePhotoPreprocessingFailure.UndecodableSource)
        }
        return ImageBounds(options.outWidth, options.outHeight)
    }

    private fun decodeSampledBitmap(sourceUri: Uri, bounds: ImageBounds): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateSampleSize(bounds.width, bounds.height)
        }
        return appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw ProfilePhotoPreprocessingException(ProfilePhotoPreprocessingFailure.UndecodableSource)
    }

    private fun applyExifOrientation(sourceUri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = readOrientation(sourceUri)
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
            if (it != bitmap && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun renderNormalizedBitmap(source: Bitmap): Bitmap {
        val outputDimensions = resizedDimensions(source.width, source.height)
        val output = Bitmap.createBitmap(outputDimensions.width, outputDimensions.height, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(TransparentBackgroundColor)
            drawBitmap(
                source,
                Rect(0, 0, source.width, source.height),
                Rect(0, 0, outputDimensions.width, outputDimensions.height),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
            )
        }
        return output
    }

    private fun readOrientation(sourceUri: Uri): Int =
        appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

}

internal fun profilePhotoPreparedUploadCacheDirectory(context: Context): File =
    File(context.cacheDir, ProfilePhotoPreparedUploadCacheDirectoryName).also { it.mkdirs() }

internal fun createPreparedUploadFile(cacheDir: File): File {
    repeat(MaxFilenameAttempts) {
        val file = File(cacheDir, "${UUID.randomUUID()}.jpg")
        if (file.createNewFile()) return file
    }
    throw ProfilePhotoPreprocessingException(ProfilePhotoPreprocessingFailure.CacheWriteFailure)
}

internal fun calculateSampleSize(width: Int, height: Int): Int {
    val longEdge = max(width, height)
    if (longEdge <= MaxOutputDimensionPx) return 1
    return ceil(longEdge / MaxOutputDimensionPx.toFloat()).toInt().coerceAtLeast(1)
}

internal fun resizedDimensions(width: Int, height: Int): ImageBounds {
    val longEdge = max(width, height)
    if (longEdge <= MaxOutputDimensionPx) return ImageBounds(width, height)
    val scale = MaxOutputDimensionPx.toFloat() / longEdge
    return ImageBounds(
        width = (width * scale).roundToInt().coerceAtLeast(1),
        height = (height * scale).roundToInt().coerceAtLeast(1),
    )
}

internal data class ImageBounds(
    val width: Int,
    val height: Int,
)

internal const val PreparedUploadMimeType: String = "image/jpeg"
internal const val MaxPreparedUploadFileSizeBytes: Long = 5L * 1024L * 1024L
internal const val MaxOutputDimensionPx: Int = 2048
internal const val PreparedUploadJpegQuality: Int = 88
internal const val ProfilePhotoPreparedUploadCacheDirectoryName: String = "profile-photo-prepared-uploads"
private const val MaxSourcePixels: Long = 100_000_000L
private const val MaxFilenameAttempts: Int = 10
private val TransparentBackgroundColor: Int = Color.WHITE
