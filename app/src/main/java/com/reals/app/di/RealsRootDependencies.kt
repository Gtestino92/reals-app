package com.reals.app.di

import com.reals.app.data.repository.FirebaseAuthRepository
import com.reals.app.data.preferences.FirstChatUnansweredSuggestionDismissalStore
import com.reals.app.domain.usecase.AcceptChatExitRequestUseCase
import com.reals.app.domain.usecase.AcceptSchedulingProposalUseCase
import com.reals.app.domain.usecase.ActivateProfileUseCase
import com.reals.app.domain.usecase.AddProfilePhotoFileUseCase
import com.reals.app.domain.usecase.CancelChatUseCase
import com.reals.app.domain.usecase.ClearLocalSessionUseCase
import com.reals.app.domain.usecase.BlockMatchParticipantUseCase
import com.reals.app.domain.usecase.CreateProfileUseCase
import com.reals.app.domain.usecase.CreateSecondChatCompletionRequestUseCase
import com.reals.app.domain.usecase.CreateSecondChatInactivityClaimUseCase
import com.reals.app.domain.usecase.CreateSecondChatNoShowClaimUseCase
import com.reals.app.domain.usecase.DeleteAccountUseCase
import com.reals.app.domain.usecase.DeleteMyAffinityAnswerUseCase
import com.reals.app.domain.usecase.DeleteMyProfileQuestionAnswerUseCase
import com.reals.app.domain.usecase.DeleteProfilePhotoUseCase
import com.reals.app.domain.usecase.DecideSecondChatCompletionRequestUseCase
import com.reals.app.domain.usecase.DismissSecondChatForConnectionUseCase
import com.reals.app.domain.usecase.EnqueueMatchmakingUseCase
import com.reals.app.domain.usecase.GetAffinityQuestionCatalogUseCase
import com.reals.app.domain.usecase.GetChatExitRequestsUseCase
import com.reals.app.domain.usecase.GetChatMessagesUseCase
import com.reals.app.domain.usecase.GetChatUseCase
import com.reals.app.domain.usecase.GetCurrentLegalDocumentsUseCase
import com.reals.app.domain.usecase.GetCountriesUseCase
import com.reals.app.domain.usecase.GetFirstChatForMatchUseCase
import com.reals.app.domain.usecase.GetHomePendingUseCase
import com.reals.app.domain.usecase.GetHomeStatusUseCase
import com.reals.app.domain.usecase.GetHomeUseCase
import com.reals.app.domain.usecase.GetLegalStatusUseCase
import com.reals.app.domain.usecase.GetMatchUseCase
import com.reals.app.domain.usecase.GetMeUseCase
import com.reals.app.domain.usecase.GetMyAffinityAnswersUseCase
import com.reals.app.domain.usecase.GetMyProfileQuestionAnswersUseCase
import com.reals.app.domain.usecase.GetPartnerPersonalMessageUseCase
import com.reals.app.domain.usecase.GetProfilePhotosUseCase
import com.reals.app.domain.usecase.GetProfileQuestionCatalogUseCase
import com.reals.app.domain.usecase.GetSchedulingAvailabilityUseCase
import com.reals.app.domain.usecase.GetSchedulingNegotiationUseCase
import com.reals.app.domain.usecase.GetSchedulingProposalsUseCase
import com.reals.app.domain.usecase.GetSecondChatForConnectionUseCase
import com.reals.app.domain.usecase.GetSecondChatStatusUseCase
import com.reals.app.domain.usecase.GetVisualProfileUseCase
import com.reals.app.domain.usecase.FinalizeAccountDeletionUseCase
import com.reals.app.domain.usecase.JoinSecondChatUseCase
import com.reals.app.domain.usecase.LeaveQueueUseCase
import com.reals.app.domain.usecase.MarkLocalFirebaseEmailVerified
import com.reals.app.domain.usecase.PatchMyAffinityAnswerUseCase
import com.reals.app.domain.usecase.ProvisionAndLoadProfileUseCase
import com.reals.app.domain.usecase.PutChatMessageReactionUseCase
import com.reals.app.domain.usecase.PutMyPersonalMessageUseCase
import com.reals.app.domain.usecase.ReactivateAccountUseCase
import com.reals.app.domain.usecase.RejectChatExitRequestUseCase
import com.reals.app.domain.usecase.RejectPartnerSchedulingProposalsUseCase
import com.reals.app.domain.usecase.RecordLegalDocumentActionUseCase
import com.reals.app.domain.usecase.ReorderProfilePhotosUseCase
import com.reals.app.domain.usecase.ReplaceProfilePhotoFileUseCase
import com.reals.app.domain.usecase.ReplaceMyProfileQuestionSelectionsUseCase
import com.reals.app.domain.usecase.RequestMutualChatExitUseCase
import com.reals.app.domain.usecase.RequestNextFirstChatGuidanceQuestionUseCase
import com.reals.app.domain.usecase.RequestPasswordResetUseCase
import com.reals.app.domain.usecase.SafetyCancelChatUseCase
import com.reals.app.domain.usecase.SendChatAudioMessageUseCase
import com.reals.app.domain.usecase.SendChatMessageUseCase
import com.reals.app.domain.usecase.SubmitChatDecisionUseCase
import com.reals.app.domain.usecase.SubmitSchedulingProposalsUseCase
import com.reals.app.domain.usecase.SubmitVisualDecisionUseCase
import com.reals.app.domain.usecase.TimeoutChatExitRequestUseCase
import com.reals.app.domain.usecase.UpdateMatchFiltersUseCase
import com.reals.app.domain.usecase.UpdateProfileUseCase
import com.reals.app.domain.usecase.UpsertMyProfileQuestionAnswerUseCase
import com.reals.app.notifications.registration.PushTokenRegistrationService
import com.reals.app.ui.root.LocalFirebaseEmailVerificationCoordinator

