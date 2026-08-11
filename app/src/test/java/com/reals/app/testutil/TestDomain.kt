package com.reals.app.testutil

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProvisionedSession

object TestDomain {
    fun session(passwordManagementAllowed: Boolean = true): ProvisionedSession = ProvisionedSession(
        user = TestDtos.user().copy(passwordManagementAllowed = passwordManagementAllowed).toDomain(),
        profileSnapshot = ProfileSnapshot.Found(TestDtos.profile().toDomain()),
    )
}
