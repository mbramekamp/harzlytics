package com.handball.stattracker.ui.theme

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable

/** Untere Navigation: gewählter Eintrag in Lila statt Materials Standard (Bernstein/`secondary`). */
@Composable
fun navigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

/** Gewählte Saison-Chips: kräftig in Lila, auch auf lila Flächen gut erkennbar. */
@Composable
fun seasonChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
)

/** Bernstein-Fläche für Hinweise mit fester Bedeutung (Gelbe Karte, 2-Minuten-Strafe, Unentschieden, Auszeit). */
@Composable
fun amberContainer() = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
