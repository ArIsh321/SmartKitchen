package com.arise.smartkitchenai.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arise.smartkitchenai.data.BarcodeResult
import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.isLowStock
import com.arise.smartkitchenai.data.stockValue
import com.arise.smartkitchenai.ui.AppViewModelFactory
import com.arise.smartkitchenai.ui.components.DateField
import com.arise.smartkitchenai.ui.components.EmptyState
import com.arise.smartkitchenai.ui.components.NumberField
import com.arise.smartkitchenai.ui.components.Stat
import com.arise.smartkitchenai.ui.components.SummaryCard
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asMoney
import com.arise.smartkitchenai.util.asQuantity
import com.arise.smartkitchenai.util.toQuantityOrNull
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: InventoryViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    // null = closed; Ingredient with id 0 = new item.
    var editing by remember { mutableStateOf<Ingredient?>(null) }

    // A scanned barcode that is already in stock switches the dialog to that ingredient.
    LaunchedEffect(scanState) {
        val result = (scanState as? ScanState.Done)?.result
        if (result is BarcodeResult.InInventory) {
            editing = result.ingredient
            viewModel.consumeScanResult()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Inventory") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.consumeScanResult()
                    editing = Ingredient(name = "", category = "", unit = "kg", quantity = 0.0, unitCost = 0.0, minStock = 0.0)
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add ingredient") },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp, // room for the FAB
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SummaryCard(
                    listOf(
                        Stat("Items", state.itemCount.toString()),
                        Stat("Stock value", state.totalValue.asMoney()),
                        Stat("Low stock", state.lowStockCount.toString(), highlight = state.lowStockCount > 0),
                    )
                )
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search name or category") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.ingredients.isEmpty()) {
                item {
                    EmptyState(
                        title = if (state.itemCount == 0) "No ingredients yet" else "No matches",
                        message = if (state.itemCount == 0) "Tap \"Add ingredient\" to start tracking your stock." else "Try a different search.",
                    )
                }
            }
            items(state.ingredients, key = { it.id }) { ingredient ->
                IngredientRow(ingredient, onClick = { editing = ingredient })
            }
        }
    }

    editing?.let { ingredient ->
        val close = {
            viewModel.consumeScanResult()
            editing = null
        }
        // Re-create the dialog's form state when switching to a different ingredient.
        key(ingredient.id) {
            IngredientDialog(
                initial = ingredient,
                scanState = scanState,
                onScan = viewModel::onBarcodeScanned,
                onScanConsumed = viewModel::consumeScanResult,
                onDismiss = close,
                onSave = { viewModel.save(it); close() },
                onDelete = if (ingredient.id != 0L) ({ viewModel.delete(ingredient); close() }) else null,
                // Close the edit form afterwards: its quantity field would otherwise be out of date.
                onRecordUsage = if (ingredient.id != 0L) ({ qty, day -> viewModel.recordUsage(ingredient.id, qty, day); close() }) else null,
            )
        }
    }
}

