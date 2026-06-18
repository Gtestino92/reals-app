package com.reals.app.testutil

import com.reals.app.data.mapper.toDomain
import com.reals.app.domain.model.ProfileSnapshot
import com.reals.app.domain.model.ProvisionedSession

object TestDomain {
    fun session(): ProvisionedSession = ProvisionedSession(
        user = TestDtos.user().toDomain(),
        profileSnapshot = ProfileSnapshot.Found(TestDtos.profile().toDomain()),
    )
}
