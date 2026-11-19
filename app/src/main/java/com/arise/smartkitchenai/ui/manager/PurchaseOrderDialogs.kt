package com.arise.smartkitchenai.ui.manager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.OrderWithItems
import com.arise.smartkitchenai.data.ReceivedLine
import com.arise.smartkitchenai.ui.components.DateField
import com.arise.smartkitchenai.ui.components.DropdownField
import com.arise.smartkitchenai.ui.components.ItemEmojiTile
import com.arise.smartkitchenai.ui.components.NumberField
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asMoney
import com.arise.smartkitchenai.util.asQuantity
import com.arise.smartkitchenai.util.toQuantityOrNull
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

/** Editable line state inside the order editor. */
private class LineState(val ingredient: Ingredient, quantity: String, unitCost: String) {
    var quantity by mutableStateOf(quantity)
    var unitCost by mutableStateOf(unitCost)
    val q get() = quantity.toQuantityOrNull()
    val c get() = unitCost.toQuantityOrNull()
    val valid get() = q != null && q!! > 0 && c != null && c!! >= 0
}

/**
 * Create or edit a draft purchase order.
 * @param existing the order being edited, or null for a new one.
 * @param prefill lines for a new order (e.g. reorder suggestions).
 */
@Composable
fun OrderEditorDialog(
    existing: OrderWithItems?,
    prefill: List<OrderLine>,
    ingredients: List<Ingredient>,
    lastSupplier: Map<Long, String>,
    onDismiss: () -> Unit,
    onSave: (supplier: String, note: String, lines: List<OrderLine>) -> Unit,
) {
    val byId = remember(ingredients) { ingredients.associateBy { it.id } }
    val lines = remember {
        mutableStateListOf<LineState>().apply {
            if (existing != null) {
                existing.items.forEach { item ->
                    byId[item.ingredientId]?.let { add(LineState(it, item.quantity.asQuantity(), item.unitCost.asQuantity())) }
                }
            } else {
                prefill.forEach { add(LineState(it.ingredient, it.quantity.asQuantity(), it.unitCost.asQuantity())) }
            }
        }
    }
    // Pre-fill the supplier when all suggested items were last bought from the same one.
    val suggestedSupplier = remember {
        prefill.mapNotNull { lastSupplier[it.ingredient.id] }.distinct().singleOrNull().orEmpty()
    }
    var supplier by rememberSaveable { mutableStateOf(existing?.order?.supplier ?: suggestedSupplier) }
    var note by rememberSaveable { mutableStateOf(existing?.order?.supplierNote.orEmpty()) }
    val valid = supplier.isNotBlank() && lines.isNotEmpty() && lines.all { it.valid }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New purchase order" else "Edit order") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = supplier, onValueChange = { supplier = it }, label = { Text("Supplier") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Supplier note (e.g. Produce • Local)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                if (lines.isEmpty()) {
                    Text("Add at least one item.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemEmojiTile(line.ingredient.name, category = line.ingredient.category, size = 36.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(line.ingredient.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { lines.remove(line) }) { Icon(Icons.Filled.Close, contentDescription = "Remove item") }
                    }
                    Row {
                        NumberField(
                            line.quantity, { line.quantity = it }, "Qty", suffix = line.ingredient.unit,
                            isError = line.q == null || line.q!! <= 0, modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        NumberField(
                            line.unitCost, { line.unitCost = it }, "Price/${line.ingredient.unit}",
                            isError = line.c == null || line.c!! < 0, modifier = Modifier.weight(1f),
                        )
                    }
                }
                val available = ingredients.filter { ing -> lines.none { it.ingredient.id == ing.id } }
                if (available.isNotEmpty()) {
                    DropdownField(
                        label = "Add item",
                        options = available,
                        selected = null,
                        optionLabel = { it.name },
                        onSelect = { lines.add(LineState(it, "", it.unitCost.asQuantity())) },
                    )
                }
                val total = lines.sumOf { (it.q ?: 0.0) * (it.c ?: 0.0) }
                Text("Total: ${total.asMoney()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(supplier, note, lines.map { OrderLine(it.ingredient, it.q!!, it.c!!) }) },
                enabled = valid,
            ) { Text("Save draft") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Confirm what actually arrived (quantities, prices, expiry) and add it to stock. */
@Composable
fun ReceiveOrderDialog(
    order: OrderWithItems,
    ingredients: List<Ingredient>,
    onDismiss: () -> Unit,
    onReceive: (lines: List<ReceivedLine>, day: Long) -> Unit,
) {
    val byId = remember(ingredients) { ingredients.associateBy { it.id } }
    val items = remember { order.items.filter { byId.containsKey(it.ingredientId) } }
    val quantities = remember { mutableStateListOf(*items.map { it.quantity.asQuantity() }.toTypedArray()) }
    val costs = remember { mutableStateListOf(*items.map { it.unitCost.asQuantity() }.toTypedArray()) }
    val expiries = remember { mutableStateListOf<Long?>(*arrayOfNulls(items.size)) }
    var day by rememberSaveable { mutableStateOf(Dates.todayEpochDay()) }
    val skipped = order.items.size - items.size
    val valid = items.indices.all {
        val q = quantities[it].toQuantityOrNull()
        val c = costs[it].toQuantityOrNull()
        q != null && q >= 0 && c != null && c >= 0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receive order — ${order.order.supplier}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Check what arrived. Set quantity to 0 for anything missing.",
                    style = MaterialTheme.typography.bodySmall,
                )
                items.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemEmojiTile(item.ingredientName, size = 36.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(item.ingredientName, style = MaterialTheme.typography.titleSmall)
                    }
                    Row {
                        NumberField(quantities[i], { quantities[i] = it }, "Received", suffix = item.unit, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        NumberField(costs[i], { costs[i] = it }, "Price/${item.unit}", modifier = Modifier.weight(1f))
                    }
                    DateField("Expiry date (optional)", expiries[i], { expiries[i] = it })
                    HorizontalDivider()
                }
                if (skipped > 0) {
                    Text(
                        "$skipped item(s) no longer in inventory and will be skipped.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                DateField("Delivery date", day, { it?.let { d -> day = d } }, clearable = false)
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onReceive(
                        items.mapIndexed { i, item ->
                            ReceivedLine(item.ingredientId, quantities[i].toQuantityOrNull()!!, costs[i].toQuantityOrNull()!!, expiries[i])
                        },
                        day,
                    )
                },
            ) { Text("Receive & add to stock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Record a single delivery that wasn't ordered through the app. */
@Composable
fun ReceiveDeliveryDialog(
    ingredients: List<Ingredient>,
    findByBarcode: suspend (String) -> Ingredient?,
    onDismiss: () -> Unit,
    onSave: (Ingredient, Double, Double, Long?, Long, String) -> Unit,
) {
    var ingredientId by rememberSaveable { mutableStateOf<Long?>(null) }
    val ingredient = ingredients.firstOrNull { it.id == ingredientId }
    var quantity by rememberSaveable { mutableStateOf("") }
    var unitCost by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf<Long?>(null) }
    var day by rememberSaveable { mutableStateOf(Dates.todayEpochDay()) }
    var supplier by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val q = quantity.toQuantityOrNull()
    val c = unitCost.toQuantityOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record a delivery") },
        text = {
            if (ingredients.isEmpty()) {
                Text("Add ingredients in the Inventory tab first.")
                return@AlertDialog
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = "Ingredient",
                    options = ingredients,
                    selected = ingredient,
                    optionLabel = { it.name },
                    onSelect = {
                        ingredientId = it.id
                        if (unitCost.isBlank()) unitCost = it.unitCost.asQuantity()
                    },
                )
                OutlinedButton(onClick = {
                    GmsBarcodeScanning.getClient(context).startScan().addOnSuccessListener { code ->
                        val raw = code.rawValue ?: return@addOnSuccessListener
                        scope.launch {
                            val found = findByBarcode(raw)
                            if (found == null) {
                                message = "This product isn't in the inventory. Add it in the Inventory tab first."
                            } else {
                                ingredientId = found.id
                                unitCost = found.unitCost.asQuantity()
                                message = null
                            }
                        }
                    }
                }) { Text("Scan barcode") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                NumberField(quantity, { quantity = it }, "Quantity received", suffix = ingredient?.unit, isError = quantity.isNotEmpty() && (q == null || q <= 0))
                NumberField(unitCost, { unitCost = it }, "Price per ${ingredient?.unit ?: "unit"}", isError = unitCost.isNotEmpty() && (c == null || c < 0))
                if (q != null && c != null) {
                    Text("Total: ${(q * c).asMoney()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                DateField("Expiry date (optional)", expiry, { expiry = it })
                DateField("Delivery date", day, { it?.let { d -> day = d } }, clearable = false)
                OutlinedTextField(
                    value = supplier, onValueChange = { supplier = it }, label = { Text("Supplier (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val valid = ingredient != null && q != null && q > 0 && c != null && c >= 0
            TextButton(onClick = { if (valid) onSave(ingredient, q, c, expiry, day, supplier) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
