package com.reals.app.di

import android.content.Context
import com.reals.app.BuildConfig
import com.reals.app.core.firebase.FirebaseAuthTokenProvider
import com.reals.app.core.network.ApiExecutor
import com.reals.app.data.api.RealsApiClient
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.usecase.ActivateProfileUseCase
import com.reals.app.domain.usecase.AddMockProfilePhotoUseCase
import com.reals.app.domain.usecase.AddProfilePhotoFileUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.DeleteAccountUseCase
import com.reals.app.domain.usecase.DeleteProfilePhotoUseCase
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
import com.reals.app.domain.usecase.ReplaceMockProfilePhotoUseCase
import com.reals.app.domain.usecase.ReplaceProfilePhotoFileUseCase
import com.reals.app.domain.usecase.UpdateMatchFiltersUseCase
import com.reals.app.domain.usecase.UpdateProfileUseCase
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val apiExecutor = ApiExecutor(json)
    private val tokenProvider = FirebaseAuthTokenProvider(appContext)
    private val api = RealsApiClient.create(BuildConfig.REALS_BASE_URL, json)

    val authRepository = FirebaseAuthRepository(appContext)
    private val meRepository = MeRepository(api, tokenProvider, apiExecutor)
    private val profileRepository = ProfileRepository(appContext, api, tokenProvider, apiExecutor)
    val provisionAndLoadProfileUseCase = ProvisionAndLoadProfileUseCase(
        meRepository = meRepository,
        profileRepository = profileRepository,
    )
    val createProfileUseCase = CreateProfileUseCase(profileRepository)
    val updateProfileUseCase = UpdateProfileUseCase(profileRepository)
    val updateMatchFiltersUseCase = UpdateMatchFiltersUseCase(profileRepository)
    val getProfilePhotosUseCase = GetProfilePhotosUseCase(profileRepository)
    val addMockProfilePhotoUseCase = AddMockProfilePhotoUseCase(profileRepository)
    val addProfilePhotoFileUseCase = AddProfilePhotoFileUseCase(profileRepository)
    val replaceMockProfilePhotoUseCase = ReplaceMockProfilePhotoUseCase(profileRepository)
    val replaceProfilePhotoFileUseCase = ReplaceProfilePhotoFileUseCase(profileRepository)
    val deleteProfilePhotoUseCase = DeleteProfilePhotoUseCase(profileRepository)
    val activateProfileUseCase = ActivateProfileUseCase(profileRepository)
    val deleteAccountUseCase = DeleteAccountUseCase(meRepository, authRepository)
}
