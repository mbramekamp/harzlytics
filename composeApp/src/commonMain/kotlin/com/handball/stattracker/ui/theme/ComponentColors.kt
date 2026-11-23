package com.handball.stattracker.ui.theme

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable

/** Untere Navigation: gewähltes Symbol in Harz-Rot auf zartem Rot, Beschriftung in Textfarbe. */
@Composable
fun navigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.onSurface,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

/** Gewählte Saison-Chips: dunkle Graphit-Pille (hell) bzw. helle Pille (dunkel); Rot bleibt Aktionen vorbehalten. */
@Composable
fun seasonChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
    selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
    selectedLeadingIconColor = MaterialTheme.colorScheme.inverseOnSurface
)

/** Bernstein-Fläche für Hinweise mit fester Bedeutung (Gelbe Karte, 2-Minuten-Strafe, Unentschieden, Auszeit). */
@Composable
fun amberContainer() = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
