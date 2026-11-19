package com.arise.smartkitchenai.ui.manager

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arise.smartkitchenai.KitchenApp
import com.arise.smartkitchenai.R
import com.arise.smartkitchenai.data.OrderStatus
import com.arise.smartkitchenai.data.OrderWithItems
import com.arise.smartkitchenai.data.lineTotal
import com.arise.smartkitchenai.ui.AppViewModelFactory
import com.arise.smartkitchenai.ui.components.ItemEmojiTile
import com.arise.smartkitchenai.ui.components.ingredientEmoji
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asMoney
import com.arise.smartkitchenai.util.asQuantity

/** Which dialog is open. */
private sealed interface PurchasingDialog {
    data class Editor(val existing: OrderWithItems?, val prefill: List<OrderLine>) : PurchasingDialog
    data class Receive(val order: OrderWithItems) : PurchasingDialog
    data class ConfirmDelete(val order: OrderWithItems) : PurchasingDialog
    data object SingleDelivery : PurchasingDialog
}

@Composable
fun PurchasingScreen(viewModel: PurchasingViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<PurchasingDialog?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val share: (OrderWithItems) -> Unit = { order ->
        val restaurant = (context.applicationContext as KitchenApp).settings.restaurantName.value
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, viewModel.orderShareText(order, restaurant))
        }
        context.startActivity(Intent.createChooser(send, "Send order to ${order.order.supplier}"))
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ManagerHeader(
                    branchName = state.branchName,
                    statusFilter = state.statusFilter,
                    onStatusFilter = viewModel::setStatusFilter,
                )
            }

            item {
                Column {
                    Text("Purchasing", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Manage purchase orders and keep your kitchen stocked.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                ReorderBanner(
                    count = state.reorder.size,
                    onClick = { dialog = PurchasingDialog.Editor(null, viewModel.suggestedLines()) },
                )
            }

            item {
                Button(
                    onClick = { dialog = PurchasingDialog.Editor(null, emptyList()) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                ) {
                    Icon(Icons.Filled.AddCircle, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Create purchase order", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(
                    onClick = { dialog = PurchasingDialog.SingleDelivery },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Record a delivery without an order") }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Purchase Orders",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    SortMenu(state.sort, viewModel::setSort)
                }
                state.statusFilter?.let {
                    Text(
                        "Showing: ${it.label} only",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (state.orders.isEmpty()) {
                item {
                    Text(
                        if (state.totalOrders == 0) "No purchase orders yet. Tap \"Create purchase order\" or the reorder banner to start one."
                        else "No orders match this filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.orders, key = { it.order.id }) { order ->
                OrderCard(
                    order = order,
                    onEdit = { dialog = PurchasingDialog.Editor(order, emptyList()) },
                    onMarkSent = {
                        viewModel.markSent(order.order)
                        share(order)
                    },
                    onShare = { share(order) },
                    onReceive = { dialog = PurchasingDialog.Receive(order) },
                    onDelete = { dialog = PurchasingDialog.ConfirmDelete(order) },
                )
            }
        }
    }

    when (val d = dialog) {
        is PurchasingDialog.Editor -> OrderEditorDialog(
            existing = d.existing,
            prefill = d.prefill,
            ingredients = state.ingredients,
            lastSupplier = state.lastSupplier,
            onDismiss = { dialog = null },
            onSave = { supplier, note, lines ->
                viewModel.saveOrder(d.existing?.order, supplier, note, lines)
                dialog = null
            },
        )
        is PurchasingDialog.Receive -> ReceiveOrderDialog(
            order = d.order,
            ingredients = state.ingredients,
            onDismiss = { dialog = null },
            onReceive = { lines, day ->
                viewModel.receiveOrder(d.order.order, lines, day)
                dialog = null
            },
        )
        is PurchasingDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete order?") },
            text = { Text("Delete the order to ${d.order.order.supplier}? Stock is not affected.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteOrder(d.order.order); dialog = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        PurchasingDialog.SingleDelivery -> ReceiveDeliveryDialog(
            ingredients = state.ingredients,
            findByBarcode = viewModel::findByBarcode,
            onDismiss = { dialog = null },
            onSave = { ing, qty, cost, expiry, day, supplier ->
                viewModel.receive(ing, qty, cost, expiry, day, supplier)
                dialog = null
            },
        )
        null -> Unit
    }
}

/** "SmartKitchen / Restaurant Operations", this device's branch, and the status filter. */
@Composable
private fun ManagerHeader(branchName: String, statusFilter: OrderStatus?, onStatusFilter: (OrderStatus?) -> Unit) {
    var filterOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    append("Smart")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) { append("Kitchen") }
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("Restaurant Operations", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Managers work in this device's branch; the Owner changes it in Profile.
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📍", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text(branchName, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box {
            Surface(
                onClick = { filterOpen = true },
                shape = CircleShape,
                color = if (statusFilter != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_filter), contentDescription = "Filter orders by status")
                }
            }
            DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }) {
                (listOf<OrderStatus?>(null) + OrderStatus.entries).forEach { status ->
                    DropdownMenuItem(
                        text = { Text((status?.label ?: "All orders") + if (status == statusFilter) "  ✓" else "") },
                        onClick = { filterOpen = false; onStatusFilter(status) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderBanner(count: Int, onClick: () -> Unit) {
    val warm = MaterialTheme.colorScheme.tertiaryContainer
    Card(
        onClick = onClick,
        enabled = count > 0,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = warm.copy(alpha = 0.55f),
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(warm, CircleShape), contentAlignment = Alignment.Center) {
                Text(if (count > 0) "📦" else "✅", fontSize = 26.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count > 0) "$count item${if (count == 1) "" else "s"} need reorder" else "All stocked up",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (count > 0) "Low stock items across all categories. Tap to start an order." else "Nothing needs ordering right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (count > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun SortMenu(sort: OrderSort, onSort: (OrderSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("Sort by: ${sort.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            OrderSort.entries.forEach {
                DropdownMenuItem(text = { Text(it.label) }, onClick = { open = false; onSort(it) })
            }
        }
    }
}

@Composable
private fun StatusChip(status: OrderStatus) {
    val (bg, fg) = when (status) {
        OrderStatus.DRAFT -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
        OrderStatus.SENT -> Color(0xFFFDEBC8) to Color(0xFF8A5300)
        OrderStatus.RECEIVED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(status.label, color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
private fun OrderCard(
    order: OrderWithItems,
    onEdit: () -> Unit,
    onMarkSent: () -> Unit,
    onShare: () -> Unit,
    onReceive: () -> Unit,
    onDelete: () -> Unit,
) {
    val o = order.order
    var menuOpen by remember { mutableStateOf(false) }
    // Alternate the supplier avatar tint like the design (green / terracotta).
    val warm = o.id % 2L == 0L
    val avatarBg = if (warm) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primaryContainer

    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(avatarBg, CircleShape), contentAlignment = Alignment.Center) {
                    Text(order.items.firstOrNull()?.let { ingredientEmoji(it.ingredientName) } ?: "🚚", fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.supplier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    val subtitle = listOfNotNull(
                        o.supplierNote.takeIf { it.isNotBlank() },
                        when (o.status) {
                            OrderStatus.DRAFT -> "Created ${Dates.format(o.createdDay)}"
                            OrderStatus.SENT -> o.sentDay?.let { "Sent ${Dates.format(it)}" }
                            OrderStatus.RECEIVED -> o.receivedDay?.let { "Received ${Dates.format(it)}" }
                        },
                    ).joinToString(" • ")
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(o.status)
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Order actions") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        val item: @Composable (String, () -> Unit) -> Unit = { label, action ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; action() })
                        }
                        if (o.status == OrderStatus.DRAFT) {
                            item("Edit", onEdit)
                            item("Mark as sent & share", onMarkSent)
                        }
                        item("Share with supplier", onShare)
                        if (o.status != OrderStatus.RECEIVED) {
                            item("Receive delivery", onReceive)
                            item("Delete", onDelete)
                        }
                    }
                }
            }

            order.items.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemEmojiTile(line.ingredientName)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(line.ingredientName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${line.quantity.asQuantity()} ${line.unit} • ${line.unitCost.asMoney()}/${line.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(line.lineTotal.asMoney(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${order.items.size} item${if (order.items.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text("Total", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(order.total.asMoney(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
