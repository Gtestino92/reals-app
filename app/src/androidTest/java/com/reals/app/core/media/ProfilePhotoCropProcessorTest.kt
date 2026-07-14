package com.reals.app.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reals.app.ui.profile.ProfilePhotoOutputHeightPx
import com.reals.app.ui.profile.ProfilePhotoOutputWidthPx
import com.reals.app.ui.profile.centeredCropTransform
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfilePhotoCropProcessorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val processor = ProfilePhotoCropProcessor(context)

    @Test
    fun outputDimensionsAreCanonicalAndJpegDecodable() = runBlocking {
        val sourceFile = writeSyntheticPng("source-output.png")
        val bitmap = processor.decodeUprightBitmap(sourceFile.toUri()).getOrThrow()
        val cropRect = centeredCropTransform(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            viewportWidth = 400f,
            viewportHeight = 500f,
        ).sourceCropRect()

        val outputUri = processor.exportCroppedJpeg(bitmap, cropRect).getOrThrow()
        val outputBitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(outputUri))

        assertEquals(ProfilePhotoOutputWidthPx, outputBitmap.width)
        assertEquals(ProfilePhotoOutputHeightPx, outputBitmap.height)
        assertTrue(File(requireNotNull(outputUri.path)).name.endsWith(".jpg"))
    }

    @Test
    fun syntheticSourceCropMapsToExpectedRegion() = runBlocking {
        val sourceFile = writeSyntheticPng("source-region.png")
        val bitmap = processor.decodeUprightBitmap(sourceFile.toUri()).getOrThrow()
        val cropRect = centeredCropTransform(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            viewportWidth = 400f,
            viewportHeight = 500f,
        ).sourceCropRect()

        val outputUri = processor.exportCroppedJpeg(bitmap, cropRect).getOrThrow()
        val outputBitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(outputUri))

        assertTrue(outputBitmap.getPixel(ProfilePhotoOutputWidthPx / 2, ProfilePhotoOutputHeightPx / 2) != Color.TRANSPARENT)
    }

    private fun writeSyntheticPng(filename: String): File {
        val file = File(context.cacheDir, filename)
        val bitmap = Bitmap.createBitmap(80, 100, Bitmap.Config.ARGB_8888)
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                bitmap.setPixel(x, y, Color.rgb(x * 3, y * 2, 128))
            }
        }
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        return file
    }
}
