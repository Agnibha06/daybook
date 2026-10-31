package com.agnibha.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agnibha.tracker.ui.App
import com.agnibha.tracker.ui.DaybookTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"
    }

    private var requestedTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        Store.init(this)
        Alerts.ensureChannels(this)
        Alarms.scheduleAll(this)
        requestedTab = intent.getStringExtra(EXTRA_TAB)
        setContent {
            DaybookTheme {
                App(requestedTab = requestedTab, consumeTab = { requestedTab = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab = intent.getStringExtra(EXTRA_TAB)
    }

    override fun onResume() {
        super.onResume()
        Store.resumeTick++
        Alarms.scheduleAll(this)
    }
}
