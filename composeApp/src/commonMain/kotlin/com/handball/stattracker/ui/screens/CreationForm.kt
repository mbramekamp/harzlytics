package com.handball.stattracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.handball.stattracker.repository.DisplayFormat

/** Ab dieser Breite erscheint ein Formular als Dialog, darunter als eigener Bildschirm (wie die Navigation). */
private val FORM_DIALOG_MIN_WIDTH = 700.dp

/**
 * Hülle für alle Formulare hinter dem "+": auf dem Handy ein ganzer Bildschirm, auf Tablet/PC ein Dialog.
 * Oben: Schließen bzw. Zurück, Titel (optional mit Untertitel) und die Hauptaktion; darunter [content].
 * [content] entscheidet selbst, ob es scrollt (Formular) oder eine Liste ist (Aufgebot).
 */
@Composable
internal fun CreationFormFrame(
    title: String,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    onClose: () -> Unit,
    subtitle: String? = null,
    showBack: Boolean = false,
    onBack: () -> Unit = onClose,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val asDialog = maxWidth >= FORM_DIALOG_MIN_WIDTH
            Surface(
                shape = if (asDialog) RoundedCornerShape(14.dp) else RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = if (asDialog) {
                    Modifier.align(Alignment.Center).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight * 0.88f)
                } else {
                    Modifier.fillMaxSize()
                }
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 4.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = if (showBack) onBack else onClose) {
                            Icon(
                                if (showBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                                contentDescription = if (showBack) "Zurück" else "Schließen"
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (subtitle != null) {
                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(onClick = onAction, enabled = actionEnabled) { Text(actionLabel) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(modifier = if (asDialog) Modifier.fillMaxWidth().weight(1f, fill = false) else Modifier.fillMaxWidth().weight(1f)) {
                        content()
                    }
                }
            }
        }
    }
}

/** Kurze Abschnittsüberschrift im Formular. */
@Composable
internal fun FormSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Auswahl zwischen wenigen gleichrangigen Möglichkeiten (z. B. Heim/Auswärts) als Segmentleiste. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

/** Ausklappliste statt langer Radio-Listen (Gegner, Saison, Position). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> DropdownField(
    label: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Datumsfeld: zeigt deutsch an ("24.11.2026"), öffnet zum Ändern einen Kalender; gespeichert wird ISO. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(label: String, isoDate: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = DisplayFormat.date(isoDate),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        // Fläche über dem Feld fängt den Tipp ab (ein schreibgeschütztes Feld reagiert sonst nicht)
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = DisplayFormat.isoToEpochMillisUtc(isoDate))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(DisplayFormat.epochMillisUtcToIso(it)) }
                    open = false
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Abbrechen") } }
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

/** Zahlenfeld (Trikotnummer, Tore) mit Zifferntastatur; nimmt nur Ziffern an. */
@Composable
internal fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() }.take(3)) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/** Standard-Innenabstand und Zeilenabstand der Formularfelder. */
internal val FormContentPadding = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
internal val FormFieldSpacing = Arrangement.spacedBy(14.dp)