@Composable
private fun IngredientRow(ingredient: Ingredient, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ingredient.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val subtitle = buildList {
                    if (ingredient.category.isNotBlank()) add(ingredient.category)
                    add("${ingredient.unitCost.asMoney()}/${ingredient.unit}")
                    ingredient.expiryEpochDay?.let { add("Exp. ${Dates.format(it)}") }
                }.joinToString(" • ")
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ingredient.isLowStock) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Low stock",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(
                        "${ingredient.quantity.asQuantity()} ${ingredient.unit}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (ingredient.isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(ingredient.stockValue.asMoney(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun IngredientDialog(
    initial: Ingredient,
    scanState: ScanState,
    onScan: (String) -> Unit,
    onScanConsumed: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Ingredient) -> Unit,
    onDelete: (() -> Unit)?,
    onRecordUsage: ((quantity: Double, epochDay: Long) -> Unit)?,
) {
    val isNew = initial.id == 0L
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var category by rememberSaveable { mutableStateOf(initial.category) }
    var unit by rememberSaveable { mutableStateOf(initial.unit) }
    var quantity by rememberSaveable { mutableStateOf(if (isNew) "" else initial.quantity.asQuantity()) }
    var unitCost by rememberSaveable { mutableStateOf(if (isNew) "" else initial.unitCost.asQuantity()) }
    var minStock by rememberSaveable { mutableStateOf(if (isNew) "" else initial.minStock.asQuantity()) }
    var expiry by rememberSaveable { mutableStateOf(initial.expiryEpochDay) }
    var barcode by rememberSaveable { mutableStateOf(initial.barcode) }
    var scanMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showUsageDialog by rememberSaveable { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    // Apply a barcode lookup result to the form. Quantity, min stock and expiry are kept.
    LaunchedEffect(scanState) {
        val result = (scanState as? ScanState.Done)?.result ?: return@LaunchedEffect
        when (result) {
            is BarcodeResult.InInventory -> return@LaunchedEffect // handled by the screen
            is BarcodeResult.Found -> {
                name = result.name
                if (result.category.isNotBlank()) category = result.category
                unit = result.unit
                result.unitCost?.let { unitCost = it.asQuantity() }
                scanMessage = "Filled from ${result.source}. Check the details, then add quantity, cost and expiry."
            }
            is BarcodeResult.NotFound ->
                scanMessage = "Product not found. Enter the details once — this barcode will be remembered."
            is BarcodeResult.LookupFailed ->
                scanMessage = "Couldn't look up the product (no internet?). Enter the details — the barcode is saved."
        }
        barcode = result.barcode
        onScanConsumed()
    }

    val context = LocalContext.current
    fun launchScanner() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_ITF,
                Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX,
            )
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code -> code.rawValue?.takeIf { it.isNotBlank() }?.let(onScan) }
            .addOnFailureListener { e -> scanMessage = "Scanner unavailable: ${e.localizedMessage}" }
        // Cancelling the scanner needs no handling.
    }

    val q = quantity.toQuantityOrNull()
    val cost = unitCost.toQuantityOrNull()
    val min = minStock.ifBlank { "0" }.toQuantityOrNull()
    val valid = name.isNotBlank() && unit.isNotBlank() &&
        q != null && q >= 0 && cost != null && cost >= 0 && min != null && min >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add ingredient" else "Edit ingredient") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ---- Barcode ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = ::launchScanner,
                        enabled = scanState !is ScanState.LookingUp,
                    ) {
                        if (scanState is ScanState.LookingUp) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Looking up…")
                        } else {
                            Text("Scan barcode")
                        }
                    }
                    barcode?.let { code ->
                        Text(
                            code,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        IconButton(onClick = { barcode = null; scanMessage = null }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Remove barcode")
                        }
                    }
                }
                scanMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                // ---- Details ----
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, isError = showErrors && name.isBlank(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category, onValueChange = { category = it }, label = { Text("Category (e.g. Produce, Dairy)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    NumberField(
                        quantity, { quantity = it }, "Quantity",
                        isError = showErrors && (q == null || q < 0), modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = unit, onValueChange = { unit = it }, label = { Text("Unit") },
                        singleLine = true, isError = showErrors && unit.isBlank(), modifier = Modifier.weight(0.6f),
                    )
                }
                NumberField(
                    unitCost, { unitCost = it }, "Cost per ${unit.ifBlank { "unit" }}",
                    isError = showErrors && (cost == null || cost < 0),
                )
                NumberField(
                    minStock, { minStock = it }, "Minimum stock level (alert below)",
                    isError = showErrors && (min == null || min < 0), suffix = unit,
                )
                DateField("Expiry date", expiry, { expiry = it })

                // ---- Usage (existing items only) ----
                if (onRecordUsage != null) {
                    OutlinedButton(onClick = { showUsageDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Record usage")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (valid) {
                    onSave(
                        initial.copy(
                            name = name.trim(), category = category.trim(), unit = unit.trim(),
                            quantity = q!!, unitCost = cost!!, minStock = min!!, expiryEpochDay = expiry,
                            barcode = barcode?.trim()?.ifBlank { null },
                        )
                    )
                } else {
                    showErrors = true
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (showUsageDialog && onRecordUsage != null) {
        RecordUsageDialog(
            ingredient = initial,
            onDismiss = { showUsageDialog = false },
            onConfirm = { qty, day ->
                showUsageDialog = false
                onRecordUsage(qty, day)
            },
        )
    }
}

@Composable
private fun RecordUsageDialog(
    ingredient: Ingredient,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double, epochDay: Long) -> Unit,
) {
    var quantity by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(Dates.todayEpochDay()) }
    var showErrors by remember { mutableStateOf(false) }
    val q = quantity.toQuantityOrNull()
    val valid = q != null && q > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record usage — ${ingredient.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "In stock: ${ingredient.quantity.asQuantity()} ${ingredient.unit}. " +
                        "Enter how much was used in cooking; it will be subtracted from stock.",
                    style = MaterialTheme.typography.bodySmall,
                )
                NumberField(
                    quantity, { quantity = it }, "Quantity used",
                    isError = showErrors && !valid, suffix = ingredient.unit,
                )
                if (q != null && q > ingredient.quantity) {
                    Text(
                        "More than current stock — stock will be set to 0.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                DateField("Date", day, { it?.let { d -> day = d } }, clearable = false)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(q!!, day) else showErrors = true }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
