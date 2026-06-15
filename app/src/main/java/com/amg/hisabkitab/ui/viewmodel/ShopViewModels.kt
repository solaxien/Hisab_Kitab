package com.amg.hisabkitab.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amg.hisabkitab.data.local.BillStatus
import com.amg.hisabkitab.data.local.BillWithItems
import com.amg.hisabkitab.data.local.LossEntryEntity
import com.amg.hisabkitab.data.local.LossReason
import com.amg.hisabkitab.data.local.PaymentMode
import com.amg.hisabkitab.data.local.PaymentResult
import com.amg.hisabkitab.data.local.ProductEntity
import com.amg.hisabkitab.data.local.SettingsEntity
import com.amg.hisabkitab.data.local.StockShortage
import com.amg.hisabkitab.data.repository.ShopRepository
import com.amg.hisabkitab.ui.common.currentDayStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ProductFilter { ALL, LOW_STOCK, OUT_OF_STOCK }
enum class ProductSort { NAME, STOCK_LOW_FIRST, VALUE_HIGH_FIRST }

data class InventoryState(
    val products: List<ProductEntity> = emptyList(),
    val query: String = "",
    val filter: ProductFilter = ProductFilter.ALL,
    val sort: ProductSort = ProductSort.NAME,
    val totalCount: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val valuePaise: Long = 0
)

class InventoryViewModel(private val repository: ShopRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(ProductFilter.ALL)
    private val sort = MutableStateFlow(ProductSort.NAME)

    val state = combine(repository.products, query, filter, sort) { products, q, f, s ->
        val matching = products.filter {
            (q.isBlank() || it.name.contains(q, true) || it.sku.contains(q, true) ||
                it.barcode.contains(q, true)) &&
                when (f) {
                    ProductFilter.ALL -> true
                    ProductFilter.LOW_STOCK -> it.stockQuantity in 1..it.lowStockThreshold
                    ProductFilter.OUT_OF_STOCK -> it.stockQuantity == 0
                }
        }
        val sorted = when (s) {
            ProductSort.NAME -> matching.sortedBy { it.name.lowercase() }
            ProductSort.STOCK_LOW_FIRST -> matching.sortedBy { it.stockQuantity }
            ProductSort.VALUE_HIGH_FIRST -> matching.sortedByDescending {
                it.purchasePricePaise * it.stockQuantity
            }
        }
        InventoryState(
            products = sorted,
            query = q,
            filter = f,
            sort = s,
            totalCount = products.size,
            lowStockCount = products.count { it.stockQuantity in 1..it.lowStockThreshold },
            outOfStockCount = products.count { it.stockQuantity == 0 },
            valuePaise = products.sumOf { it.purchasePricePaise * it.stockQuantity }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryState())

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: ProductFilter) { filter.value = value }
    fun setSort(value: ProductSort) { sort.value = value }
    fun save(product: ProductEntity) = viewModelScope.launch { repository.saveProduct(product) }
    fun delete(id: Long) = viewModelScope.launch { repository.deleteProduct(id) }
    fun restock(id: Long, quantity: Int) = viewModelScope.launch {
        if (quantity > 0) repository.restock(id, quantity)
    }
}

enum class BillFilter { ACTIVE, PAID, CANCELLED }

data class BillsState(
    val bills: List<BillWithItems> = emptyList(),
    val query: String = "",
    val filter: BillFilter = BillFilter.ACTIVE,
    val paymentShortages: List<StockShortage> = emptyList(),
    val pendingPaymentMode: PaymentMode? = null,
    val pendingBillId: Long? = null
)

