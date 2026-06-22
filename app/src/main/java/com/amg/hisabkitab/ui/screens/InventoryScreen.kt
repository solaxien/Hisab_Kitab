package com.amg.hisabkitab.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.amg.hisabkitab.data.local.ProductEntity
import com.amg.hisabkitab.ui.common.money
import com.amg.hisabkitab.ui.common.parseRupees
import com.amg.hisabkitab.ui.components.AppScaffold
import com.amg.hisabkitab.ui.components.HkCard
import com.amg.hisabkitab.ui.scanner.BarcodeScannerScreen
import com.amg.hisabkitab.ui.theme.HisabKitabTheme
import com.amg.hisabkitab.ui.viewmodel.InventoryState
import com.amg.hisabkitab.ui.viewmodel.ProductFilter
import com.amg.hisabkitab.ui.viewmodel.ProductSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    state: InventoryState,
    showAddProductOnOpen: Boolean = false,
    newProductBarcode: String? = null,
    onNewProductBarcodeConsumed: () -> Unit = {},
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
    var editorBarcode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(newProductBarcode) {
        if (!newProductBarcode.isNullOrBlank()) {
            editing = null
            editorBarcode = newProductBarcode
            showEditor = true
            onNewProductBarcodeConsumed()
        }
    }

    LaunchedEffect(showAddProductOnOpen) {
        if (showAddProductOnOpen) {
            editing = null
            showEditor = true
        }
    }

    AppScaffold(
        title = "Inventory",
        currentRoute = "inventory",
        onNavigate = onNavigate,
        onSettings = onSettings,
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; editorBarcode = null; showEditor = true }) {
                Icon(Icons.Outlined.Add, "Add product")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
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
                    onEdit = { editing = product; editorBarcode = null; showEditor = true },
                    onRestock = { restocking = product },
                    onDelete = { deleting = product }
                )
            }
        }
    }
    if (showEditor) {
        ProductEditor(
            product = editing,
            initialBarcode = editorBarcode.orEmpty(),
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
                    if (product.sku.isNotBlank()) {
                        Text("SKU: ${product.sku}", style = MaterialTheme.typography.labelMedium)
                    }
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
    initialBarcode: String = "",
    onDismiss: () -> Unit,
    onSave: (ProductEntity) -> Unit
) {
    var name by remember(product) { mutableStateOf(product?.name.orEmpty()) }
    var sku by remember(product) { mutableStateOf(product?.sku.orEmpty()) }
    var barcode by remember(product, initialBarcode) {
        mutableStateOf(product?.barcode ?: initialBarcode)
    }
    var purchase by remember(product) { mutableStateOf(product?.purchasePricePaise?.let { (it / 100.0).toString() }.orEmpty()) }
    var selling by remember(product) { mutableStateOf(product?.sellingPricePaise?.let { (it / 100.0).toString() }.orEmpty()) }
    var stock by remember(product) { mutableStateOf(product?.stockQuantity?.toString().orEmpty()) }
    var threshold by remember(product) { mutableStateOf(product?.lowStockThreshold?.toString() ?: "5") }
    var margin by remember(product) {
        val m = if (product != null && product.purchasePricePaise > 0) {
            "%.2f".format((product.sellingPricePaise - product.purchasePricePaise) * 100.0 / product.purchasePricePaise)
        } else ""
        mutableStateOf(m)
    }
    var scanBarcode by remember { mutableStateOf(false) }
    val nameFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val valid = name.isNotBlank() && barcode.isNotBlank() &&
        parseRupees(purchase) != null && parseRupees(selling) != null &&
        (stock.toIntOrNull() ?: -1) >= 0 && (threshold.toIntOrNull() ?: -1) >= 0

    LaunchedEffect(product) {
        if (product == null) {
            nameFocusRequester.requestFocus()
            keyboard?.show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Add Product" else "Edit Product") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product name") },
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester)
                )
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("SKU") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("Barcode") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Scan with camera or type manually") },
                    trailingIcon = {
                        IconButton(onClick = { scanBarcode = true }) {
                            Icon(
                                Icons.Outlined.QrCodeScanner,
                                contentDescription = "Scan product barcode"
                            )
                        }
                    },
                    singleLine = true
                )
                DecimalField(purchase, { purchase = it }, "Purchase price", Modifier.fillMaxWidth())
                DecimalField(selling, { selling = it }, "Selling price", Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DecimalField(
                        margin,
                        { margin = it },
                        "Profit margin",
                        Modifier.weight(1f),
                        suffix = "%"
                    )
                    TextButton(
                        onClick = {
                            val costPaise = parseRupees(purchase)
                            val sellingPaise = parseRupees(selling)
                            if (costPaise != null && sellingPaise != null && costPaise > 0) {
                                val profitMargin = (sellingPaise - costPaise) * 100.0 / costPaise
                                margin = "%.2f".format(profitMargin)
                            }
                        }
                    ) { Text("Calculate") }
                }
                NumberField(stock, { stock = it }, "Current stock", Modifier.fillMaxWidth())
                NumberField(threshold, { threshold = it }, "Low-stock threshold", Modifier.fillMaxWidth())
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
    if (scanBarcode) {
        Dialog(
            onDismissRequest = { scanBarcode = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BarcodeScannerScreen(
                onBack = { scanBarcode = false },
                onDetected = { detectedBarcode ->
                    barcode = detectedBarcode
                    scanBarcode = false
                }
            )
        }
    }
}

