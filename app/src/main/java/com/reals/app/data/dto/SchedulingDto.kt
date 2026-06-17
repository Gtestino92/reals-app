package com.reals.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class AddProposalRequestDto(
    val proposedDateTimes: List<String>,
)

@Serializable
data class ConnectionResponseDto(
    val id: String,
    val matchId: String,
    val userAId: String,
    val userBId: String,
    val state: String,
    val schedulingExpiresAt: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class ScheduleProposalResponseDto(
    val id: String,
    val connectionId: String,
    val userId: String,
    val roundNumber: Int,
    val preferenceOrder: Int,
    val proposedDateTime: String,
    val status: String,
    val chatId: String? = null,
    val createdAt: String,
)

@Serializable
data class NegotiationResponseDto(
    val id: String,
    val connectionId: String,
    val roundNumber: Int,
    val status: String,
    val confirmedDateTime: String? = null,
    val chatId: String? = null,
    val createdAt: String,
    val updatedAt: String,
)
