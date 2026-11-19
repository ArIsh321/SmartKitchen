package com.arise.smartkitchenai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val BACKSPACE = "⌫"

/**
 * Large on-screen keypad. [allowDecimal] adds a "." key (for quantities); otherwise the
 * bottom-left key is empty (for PINs).
 */
@Composable
fun NumberPad(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = false,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (allowDecimal) "." else "", "0", BACKSPACE),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        FilledTonalButton(
                            onClick = { if (key == BACKSPACE) onBackspace() else onKey(key[0]) },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            Text(key, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Shows filled/empty dots for a PIN being typed. */
@Composable
fun PinDots(length: Int, filled: Int, isError: Boolean = false) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(length) { i ->
            Surface(
                shape = CircleShape,
                color = if (i < filled) color else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(18.dp),
            ) { Box(Modifier) }
        }
    }
}