class BillsViewModel(private val repository: ShopRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(BillFilter.ACTIVE)
    private val shortages = MutableStateFlow<List<StockShortage>>(emptyList())
    private val pendingMode = MutableStateFlow<PaymentMode?>(null)
    private val pendingBill = MutableStateFlow<Long?>(null)

    val state = combine(
        repository.bills, query, filter, shortages, pendingMode, pendingBill
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val bills = values[0] as List<BillWithItems>
        val q = values[1] as String
        val f = values[2] as BillFilter
        val status = BillStatus.valueOf(f.name)
        BillsState(
            bills = bills.filter {
                it.bill.status == status &&
                    (q.isBlank() || it.bill.billNumber.contains(q, true) ||
                        it.bill.customerName.orEmpty().contains(q, true))
            },
            query = q,
            filter = f,
            paymentShortages = values[3] as List<StockShortage>,
            pendingPaymentMode = values[4] as PaymentMode?,
            pendingBillId = values[5] as Long?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsState())

    val allBills = repository.bills.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val products = repository.products.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    fun bill(id: Long) = repository.bill(id)
    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: BillFilter) { filter.value = value }
    suspend fun createBill(customer: String?): Long = repository.createBill(customer)
    fun addProduct(billId: Long, productId: Long) = viewModelScope.launch {
        repository.addProduct(billId, productId)
    }
    fun addBarcode(billId: Long, barcode: String, onResult: (Boolean) -> Unit) =
        viewModelScope.launch { onResult(repository.addBarcode(billId, barcode)) }
    fun setQuantity(itemId: Long, billId: Long, quantity: Int) = viewModelScope.launch {
        repository.setQuantity(itemId, billId, quantity)
    }
    fun removeItem(id: Long) = viewModelScope.launch { repository.removeBillItem(id) }
    fun updateCustomer(id: Long, name: String) = viewModelScope.launch {
        repository.updateCustomer(id, name)
    }
    fun cancel(id: Long) = viewModelScope.launch { repository.cancelBill(id) }
    fun pay(id: Long, mode: PaymentMode, force: Boolean = false, onPaid: () -> Unit) =
        viewModelScope.launch {
            when (val result = repository.payBill(id, mode, force)) {
                PaymentResult.Success -> {
                    clearPaymentWarning()
                    onPaid()
                }
                is PaymentResult.InsufficientStock -> {
                    shortages.value = result.shortages
                    pendingMode.value = mode
                    pendingBill.value = id
                }
            }
        }
    fun forcePayment(onPaid: () -> Unit) {
        val id = pendingBill.value ?: return
        val mode = pendingMode.value ?: return
        pay(id, mode, true, onPaid)
    }
    fun clearPaymentWarning() {
        shortages.value = emptyList()
        pendingMode.value = null
        pendingBill.value = null
    }
}

enum class AnalyticsPeriod { THIS_MONTH, LAST_MONTH, CUSTOM }

data class DashboardState(
    val ownerName: String = "Shop Owner",
    val todaySalesPaise: Long = 0,
    val cashPaise: Long = 0,
    val upiPaise: Long = 0,
    val activeBills: List<BillWithItems> = emptyList(),
    val lowStock: List<ProductEntity> = emptyList()
)

data class AnalyticsState(
    val period: AnalyticsPeriod = AnalyticsPeriod.THIS_MONTH,
    val revenuePaise: Long = 0,
    val cashPaise: Long = 0,
    val upiPaise: Long = 0,
    val grossProfitPaise: Long = 0,
    val lossPaise: Long = 0,
    val netProfitPaise: Long = 0,
    val paidBills: Int = 0,
    val dailyRevenuePaise: List<Long> = emptyList(),
    val losses: List<LossEntryEntity> = emptyList()
) {
    val marginPercent: Double
        get() = if (revenuePaise == 0L) 0.0 else netProfitPaise * 100.0 / revenuePaise
}

