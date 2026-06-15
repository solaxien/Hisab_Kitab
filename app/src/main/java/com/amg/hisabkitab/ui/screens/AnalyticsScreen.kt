package com.amg.hisabkitab.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.amg.hisabkitab.data.local.LossReason
import com.amg.hisabkitab.data.local.ProductEntity
import com.amg.hisabkitab.ui.common.dateTime
import com.amg.hisabkitab.ui.common.money
import com.amg.hisabkitab.ui.components.AppScaffold
import com.amg.hisabkitab.ui.components.HkCard
import com.amg.hisabkitab.ui.viewmodel.AnalyticsPeriod
import com.amg.hisabkitab.ui.viewmodel.AnalyticsState
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    state: AnalyticsState,
    products: List<ProductEntity>,
    onNavigate: (String) -> Unit,
    onSettings: () -> Unit,
    onPeriod: (AnalyticsPeriod) -> Unit,
    onCustomRange: (Long, Long) -> Unit,
    onRecordLoss: (Long, Int, LossReason, String?) -> Unit
) {
    var showLoss by remember { mutableStateOf(false) }
    var showRange by remember { mutableStateOf(false) }
    AppScaffold(
        title = "Analytics",
        currentRoute = "analytics",
        onNavigate = onNavigate,
        onSettings = onSettings
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, top = 20.dp, end = 20.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AnalyticsPeriod.entries.forEachIndexed { index, period ->
                        SegmentedButton(
                            selected = state.period == period,
                            onClick = {
                                if (period == AnalyticsPeriod.CUSTOM) showRange = true
                                else onPeriod(period)
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AnalyticsPeriod.entries.size
                            )
                        ) {
                            Text(
                                when (period) {
                                    AnalyticsPeriod.THIS_MONTH -> "This Month"
                                    AnalyticsPeriod.LAST_MONTH -> "Last Month"
                                    AnalyticsPeriod.CUSTOM -> "Custom"
                                }
                            )
                        }
                    }
                }
            }
            item {
                HkCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp)) {
                        Text("TOTAL REVENUE", style = MaterialTheme.typography.labelLarge)
                        Text(
                            money(state.revenuePaise),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${state.paidBills} paid bills",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SalesTrendChart(state.dailyRevenuePaise)
                    }
                }
            }
            item {
                HkCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Payment Split", style = MaterialTheme.typography.titleLarge)
                        val total = state.revenuePaise.coerceAtLeast(1)
                        Text("UPI  ${money(state.upiPaise)}")
                        LinearProgressIndicator(
                            progress = { state.upiPaise.toFloat() / total },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Cash  ${money(state.cashPaise)}")
                        LinearProgressIndicator(
                            progress = { state.cashPaise.toFloat() / total },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item { AnalyticsMetric("Gross Profit", state.grossProfitPaise) }
            item {
                HkCard(Modifier.fillMaxWidth(), outlined = true) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Inventory Losses", style = MaterialTheme.typography.titleLarge)
                            Button(onClick = { showLoss = true }, enabled = products.isNotEmpty()) {
                                Text("Add Entry")
                            }
                        }
                        Text(
                            money(state.lossPaise),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            item {
                HkCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Net Profit", style = MaterialTheme.typography.titleLarge)
                        Text(
                            money(state.netProfitPaise),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("%.1f%% margin".format(state.marginPercent))
                    }
                }
            }
            if (state.losses.isNotEmpty()) item {
                Text("Loss History", style = MaterialTheme.typography.titleLarge)
            }
            items(state.losses, key = { it.id }) { loss ->
                HkCard(Modifier.fillMaxWidth(), outlined = true) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(loss.productName, style = MaterialTheme.typography.titleMedium)
                            Text("${loss.quantity} • ${loss.reason.name.replace('_', ' ')}")
                            Text(
                                dateTime(loss.createdAt),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(money(loss.purchasePricePaise * loss.quantity))
                    }
                }
            }
        }
    }
    if (showLoss) {
        LossDialog(
            products = products,
            onDismiss = { showLoss = false },
            onSave = { product, quantity, reason, note ->
                onRecordLoss(product, quantity, reason, note)
                showLoss = false
            }
        )
    }
    if (showRange) {
        val rangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(
                    enabled = rangeState.selectedStartDateMillis != null &&
                        rangeState.selectedEndDateMillis != null,
                    onClick = {
                        onCustomRange(
                            rangeState.selectedStartDateMillis!!,
                            rangeState.selectedEndDateMillis!!
                        )
                        showRange = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SalesTrendChart(values: List<Long>) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.surfaceVariant
    val chartValues = if (values.isEmpty()) listOf(0L) else values.takeLast(14)
    val maximum = max(1L, chartValues.maxOrNull() ?: 1L)
    Canvas(Modifier.fillMaxWidth().height(132.dp).padding(top = 20.dp)) {
        val gap = size.width / chartValues.size
        val barWidth = (gap * 0.55f).coerceAtLeast(4.dp.toPx())
        chartValues.forEachIndexed { index, value ->
            val barHeight = size.height * (value.toFloat() / maximum)
            val x = gap * index + gap / 2
            drawLine(
                color = if (value == 0L) muted else primary,
                start = Offset(x, size.height),
                end = Offset(x, size.height - barHeight.coerceAtLeast(4.dp.toPx())),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun AnalyticsMetric(title: String, value: Long) {
    HkCard(Modifier.fillMaxWidth(), outlined = true) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(money(value), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun LossDialog(
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onSave: (Long, Int, LossReason, String?) -> Unit
) {
    var selected by remember { mutableStateOf(products.firstOrNull()?.id) }
    var quantity by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(LossReason.EXPIRED) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record inventory loss") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Product", style = MaterialTheme.typography.labelLarge) }
                items(products, key = { it.id }) { product ->
                    androidx.compose.material3.FilterChip(
                        selected = selected == product.id,
                        onClick = { selected = product.id },
                        label = { Text(product.name) }
                    )
                }
                item {
                    OutlinedTextField(
                        quantity,
                        { quantity = it.filter(Char::isDigit) },
                        label = { Text("Quantity") },
                        suffix = { Text("units") },
                        singleLine = true
                    )
                }
                item { Text("Reason", style = MaterialTheme.typography.labelLarge) }
                items(LossReason.entries) { entry ->
                    androidx.compose.material3.FilterChip(
                        selected = reason == entry,
                        onClick = { reason = entry },
                        label = {
                            Text(entry.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() })
                        }
                    )
                }
                item {
                    OutlinedTextField(
                        note,
                        { note = it },
                        label = { Text("Note") },
                        supportingText = { Text("Optional") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selected != null && (quantity.toIntOrNull() ?: 0) > 0,
                onClick = { onSave(selected!!, quantity.toInt(), reason, note.ifBlank { null }) }
            ) { Text("Save Loss") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
