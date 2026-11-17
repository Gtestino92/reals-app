package com.reals.app.ui.root

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProfileStatus

/** Saves only the existing profile destinations, never server responses or resource ownership. */
@Composable
internal fun ProfileNavigationRestoration(
    state: RealsRootUiState.Ready,
    openProfile: () -> Unit,
    openSearch: () -> Unit,
    openPreview: () -> Unit,
    openQuestions: () -> Unit,
    openAdditionalQuestions: () -> Unit,
) {
    val profile = (state.session.profileSnapshot as? ProfileSnapshot.Found)?.profile ?: return
    key(state.session.user.id, profile.id) {
        var savedRoute by rememberSaveable { mutableStateOf<String?>(null) }
        var firstReady by remember { mutableStateOf(true) }
        LaunchedEffect(state) {
            if (firstReady) {
                // Bootstrap may still install the initial Home result. Restore after it finishes.
                if (state.homeLoading) return@LaunchedEffect
                firstReady = false
                if (profile.status in listOf(ProfileStatus.Active, ProfileStatus.Draft)) {
                    when (savedRoute) {
                        "search" -> openSearch()
                        "profile" -> openProfile()
                        "preview" -> { openProfile(); openPreview() }
                        "questions", "additional" -> {
                            openProfile(); openQuestions()
                            if (savedRoute == "additional") openAdditionalQuestions()
                        }
                    }
                    if (savedRoute != null) return@LaunchedEffect
                }
            }
            savedRoute = when {
                state.ownerPreview.open -> "preview"
                state.profileQuestions.open -> if (state.profileQuestions.destination == ProfileQuestionDestination.Questions) "additional" else "questions"
                state.editingActiveProfile -> if (state.profileManagementDestination == ProfileManagementDestination.Search) "search" else "profile"
                else -> null
            }
        }
    }
}
