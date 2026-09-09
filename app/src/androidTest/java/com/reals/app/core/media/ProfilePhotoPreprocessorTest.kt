package com.reals.app.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ProfilePhotoPreprocessorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preprocessor = ProfilePhotoPreprocessor(context)

    @Test
    fun landscapeImageIsResizedWithinMaximumDimensionAndKeepsAspectRatio() = runBlocking {
        val source = writeBitmap("landscape.jpg", 4096, 1024, Bitmap.CompressFormat.JPEG)

        val prepared = preprocessor.prepare(source.toUri()).getOrThrow()
        val output = BitmapFactory.decodeFile(prepared.file.absolutePath)

        assertEquals("image/jpeg", prepared.mimeType)
        assertTrue(prepared.filename.endsWith(".jpg"))
        assertTrue(output.width <= MaxOutputDimensionPx)
        assertTrue(output.height <= MaxOutputDimensionPx)
        assertRatioClose(4f, output.width.toFloat() / output.height)
        assertIsJpeg(prepared.file)
        prepared.file.delete()
        Unit
    }

    @Test
    fun portraitImageIsResizedWithinMaximumDimension() = runBlocking {
        val source = writeBitmap("portrait.jpg", 1024, 4096, Bitmap.CompressFormat.JPEG)

        val prepared = preprocessor.prepare(source.toUri()).getOrThrow()
        val output = BitmapFactory.decodeFile(prepared.file.absolutePath)

        assertTrue(output.width <= MaxOutputDimensionPx)
        assertTrue(output.height <= MaxOutputDimensionPx)
        assertRatioClose(0.25f, output.width.toFloat() / output.height)
        prepared.file.delete()
        Unit
    }

    @Test
    fun smallerImageIsNotEnlarged() = runBlocking {
        val source = writeBitmap("small.jpg", 80, 100, Bitmap.CompressFormat.JPEG)

        val prepared = preprocessor.prepare(source.toUri()).getOrThrow()
        val output = BitmapFactory.decodeFile(prepared.file.absolutePath)

        assertEquals(80, output.width)
        assertEquals(100, output.height)
        prepared.file.delete()
        Unit
    }

    @Test
    fun pngInputBecomesJpegWithOpaqueWhiteTransparencyBackground() = runBlocking {
        val source = writeTransparentPng("transparent.png")

        val prepared = preprocessor.prepare(source.toUri()).getOrThrow()
        val output = BitmapFactory.decodeFile(prepared.file.absolutePath)
        val corner = output.getPixel(0, 0)

        assertIsJpeg(prepared.file)
        assertEquals("image/jpeg", prepared.mimeType)
        assertTrue(Color.red(corner) > 240)
        assertTrue(Color.green(corner) > 240)
        assertTrue(Color.blue(corner) > 240)
        assertEquals(255, Color.alpha(corner))
        prepared.file.delete()
        Unit
    }

    @Test
    fun rotatedExifInputProducesUprightOutputAndMetadataIsNotCopied() = runBlocking {
        val source = writeBitmap("rotated.jpg", 40, 80, Bitmap.CompressFormat.JPEG)
        ExifInterface(source.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_MAKE, "Test Camera")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "34/1,36/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S")
            saveAttributes()
        }

        val prepared = preprocessor.prepare(source.toUri()).getOrThrow()
        val output = BitmapFactory.decodeFile(prepared.file.absolutePath)
        val outputExif = ExifInterface(prepared.file.absolutePath)

        assertTrue(output.width > output.height)
        assertNull(outputExif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(outputExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        prepared.file.delete()
        Unit
    }

    @Test
    fun malformedInputProducesControlledFailure() = runBlocking {
        val source = File(context.cacheDir, "malformed-profile-photo.bin").apply {
            writeText("not an image")
        }

        val failure = preprocessor.prepare(source.toUri()).exceptionOrNull()

        assertTrue(failure is ProfilePhotoPreprocessingException)
        assertEquals(
            ProfilePhotoPreprocessingFailure.UndecodableSource,
            (failure as ProfilePhotoPreprocessingException).failure,
        )
    }

    private fun writeBitmap(
        filename: String,
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat,
    ): File {
        val file = File(context.cacheDir, filename)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, Color.rgb((x * 255) / width, (y * 255) / height, 128))
            }
        }
        file.outputStream().use { output -> bitmap.compress(format, 100, output) }
        bitmap.recycle()
        return file
    }

    private fun writeTransparentPng(filename: String): File {
        val file = File(context.cacheDir, filename)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (x in 8 until 24) {
            for (y in 8 until 24) {
                bitmap.setPixel(x, y, Color.RED)
            }
        }
        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        bitmap.recycle()
        return file
    }

    private fun assertIsJpeg(file: File) {
        val bytes = file.inputStream().use { input -> ByteArray(2).also { input.read(it) } }
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xD8, bytes[1].toInt() and 0xFF)
    }

    private fun assertRatioClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.03f)
    }
}
