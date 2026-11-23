package com.handball.stattracker

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.handball.stattracker.ui.HandballApp

/** Einstieg der Web-Version: zeichnet die App in die ganze Browserseite (index.html). */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        HandballApp()
    }
}
