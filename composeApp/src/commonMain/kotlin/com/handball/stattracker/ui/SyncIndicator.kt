package com.handball.stattracker.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.SyncState

/** Einziger Sync-Hinweis der App: ein Symbol. Antippen startet eine Synchronisation. */
@Composable
fun SyncIndicator(state: SyncState, onSync: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onSync, enabled = state != SyncState.SYNCING, modifier = modifier) {
        when (state) {
            SyncState.SYNCING -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            SyncState.SYNCED -> Icon(Icons.Default.CloudDone, "Synchronisiert", tint = MaterialTheme.colorScheme.tertiary)
            SyncState.SYNCED_PENDING -> Icon(Icons.Default.CloudSync, "Änderungen werden gesendet", tint = MaterialTheme.colorScheme.primary)
            SyncState.OFFLINE -> Icon(Icons.Default.CloudOff, "Offline", tint = MaterialTheme.colorScheme.secondary)
            SyncState.UNAVAILABLE -> Icon(Icons.Default.CloudOff, "Sync nicht möglich", tint = MaterialTheme.colorScheme.error)
            SyncState.IDLE -> Icon(Icons.Default.Cloud, "Noch nicht synchronisiert", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
