package com.amg.hisabkitab.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.amg.hisabkitab.domain.model.billProfitPaise
import com.amg.hisabkitab.domain.model.billTotalPaise

data class BillWithItems(
    @Embedded val bill: BillEntity,
    @Relation(parentColumn = "id", entityColumn = "billId")
    val items: List<BillItemEntity>
) {
    val totalPaise: Long get() = billTotalPaise(items)
    val profitPaise: Long get() = billProfitPaise(items)
    val itemCount: Int get() = items.sumOf { it.quantity }
}

data class StockShortage(
    val productId: Long,
    val productName: String,
    val requested: Int,
    val available: Int
)

sealed interface PaymentResult {
    data object Success : PaymentResult
    data class InsufficientStock(val shortages: List<StockShortage>) : PaymentResult
}
