package com.arise.smartkitchenai.ui.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arise.smartkitchenai.R
import com.arise.smartkitchenai.data.Ingredient
import com.arise.smartkitchenai.data.WasteReason
import com.arise.smartkitchenai.data.isLowStock
import com.arise.smartkitchenai.ui.AppViewModelFactory
import com.arise.smartkitchenai.ui.components.ingredientEmoji
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asQuantity
import com.arise.smartkitchenai.util.toQuantityOrNull
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Calendar

private enum class EntryMode { USAGE, WASTE }

/** An entry unit and how many of the ingredient's own unit one of it equals. */
private data class EntryUnit(val label: String, val toBase: Double)

/** kg↔g and L↔mL conversions; any other unit is entered as-is. */
private fun unitOptions(unit: String): List<EntryUnit> = when (unit.trim().lowercase()) {
    "kg" -> listOf(EntryUnit(unit, 1.0), EntryUnit("g", 0.001))
    "g" -> listOf(EntryUnit(unit, 1.0), EntryUnit("kg", 1000.0))
    "l", "litre", "liter" -> listOf(EntryUnit(unit, 1.0), EntryUnit("mL", 0.001))
    "ml" -> listOf(EntryUnit(unit, 1.0), EntryUnit("L", 1000.0))
    else -> listOf(EntryUnit(unit, 1.0))
}

private fun currentShift(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..10 -> "Breakfast Shift"
    in 11..15 -> "Lunch Shift"
    else -> "Dinner Shift"
}

