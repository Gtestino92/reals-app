package com.reals.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reals.app.MainActivity
import com.reals.app.R
import com.reals.app.notifications.PushNotificationContract.EXTRA_MATCH_ID
import com.reals.app.notifications.PushNotificationContract.EXTRA_PUSH_TYPE
import com.reals.app.notifications.PushNotificationContract.EXTRA_REFRESH_HOME
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.VISUAL_REVIEW_CHANNEL_ID
import com.reals.app.notifications.PushNotificationContract.VISUAL_REVIEW_NOTIFICATION_ID_BASE

object NotificationHelper {
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            VISUAL_REVIEW_CHANNEL_ID,
            "Revisiones",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Avisos cuando hay una revisión visual disponible"
        }

        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun showVisualReviewAvailable(context: Context, matchId: String?) {
        if (!canPostNotifications(context)) return

        val notification = NotificationCompat.Builder(context, VISUAL_REVIEW_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tenés una revisión disponible")
            .setContentText("Ya podés revisar el perfil visual de una conversación reciente.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Ya podés revisar el perfil visual de una conversación reciente."),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(visualReviewPendingIntent(context, matchId))
            .build()

        NotificationManagerCompat.from(context).notify(
            VISUAL_REVIEW_NOTIFICATION_ID_BASE + notificationSuffix(matchId),
            notification,
        )
    }

    private fun visualReviewPendingIntent(context: Context, matchId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_PUSH_TYPE, TYPE_VISUAL_REVIEW_AVAILABLE)
            putExtra(EXTRA_MATCH_ID, matchId)
            putExtra(EXTRA_REFRESH_HOME, true)
        }

        return PendingIntent.getActivity(
            context,
            VISUAL_REVIEW_NOTIFICATION_ID_BASE + notificationSuffix(matchId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationSuffix(matchId: String?): Int =
        matchId?.hashCode()?.floorMod(9000) ?: 0

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
