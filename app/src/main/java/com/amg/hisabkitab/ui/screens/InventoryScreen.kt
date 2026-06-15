package com.amg.hisabkitab.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.amg.hisabkitab.data.local.ProductEntity
import com.amg.hisabkitab.ui.common.money
import com.amg.hisabkitab.ui.common.parseRupees
import com.amg.hisabkitab.ui.components.AppScaffold
import com.amg.hisabkitab.ui.components.HkCard
import com.amg.hisabkitab.ui.viewmodel.InventoryState
import com.amg.hisabkitab.ui.viewmodel.ProductFilter
import com.amg.hisabkitab.ui.viewmodel.ProductSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    state: InventoryState,
    onNavigate: (String) -> Unit,
    onSettings: () -> Unit,
    onQuery: (String) -> Unit,
    onFilter: (ProductFilter) -> Unit,
    onSort: (ProductSort) -> Unit,
    onSave: (ProductEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onRestock: (Long, Int) -> Unit,
    onScan: () -> Unit
) {
    var editing by remember { mutableStateOf<ProductEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var restocking by remember { mutableStateOf<ProductEntity?>(null) }
    var deleting by remember { mutableStateOf<ProductEntity?>(null) }
    var showSort by remember { mutableStateOf(false) }

    AppScaffold(
        title = "Inventory",
        currentRoute = "inventory",
        onNavigate = onNavigate,
        onSettings = onSettings,
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) {
                Icon(Icons.Outlined.Add, "Add product")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search product, SKU or barcode") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (state.query.isEmpty()) onScan() else onQuery("")
                        }) {
                            Icon(
                                if (state.query.isEmpty()) Icons.Outlined.QrCodeScanner else Icons.Outlined.Clear,
                                if (state.query.isEmpty()) "Scan barcode" else "Clear product search"
                            )
                        }
                    },
                    singleLine = true
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ProductFilter.entries) { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { onFilter(filter) },
                            label = { Text(filter.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
            item {
                AssistChip(
                    onClick = { showSort = true },
                    label = { Text("Sort: ${sortLabel(state.sort)}") },
                    leadingIcon = { Icon(Icons.Outlined.Sort, contentDescription = null) }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard("Products", state.totalCount.toString(), Modifier.weight(1f))
                    SummaryCard("Low stock", state.lowStockCount.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard("Out of stock", state.outOfStockCount.toString(), Modifier.weight(1f))
                    SummaryCard("Est. value", money(state.valuePaise), Modifier.weight(1f))
                }
            }
            if (state.products.isEmpty()) {
                item { EmptyMessage("No products match this view.") }
            }
            items(state.products, key = { it.id }) { product ->
                ProductCard(
                    product = product,
                    onEdit = { editing = product; showEditor = true },
                    onRestock = { restocking = product },
                    onDelete = { deleting = product }
                )
            }
        }
    }
    if (showEditor) {
        ProductEditor(
            product = editing,
            onDismiss = { showEditor = false },
            onSave = { onSave(it); showEditor = false }
        )
    }
    restocking?.let { product ->
        RestockSheet(
            product = product,
            onDismiss = { restocking = null },
            onConfirm = { onRestock(product.id, it); restocking = null }
        )
    }
    if (showSort) {
        ModalBottomSheet(onDismissRequest = { showSort = false }) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Sort inventory", style = MaterialTheme.typography.titleLarge)
                ProductSort.entries.forEach { sort ->
                    FilterChip(
                        selected = state.sort == sort,
                        onClick = { onSort(sort); showSort = false },
                        label = { Text(sortLabel(sort)) }
                    )
                }
            }
        }
    }
    deleting?.let { product ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete product?") },
            text = { Text("${product.name} will be permanently removed if it is not used by a loss record.") },
            confirmButton = {
                TextButton(onClick = { onDelete(product.id); deleting = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier) {
    HkCard(modifier, outlined = true) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ProductCard(
    product: ProductEntity,
    onEdit: () -> Unit,
    onRestock: () -> Unit,
    onDelete: () -> Unit
) {
    HkCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(product.name, style = MaterialTheme.typography.titleLarge)
                    Text("SKU: ${product.sku}", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "${product.stockQuantity} in stock",
                    color = if (product.stockQuantity <= product.lowStockThreshold)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Purchase price", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(product.purchasePricePaise))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Selling price", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(money(product.sellingPricePaise), color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
                FilledTonalButton(onClick = onRestock, modifier = Modifier.weight(1f)) { Text("Restock") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete product") }
            }
        }
    }
}

@Composable
private fun ProductEditor(
    product: ProductEntity?,
    onDismiss: () -> Unit,
    onSave: (ProductEntity) -> Unit
) {
    var name by remember(product) { mutableStateOf(product?.name.orEmpty()) }
    var sku by remember(product) { mutableStateOf(product?.sku.orEmpty()) }
    var barcode by remember(product) { mutableStateOf(product?.barcode.orEmpty()) }
    var purchase by remember(product) { mutableStateOf(product?.purchasePricePaise?.let { (it / 100.0).toString() }.orEmpty()) }
    var selling by remember(product) { mutableStateOf(product?.sellingPricePaise?.let { (it / 100.0).toString() }.orEmpty()) }
    var stock by remember(product) { mutableStateOf(product?.stockQuantity?.toString().orEmpty()) }
    var threshold by remember(product) { mutableStateOf(product?.lowStockThreshold?.toString() ?: "5") }
    var margin by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && sku.isNotBlank() && barcode.isNotBlank() &&
        parseRupees(purchase) != null && parseRupees(selling) != null &&
        stock.toIntOrNull() != null && threshold.toIntOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Add Product" else "Edit Product") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(name, { name = it }, label = { Text("Product name") }) }
                item { OutlinedTextField(sku, { sku = it }, label = { Text("SKU") }) }
                item { OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode") }) }
                item { DecimalField(purchase, { purchase = it }, "Purchase price") }
                item { DecimalField(selling, { selling = it }, "Selling price") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField(
                            margin,
                            { margin = it },
                            "Profit margin",
                            Modifier.weight(1f),
                            suffix = "%"
                        )
                        TextButton(
                            onClick = {
                                val cost = purchase.toDoubleOrNull()
                                val percent = margin.toDoubleOrNull()
                                if (cost != null && percent != null) {
                                    selling = "%.2f".format(cost * (1 + percent / 100))
                                }
                            }
                        ) { Text("Calculate") }
                    }
                }
                item { NumberField(stock, { stock = it }, "Current stock") }
                item { NumberField(threshold, { threshold = it }, "Low-stock threshold") }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        ProductEntity(
                            id = product?.id ?: 0,
                            name = name.trim(),
                            barcode = barcode.trim(),
                            sku = sku.trim(),
                            purchasePricePaise = parseRupees(purchase)!!,
                            sellingPricePaise = parseRupees(selling)!!,
                            stockQuantity = stock.toInt(),
                            lowStockThreshold = threshold.toInt(),
                            createdAt = product?.createdAt ?: now,
                            updatedAt = now
                        )
                    )
                }
            ) { Text("Save Product") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun QuantityDialog(title: String, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var quantity by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { NumberField(quantity, { quantity = it }, "Quantity") },
        confirmButton = {
            TextButton(
                enabled = (quantity.toIntOrNull() ?: 0) > 0,
                onClick = { onConfirm(quantity.toInt()) }
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestockSheet(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var quantity by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Restock ${product.name}", style = MaterialTheme.typography.titleLarge)
            Text(
                "Current stock: ${product.stockQuantity} units",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Quantity to add") },
                suffix = { Text("units") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Button(
                enabled = (quantity.toIntOrNull() ?: 0) > 0,
                onClick = { onConfirm(quantity.toInt()) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Confirm Restock") }
        }
    }
}

@Composable
private fun DecimalField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null
) = OutlinedTextField(
    value, onValue, modifier = modifier,
    label = { Text(label) },
    prefix = if (suffix == null) ({ Text("₹") }) else null,
    suffix = suffix?.let { valueSuffix -> ({ Text(valueSuffix) }) },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    singleLine = true
)

@Composable
private fun NumberField(value: String, onValue: (String) -> Unit, label: String) =
    OutlinedTextField(
        value, onValue,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )

private fun sortLabel(sort: ProductSort): String = when (sort) {
    ProductSort.NAME -> "Name"
    ProductSort.STOCK_LOW_FIRST -> "Low first"
    ProductSort.VALUE_HIGH_FIRST -> "Value"
}
