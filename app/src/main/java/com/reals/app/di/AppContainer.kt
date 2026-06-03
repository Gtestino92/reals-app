package com.reals.app.di

import android.content.Context
import com.reals.app.BuildConfig
import com.reals.app.core.firebase.FirebaseAuthTokenProvider
import com.reals.app.core.network.ApiExecutor
import com.reals.app.data.api.RealsApiClient
import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.repository.MeRepository
import com.reals.app.data.repository.ProfileRepository
import com.reals.app.domain.usecase.CompleteAndActivateProfileUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
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
    private val profileRepository = ProfileRepository(api, tokenProvider, apiExecutor)
    val provisionAndLoadProfileUseCase = ProvisionAndLoadProfileUseCase(
        meRepository = meRepository,
        profileRepository = profileRepository,
    )
    val createProfileUseCase = CreateProfileUseCase(profileRepository)
    val completeAndActivateProfileUseCase = CompleteAndActivateProfileUseCase(profileRepository)
}
