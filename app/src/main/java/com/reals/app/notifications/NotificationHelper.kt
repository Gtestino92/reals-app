package com.reals.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reals.app.R
import com.reals.app.notifications.PushNotificationContract.GENERAL_UPDATES_CHANNEL_ID
import com.reals.app.notifications.PushNotificationContract.MATCH_FOUND_NOTIFICATION_ID_BASE
import com.reals.app.notifications.PushNotificationContract.SCHEDULING_AVAILABLE_NOTIFICATION_ID_BASE
import com.reals.app.notifications.PushNotificationContract.SECOND_CHAT_REMINDER_NOTIFICATION_ID_BASE
import com.reals.app.notifications.PushNotificationContract.TYPE_MATCH_FOUND
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_CONFIRMED
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_PROPOSALS_RECEIVED
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_REMINDER
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_STARTED
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_REMINDER
import com.reals.app.notifications.PushNotificationContract.VISUAL_REVIEW_NOTIFICATION_ID_BASE

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val MATCH_FOUND_NOTIFICATION_TAG_PREFIX = "match-found-"
    internal const val GENERAL_UPDATES_CHANNEL_IMPORTANCE =
        NotificationManager.IMPORTANCE_HIGH

    internal const val GENERAL_UPDATES_NOTIFICATION_PRIORITY =
        NotificationCompat.PRIORITY_HIGH

    fun ensureChannels(context: Context) {
        val channel = NotificationChannel(
            GENERAL_UPDATES_CHANNEL_ID,
            "Actualizaciones de Reals",
            GENERAL_UPDATES_CHANNEL_IMPORTANCE,
        ).apply {
            description = "Avisos sobre chats, revisiones y coordinación"
        }

        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun cancelAllMatchFound(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
            ?: return
        val notificationManagerCompat = NotificationManagerCompat.from(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.activeNotifications
                .filter { activeNotification ->
                    isMatchFoundNotificationIdentity(
                        tag = activeNotification.tag,
                        id = activeNotification.id,
                    )
                }
                .forEach { activeNotification ->
                    notificationManagerCompat.cancel(
                        activeNotification.tag,
                        activeNotification.id,
                    )
                }
        }

        notificationManagerCompat.cancel(MATCH_FOUND_NOTIFICATION_ID_BASE)
    }

    @SuppressLint("MissingPermission")
    fun showVisualReviewReminder(context: Context, matchId: String?) {
        if (!canPostNotifications(context)) return

        val notification = NotificationCompat.Builder(context, GENERAL_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("Revisión visual pendiente")
            .setContentText("Entrá a Reals para completarla antes de que venza.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Entrá a Reals para completarla antes de que venza."),
            )
            .setPriority(GENERAL_UPDATES_NOTIFICATION_PRIORITY)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(visualReviewPendingIntent(context, matchId))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                VISUAL_REVIEW_NOTIFICATION_ID_BASE + notificationSuffix(matchId),
                notification,
            )
        } catch (exception: SecurityException) {
            Log.w(
                TAG,
                "Could not show visual review reminder because permission was denied.",
                exception
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun showSecondChatReminder(context: Context, connectionId: String?, availableAt: String? = null) {
        if (!canPostNotifications(context)) return

        val body = "Entr\u00e1 a Reals para ver el horario y prepararte."
        val notification = NotificationCompat.Builder(context, GENERAL_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("Tu segunda charla empieza pronto")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(GENERAL_UPDATES_NOTIFICATION_PRIORITY)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(secondChatReminderPendingIntent(context, connectionId, availableAt))
            .build()

        try {
            val identity = secondChatNotificationDisplayIdentity(connectionId)
            NotificationManagerCompat.from(context).notify(
                identity.tag,
                identity.id,
                notification,
            )
        } catch (exception: SecurityException) {
            Log.w(
                TAG,
                "Could not show second chat reminder because permission was denied.",
                exception
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun showSecondChatStarted(
        context: Context,
        connectionId: String?,
        matchId: String?,
        availableAt: String? = null,
    ) {
        if (!canPostNotifications(context)) return

        val (title, body) = secondChatStartedNotificationCopy()
        val notification = NotificationCompat.Builder(context, GENERAL_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(GENERAL_UPDATES_NOTIFICATION_PRIORITY)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(secondChatStartedPendingIntent(context, connectionId, matchId, availableAt))
            .build()

        try {
            val identity = secondChatNotificationDisplayIdentity(connectionId)
            NotificationManagerCompat.from(context).notify(
                identity.tag,
                identity.id,
                notification,
            )
        } catch (exception: SecurityException) {
            Log.w(
                TAG,
                "Could not show second chat started notification because permission was denied.",
                exception
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun showSchedulingAvailable(
        context: Context,
        connectionId: String?,
        matchId: String?,
        type: String = TYPE_SCHEDULING_AVAILABLE,
    ) {
        if (!canPostNotifications(context)) return

        val (title, body) = schedulingNotificationCopy(type)
        val notification = NotificationCompat.Builder(context, GENERAL_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(GENERAL_UPDATES_NOTIFICATION_PRIORITY)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(schedulingAvailablePendingIntent(context, type, connectionId, matchId))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                SCHEDULING_AVAILABLE_NOTIFICATION_ID_BASE + notificationSuffix(connectionId ?: matchId),
                notification,
            )
        } catch (exception: SecurityException) {
            Log.w(
                TAG,
                "Could not show scheduling notification because permission was denied.",
                exception
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun showMatchFound(
        context: Context,
        matchId: String?,
    ) {
        if (!canPostNotifications(context)) return

        val (title, body) = matchFoundNotificationCopy()

        val notification = NotificationCompat.Builder(
            context,
            GENERAL_UPDATES_CHANNEL_ID,
        )
            .setSmallIcon(R.drawable.ic_stat_name)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.ic_notification_large,
                ),
            )
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body),
            )
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setPriority(GENERAL_UPDATES_NOTIFICATION_PRIORITY)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(
                matchFoundPendingIntent(
                    context = context,
                    matchId = matchId,
                ),
            )
            .build()

        try {
            val identity = matchFoundNotificationDisplayIdentity(matchId)

            NotificationManagerCompat.from(context).notify(
                identity.tag,
                identity.id,
                notification,
            )
        } catch (exception: SecurityException) {
            Log.w(
                TAG,
                "Could not show match found notification because permission was denied.",
                exception,
            )
        }
    }

    private fun matchFoundPendingIntent(
        context: Context,
        matchId: String?,
    ) = NotificationPendingIntents.mainActivity(
        context,
        MATCH_FOUND_NOTIFICATION_ID_BASE + notificationSuffix(matchId),
        TYPE_MATCH_FOUND,
        null,
        matchId,
        null,
    )

    internal fun matchFoundNotificationCopy(): Pair<String, String> =
        "Encontramos un chat" to "Tu nuevo chat ya está disponible."

    internal fun matchFoundNotificationTag(matchId: String?): String? =
        normalizedNotificationTargetId(matchId)
            ?.let { "$MATCH_FOUND_NOTIFICATION_TAG_PREFIX$it" }

    internal fun isMatchFoundNotificationIdentity(
        tag: String?,
        id: Int,
    ): Boolean =
        tag?.startsWith(MATCH_FOUND_NOTIFICATION_TAG_PREFIX) == true ||
                (tag == null && id == MATCH_FOUND_NOTIFICATION_ID_BASE)

    internal fun matchFoundNotificationDisplayIdentity(
        matchId: String?,
    ): NotificationDisplayIdentity {
        val tag = matchFoundNotificationTag(matchId)

        return if (tag != null) {
            NotificationDisplayIdentity(
                tag = tag,
                id = 0,
            )
        } else {
            NotificationDisplayIdentity(
                tag = null,
                id = MATCH_FOUND_NOTIFICATION_ID_BASE,
            )
        }
    }

    private fun visualReviewPendingIntent(context: Context, matchId: String?) =
        NotificationPendingIntents.mainActivity(
            context,
            VISUAL_REVIEW_NOTIFICATION_ID_BASE + notificationSuffix(matchId),
            TYPE_VISUAL_REVIEW_REMINDER,
        )

    private fun secondChatReminderPendingIntent(
        context: Context,
        connectionId: String?,
        availableAt: String?,
    ) = NotificationPendingIntents.mainActivity(
        context,
        secondChatNotificationId(connectionId),
        TYPE_SECOND_CHAT_REMINDER,
        connectionId,
        null,
        availableAt,
    )

    private fun secondChatStartedPendingIntent(
        context: Context,
        connectionId: String?,
        matchId: String?,
        availableAt: String?,
    ) = NotificationPendingIntents.mainActivity(
        context,
        secondChatNotificationId(connectionId),
        TYPE_SECOND_CHAT_STARTED,
        connectionId,
        matchId,
        availableAt,
    )

    private fun schedulingAvailablePendingIntent(
        context: Context,
        type: String,
        connectionId: String?,
        matchId: String?,
    ) = NotificationPendingIntents.mainActivity(
        context,
        SCHEDULING_AVAILABLE_NOTIFICATION_ID_BASE + notificationSuffix(connectionId ?: matchId),
        type,
        connectionId,
        matchId,
        null,
    )

    internal fun schedulingNotificationCopy(type: String): Pair<String, String> = when (type) {
        TYPE_SCHEDULING_PROPOSALS_RECEIVED -> "Nuevos horarios propuestos" to
                "Entr\u00e1 a Reals para revisar las opciones."
        TYPE_SCHEDULING_CONFIRMED -> "Horario confirmado" to
                "Entr\u00e1 a Reals para ver la segunda charla."
        else -> "Coordinaci\u00f3n disponible" to
                "Ya pod\u00e9s coordinar horarios en Reals."
    }

    internal fun secondChatStartedNotificationCopy(): Pair<String, String> =
        "Tu segunda charla ya empezó" to "Entrá ahora a Reals para sumarte."

    internal fun secondChatNotificationTag(connectionId: String?): String? =
        normalizedNotificationTargetId(connectionId)?.let { "second-chat-$it" }

    internal fun secondChatNotificationDisplayIdentity(connectionId: String?): NotificationDisplayIdentity {
        val tag = secondChatNotificationTag(connectionId)
        return if (tag != null) {
            NotificationDisplayIdentity(tag = tag, id = 0)
        } else {
            NotificationDisplayIdentity(tag = null, id = secondChatNotificationId(connectionId))
        }
    }

    internal fun secondChatNotificationId(connectionId: String?): Int =
        SECOND_CHAT_REMINDER_NOTIFICATION_ID_BASE + notificationSuffix(
            normalizedNotificationTargetId(connectionId),
        )

    internal fun notificationSuffix(matchId: String?): Int =
        matchId?.hashCode()?.floorMod(9000) ?: 0

    private fun normalizedNotificationTargetId(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

internal data class NotificationDisplayIdentity(
    val tag: String?,
    val id: Int,
)
