package com.arise.smartkitchenai.ui.manager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arise.smartkitchenai.data.DailySales
import com.arise.smartkitchenai.ui.AppViewModelFactory
import com.arise.smartkitchenai.ui.components.EmptyState
import com.arise.smartkitchenai.ui.components.SalesDialog
import com.arise.smartkitchenai.ui.components.Stat
import com.arise.smartkitchenai.ui.components.SummaryCard
import com.arise.smartkitchenai.util.Dates
import com.arise.smartkitchenai.util.asMoney

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerSalesScreen(viewModel: ManagerSalesViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // null = closed; amount -1 = new entry.
    var editing by remember { mutableStateOf<DailySales?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Daily sales") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = DailySales(viewModel.currentBranchId, Dates.todayEpochDay(), -1.0) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (state.todayEntered) "Enter sales" else "Enter today's sales") },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SummaryCard(
                    listOf(
                        Stat("Last 7 days", state.last7Days.asMoney()),
                        Stat("Last 30 days", state.last30Days.asMoney()),
                        Stat("Today", if (state.todayEntered) "Entered" else "Missing", highlight = !state.todayEntered),
                    )
                )
            }
            if (state.sales.isEmpty()) {
                item { EmptyState("No sales yet", "Enter each day's total sales at closing, from the till / POS report.") }
            }
            items(state.sales, key = { it.epochDay }) { sales ->
                Card(onClick = { editing = sales }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                            Text(Dates.format(sales.epochDay), style = MaterialTheme.typography.titleSmall)
                        }
                        Text(sales.amount.asMoney(), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { viewModel.delete(sales) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete sales entry")
                        }
                    }
                }
            }
        }
    }

    editing?.let { sales ->
        SalesDialog(
            initialDay = sales.epochDay,
            initialAmount = sales.amount.takeIf { it >= 0 },
            branches = null, // managers record for this device's branch only
            initialBranchId = sales.branchId,
            onDismiss = { editing = null },
            onSave = { _, day, amount ->
                if (sales.amount >= 0 && day != sales.epochDay) viewModel.delete(sales)
                viewModel.save(day, amount)
                editing = null
            },
        )
    }
}