private fun dayLabel(epochDay: Long): String = when (Dates.todayEpochDay() - epochDay) {
    0L -> "Today"
    1L -> "Yesterday"
    else -> Dates.format(epochDay)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryScreen(viewModel: StaffViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scanned by viewModel.scanned.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var mode by rememberSaveable { mutableStateOf(EntryMode.USAGE) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var unitIndex by rememberSaveable { mutableStateOf(0) }
    var reason by rememberSaveable { mutableStateOf(WasteReason.SPOILED) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showAllActivity by rememberSaveable { mutableStateOf(false) }

    val selected = state.ingredients.firstOrNull { it.id == selectedId }
    val units = unitOptions(selected?.unit ?: "kg")
    val unit = units.getOrElse(unitIndex) { units.first() }
    val entered = quantity.toQuantityOrNull()
    val baseQuantity = entered?.times(unit.toBase)

    val select: (Ingredient) -> Unit = {
        selectedId = it.id
        unitIndex = 0
    }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(scanned) {
        scanned?.let {
            select(it)
            showPicker = false
            viewModel.consumeScanned()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { StaffHeader() }

            item {
                Column {
                    Text("Quick Entry", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Track usage and waste. Keep it moving.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeCard(
                        selected = mode == EntryMode.USAGE,
                        color = MaterialTheme.colorScheme.primary,
                        emoji = "🍃",
                        title = "Log Ingredient Usage",
                        subtitle = "Record ingredients used in production",
                        onClick = { mode = EntryMode.USAGE },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    ModeCard(
                        selected = mode == EntryMode.WASTE,
                        color = MaterialTheme.colorScheme.tertiary,
                        emoji = "🗑️",
                        title = "Report Waste",
                        subtitle = "Log spoiled, trimmed or discarded items",
                        onClick = { mode = EntryMode.WASTE },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }

            item {
                SectionHeader("Quick Select Ingredients", onSeeAll = { showPicker = true })
                Spacer(Modifier.height(8.dp))
                if (state.ingredients.isEmpty()) {
                    Text(
                        "No ingredients yet. The manager needs to add them to the inventory.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.quickSelect.forEach { ing ->
                            IngredientChip(ing, selected = ing.id == selectedId, onClick = { select(ing) })
                        }
                        // A selection from "See All" that isn't in the quick list is shown too.
                        if (selected != null && state.quickSelect.none { it.id == selected.id }) {
                            IngredientChip(selected, selected = true, onClick = {})
                        }
                    }
                }
            }

            item {
                EntryCard(
                    mode = mode,
                    selected = selected,
                    quantity = quantity,
                    units = units,
                    unit = unit,
                    reason = reason,
                    baseQuantity = baseQuantity,
                    onQuantity = { quantity = it },
                    onUnit = { unitIndex = units.indexOf(it) },
                    onReason = { reason = it },
                    onPickIngredient = { showPicker = true },
                    onSave = {
                        val ing = selected ?: return@EntryCard
                        val q = baseQuantity ?: return@EntryCard
                        if (mode == EntryMode.USAGE) viewModel.recordUsed(ing, q) else viewModel.recordWasted(ing, q, reason)
                        quantity = ""
                    },
                )
            }

            item {
                SectionHeader("Recent Activity", onSeeAll = if (state.recent.size > 3) ({ showAllActivity = true }) else null)
            }
            if (state.recent.isEmpty()) {
                item {
                    Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.recent.take(3), key = { it.key }) { ActivityRow(it) }
        }
    }

    if (showPicker) {
        IngredientPickerSheet(
            ingredients = state.ingredients,
            onScan = viewModel::onBarcodeScanned,
            onDismiss = { showPicker = false },
            onSelect = {
                select(it)
                showPicker = false
            },
        )
    }

    if (showAllActivity) {
        ModalBottomSheet(onDismissRequest = { showAllActivity = false }) {
            Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
                Text("Recent Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(state.recent, key = { it.key }) { ActivityRow(it) }
                }
            }
        }
    }
}

@Composable
private fun StaffHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.ic_chef_hat),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("SmartKitchen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Smarter Food Operations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(currentShift(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text("Kitchen Staff", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(48.dp).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("👨‍🍳", fontSize = 24.sp) }
    }
}

@Composable
private fun ModeCard(
    selected: Boolean,
    color: Color,
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) color else color.copy(alpha = 0.45f)),
        border = if (selected) BorderStroke(3.dp, Color.White.copy(alpha = 0.9f)) else null,
    ) {
        Column(Modifier.padding(16.dp).fillMaxHeight()) {
            Box(
                Modifier.size(52.dp).background(Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 24.sp) }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White)
            }
            if (selected) {
                Text("✓ Selected", style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text("See All", color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun IngredientChip(ingredient: Ingredient, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(ingredientEmoji(ingredient.name, ingredient.category), fontSize = 20.sp) }
            Spacer(Modifier.width(8.dp))
            Text(ingredient.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun EntryCard(
    mode: EntryMode,
    selected: Ingredient?,
    quantity: String,
    units: List<EntryUnit>,
    unit: EntryUnit,
    reason: WasteReason,
    baseQuantity: Double?,
    onQuantity: (String) -> Unit,
    onUnit: (EntryUnit) -> Unit,
    onReason: (WasteReason) -> Unit,
    onPickIngredient: () -> Unit,
    onSave: () -> Unit,
) {
    var unitMenu by remember { mutableStateOf(false) }
    val saveColor = if (mode == EntryMode.USAGE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Selected ingredient (stock quantity only — staff don't see prices).
            if (selected == null) {
                TextButton(onClick = onPickIngredient) { Text("Select an ingredient to start") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ingredientEmoji(selected.name, selected.category), fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(selected.name) }
                            append("  ·  In stock ${selected.quantity.asQuantity()} ${selected.unit}")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected.isLowStock) Icon(Icons.Filled.Warning, contentDescription = "Low stock", tint = MaterialTheme.colorScheme.error)
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Quantity", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            quantity.ifEmpty { "0.0" },
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = if (quantity.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        )
                    }
                }
                Column(Modifier.width(110.dp)) {
                    Text("Unit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.padding(top = 4.dp)) {
                        OutlinedCard(
                            onClick = { if (units.size > 1) unitMenu = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(unit.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (units.size > 1) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Change unit")
                            }
                        }
                        DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                            units.forEach { u ->
                                DropdownMenuItem(text = { Text(u.label) }, onClick = { unitMenu = false; onUnit(u) })
                            }
                        }
                    }
                }
            }

            if (mode == EntryMode.WASTE) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WasteReason.entries.forEach { r ->
                        FilterChip(selected = reason == r, onClick = { onReason(r) }, label = { Text(r.label) })
                    }
                }
            }

            if (selected != null && baseQuantity != null && baseQuantity > selected.quantity) {
                Text(
                    "More than in stock — stock will be set to 0.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            StaffKeypad(
                onKey = { key ->
                    val allowed = if (key == '.') !quantity.contains('.') else quantity.length < 7
                    if (allowed) onQuantity(if (key == '.' && quantity.isEmpty()) "0." else quantity + key)
                },
                onBackspace = { onQuantity(quantity.dropLast(1)) },
            )

            Button(
                onClick = onSave,
                enabled = selected != null && baseQuantity != null && baseQuantity > 0,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = saveColor),
                modifier = Modifier.fillMaxWidth().height(60.dp),
            ) {
                Text(
                    if (mode == EntryMode.USAGE) "Save Entry" else "Save Waste",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** 4-column keypad laid out like the design: 1 2 3 ⌫ / 4 5 6 . / 7 8 9 0. */
@Composable
private fun StaffKeypad(onKey: (Char) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf("123⌫", "456.", "7890")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    Surface(
                        onClick = { if (key == '⌫') onBackspace() else onKey(key) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).height(60.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                key.toString(),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Medium,
                                // Give the backspace glyph a spoken name.
                                modifier = if (key == '⌫') Modifier.semantics { contentDescription = "Delete" } else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: Activity) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(
                    if (activity.wasted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) { Text(if (activity.wasted) "🗑️" else "🍃", fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("${activity.quantity.asQuantity()} ${activity.unit} ${activity.name}")
                        }
                        append(if (activity.wasted) " wasted" else " used")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    listOfNotNull(dayLabel(activity.epochDay), activity.reason?.label).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientPickerSheet(
    ingredients: List<Ingredient>,
    onScan: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Ingredient) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val filtered = if (query.isBlank()) ingredients else ingredients.filter { it.name.contains(query, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("All ingredients", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Find ingredient") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    GmsBarcodeScanning.getClient(context).startScan()
                        .addOnSuccessListener { code -> code.rawValue?.let(onScan) }
                }) { Text("Scan") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(filtered, key = { it.id }) { ing ->
                    Card(onClick = { onSelect(ing) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ingredientEmoji(ing.name, ing.category), fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ing.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("In stock: ${ing.quantity.asQuantity()} ${ing.unit}", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (ing.isLowStock) Icon(Icons.Filled.Warning, contentDescription = "Low stock", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
