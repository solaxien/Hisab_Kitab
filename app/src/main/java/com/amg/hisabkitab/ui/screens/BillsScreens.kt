package com.amg.hisabkitab.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amg.hisabkitab.data.local.BillEntity
import com.amg.hisabkitab.data.local.BillItemEntity
import com.amg.hisabkitab.data.local.BillStatus
import com.amg.hisabkitab.data.local.BillWithItems
import com.amg.hisabkitab.data.local.PaymentMode
import com.amg.hisabkitab.data.local.ProductEntity
import com.amg.hisabkitab.data.local.StockShortage
import com.amg.hisabkitab.ui.common.dateTime
import com.amg.hisabkitab.ui.common.money
import com.amg.hisabkitab.ui.components.AppScaffold
import com.amg.hisabkitab.ui.components.HkCard
import com.amg.hisabkitab.ui.theme.HisabKitabTheme
import com.amg.hisabkitab.ui.viewmodel.BillFilter
import com.amg.hisabkitab.ui.viewmodel.BillsState

@Composable
fun BillsScreen(
    state: BillsState,
    onNavigate: (String) -> Unit,
    onSettings: () -> Unit,
    onQuery: (String) -> Unit,
    onFilter: (BillFilter) -> Unit,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit
) {
    AppScaffold(
        title = "Bills",
        currentRoute = "bills",
        onNavigate = onNavigate,
        onSettings = onSettings,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Create Bill") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, top = 20.dp, end = 20.dp, bottom = 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search bills or customers") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQuery("") }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Clear bill search")
                            }
                        }
                    },
                    singleLine = true
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(BillFilter.entries) { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { onFilter(filter) },
                            label = { Text(filter.label()) }
                        )
                    }
                }
            }
            if (state.bills.isEmpty()) {
                item { EmptyMessage("No ${state.filter.label().lowercase()} bills.") }
            }
            items(state.bills, key = { it.bill.id }) { bill ->
                HkCard(Modifier.fillMaxWidth().clickable { onOpen(bill.bill.id) }) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                bill.bill.customerName ?: "Walk-in customer",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                money(bill.totalPaise),
                                style = MaterialTheme.typography.titleLarge,
                                color = if (bill.bill.status == BillStatus.ACTIVE)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (bill.bill.status == BillStatus.CANCELLED)
                                    TextDecoration.LineThrough else TextDecoration.None
                            )
                        }
                        Text(
                            "#${bill.bill.billNumber} • ${bill.itemCount} items",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                dateTime(bill.bill.createdAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            BillStatusBadge(bill.bill.status)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillStatusBadge(status: BillStatus) {
    val container = when (status) {
        BillStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
        BillStatus.PAID -> MaterialTheme.colorScheme.secondaryContainer
        BillStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (status) {
        BillStatus.CANCELLED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            status.name.lowercase().replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    bill: BillWithItems?,
    products: List<ProductEntity>,
    shortages: List<StockShortage>,
    onBack: () -> Unit,
    onAddProduct: (Long) -> Unit,
    onScan: () -> Unit,
    onCustomerChange: (String) -> Unit,
    onQuantity: (Long, Int) -> Unit,
    onRemove: (Long) -> Unit,
    onCancelBill: () -> Unit,
    onPay: (PaymentMode) -> Unit,
    onForcePay: () -> Unit,
    onDismissShortage: () -> Unit
) {
    var showProducts by remember { mutableStateOf(false) }
    var productQuery by remember { mutableStateOf("") }
    var showPayment by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var selectedPayment by remember { mutableStateOf(PaymentMode.CASH) }
    var customer by remember(bill?.bill?.customerName) {
        mutableStateOf(bill?.bill?.customerName.orEmpty())
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(bill?.bill?.billNumber ?: "Bill") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (bill == null) {
            Column(Modifier.padding(padding).padding(20.dp)) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, top = 20.dp, end = 20.dp, bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = customer,
                        onValueChange = {
                            customer = it
                            onCustomerChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Customer name") },
                        supportingText = { Text("Optional for walk-in customers") },
                        enabled = bill.bill.status == BillStatus.ACTIVE,
                        singleLine = true
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showProducts = true },
                            enabled = bill.bill.status == BillStatus.ACTIVE,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Text(" Add item")
                        }
                        OutlinedButton(
                            onClick = onScan,
                            enabled = bill.bill.status == BillStatus.ACTIVE,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                            Text(" Scan")
                        }
                    }
                }
                if (bill.items.isEmpty()) item {
                    EmptyMessage("This active bill has no items yet.")
                }
                items(bill.items, key = { it.id }) { item ->
                    HkCard(Modifier.fillMaxWidth(), outlined = true) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    item.productName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(money(item.sellingPricePaise * item.quantity))
                            }
                            Text(
                                "${money(item.sellingPricePaise)} each",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (bill.bill.status == BillStatus.ACTIVE) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onQuantity(item.id, item.quantity - 1) }) {
                                        Text("−")
                                    }
                                    Text(
                                        item.quantity.toString(),
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    OutlinedButton(onClick = { onQuantity(item.id, item.quantity + 1) }) {
                                        Text("+")
                                    }
                                    IconButton(onClick = { onRemove(item.id) }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Remove ${item.productName}")
                                    }
                                }
                            } else {
                                Text("Quantity: ${item.quantity}")
                            }
                        }
                    }
                }
                item {
                    HkCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(22.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total", style = MaterialTheme.typography.titleLarge)
                            Text(
                                money(bill.totalPaise),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (bill.bill.status == BillStatus.ACTIVE) {
                    item {
                        Button(
                            onClick = { showPayment = true },
                            enabled = bill.items.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Mark Paid") }
                    }
                    item {
                        OutlinedButton(
                            onClick = { confirmCancel = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancel Bill") }
                    }
                }
            }
        }
    }
    if (showProducts) {
        ModalBottomSheet(onDismissRequest = { showProducts = false }) {
            Text(
                "Select a product",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = productQuery,
                onValueChange = { productQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                label = { Text("Search products") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (productQuery.isNotEmpty()) {
                        IconButton(onClick = { productQuery = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear product search")
                        }
                    }
                },
                singleLine = true
            )
            val matchingProducts = products.filter { product ->
                productQuery.isBlank() || product.name.contains(productQuery, true) ||
                    product.sku.contains(productQuery, true) || product.barcode.contains(productQuery, true)
            }
            LazyColumn(Modifier.navigationBarsPadding()) {
                items(matchingProducts, key = { it.id }) { product ->
                    ListItem(
                        headlineContent = { Text(product.name) },
                        supportingContent = { Text("${product.stockQuantity} in stock • ${product.sku}") },
                        trailingContent = { Text(money(product.sellingPricePaise)) },
                        modifier = Modifier.clickable {
                            onAddProduct(product.id)
                            showProducts = false
                        }
                    )
                }
            }
        }
    }
    if (showPayment) {
        ModalBottomSheet(onDismissRequest = { showPayment = false }) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Choose payment method", style = MaterialTheme.typography.titleLarge)
                PaymentMode.entries.forEach { mode ->
                    ListItem(
                        headlineContent = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingContent = {
                            RadioButton(
                                selected = selectedPayment == mode,
                                onClick = { selectedPayment = mode }
                            )
                        },
                        modifier = Modifier.clickable { selectedPayment = mode }
                    )
                }
                Button(
                    onClick = {
                        showPayment = false
                        onPay(selectedPayment)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Confirm Payment") }
            }
        }
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this bill?") },
            text = { Text("It will remain in history and will not affect inventory or analytics.") },
            confirmButton = {
                Button(onClick = { confirmCancel = false; onCancelBill() }) { Text("Cancel Bill") }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep Active") } }
        )
    }
    if (shortages.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissShortage,
            title = { Text("Insufficient recorded stock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("These products exceed recorded inventory:")
                    shortages.forEach {
                        Text("• ${it.productName}: need ${it.requested}, have ${it.available}")
                    }
                    Text("Paying anyway will set their recorded stock to zero.")
                }
            },
            confirmButton = { Button(onClick = onForcePay) { Text("Mark Paid Anyway") } },
            dismissButton = { TextButton(onClick = onDismissShortage) { Text("Cancel") } }
        )
    }
}

private fun BillFilter.label(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }


@Preview(showBackground = true)
@Composable
fun BillsPreview() {
    val mockBill = BillWithItems(
        bill = BillEntity(
            id = 1,
            billNumber = "HK-1001",
            customerName = "Rahul Sharma",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = BillStatus.ACTIVE
        ),
        items = listOf(
            BillItemEntity(1, 1, 1, "Aashirvaad Atta 5kg", 45000, 48000, 1),
            BillItemEntity(2, 1, 2, "Tata Salt 1kg", 2000, 2500, 2)
        )
    )

    HisabKitabTheme {
        BillsScreen(
            state = BillsState(
                bills = listOf(mockBill),
                filter = BillFilter.ACTIVE
            ),
            onNavigate = {},
            onSettings = {},
            onQuery = {},
            onFilter = {},
            onCreate = {},
            onOpen = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun BillDetailPreview() {
    val mockBill = BillWithItems(
        bill = BillEntity(
            id = 1,
            billNumber = "HK-1001",
            customerName = "Rahul Sharma",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = BillStatus.ACTIVE
        ),
        items = listOf(
            BillItemEntity(1, 1, 1, "Aashirvaad Atta 5kg", 45000, 48000, 1),
            BillItemEntity(2, 1, 2, "Tata Salt 1kg", 2000, 2500, 2)
        )
    )

    HisabKitabTheme {
        BillDetailScreen(
            bill = mockBill,
            products = emptyList(),
            shortages = emptyList(),
            onBack = {},
            onAddProduct = {},
            onScan = {},
            onCustomerChange = {},
            onQuantity = { _, _ -> },
            onRemove = {},
            onCancelBill = {},
            onPay = {},
            onForcePay = {},
            onDismissShortage = {}
        )
    }
}