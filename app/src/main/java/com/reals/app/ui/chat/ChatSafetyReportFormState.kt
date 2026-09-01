package com.reals.app.ui.chat

import androidx.compose.runtime.saveable.Saver
import com.reals.app.domain.model.ChatExitReason

internal data class ChatSafetyReportFormState(
    val details: String = "",
    val reasonRawValue: String = ChatExitReason.InappropriateBehavior.rawValue,
    val blockUser: Boolean = false,
) {
    fun resetAfterAcceptedSubmit(): ChatSafetyReportFormState = initial()

    companion object {
        fun initial(): ChatSafetyReportFormState = ChatSafetyReportFormState()
    }
}

internal val ChatSafetyReportFormStateSaver: Saver<ChatSafetyReportFormState, List<Any>> =
    Saver(
        save = { state ->
            listOf(
                state.details,
                state.reasonRawValue,
                state.blockUser,
            )
        },
        restore = { values ->
            ChatSafetyReportFormState(
                details = values[0] as String,
                reasonRawValue = values[1] as String,
                blockUser = values[2] as Boolean,
            )
        },
    )
