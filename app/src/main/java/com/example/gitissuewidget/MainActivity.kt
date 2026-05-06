package com.example.gitissuewidget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.gitissuewidget.ui.main.MainScreen
import com.example.gitissuewidget.ui.newissue.NewIssueScreen
import com.example.gitissuewidget.ui.settings.SettingsScreen
import com.example.gitissuewidget.ui.theme.GitIssueWidgetTheme

class MainActivity : ComponentActivity() {

    private var pendingIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingIntent = intent
        setContent {
            GitIssueWidgetTheme {
                AppRoot(intentSignal = pendingIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent = intent
    }

    companion object {
        const val EXTRA_OPEN_NEW_ISSUE = "com.example.gitissuewidget.EXTRA_OPEN_NEW_ISSUE"
    }
}

private enum class Screen { MAIN, SETTINGS, NEW_ISSUE }

@Composable
private fun AppRoot(intentSignal: Intent?) {
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }

    LaunchedEffect(intentSignal) {
        if (intentSignal?.getBooleanExtra(MainActivity.EXTRA_OPEN_NEW_ISSUE, false) == true) {
            screen = Screen.NEW_ISSUE
            intentSignal.removeExtra(MainActivity.EXTRA_OPEN_NEW_ISSUE)
        }
    }

    when (screen) {
        Screen.MAIN -> MainScreen(
            onOpenSettings = { screen = Screen.SETTINGS },
            onCreateIssue = { screen = Screen.NEW_ISSUE },
        )
        Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.MAIN })
        Screen.NEW_ISSUE -> NewIssueScreen(
            onBack = { screen = Screen.MAIN },
            onCreated = { screen = Screen.MAIN },
        )
    }
}
