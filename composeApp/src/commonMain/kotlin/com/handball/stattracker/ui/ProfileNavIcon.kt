package com.handball.stattracker.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Symbol für "Profil" in der Navigation: die eigene Trikotnummer als kleines Schild
 * (wie das "#18" im Profilkopf), damit es nicht mit dem Konto-Symbol in der Kopfzeile verwechselt wird.
 * Ohne Nummer (z. B. Staff) das Personen-Symbol. Gewählt: gefüllt, sonst umrandet.
 */
@Composable
fun ProfileNavIcon(jerseyNumber: Int?, selected: Boolean) {
    if (jerseyNumber == null) {
        Icon(Icons.Default.Person, contentDescription = null)
        return
    }
    val color = LocalContentColor.current
    val shape = MaterialTheme.shapes.small
    // Gleiche Höhe wie die 24-dp-Symbole, damit die Beschriftung auf einer Linie bleibt
    Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
    Box(
        modifier = Modifier
            .height(20.dp)
            .defaultMinSize(minWidth = 24.dp)
            .then(
                if (selected) Modifier.background(color, shape)
                else Modifier.border(1.5.dp, color, shape)
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = jerseyNumber.toString(),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 13.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else color,
            maxLines = 1
        )
    }
    }
}
