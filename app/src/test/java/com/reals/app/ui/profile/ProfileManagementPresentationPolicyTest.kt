package com.reals.app.ui.profile

import com.reals.app.domain.model.ProfileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileManagementPresentationPolicyTest {
    @Test
    fun `profile surface shows direct profile content without account management`() {
        val policy = profileManagementPresentationPolicy(
            managementSurface = ProfileManagementSurface.Profile,
            profileStatus = ProfileStatus.Active,
        )

        assertTrue(policy.showProfileSection)
        assertFalse(policy.showSearchSection)
        assertFalse(policy.profileSectionCollapsible)
        assertFalse(policy.showAccountManagement)
        assertEquals(ProfileSection.Profile, policy.initialExpandedSection)
    }

    @Test
    fun `search surface shows direct preferences content without account management`() {
        val policy = profileManagementPresentationPolicy(
            managementSurface = ProfileManagementSurface.Search,
            profileStatus = ProfileStatus.Active,
        )

        assertFalse(policy.showProfileSection)
        assertTrue(policy.showSearchSection)
        assertFalse(policy.searchSectionCollapsible)
        assertFalse(policy.showAccountManagement)
        assertEquals(ProfileSection.Filters, policy.initialExpandedSection)
    }

    @Test
    fun `setup surface preserves collapsible sections and account management`() {
        val activePolicy = profileManagementPresentationPolicy(
            managementSurface = ProfileManagementSurface.Setup,
            profileStatus = ProfileStatus.Active,
        )
        val draftPolicy = profileManagementPresentationPolicy(
            managementSurface = ProfileManagementSurface.Setup,
            profileStatus = ProfileStatus.Draft,
        )

        assertTrue(activePolicy.showProfileSection)
        assertTrue(activePolicy.showSearchSection)
        assertTrue(activePolicy.profileSectionCollapsible)
        assertTrue(activePolicy.searchSectionCollapsible)
        assertTrue(activePolicy.showAccountManagement)
        assertNull(activePolicy.initialExpandedSection)
        assertEquals(ProfileSection.Photos, draftPolicy.initialExpandedSection)
    }
}
