package com.handball.stattracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.AuthActionResult
import com.handball.stattracker.model.AuthState
import com.handball.stattracker.repository.AuthRepository
import kotlinx.coroutines.launch

private enum class AuthMode { LOGIN, REGISTER, RESET }

@Composable
private fun AuthScaffold(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BrandHeader()
                    content()
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "H",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
    Text("Harzlytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun AuthMessage(result: AuthActionResult?) {
    when (result) {
        is AuthActionResult.Error -> Text(
            result.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        is AuthActionResult.Info -> Text(
            result.message,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        else -> Unit
    }
}

@Composable
fun LoginScreen(auth: AuthRepository, initialMessage: String?) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember(initialMessage) {
        mutableStateOf<AuthActionResult?>(initialMessage?.let { AuthActionResult.Info(it) })
    }

    fun submit() {
        if (busy) return
        busy = true
        result = null
        scope.launch {
            val outcome = when (mode) {
                AuthMode.LOGIN -> auth.signIn(email, password)
                AuthMode.REGISTER -> auth.signUp(email, password, fullName, inviteCode)
                AuthMode.RESET -> auth.sendPasswordReset(email)
            }
            busy = false
            if (outcome !is AuthActionResult.Success) result = outcome
            if (outcome is AuthActionResult.Info && mode == AuthMode.REGISTER) {
                mode = AuthMode.LOGIN
                password = ""
            }
        }
    }

    AuthScaffold {
        Text(
            when (mode) {
                AuthMode.LOGIN -> "Anmelden"
                AuthMode.REGISTER -> "Konto erstellen"
                AuthMode.RESET -> "Passwort zurücksetzen"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (mode == AuthMode.REGISTER) {
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Vor- und Nachname") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-Mail") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = if (mode == AuthMode.RESET) ImeAction.Done else ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth()
        )

        if (mode != AuthMode.RESET) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Passwort") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Passwort verbergen" else "Passwort anzeigen"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (mode == AuthMode.LOGIN) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                supportingText = if (mode == AuthMode.REGISTER) {
                    { Text("Mind. 8 Zeichen, eine Zahl, ein Großbuchstabe") }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (mode == AuthMode.REGISTER) {
            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it },
                label = { Text("Einladungscode (optional)") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth()
            )
        }

        AuthMessage(result)

        Button(
            onClick = { submit() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    when (mode) {
                        AuthMode.LOGIN -> "Anmelden"
                        AuthMode.REGISTER -> "Registrieren"
                        AuthMode.RESET -> "Link senden"
                    }
                )
            }
        }

        when (mode) {
            AuthMode.LOGIN -> {
                TextButton(onClick = { mode = AuthMode.REGISTER; result = null }, enabled = !busy) {
                    Text("Noch kein Konto? Registrieren")
                }
                TextButton(onClick = { mode = AuthMode.RESET; result = null }, enabled = !busy) {
                    Text("Passwort vergessen?")
                }
            }
            AuthMode.REGISTER, AuthMode.RESET -> {
                TextButton(onClick = { mode = AuthMode.LOGIN; result = null }, enabled = !busy) {
                    Text("Zurück zur Anmeldung")
                }
            }
        }
    }
}

@Composable
fun PendingApprovalScreen(auth: AuthRepository, state: AuthState.PendingApproval) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AuthActionResult?>(null) }

    AuthScaffold {
        Text("Freischaltung ausstehend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (state.profile == null) {
                "Für ${state.session.email} wurde noch kein Vereinsprofil gefunden. " +
                    "Bitte wende dich an deinen Vereins-Admin."
            } else {
                "Angemeldet als ${state.session.email}. Dein Konto muss noch von einem Vereins-Admin freigeschaltet werden."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AuthMessage(result)
        Button(
            onClick = {
                busy = true
                result = null
                scope.launch {
                    val outcome = auth.reloadProfile()
                    busy = false
                    if (outcome !is AuthActionResult.Success) result = outcome
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Erneut prüfen")
        }
        TextButton(onClick = { scope.launch { auth.signOut() } }, enabled = !busy) {
            Text("Abmelden")
        }
    }
}

@Composable
fun AuthLoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
fun NotConfiguredScreen() {
    AuthScaffold {
        Text("App nicht eingerichtet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Die Verbindung zum Verein fehlt. Bitte wende dich an den Administrator.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
