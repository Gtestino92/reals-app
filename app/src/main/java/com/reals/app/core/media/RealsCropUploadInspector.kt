package com.reals.app.core.media

import android.graphics.BitmapFactory
import android.net.Uri
import com.reals.app.ui.profile.ProfilePhotoOutputHeightPx
import com.reals.app.ui.profile.ProfilePhotoOutputWidthPx
import java.io.File

internal fun interface RealsCropUploadInspector {
    fun inspect(uri: Uri?): RealsCropInspection
}

internal sealed interface RealsCropInspection {
    data class Trusted(
        val file: File,
        val width: Int,
        val height: Int,
        val fileSizeBytes: Long,
    ) : RealsCropInspection

    data object NotTrusted : RealsCropInspection
}

internal data class EncodedImageMetadata(
    val width: Int,
    val height: Int,
    val mimeType: String?,
)

internal fun interface EncodedImageMetadataReader {
    fun read(file: File): EncodedImageMetadata?
}

internal object AndroidEncodedImageMetadataReader : EncodedImageMetadataReader {
    override fun read(file: File): EncodedImageMetadata? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.inputStream().use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        return EncodedImageMetadata(
            width = options.outWidth,
            height = options.outHeight,
            mimeType = options.outMimeType,
        )
    }
}

internal class DefaultRealsCropUploadInspector(
    private val cropCacheDir: File,
    private val metadataReader: EncodedImageMetadataReader = AndroidEncodedImageMetadataReader,
    private val maxFileSizeBytes: Long = MaxPreparedUploadFileSizeBytes,
) : RealsCropUploadInspector {
    override fun inspect(uri: Uri?): RealsCropInspection =
        inspect(scheme = uri?.scheme, path = uri?.path)

    fun inspect(scheme: String?, path: String?): RealsCropInspection {
        if (scheme != "file") return RealsCropInspection.NotTrusted
        val candidate = path?.let(::File) ?: return RealsCropInspection.NotTrusted
        val canonicalFile = runCatching { candidate.canonicalFile }.getOrNull()
            ?: return RealsCropInspection.NotTrusted
        val canonicalDirectory = runCatching { cropCacheDir.canonicalFile }.getOrNull()
            ?: return RealsCropInspection.NotTrusted

        if (canonicalFile.parentFile != canonicalDirectory) return RealsCropInspection.NotTrusted
        if (!canonicalFile.exists()) return RealsCropInspection.NotTrusted
        if (!canonicalFile.isFile) return RealsCropInspection.NotTrusted
        if (!canonicalFile.canRead()) return RealsCropInspection.NotTrusted

        val length = canonicalFile.length()
        if (length <= 0L || length > maxFileSizeBytes) return RealsCropInspection.NotTrusted

        val metadata = runCatching { metadataReader.read(canonicalFile) }.getOrNull()
            ?: return RealsCropInspection.NotTrusted
        if (metadata.mimeType != PreparedUploadMimeType) return RealsCropInspection.NotTrusted
        if (metadata.width != ProfilePhotoOutputWidthPx) return RealsCropInspection.NotTrusted
        if (metadata.height != ProfilePhotoOutputHeightPx) return RealsCropInspection.NotTrusted

        return RealsCropInspection.Trusted(
            file = canonicalFile,
            width = metadata.width,
            height = metadata.height,
            fileSizeBytes = length,
        )
    }
}