@Composable
fun QuantityDialog(title: String, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var quantity by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { NumberField(quantity, { quantity = it }, "Quantity", Modifier.fillMaxWidth()) },
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
private fun NumberField(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier = Modifier) =
    OutlinedTextField(
        value, onValue,
        modifier = modifier,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )

private fun sortLabel(sort: ProductSort): String = when (sort) {
    ProductSort.NAME -> "Name"
    ProductSort.STOCK_LOW_FIRST -> "Low first"
    ProductSort.VALUE_HIGH_FIRST -> "Value"
}

@Preview(showBackground = true)
@Composable
fun InventoryScreenPreview() {
    val mockProducts = List(3) { i ->
        ProductEntity(
            id = i.toLong() + 1,
            name = "Product ${i + 1}",
            barcode = "89012345678$i",
            sku = "SKU-${i + 1}",
            purchasePricePaise = 10000,
            sellingPricePaise = 15000,
            stockQuantity = if (i == 0) 2 else 10,
            lowStockThreshold = 5,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    HisabKitabTheme {
        InventoryScreen(
            state = InventoryState(
                products = mockProducts,
                totalCount = 3,
                lowStockCount = 1,
                outOfStockCount = 0,
                valuePaise = mockProducts.sumOf { it.sellingPricePaise.toLong() * it.stockQuantity }
            ),
            onNavigate = {},
            onSettings = {},
            onQuery = {},
            onFilter = {},
            onSort = {},
            onSave = {},
            onDelete = {},
            onRestock = { _, _ -> },
            onScan = {}
        )
    }
}

@Preview(showBackground = true, name = "Add Product Mode")
@Composable
fun ProductEditorAddPreview() {
    HisabKitabTheme {
        Surface {
            // Passing null shows an empty form
            ProductEditor(
                product = null,
                onDismiss = {},
                onSave = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Edit Product Mode")
@Composable
fun ProductEditorEditPreview() {
    val existingProduct = ProductEntity(
        id = 1,
        name = "Amul Butter 100g",
        barcode = "8901234123456",
        sku = "DAI-AMU-100",
        purchasePricePaise = 5200,  // ₹52.00
        sellingPricePaise = 5800,   // ₹58.00
        stockQuantity = 25,
        lowStockThreshold = 10,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    HisabKitabTheme {
        Surface {
            ProductEditor(
                product = existingProduct,
                onDismiss = {},
                onSave = {}
            )
        }
    }
}
