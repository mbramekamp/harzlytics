package com.handball.stattracker.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.Season

/**
 * Die eine Saison-Auswahl der App (Kopfzeile). Alle Screens zeigen die hier gewählte Saison;
 * beim Start ist es die aktuelle (siehe `SeasonSelection`).
 */
@Composable
fun SeasonPicker(
    seasons: List<Season>,
    selectedSeasonId: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (seasons.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val label = seasons.find { it.seasonId == selectedSeasonId }?.division ?: seasons.first().division

    Box(modifier) {
        TextButton(
            modifier = if (compact) Modifier.height(30.dp) else Modifier,
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Saison wählen")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            seasons.forEach { season ->
                DropdownMenuItem(
                    text = { Text(season.division) },
                    trailingIcon = {
                        if (season.seasonId == selectedSeasonId) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onSelect(season.seasonId)
                    }
                )
            }
        }
    }
}
