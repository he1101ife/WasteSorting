package com.example.wastesorting.util

import androidx.compose.ui.graphics.Color
import com.example.wastesorting.ui.theme.HazardousColor
import com.example.wastesorting.ui.theme.KitchenColor
import com.example.wastesorting.ui.theme.OtherColor
import com.example.wastesorting.ui.theme.RecyclableColor

fun catColor(category: String): Color = when (category) {
    "可回收物" -> RecyclableColor
    "厨余垃圾" -> KitchenColor
    "有害垃圾" -> HazardousColor
    "其他垃圾" -> OtherColor
    else -> Color.Gray
}
