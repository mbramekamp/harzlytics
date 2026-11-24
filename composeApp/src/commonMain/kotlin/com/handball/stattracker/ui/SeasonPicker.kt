package com.handball.stattracker.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
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
import com.handball.stattracker.model.SeasonScope
import androidx.compose.foundation.layout.padding

/**
 * Die eine Saison-Auswahl der App (Kopfzeile). Alle Screens zeigen die hier gewählte Saison;
 * beim Start ist es die aktuelle (siehe `SeasonSelection`).
 */
@Composable
fun SeasonPicker(
    seasons: List<Season>,
    scopes: List<SeasonScope>,
    selected: SeasonScope,
    onSelect: (SeasonScope) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (seasons.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        TextButton(
            modifier = if (compact) Modifier.height(30.dp) else Modifier,
            onClick = { expanded = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                scopeLabel(seasons, selected),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Saison wählen")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            scopes.forEach { scope ->
                val season = seasons.find { it.seasonId == scope.seasonId } ?: return@forEach
                DropdownMenuItem(
                    // Vorbereitung eingerückt unter ihrer Saison
                    text = {
                        Text(
                            if (scope.isPreseason) "Vorbereitung" else season.division,
                            modifier = if (scope.isPreseason) Modifier.padding(start = 16.dp) else Modifier
                        )
                    },
                    trailingIcon = {
                        if (scope == selected) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onSelect(scope)
                    }
                )
            }
        }
    }
}

/** Anzeigename eines Saison-Bereichs, z. B. "Oberliga Nord 2026/27" oder "Vorbereitung – Oberliga Nord 2026/27". */
fun scopeLabel(seasons: List<Season>, scope: SeasonScope): String {
    val division = seasons.find { it.seasonId == scope.seasonId }?.division ?: seasons.firstOrNull()?.division.orEmpty()
    return if (scope.isPreseason) "Vorbereitung – $division" else division
}
