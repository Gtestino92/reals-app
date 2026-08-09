package com.reals.app.notifications

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.time.Instant

interface MatchFoundInvalidationStore {
    fun recordInvalidation(
        matchId: String,
        expiresAt: Instant,
        now: Instant,
    )

    fun isInvalidated(
        matchId: String,
        now: Instant,
    ): Boolean
}

class SharedPreferencesMatchFoundInvalidationStore(
    context: Context,
) : MatchFoundInvalidationStore {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        "match_found_invalidations",
        Context.MODE_PRIVATE,
    )

    override fun recordInvalidation(
        matchId: String,
        expiresAt: Instant,
        now: Instant,
    ) {
        val normalizedMatchId = matchId.trimToNonBlank() ?: return
        val expiryMillis = expiresAt.toEpochMilli()
        val nowMillis = now.toEpochMilli()
        val editor = preferences.edit()

        removeExpired(editor, nowMillis)
        if (expiryMillis <= nowMillis) {
            editor.remove(normalizedMatchId)
        } else {
            editor.putLong(normalizedMatchId, expiryMillis)
        }
        if (!editor.commit()) {
            Log.w(TAG, "Could not synchronously persist match found invalidation tombstone.")
        }
    }

    override fun isInvalidated(
        matchId: String,
        now: Instant,
    ): Boolean {
        val normalizedMatchId = matchId.trimToNonBlank() ?: return false
        val nowMillis = now.toEpochMilli()
        val editor = preferences.edit()

        removeExpired(editor, nowMillis)
        val expiresAtMillis = preferences.getLong(normalizedMatchId, Long.MIN_VALUE)
        return if (expiresAtMillis > nowMillis) {
            editor.apply()
            true
        } else {
            editor.remove(normalizedMatchId).apply()
            false
        }
    }

    private fun removeExpired(
        editor: SharedPreferences.Editor,
        nowMillis: Long,
    ) {
        preferences.all.forEach { (key, value) ->
            val expiresAtMillis = value as? Long ?: return@forEach
            if (expiresAtMillis <= nowMillis) {
                editor.remove(key)
            }
        }
    }

    private companion object {
        const val TAG = "MatchFoundInvalidationStore"
    }
}

class InMemoryMatchFoundInvalidationStore(
    private val values: MutableMap<String, Long> = mutableMapOf(),
) : MatchFoundInvalidationStore {
    override fun recordInvalidation(
        matchId: String,
        expiresAt: Instant,
        now: Instant,
    ) {
        val normalizedMatchId = matchId.trimToNonBlank() ?: return

        removeExpired(now)
        if (expiresAt.isAfter(now)) {
            values[normalizedMatchId] = expiresAt.toEpochMilli()
        } else {
            values.remove(normalizedMatchId)
        }
    }

    override fun isInvalidated(
        matchId: String,
        now: Instant,
    ): Boolean {
        val normalizedMatchId = matchId.trimToNonBlank() ?: return false

        removeExpired(now)
        val expiresAtMillis = values[normalizedMatchId] ?: return false
        return if (expiresAtMillis > now.toEpochMilli()) {
            true
        } else {
            values.remove(normalizedMatchId)
            false
        }
    }

    private fun removeExpired(now: Instant) {
        val nowMillis = now.toEpochMilli()
        values.entries.removeAll { (_, expiresAtMillis) ->
            expiresAtMillis <= nowMillis
        }
    }
}
