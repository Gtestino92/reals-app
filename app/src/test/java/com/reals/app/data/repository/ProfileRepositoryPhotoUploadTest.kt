package com.reals.app.data.repository

import com.reals.app.core.media.PreparedProfilePhotoUpload
import com.reals.app.core.media.PreparedUploadFileOwnership
import com.reals.app.core.media.PreparedUploadMimeType
import com.reals.app.core.media.ProfilePhotoUploadPreprocessor
import com.reals.app.core.network.ApiResult
import com.reals.app.testutil.FakeAuthTokenProvider
import com.reals.app.testutil.FakeRealsApi
import com.reals.app.testutil.backendErrorResponse
import com.reals.app.testutil.testApiExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProfileRepositoryPhotoUploadTest {
    @Test
    fun repositoryOwnedPreparedFileIsDeletedAfterSuccess() = runTest {
        val file = writeTempJpeg("success")
        val repository = repository(preprocessor = StaticPreprocessor(preparedUpload(file)))

        val result = repository.addMyProfilePhotoFile(null, position = 1)

        assertTrue(result is ApiResult.Success)
        assertFalse(file.exists())
    }

    @Test
    fun repositoryOwnedPreparedFileIsDeletedAfterNetworkFailure() = runTest {
        val file = writeTempJpeg("network-failure")
        val api = FakeRealsApi().apply {
            photoResponse = backendErrorResponse(500, "SERVER_ERROR", "boom")
        }
        val repository = repository(api = api, preprocessor = StaticPreprocessor(preparedUpload(file)))

        val result = repository.replaceMyProfilePhotoFile("photo-1", null)

        assertTrue(result is ApiResult.Failure)
        assertFalse(file.exists())
    }

    @Test
    fun repositoryOwnedPreparedFileIsDeletedAfterAuthFailure() = runTest {
        val file = writeTempJpeg("auth-failure")
        val tokenProvider = FakeAuthTokenProvider().apply { failMissingToken() }
        val repository = repository(
            tokenProvider = tokenProvider,
            preprocessor = StaticPreprocessor(preparedUpload(file)),
        )

        val result = repository.addMyProfilePhotoFile(null, position = 1)

        assertTrue(result is ApiResult.Failure)
        assertFalse(file.exists())
    }

    @Test
    fun callerOwnedPreviewFileIsNotDeletedByRepository() = runTest {
        val file = writeTempJpeg("caller-owned")
        val repository = repository(
            preprocessor = StaticPreprocessor(
                preparedUpload(file, ownership = PreparedUploadFileOwnership.CallerOwned),
            ),
        )

        val result = repository.addMyProfilePhotoFile(null, position = 1)

        assertTrue(result is ApiResult.Success)
        assertTrue(file.exists())
        file.delete()
    }

    @Test
    fun cancellationDeletesRepositoryOwnedPreparedFile() = runTest {
        val file = writeTempJpeg("cancel")
        val responseStarted = CompletableDeferred<Unit>()
        val api = FakeRealsApi().apply {
            beforeGetProfilePhotosResponse = { }
            beforeReorderPhotosResponse = { }
            beforeGetHomeResponse = { }
            beforeGetHomeStatusResponse = { }
            beforeGetMatchResponse = { }
            beforeGetFirstChatForMatchResponse = { }
            beforeGetChatResponse = { }
            beforeGetChatMessagesResponse = { }
            beforeSendChatMessageResponse = { }
            beforeSendChatAudioMessageResponse = { }
            beforeGetChatExitRequestsResponse = { }
            beforeGetSecondChatStatusResponse = { }
            beforeGetConnectionNegotiationResponse = { }
            beforeGetConnectionSchedulingAvailabilityResponse = { }
            beforeSubmitConnectionProposalsResponse = { }
            beforeAcceptConnectionProposalResponse = { }
            beforeRejectConnectionPartnerProposalsResponse = { }
            beforeGetPartnerPersonalMessageResponse = { }
        }
        api.beforeAddPhotoResponse = {
            responseStarted.complete(Unit)
            awaitCancellation()
        }
        val repository = repository(api = api, preprocessor = StaticPreprocessor(preparedUpload(file)))

        val job = launch { repository.addMyProfilePhotoFile(null, position = 1) }
        responseStarted.await()
        assertTrue(file.exists())

        job.cancel()
        job.join()

        assertFalse(file.exists())
    }

    private fun repository(
        api: FakeRealsApi = FakeRealsApi(),
        tokenProvider: FakeAuthTokenProvider = FakeAuthTokenProvider(),
        preprocessor: ProfilePhotoUploadPreprocessor,
    ): ProfileRepository =
        ProfileRepository(
            context = null,
            api = api,
            tokenProvider = tokenProvider,
            apiExecutor = testApiExecutor(),
            photoPreprocessor = preprocessor,
        )

    private class StaticPreprocessor(
        private val prepared: PreparedProfilePhotoUpload,
    ) : ProfilePhotoUploadPreprocessor {
        override suspend fun prepare(sourceUri: android.net.Uri?): Result<PreparedProfilePhotoUpload> =
            Result.success(prepared)
    }

    private fun preparedUpload(
        file: File,
        ownership: PreparedUploadFileOwnership = PreparedUploadFileOwnership.RepositoryOwned,
    ): PreparedProfilePhotoUpload =
        PreparedProfilePhotoUpload(
            file = file,
            mimeType = PreparedUploadMimeType,
            filename = file.name,
            width = 100,
            height = 100,
            fileSizeBytes = file.length(),
            fileOwnership = ownership,
        )

    private fun writeTempJpeg(prefix: String): File =
        File.createTempFile("prepared-$prefix", ".jpg").apply {
            writeText("jpeg")
            deleteOnExit()
        }

}
