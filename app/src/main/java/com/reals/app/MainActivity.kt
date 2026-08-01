package com.reals.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.reals.app.notifications.PushNotificationContract.EXTRA_PUSH_TYPE
import com.reals.app.notifications.PushNotificationContract.EXTRA_REFRESH_HOME
import com.reals.app.notifications.PushNotificationOpenContract
import com.reals.app.ui.root.RealsApp
import com.reals.app.ui.theme.RealsAppTheme

class MainActivity : ComponentActivity() {
    private var notificationOpenNonce by mutableLongStateOf(0L)
    private var notificationOpenType by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as RealsApplication).appContainer
        handleNotificationIntent(intent)
        setContent {
            RealsAppTheme {
                RealsApp(
                    appContainer = appContainer,
                    notificationOpenNonce = notificationOpenNonce,
                    notificationOpenType = notificationOpenType,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return

        val pushType = PushNotificationOpenContract.resolveType(
            internalPushType = intent.getStringExtra(EXTRA_PUSH_TYPE),
            rawFcmType = intent.getStringExtra(RAW_FCM_TYPE),
        )
        val shouldRefreshHome = intent.getBooleanExtra(EXTRA_REFRESH_HOME, false) ||
            PushNotificationOpenContract.shouldHandleExternalOpen(pushType)
        if (!shouldRefreshHome) return

        notificationOpenType = pushType
        notificationOpenNonce = System.currentTimeMillis()
    }

    private companion object {
        const val RAW_FCM_TYPE = "type"
    }
}
