package com.reals.app.domain.model

sealed interface LegalDocumentType {
    val rawValue: String

    data object TermsOfUse : LegalDocumentType {
        override val rawValue: String = "TERMS_OF_USE"
    }

    data object PrivacyNotice : LegalDocumentType {
        override val rawValue: String = "PRIVACY_NOTICE"
    }

    data object CommunityGuidelines : LegalDocumentType {
        override val rawValue: String = "COMMUNITY_GUIDELINES"
    }

    data class Unknown(override val rawValue: String) : LegalDocumentType

    companion object {
        fun fromBackend(value: String): LegalDocumentType = when (value.uppercase()) {
            TermsOfUse.rawValue -> TermsOfUse
            PrivacyNotice.rawValue -> PrivacyNotice
            CommunityGuidelines.rawValue -> CommunityGuidelines
            else -> Unknown(value)
        }
    }
}

sealed interface LegalDocumentAction {
    val rawValue: String

    data object Accepted : LegalDocumentAction {
        override val rawValue: String = "ACCEPTED"
    }

    data object Acknowledged : LegalDocumentAction {
        override val rawValue: String = "ACKNOWLEDGED"
    }

    data class Unknown(override val rawValue: String) : LegalDocumentAction

    companion object {
        fun fromBackend(value: String): LegalDocumentAction = when (value.uppercase()) {
            Accepted.rawValue -> Accepted
            Acknowledged.rawValue -> Acknowledged
            else -> Unknown(value)
        }
    }
}

data class CurrentLegalDocument(
    val type: LegalDocumentType,
    val version: String,
    val url: String,
    val requiredAction: LegalDocumentAction,
)

data class LegalDocumentStatus(
    val type: LegalDocumentType,
    val version: String,
    val requiredAction: LegalDocumentAction,
    val recordedAction: LegalDocumentAction?,
    val actedAt: String?,
    val satisfied: Boolean,
)

data class LegalStatus(
    val requirementsSatisfied: Boolean,
    val documents: List<LegalDocumentStatus>,
)

data class LegalDocumentActionRecord(
    val id: String,
    val documentType: LegalDocumentType,
    val documentVersion: String,
    val action: LegalDocumentAction,
    val actedAt: String,
)
