package com.reals.app.foreground

sealed interface ForegroundDestination {
    data object Home : ForegroundDestination

    data class FirstChat(
        val matchId: String,
        val chatId: String?,
    ) : ForegroundDestination

    data class SecondChat(
        val connectionId: String,
    ) : ForegroundDestination

    data class VisualReview(
        val matchId: String,
    ) : ForegroundDestination

    data class Scheduling(
        val connectionId: String,
    ) : ForegroundDestination

    data class PartnerProfile(
        val matchId: String,
    ) : ForegroundDestination

    data object ProfileManagement : ForegroundDestination
    data object Other : ForegroundDestination
}
