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
import com.reals.app.notifications.PushNotificationContract.TYPE_SCHEDULING_AVAILABLE
import com.reals.app.notifications.PushNotificationContract.TYPE_SECOND_CHAT_REMINDER
import com.reals.app.notifications.PushNotificationContract.TYPE_VISUAL_REVIEW_AVAILABLE
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

        val pushType = intent.getStringExtra(EXTRA_PUSH_TYPE)
        val shouldRefreshHome = intent.getBooleanExtra(EXTRA_REFRESH_HOME, false) ||
            pushType == TYPE_VISUAL_REVIEW_AVAILABLE ||
            pushType == TYPE_SCHEDULING_AVAILABLE ||
            pushType == TYPE_SECOND_CHAT_REMINDER
        if (!shouldRefreshHome) return

        notificationOpenType = pushType
        notificationOpenNonce = System.currentTimeMillis()
    }
}
