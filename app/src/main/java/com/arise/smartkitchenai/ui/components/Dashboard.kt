package com.arise.smartkitchenai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arise.smartkitchenai.R
import com.arise.smartkitchenai.ai.AiTip
import com.arise.smartkitchenai.ai.TipType
import com.arise.smartkitchenai.data.Branch
import com.arise.smartkitchenai.ui.theme.BadTerracotta
import com.arise.smartkitchenai.ui.theme.GoodGreen
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asQuantity
import com.arise.smartkitchenai.util.toQuantityOrNull

/** Logo, app name + tagline, and the owner's initials avatar. */
@Composable
fun BrandHeader(ownerName: String, onAvatarClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_chef_hat), contentDescription = null, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("SmartKitchen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "BETTER FOOD. BRIGHTER BUSINESS.",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials(ownerName),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

/** Outlined dropdown "chip" (branch / period pickers). */
@Composable
fun <T> PickerChip(
    leading: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedCard(onClick = { open = true }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(leading, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(selectedLabel, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

/** Headline number with change vs. the previous period. Direction is shown by arrow + sign, not colour alone. */
@Composable
fun KpiCard(
    emoji: String,
    emojiBackground: Color,
    label: String,
    value: String,
    change: Double?,
    higherIsBetter: Boolean,
    comparisonLabel: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp)) {
            Box(Modifier.size(40.dp).background(emojiBackground, CircleShape), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 18.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (change == null) {
                Text("No earlier data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val good = if (higherIsBetter) change >= 0 else change <= 0
                Text(
                    (if (change >= 0) "↑ +" else "↓ ") + change.asQuantityOneDecimal() + "%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (good) GoodGreen else BadTerracotta,
                )
                Text(comparisonLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

private fun Double.asQuantityOneDecimal(): String = String.format(java.util.Locale.getDefault(), "%.1f", this)

private fun TipType.emoji(): String = when (this) {
    TipType.ORDERING -> "🛒"
    TipType.WASTE -> "🗑️"
    TipType.EXPIRY -> "⏰"
    TipType.PREP -> "🔪"
    TipType.SALES -> "📈"
    TipType.OTHER -> "💡"
}

/** AI recommendation card; alternates terracotta and green tints like the dashboard design. */
@Composable
fun AiTipCard(
    tip: AiTip,
    index: Int,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    maxDetailLines: Int = Int.MAX_VALUE,
) {
    val warm = index % 2 == 0
    val container = if (warm) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val accent = if (warm) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(Color.White.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(tip.type.emoji(), fontSize = 18.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(tip.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(tip.detail, style = MaterialTheme.typography.bodyMedium, maxLines = maxDetailLines, overflow = TextOverflow.Ellipsis)
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) { Text("$actionLabel →", modifier = Modifier.padding(horizontal = 8.dp)) }
            }
        }
    }
}

/**
 * Enter or edit a day's total sales. [branches] non-null lets the Owner pick the branch;
 * Managers record for their device's branch only.
 *
 * @param existingAmount when given, the amount field is re-filled with the saved sales of the
 * selected branch + day whenever either changes, so a total from another branch (or from
 * "All branches") is never saved into the wrong branch by accident.
 */
@Composable
fun SalesDialog(
    initialDay: Long,
    initialAmount: Double?,
    branches: List<Branch>?,
    initialBranchId: Long,
    onDismiss: () -> Unit,
    onSave: (branchId: Long, epochDay: Long, amount: Double) -> Unit,
    existingAmount: ((branchId: Long, epochDay: Long) -> Double?)? = null,
) {
    var day by rememberSaveable { mutableStateOf(initialDay) }
    var branchId by rememberSaveable { mutableStateOf(initialBranchId) }
    var amount by rememberSaveable {
        mutableStateOf((existingAmount?.invoke(initialBranchId, initialDay) ?: initialAmount)?.asQuantity().orEmpty())
    }
    // Re-fill when the branch or date changes (skipped on first composition, which used the initial value).
    var lastKey by rememberSaveable { mutableStateOf("$initialBranchId-$initialDay") }
    if (existingAmount != null && lastKey != "$branchId-$day") {
        lastKey = "$branchId-$day"
        amount = existingAmount(branchId, day)?.asQuantity().orEmpty()
    }
    val value = amount.toQuantityOrNull()
    val valid = value != null && value >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily sales") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter the day's total sales from your till / POS report. Re-entering a day replaces it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (branches != null) {
                    DropdownField(
                        label = "Branch",
                        options = branches,
                        selected = branches.firstOrNull { it.id == branchId },
                        optionLabel = { it.name },
                        onSelect = { branchId = it.id },
                    )
                }
                DateField("Date", day, { it?.let { d -> day = d } }, clearable = false)
                NumberField(amount, { amount = it }, "Total sales", isError = amount.isNotEmpty() && !valid)
                if (day > Dates.todayEpochDay()) {
                    Text("This date is in the future.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onSave(branchId, day, value) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
