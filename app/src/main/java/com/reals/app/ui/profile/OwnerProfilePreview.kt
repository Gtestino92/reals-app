package com.reals.app.ui.profile

import com.reals.app.domain.model.*

/** A projection of confirmed owner responses, with no match or private affinity data. */
internal fun ownerProfilePreview(
    profile: Profile,
    photos: List<ProfilePhoto>,
    catalog: ProfileQuestionCatalog,
    answers: List<ProfileQuestionAnswer>,
): VisualProfile = VisualProfile(
    profileId = profile.id,
    displayName = profile.displayName,
    age = profile.age,
    city = profile.city,
    countryCode = profile.countryCode,
    bio = profile.bio,
    photos = photos.filter { it.isApprovedForExternalDisplay() }.sortedBy { it.position },
    profileQuestions = catalog.rows(answers).filter { row ->
        row.selectable && row.answer?.questionSemanticVersion == row.question.semanticVersion &&
            row.selectedPosition?.let { it > 0 } == true
    }.sortedBy { it.selectedPosition }.distinctBy { it.question.id }.take(3).map { row ->
        PublicProfileQuestion(row.question.id, row.question.prompt, row.answer!!.answer, row.selectedPosition!!)
    },
    visualExpiresAt = null,
    myPersonalMessageSubmitted = false,
    partnerPersonalMessageSubmitted = false,
    partnerPersonalMessageRead = false,
    decisionRequiresPartnerPersonalMessageRead = false,
)
