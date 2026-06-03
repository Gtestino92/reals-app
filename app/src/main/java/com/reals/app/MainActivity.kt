package com.reals.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.reals.app.ui.root.RealsApp
import com.reals.app.ui.theme.RealsAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as RealsApplication).appContainer
        setContent {
            RealsAppTheme {
                RealsApp(appContainer)
            }
        }
    }
}
