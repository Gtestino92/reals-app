package com.reals.app.domain.model

sealed interface ProfileStatus {
    val rawValue: String
    val label: String

    data object Draft : ProfileStatus {
        override val rawValue = "DRAFT"
        override val label = "Borrador"
    }

    data object Active : ProfileStatus {
        override val rawValue = "ACTIVE"
        override val label = "Activo"
    }

    data object Inactive : ProfileStatus {
        override val rawValue = "INACTIVE"
        override val label = "Inactivo"
    }

    data class Unknown(override val rawValue: String) : ProfileStatus {
        override val label = "Desconocido"
    }

    companion object {
        fun fromBackend(value: String): ProfileStatus = when (value) {
            Draft.rawValue -> Draft
            Active.rawValue -> Active
            Inactive.rawValue -> Inactive
            else -> Unknown(value)
        }
    }
}