data class RealsRootDependencies(
    val session: SessionFeatureDependencies,
    val account: AccountFeatureDependencies,
    val legal: LegalFeatureDependencies,
    val profile: ProfileFeatureDependencies,
    val home: HomeFeatureDependencies,
    val manualBlock: ManualBlockFeatureDependencies,
    val firstChat: FirstChatFeatureDependencies,
    val secondChat: SecondChatFeatureDependencies,
    val visualApproval: VisualApprovalFeatureDependencies,
    val scheduling: SchedulingFeatureDependencies,
    val affinity: AffinityFeatureDependencies,
    val profileQuestions: ProfileQuestionFeatureDependencies,
)

data class ManualBlockFeatureDependencies(
    val blockMatchParticipant: BlockMatchParticipantUseCase,
)

data class SessionFeatureDependencies(
    val authRepository: FirebaseAuthRepository,
    val requestPasswordReset: RequestPasswordResetUseCase,
    val clearLocalSession: ClearLocalSessionUseCase,
    val provisionAndLoadProfile: ProvisionAndLoadProfileUseCase,
    val getMe: GetMeUseCase,
    val pushTokenRegistrationService: PushTokenRegistrationService,
    val markLocalFirebaseEmailVerified: MarkLocalFirebaseEmailVerified =
        MarkLocalFirebaseEmailVerified { com.reals.app.core.network.ApiResult.Success(Unit) },
    val localFirebaseEmailAutoVerificationEnabled: Boolean = false,
    val localFirebaseEmailVerificationCoordinator: LocalFirebaseEmailVerificationCoordinator =
        LocalFirebaseEmailVerificationCoordinator(
            localFirebaseEmailAutoVerificationEnabled = localFirebaseEmailAutoVerificationEnabled,
            authRepository = authRepository,
            markLocalFirebaseEmailVerified = markLocalFirebaseEmailVerified,
        ),
)

data class AccountFeatureDependencies(
    val reactivateAccount: ReactivateAccountUseCase,
    val deleteAccount: DeleteAccountUseCase,
    val finalizeAccountDeletion: FinalizeAccountDeletionUseCase,
)

data class LegalFeatureDependencies(
    val getCurrentDocuments: GetCurrentLegalDocumentsUseCase,
    val getStatus: GetLegalStatusUseCase,
    val recordAction: RecordLegalDocumentActionUseCase,
)

data class ProfileFeatureDependencies(
    val createProfile: CreateProfileUseCase,
    val updateProfile: UpdateProfileUseCase,
    val getCountries: GetCountriesUseCase,
    val updateMatchFilters: UpdateMatchFiltersUseCase,
    val getProfilePhotos: GetProfilePhotosUseCase,
    val addProfilePhotoFile: AddProfilePhotoFileUseCase,
    val replaceProfilePhotoFile: ReplaceProfilePhotoFileUseCase,
    val deleteProfilePhoto: DeleteProfilePhotoUseCase,
    val reorderProfilePhotos: ReorderProfilePhotosUseCase,
    val activateProfile: ActivateProfileUseCase,
)

