package com.arise.smartkitchenai.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arise.smartkitchenai.auth.PinResult
import com.arise.smartkitchenai.auth.Role
import com.arise.smartkitchenai.auth.RoleManager
import com.arise.smartkitchenai.ui.components.NumberPad
import com.arise.smartkitchenai.ui.components.PinDots

/**
 * First launch: the Owner creates a PIN for each role (entered twice to confirm).
 * PINs must all be different, otherwise one PIN would unlock several roles.
 */
@Composable
fun FirstTimeSetupScreen(roleManager: RoleManager) {
    // PINs are kept only in memory (never in saved state) while setting up.
    var roleIndex by remember { mutableStateOf(0) }
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val chosen = remember { mutableMapOf<Role, String>() }

    val role = Role.entries[roleIndex]
    val confirming = firstEntry != null
    val intro = if (roleIndex == 0 && !confirming) "Welcome to SmartKitchen AI. As the Owner, set up a PIN for each role. " else ""

    PinEntry(
        title = if (confirming) "Confirm ${role.label} PIN" else "Create ${role.label} PIN",
        subtitle = intro + "${role.label}: ${role.description}. You can change PINs later.",
        errorMessage = error,
        onBack = if (confirming) ({ firstEntry = null; error = null }) else null,
        onPinComplete = { pin ->
            when {
                !confirming && chosen.any { (r, p) -> r != role && p == pin } ->
                    error = "This PIN is already used for another role. Choose a different one."
                !confirming -> {
                    firstEntry = pin
                    error = null
                }
                pin != firstEntry -> {
                    firstEntry = null
                    error = "PINs didn't match. Try again."
                }
                else -> {
                    chosen[role] = pin
                    roleManager.setPin(role, pin)
                    firstEntry = null
                    error = null
                    if (roleIndex < Role.entries.lastIndex) roleIndex++ else roleManager.startSession(Role.OWNER)
                }
            }
        },
    )
}

/** "Who are you?" role picker followed by PIN entry. */
@Composable
fun LoginScreen(roleManager: RoleManager) {
    var selected by remember { mutableStateOf<Role?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val role = selected
    if (role == null) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("SmartKitchen AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Who are you?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Role.entries.forEach { r ->
                Card(
                    onClick = { selected = r; error = null },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(r.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(r.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    } else {
        PinEntry(
            title = "${role.label} PIN",
            subtitle = "Enter your ${RoleManager.PIN_LENGTH}-digit PIN.",
            errorMessage = error,
            onBack = { selected = null; error = null },
            onPinComplete = { pin ->
                error = when (val result = roleManager.login(role, pin)) {
                    PinResult.Success -> null
                    is PinResult.Wrong -> "Wrong PIN. ${result.attemptsLeft} attempts left."
                    is PinResult.LockedOut -> "Too many wrong attempts. Try again in ${result.secondsLeft} seconds."
                }
            },
        )
    }
}

@Composable
private fun PinEntry(
    title: String,
    subtitle: String,
    errorMessage: String?,
    onBack: (() -> Unit)?,
    onPinComplete: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().height(48.dp)) {
            if (onBack != null) TextButton(onClick = onBack) { Text("← Back") }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(32.dp))
        PinDots(length = RoleManager.PIN_LENGTH, filled = pin.length, isError = errorMessage != null && pin.isEmpty())
        Column(Modifier.height(56.dp), verticalArrangement = Arrangement.Center) {
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
        NumberPad(
            onKey = { digit ->
                if (pin.length < RoleManager.PIN_LENGTH) {
                    pin += digit
                    if (pin.length == RoleManager.PIN_LENGTH) {
                        val entered = pin
                        pin = ""
                        onPinComplete(entered)
                    }
                }
            },
            onBackspace = { pin = pin.dropLast(1) },
            modifier = Modifier.widthIn(max = 360.dp),
        )
    }
}