class DashboardViewModel(private val repository: ShopRepository) : ViewModel() {
    private val period = MutableStateFlow(AnalyticsPeriod.THIS_MONTH)
    private val customRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val products = repository.products.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val dashboard = combine(repository.bills, repository.products, repository.settings) {
            bills, products, settings ->
        val start = currentDayStart()
        val paidToday = bills.filter { it.bill.status == BillStatus.PAID && (it.bill.paidAt ?: 0) >= start }
        DashboardState(
            ownerName = settings.ownerName.ifBlank { "Shop Owner" },
            todaySalesPaise = paidToday.sumOf { it.totalPaise },
            cashPaise = paidToday.filter { it.bill.paymentMode == PaymentMode.CASH }.sumOf { it.totalPaise },
            upiPaise = paidToday.filter { it.bill.paymentMode == PaymentMode.UPI }.sumOf { it.totalPaise },
            activeBills = bills.filter { it.bill.status == BillStatus.ACTIVE }.take(3),
            lowStock = products.filter { it.stockQuantity <= it.lowStockThreshold }.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    val analytics: StateFlow<AnalyticsState> = combine(
        repository.bills, repository.losses, period, customRange
    ) { bills, losses, selected, custom ->
        val (start, end) = rangeFor(selected, custom)
        val paid = bills.filter {
            it.bill.status == BillStatus.PAID && (it.bill.paidAt ?: 0) in start until end
        }
        val selectedLosses = losses.filter { it.createdAt in start until end }
        val revenue = paid.sumOf { it.totalPaise }
        val grossProfit = paid.sumOf { it.profitPaise }
        val loss = selectedLosses.sumOf { it.purchasePricePaise * it.quantity }
        val zone = ZoneId.of("Asia/Kolkata")
        val dailyRevenue = paid
            .groupBy { Instant.ofEpochMilli(it.bill.paidAt ?: 0).atZone(zone).toLocalDate() }
            .toSortedMap()
            .values
            .map { dayBills -> dayBills.sumOf { it.totalPaise } }
        AnalyticsState(
            period = selected,
            revenuePaise = revenue,
            cashPaise = paid.filter { it.bill.paymentMode == PaymentMode.CASH }.sumOf { it.totalPaise },
            upiPaise = paid.filter { it.bill.paymentMode == PaymentMode.UPI }.sumOf { it.totalPaise },
            grossProfitPaise = grossProfit,
            lossPaise = loss,
            netProfitPaise = grossProfit - loss,
            paidBills = paid.size,
            dailyRevenuePaise = dailyRevenue,
            losses = selectedLosses
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    fun setPeriod(value: AnalyticsPeriod) { period.value = value }
    fun setCustomRange(start: Long, endInclusive: Long) {
        customRange.value = start to endInclusive + 86_400_000L
        period.value = AnalyticsPeriod.CUSTOM
    }
    fun recordLoss(productId: Long, quantity: Int, reason: LossReason, note: String?) =
        viewModelScope.launch {
            if (quantity > 0) repository.recordLoss(productId, quantity, reason, note)
        }

    private fun rangeFor(
        selected: AnalyticsPeriod,
        custom: Pair<Long, Long>?
    ): Pair<Long, Long> {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = LocalDate.now(zone)
        val first = now.withDayOfMonth(1)
        val dates = when (selected) {
            AnalyticsPeriod.THIS_MONTH -> first to first.plusMonths(1)
            AnalyticsPeriod.LAST_MONTH -> first.minusMonths(1) to first
            AnalyticsPeriod.CUSTOM -> {
                if (custom != null) return custom
                first to first.plusMonths(1)
            }
        }
        return dates.first.atStartOfDay(zone).toInstant().toEpochMilli() to
            dates.second.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

class SettingsViewModel(private val repository: ShopRepository) : ViewModel() {
    val settings = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsEntity()
    )
    fun save(value: SettingsEntity) = viewModelScope.launch { repository.saveSettings(value) }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T : ViewModel> shopViewModelFactory(
    repository: ShopRepository
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = when (T::class) {
        InventoryViewModel::class -> InventoryViewModel(repository)
        BillsViewModel::class -> BillsViewModel(repository)
        DashboardViewModel::class -> DashboardViewModel(repository)
        SettingsViewModel::class -> SettingsViewModel(repository)
        else -> error("Unsupported ViewModel ${T::class}")
    } as VM
}
