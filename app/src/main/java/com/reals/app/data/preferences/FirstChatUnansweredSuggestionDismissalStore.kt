package com.reals.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.nio.charset.StandardCharsets

interface FirstChatUnansweredSuggestionDismissalStore {
    fun dismissedPeriod(userId: String, chatId: String): String?

    fun dismissPeriod(userId: String, chatId: String, periodReference: String)
}

class SharedPreferencesFirstChatUnansweredSuggestionDismissalStore(
    context: Context,
) : FirstChatUnansweredSuggestionDismissalStore {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        "first_chat_unanswered_suggestion_dismissals",
        Context.MODE_PRIVATE,
    )

    override fun dismissedPeriod(userId: String, chatId: String): String? =
        preferences.getString(key(userId, chatId), null)

    override fun dismissPeriod(userId: String, chatId: String, periodReference: String) {
        preferences.edit().putString(key(userId, chatId), periodReference).apply()
    }

    private fun key(userId: String, chatId: String): String =
        "${userId.preferenceKeyPart()}:${chatId.preferenceKeyPart()}"
}

class InMemoryFirstChatUnansweredSuggestionDismissalStore(
    private val values: MutableMap<String, String> = mutableMapOf(),
) : FirstChatUnansweredSuggestionDismissalStore {
    override fun dismissedPeriod(userId: String, chatId: String): String? =
        values[key(userId, chatId)]

    override fun dismissPeriod(userId: String, chatId: String, periodReference: String) {
        values[key(userId, chatId)] = periodReference
    }

    private fun key(userId: String, chatId: String): String = "$userId:$chatId"
}

private fun String.preferenceKeyPart(): String =
    Base64.encodeToString(toByteArray(StandardCharsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
