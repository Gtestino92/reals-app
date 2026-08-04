package com.reals.app.core.media

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal class ProfilePhotoUploadPipelinePreprocessor(
    private val fallbackPreprocessor: ProfilePhotoUploadPreprocessor,
    private val cropInspector: RealsCropUploadInspector,
    private val preparedCacheDir: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProfilePhotoUploadPreprocessor {
    override suspend fun prepare(sourceUri: Uri?): Result<PreparedProfilePhotoUpload> =
        withContext(dispatcher) {
            when (val inspection = cropInspector.inspect(sourceUri)) {
                is RealsCropInspection.Trusted -> prepareTrustedCrop(inspection)
                RealsCropInspection.NotTrusted -> fallbackPreprocessor.prepare(sourceUri)
            }
        }

    private fun prepareTrustedCrop(inspection: RealsCropInspection.Trusted): Result<PreparedProfilePhotoUpload> {
        var outputFile: File? = null
        return try {
            outputFile = createPreparedUploadFile(preparedCacheDir)
            inspection.file.copyTo(outputFile, overwrite = true)
            Result.success(
                PreparedProfilePhotoUpload(
                    file = outputFile,
                    mimeType = PreparedUploadMimeType,
                    filename = outputFile.name,
                    width = inspection.width,
                    height = inspection.height,
                    fileSizeBytes = outputFile.length(),
                    fileOwnership = PreparedUploadFileOwnership.RepositoryOwned,
                    usedTrustedCropFastPath = true,
                ),
            )
        } catch (exception: ProfilePhotoPreprocessingException) {
            outputFile?.delete()
            Result.failure(exception)
        } catch (exception: CancellationException) {
            outputFile?.delete()
            throw exception
        } catch (exception: Exception) {
            outputFile?.delete()
            Result.failure(ProfilePhotoPreprocessingException(ProfilePhotoPreprocessingFailure.CacheWriteFailure))
        }
    }
}
