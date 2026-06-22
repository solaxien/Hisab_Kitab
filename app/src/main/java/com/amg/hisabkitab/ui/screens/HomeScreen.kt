package com.amg.hisabkitab.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amg.hisabkitab.ui.common.date
import com.amg.hisabkitab.ui.common.money
import com.amg.hisabkitab.ui.components.AppScaffold
import com.amg.hisabkitab.ui.components.HkCard
import com.amg.hisabkitab.ui.viewmodel.DashboardState

@Composable
fun HomeScreen(
    state: DashboardState,
    onNavigate: (String) -> Unit,
    onSettings: () -> Unit,
    onCreateBill: () -> Unit,
    onOpenBill: (Long) -> Unit
) {
    AppScaffold(
        title = "HisabKitab",
        currentRoute = "home",
        onNavigate = onNavigate,
        onSettings = onSettings,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateBill,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Create Bill") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, top = 20.dp, end = 20.dp, bottom = 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Namaste, ${state.ownerName}", style = MaterialTheme.typography.headlineMedium)
                Text(
                    date(System.currentTimeMillis()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                HkCard(Modifier.fillMaxWidth(), outlined = true) {
                    Column(Modifier.padding(24.dp)) {
                        Text("TODAY'S SALES", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            money(state.todaySalesPaise),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Cash", money(state.cashPaise), Icons.Outlined.Payments, Modifier.weight(1f))
                    MetricCard("UPI", money(state.upiPaise), Icons.Outlined.QrCode2, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Active Bills", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { onNavigate("bills") }) { Text("View All") }
                }
            }
            if (state.activeBills.isEmpty()) {
                item { EmptyMessage("No active bills. Create one to start today's sale.") }
            } else {
                items(state.activeBills, key = { it.bill.id }) { bill ->
                    HkCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    bill.bill.customerName
                                        ?: "Walk-in Bill #${bill.bill.billNumber.takeLast(3)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${bill.itemCount} items • Active",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                money(bill.totalPaise),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            item { Text("Inventory Alerts", style = MaterialTheme.typography.titleLarge) }
            if (state.lowStock.isEmpty()) {
                item { EmptyMessage("Stock levels look healthy.") }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.lowStock, key = { it.id }) { product ->
                            HkCard(Modifier.width(220.dp), outlined = true) {
                                Column(Modifier.padding(20.dp)) {
                                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        product.sku,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            "${product.stockQuantity} left",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    HkCard(modifier, outlined = true) {
        Column(Modifier.padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun EmptyMessage(text: String) {
    HkCard(Modifier.fillMaxWidth(), outlined = true) {
        Text(
            text,
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@androidx.compose.runtime.Composable
fun HomeScreenPreview() {
    com.amg.hisabkitab.ui.theme.HisabKitabTheme {
        HomeScreen(
            state = com.amg.hisabkitab.ui.viewmodel.DashboardState(
                ownerName = "Shop Owner",
                todaySalesPaise = 150000,
                cashPaise = 80000,
                upiPaise = 70000
            ),
            onNavigate = {},
            onSettings = {},
            onCreateBill = {},
            onOpenBill = {}
        )
    }
}