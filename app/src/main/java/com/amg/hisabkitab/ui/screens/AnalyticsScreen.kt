package com.amg.hisabkitab.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onRecordLoss: (Long, Int, LossReason, String?) -> Unit
) {
    var showLoss by remember { mutableStateOf(false) }
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
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = state.period.label(),
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        readOnly = true,
                        label = { Text("Date range") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        AnalyticsPeriod.entries.forEach { period ->
                            DropdownMenuItem(
                                text = { Text(period.label()) },
                                onClick = { onPeriod(period); expanded = false }
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
                        Spacer(Modifier.size(4.dp))
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
            Spacer(Modifier.size(4.dp))
            Text(money(value), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LossDialog(
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onSave: (Long, Int, LossReason, String?) -> Unit
) {
    var selected by remember { mutableStateOf<Long?>(null) }
    var productExpanded by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(LossReason.EXPIRED) }
    var note by remember { mutableStateOf("") }
    val selectedProduct = products.firstOrNull { it.id == selected }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record inventory loss") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = productExpanded,
                    onExpandedChange = { productExpanded = !productExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedProduct?.name.orEmpty(),
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        readOnly = true,
                        label = { Text("Product") },
                        placeholder = { Text("Choose a product") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(productExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = productExpanded,
                        onDismissRequest = { productExpanded = false }
                    ) {
                        products.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.name) },
                                onClick = { selected = product.id; productExpanded = false }
                            )
                        }
                    }
                }
                if (selectedProduct != null) {
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(selectedProduct.name) },
                        supportingContent = { Text("${selectedProduct.stockQuantity} units available") },
                        trailingContent = {
                            IconButton(onClick = { selected = null }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Remove selected product")
                            }
                        }
                    )
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter(Char::isDigit) },
                    label = { Text("Quantity") },
                    suffix = { Text("units") },
                    singleLine = true
                )
                Text("Reason", style = MaterialTheme.typography.labelLarge)
                LossReason.entries.forEach { entry ->
                    androidx.compose.material3.FilterChip(
                        selected = reason == entry,
                        onClick = { reason = entry },
                        label = { Text(entry.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    supportingText = { Text("Optional") }
                )
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

private fun AnalyticsPeriod.label(): String = when (this) {
    AnalyticsPeriod.TODAY -> "Today"
    AnalyticsPeriod.YESTERDAY -> "Yesterday"
    AnalyticsPeriod.THIS_WEEK -> "This Week"
    AnalyticsPeriod.LAST_WEEK -> "Last Week"
}
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@androidx.compose.runtime.Composable
fun AnalyticsScreenPreview() {
    val dummyProducts = listOf(
        com.amg.hisabkitab.data.local.ProductEntity(1, "Product A", "123", "SKU1", 10000, 15000, 10, 5, 0, 0),
        com.amg.hisabkitab.data.local.ProductEntity(2, "Product B", "456", "SKU2", 20000, 30000, 20, 5, 0, 0)
    )
    com.amg.hisabkitab.ui.theme.HisabKitabTheme {
        AnalyticsScreen(
            state = com.amg.hisabkitab.ui.viewmodel.AnalyticsState(
                revenuePaise = 500000,
                cashPaise = 200000,
                upiPaise = 300000,
                grossProfitPaise = 150000,
                lossPaise = 10000,
                netProfitPaise = 140000,
                paidBills = 42,
                dailyRevenuePaise = listOf(10000, 20000, 15000, 30000, 25000, 40000, 35000)
            ),
            products = dummyProducts,
            onNavigate = {},
            onSettings = {},
            onPeriod = {},
            onRecordLoss = { _, _, _, _ -> }
        )
    }
}