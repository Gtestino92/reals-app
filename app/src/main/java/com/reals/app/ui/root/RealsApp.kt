package com.reals.app.ui.root

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reals.app.core.network.toDisplayMessage
import com.reals.app.di.AppContainer
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.ui.auth.LoginScreen
import com.reals.app.ui.common.FullScreenMessage
import com.reals.app.ui.profile.CreateProfileScreen
import com.reals.app.ui.profile.ProfileActivationResultScreen
import com.reals.app.ui.profile.ProfileStatusScreen

@Composable
fun RealsApp(appContainer: AppContainer) {
    val viewModel: RealsRootViewModel = viewModel(
        factory = RealsRootViewModelFactory(appContainer),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            RealsRootUiState.Checking -> FullScreenMessage(
                title = "Inicializando Reals",
                body = "Preparando autenticacion y cliente local.",
            )

            is RealsRootUiState.MissingFirebase -> FullScreenMessage(
                title = "Falta Firebase",
                body = current.message,
                primaryActionLabel = "Reintentar",
                onPrimaryAction = viewModel::refreshSession,
            )

            is RealsRootUiState.Login -> LoginScreen(
                loading = current.loading,
                error = current.error,
                onSignIn = viewModel::signIn,
                onSignUp = viewModel::signUp,
            )

            is RealsRootUiState.LoadingSession -> FullScreenMessage(
                title = "Conectando con backend local",
                body = "Provisionando usuario${current.email?.let { " $it" } ?: ""} y cargando perfil.",
            )

            is RealsRootUiState.Ready -> when (current.session.profileSnapshot) {
                ProfileSnapshot.Missing -> CreateProfileScreen(
                    loading = current.creatingProfile,
                    error = current.profileCreateError,
                    onSubmit = viewModel::createProfile,
                    onRefresh = viewModel::refreshSession,
                    onSignOut = viewModel::signOut,
                )

                is ProfileSnapshot.Found -> ProfileStatusScreen(
                    session = current.session,
                    photoActionLoading = current.addingPhoto,
                    photoActionError = current.photoActionError,
                    photoActionMessage = current.photoActionMessage,
                    activationLoading = current.activatingProfile,
                    activationError = current.profileActivationError,
                    onAddMockPhoto = viewModel::addMockProfilePhoto,
                    onActivateProfile = { viewModel.activateProfile() },
                    onRefresh = viewModel::refreshSession,
                    onSignOut = viewModel::signOut,
                )
            }

            is RealsRootUiState.ActivationComplete -> ProfileActivationResultScreen(
                session = current.session,
                result = current.result,
                onRefresh = viewModel::refreshSession,
                onSignOut = viewModel::signOut,
            )

            is RealsRootUiState.Failure -> FullScreenMessage(
                title = "No se pudo cargar Reals",
                body = current.error.toDisplayMessage(),
                primaryActionLabel = "Reintentar",
                onPrimaryAction = viewModel::refreshSession,
                secondaryActionLabel = "Cerrar sesion",
                onSecondaryAction = viewModel::signOut,
            )
        }
    }
}
