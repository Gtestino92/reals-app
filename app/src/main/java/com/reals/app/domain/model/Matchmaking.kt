package com.reals.app.domain.model

data class SearchLocationInput(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Int?,
)

data class QueueStatus(
    val userId: String,
    val inQueue: Boolean,
)