data class HomeFeatureDependencies(
    val enqueueMatchmaking: EnqueueMatchmakingUseCase,
    val getHome: GetHomeUseCase,
    val getHomeStatus: GetHomeStatusUseCase,
    val getHomePending: GetHomePendingUseCase,
    val leaveQueue: LeaveQueueUseCase,
    val dismissSecondChat: DismissSecondChatForConnectionUseCase,
)

data class FirstChatFeatureDependencies(
    val getMatch: GetMatchUseCase,
    val getFirstChatForMatch: GetFirstChatForMatchUseCase,
    val submitChatDecision: SubmitChatDecisionUseCase,
    val getChatMessages: GetChatMessagesUseCase,
    val sendChatMessage: SendChatMessageUseCase,
    val sendChatAudioMessage: SendChatAudioMessageUseCase,
    val putMessageReaction: PutChatMessageReactionUseCase,
    val requestNextFirstChatGuidanceQuestion: RequestNextFirstChatGuidanceQuestionUseCase,
    val getChatExitRequests: GetChatExitRequestsUseCase,
    val requestMutualChatExit: RequestMutualChatExitUseCase,
    val acceptChatExitRequest: AcceptChatExitRequestUseCase,
    val rejectChatExitRequest: RejectChatExitRequestUseCase,
    val timeoutChatExitRequest: TimeoutChatExitRequestUseCase,
    val cancelChat: CancelChatUseCase,
    val safetyCancelChat: SafetyCancelChatUseCase,
    val unansweredSuggestionDismissalStore: FirstChatUnansweredSuggestionDismissalStore,
)

data class SecondChatFeatureDependencies(
    val getStatus: GetSecondChatStatusUseCase,
    val join: JoinSecondChatUseCase,
    val createNoShowClaim: CreateSecondChatNoShowClaimUseCase,
    val getChat: GetChatUseCase,
    val getSecondChatForConnection: GetSecondChatForConnectionUseCase,
    val getChatMessages: GetChatMessagesUseCase,
    val sendChatMessage: SendChatMessageUseCase,
    val sendChatAudioMessage: SendChatAudioMessageUseCase,
    val putMessageReaction: PutChatMessageReactionUseCase,
    val safetyCancelChat: SafetyCancelChatUseCase,
    val createCompletionRequest: CreateSecondChatCompletionRequestUseCase,
    val decideCompletionRequest: DecideSecondChatCompletionRequestUseCase,
    val createInactivityClaim: CreateSecondChatInactivityClaimUseCase,
)

data class VisualApprovalFeatureDependencies(
    val getMatch: GetMatchUseCase,
    val getVisualProfile: GetVisualProfileUseCase,
    val submitVisualDecision: SubmitVisualDecisionUseCase,
    val putMyPersonalMessage: PutMyPersonalMessageUseCase,
    val getPartnerPersonalMessage: GetPartnerPersonalMessageUseCase,
)

data class SchedulingFeatureDependencies(
    val getNegotiation: GetSchedulingNegotiationUseCase,
    val getProposals: GetSchedulingProposalsUseCase,
    val getAvailability: GetSchedulingAvailabilityUseCase,
    val submitProposals: SubmitSchedulingProposalsUseCase,
    val acceptProposal: AcceptSchedulingProposalUseCase,
    val rejectPartnerProposals: RejectPartnerSchedulingProposalsUseCase,
)

data class AffinityFeatureDependencies(
    val getCatalog: GetAffinityQuestionCatalogUseCase,
    val getMyAnswers: GetMyAffinityAnswersUseCase,
    val patchAnswer: PatchMyAffinityAnswerUseCase,
    val deleteAnswer: DeleteMyAffinityAnswerUseCase,
)

data class ProfileQuestionFeatureDependencies(
    val getCatalog: GetProfileQuestionCatalogUseCase,
    val getMyAnswers: GetMyProfileQuestionAnswersUseCase,
    val upsertAnswer: UpsertMyProfileQuestionAnswerUseCase,
    val deleteAnswer: DeleteMyProfileQuestionAnswerUseCase,
    val replaceSelections: ReplaceMyProfileQuestionSelectionsUseCase,
)
