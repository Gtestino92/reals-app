package com.reals.app.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.reals.app.MainActivity;

public final class NotificationPendingIntents {
    private NotificationPendingIntents() {
    }

    public static PendingIntent mainActivity(
            Context context,
            int requestCode,
            String pushType
    ) {
        return mainActivity(context, requestCode, pushType, null, null, null);
    }

    public static PendingIntent mainActivity(
            Context context,
            int requestCode,
            String pushType,
            String connectionId,
            String matchId,
            String availableAt
    ) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(PushNotificationContract.EXTRA_PUSH_TYPE, pushType);
        intent.putExtra(PushNotificationContract.EXTRA_REFRESH_HOME, true);
        if (connectionId != null) {
            intent.putExtra(PushNotificationContract.EXTRA_CONNECTION_ID, connectionId);
        }
        if (matchId != null) {
            intent.putExtra(PushNotificationContract.EXTRA_MATCH_ID, matchId);
        }
        if (availableAt != null) {
            intent.putExtra(PushNotificationContract.EXTRA_AVAILABLE_AT, availableAt);
        }

        return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT
                        | PendingIntent.FLAG_ONE_SHOT
                        | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
