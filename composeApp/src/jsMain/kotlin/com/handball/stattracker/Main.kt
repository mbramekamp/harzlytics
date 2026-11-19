package com.handball.stattracker

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.handball.stattracker.ui.HandballApp
import kotlinx.browser.document
import org.jetbrains.skiko.wasm.onWasmReady

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    onWasmReady {
        document.title = "Harzlytics"
        ComposeViewport(viewportContainerId = "ComposeTarget") {
            HandballApp()
        }
    }
}
