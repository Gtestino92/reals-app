package com.reals.app.domain.model

data class ProfileQuestionCatalog(
    val catalogVersion: String,
    val questions: List<ProfileQuestion>,
)

data class ProfileQuestion(
    val id: String,
    val semanticVersion: Int,
    val contentVersion: Int,
    val prompt: String,
    val displayOrder: Int,
)

data class ProfileQuestionAnswer(
    val questionId: String,
    val questionSemanticVersion: Int,
    val answer: String,
    val selectedPosition: Int?,
    val current: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

data class PublicProfileQuestion(
    val questionId: String,
    val prompt: String,
    val answer: String,
    val position: Int,
)
