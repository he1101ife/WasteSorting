package com.example.wastesorting.ui.screen

import android.graphics.Bitmap

sealed class Screen {
    object Main : Screen()
    object BrowseRecords : Screen()
    object Statistics : Screen()
    object SimpleCamera : Screen()
    data class SimpleResult(val bitmap: Bitmap, val recordId: Long) : Screen()
    data class Result(val bitmap: Bitmap, val recordId: Long, val showAiSuggestion: Boolean = true) : Screen()
    data class RecordDetail(val recordId: Long) : Screen()
    object AiMode : Screen()
}
