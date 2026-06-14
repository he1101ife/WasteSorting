package com.example.wastesorting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.wastesorting.ui.screen.AiModeScreen
import com.example.wastesorting.ui.screen.BrowseRecordsScreen
import com.example.wastesorting.ui.screen.MainScreen
import com.example.wastesorting.ui.screen.RecordDetailScreen
import com.example.wastesorting.ui.screen.ResultScreen
import com.example.wastesorting.ui.screen.Screen
import com.example.wastesorting.ui.screen.SimpleCameraScreen
import com.example.wastesorting.ui.screen.StatisticsScreen
import com.example.wastesorting.ui.theme.WasteSortingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WasteSortingTheme { AppRoot() }
        }
    }
}

@Composable
fun AppRoot() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    val navTo = { screen: Screen -> currentScreen = screen }

    when (val screen = currentScreen) {
        is Screen.Main -> MainScreen(
            onNavigateToRecords = { navTo(Screen.BrowseRecords) },
            onNavigateToStatistics = { navTo(Screen.Statistics) },
            onNavigateToSimpleCamera = { navTo(Screen.SimpleCamera) },
            onNavigateToAiMode = { navTo(Screen.AiMode) }
        )
        is Screen.SimpleCamera -> SimpleCameraScreen(
            onBack = { navTo(Screen.Main) },
            onNavigateToResult = { bmp, id -> navTo(Screen.SimpleResult(bmp, id)) }
        )
        is Screen.SimpleResult -> ResultScreen(
            bitmap = screen.bitmap, recordId = screen.recordId, showAiSuggestion = false,
            onBack = { navTo(Screen.Main) }
        )
        is Screen.AiMode -> AiModeScreen(
            onBack = { navTo(Screen.Main) },
            onNavigateToResult = { bmp, id -> navTo(Screen.Result(bmp, id, showAiSuggestion = true)) }
        )
        is Screen.Result -> ResultScreen(
            bitmap = screen.bitmap, recordId = screen.recordId, showAiSuggestion = screen.showAiSuggestion,
            onBack = { navTo(if (screen.showAiSuggestion) Screen.AiMode else Screen.Main) }
        )
        is Screen.BrowseRecords -> BrowseRecordsScreen(
            onBack = { navTo(Screen.Main) },
            onRecordClick = { navTo(Screen.RecordDetail(it)) }
        )
        is Screen.RecordDetail -> RecordDetailScreen(
            recordId = screen.recordId,
            onBack = { navTo(Screen.BrowseRecords) }
        )
        is Screen.Statistics -> StatisticsScreen(
            onBack = { navTo(Screen.Main) }
        )
    }
}
