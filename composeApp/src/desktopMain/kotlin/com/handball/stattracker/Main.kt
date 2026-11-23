package com.handball.stattracker

import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.handball.stattracker.ui.HandballApp
import com.handball.stattracker.ui.brand.harzMarkVector

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Harzlytics",
        icon = rememberVectorPainter(harzMarkVector()),
        state = rememberWindowState(width = 1280.dp, height = 840.dp)
    ) {
        HandballApp()
    }
}
