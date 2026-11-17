package com.reals.app.data.repository

import com.reals.app.domain.model.sameProfileOwner
import com.reals.app.testutil.TestDomain
import org.junit.Assert.*
import org.junit.Test

class AuthenticationSessionIdentityTest {
    @Test fun profileReloadsCarryAuthenticationIdentityAndRejectMidReadReplacement() = kotlinx.coroutines.test.runTest {
        val api = com.reals.app.testutil.FakeRealsApi()
        val token = com.reals.app.testutil.FakeAuthTokenProvider()
        val executor = com.reals.app.testutil.testApiExecutor()
        var identity = "login-1"
        val load = com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase(
            MeRepository(api, token, executor), ProfileRepository(null, api, token, executor), { identity })
        val user = TestDomain.session().user
        val first = load.loadProfileFor(user) as com.reals.app.core.network.ApiResult.Success
        val renewed = load.loadProfileFor(user.copy(email = "renewed@test")) as com.reals.app.core.network.ApiResult.Success
        assertTrue(first.value.sameProfileOwner(renewed.value))
        api.beforeGetProfileResponse = { identity = "login-2" }
        assertTrue(load.loadProfileFor(user) is com.reals.app.core.network.ApiResult.Failure)
        api.beforeGetProfileResponse = {}
        api.profileResponse = retrofit2.Response.success(com.reals.app.testutil.TestDtos.profile().copy(userId = "other"))
        assertTrue(load.loadProfileFor(user) is com.reals.app.core.network.ApiResult.Failure)
    }
    @Test fun tokenAndProfileRenewalsPreserveIdentityButNewLoginsDoNot() {
        val identity = AuthenticationSessionIdentity()
        val original = identity.current("firebase-user")
        assertEquals(original, identity.current("firebase-user"))
        assertNotEquals(original, identity.current("another-user"))
        identity.replace()
        assertNotEquals(original, identity.current("firebase-user"))
    }
    @Test fun profileOwnershipIncludesAuthenticationLifetime() {
        val session = TestDomain.session()
        assertTrue(session.sameProfileOwner(session.copy(user = session.user.copy(email = "renewed@test"))))
        assertFalse(session.sameProfileOwner(session.copy(authenticationSessionId = "new-login")))
        assertFalse(session.sameProfileOwner(session.copy(user = session.user.copy(id = "another"))))
        assertFalse(session.sameProfileOwner(null))
    }
}
