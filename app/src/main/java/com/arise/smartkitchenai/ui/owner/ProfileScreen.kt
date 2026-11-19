package com.arise.smartkitchenai.ui.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arise.smartkitchenai.auth.Role
import com.arise.smartkitchenai.auth.RoleManager
import com.arise.smartkitchenai.data.Branch
import com.arise.smartkitchenai.ui.AppViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Branch being renamed; Branch(id = 0) = adding a new one.
    var branchDialog by remember { mutableStateOf<Branch?>(null) }
    var pinDialog by rememberSaveable { mutableStateOf<Role?>(null) }
    var pinChangedFor by remember { mutableStateOf<Role?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section("Restaurant") {
                    SavableField("Restaurant name", state.restaurantName, viewModel::setRestaurantName)
                    SavableField("Owner name (shown as initials)", state.ownerName, viewModel::setOwnerName)
                }
            }

            item {
                Section("Branches") {
                    Text(
                        "Manager and Kitchen staff on this device work in the selected branch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.branches.forEach { branch ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = branch.id == state.deviceBranchId,
                                onClick = { viewModel.setDeviceBranch(branch.id) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(branch.name, style = MaterialTheme.typography.bodyLarge)
                                if (branch.id == state.deviceBranchId) {
                                    Text("This device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            TextButton(onClick = { branchDialog = branch }) { Text("Rename") }
                        }
                    }
                    OutlinedButton(onClick = { branchDialog = Branch(id = 0, name = "") }) { Text("Add branch") }
                    Text(
                        "Note: data is stored on this device only. To see branches that use other devices, " +
                            "cloud sync is needed (planned next phase).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Section("Security") {
                    Role.entries.forEach { role ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${role.label} PIN", style = MaterialTheme.typography.bodyLarge)
                                if (pinChangedFor == role) {
                                    Text("PIN changed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            TextButton(onClick = { pinDialog = role }) { Text("Change") }
                        }
                    }
                }
            }

            item {
                FilledTonalButton(onClick = viewModel::lock, modifier = Modifier.fillMaxWidth()) { Text("Lock / switch user") }
            }
        }
    }

    branchDialog?.let { branch ->
        NameDialog(
            title = if (branch.id == 0L) "Add branch" else "Rename branch",
            initial = branch.name,
            onDismiss = { branchDialog = null },
            onSave = { name ->
                if (branch.id == 0L) viewModel.addBranch(name) else viewModel.renameBranch(branch, name)
                branchDialog = null
            },
        )
    }

    pinDialog?.let { role ->
        ChangePinDialog(
            role = role,
            onDismiss = { pinDialog = null },
            onSave = { pin ->
                viewModel.changePin(role, pin).also { error ->
                    if (error == null) {
                        pinChangedFor = role
                        pinDialog = null
                    }
                }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** Text field with a Save button that appears once the value is edited. */
@Composable
private fun SavableField(label: String, value: String, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(value) }
    LaunchedEffect(value) { text = value }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        if (text.trim() != value && text.isNotBlank()) {
            TextButton(onClick = { onSave(text) }) { Text("Save") }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Branch name") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** @param onSave returns an error message, or null on success. */
@Composable
private fun ChangePinDialog(role: Role, onDismiss: () -> Unit, onSave: (String) -> String?) {
    // PINs are kept only in memory, never in saved state.
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val digitsOnly = { s: String -> s.filter(Char::isDigit).take(RoleManager.PIN_LENGTH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New ${role.label} PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin, onValueChange = { pin = digitsOnly(it) }, label = { Text("New PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = digitsOnly(it) }, label = { Text("Confirm PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = if (pin != confirm) "PINs don't match." else onSave(pin)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
