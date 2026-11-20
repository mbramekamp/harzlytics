package com.handball.stattracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.handball.stattracker.storage.PlatformStorage
import com.handball.stattracker.ui.HandballApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dauerhaften App-Ordner für lokale Daten festlegen (und alte Cache-Daten übernehmen)
        PlatformStorage.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            HandballApp()
        }
    }
}
