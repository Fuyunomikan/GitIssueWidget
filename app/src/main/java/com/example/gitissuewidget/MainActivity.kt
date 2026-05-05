package com.example.gitissuewidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.gitissuewidget.ui.main.MainScreen
import com.example.gitissuewidget.ui.settings.SettingsScreen
import com.example.gitissuewidget.ui.theme.GitIssueWidgetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GitIssueWidgetTheme {
                AppRoot()
            }
        }
    }
}

private enum class Screen { MAIN, SETTINGS }

@Composable
private fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    when (screen) {
        Screen.MAIN -> MainScreen(onOpenSettings = { screen = Screen.SETTINGS })
        Screen.SETTINGS -> SettingsScreen(onBack = { screen = Screen.MAIN })
    }
}
