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
    data object NotFound : PaymentResult
    data class InvalidStatus(val status: BillStatus) : PaymentResult
    data class MissingProducts(val productIds: List<Long>) : PaymentResult
    data class InsufficientStock(val shortages: List<StockShortage>) : PaymentResult
}

sealed interface BillMutationResult {
    data object Success : BillMutationResult
    data object NotFound : BillMutationResult
    data object InvalidQuantity : BillMutationResult
    data class InvalidStatus(val status: BillStatus) : BillMutationResult
}

sealed interface ProductDeleteResult {
    data object Success : ProductDeleteResult
    data object NotFound : ProductDeleteResult
    data object ReferencedByActiveBill : ProductDeleteResult
}

sealed interface ProductSaveResult {
    data class Success(val id: Long) : ProductSaveResult
    data object InvalidProduct : ProductSaveResult
    data object DuplicateSkuOrBarcode : ProductSaveResult
}

sealed interface InventoryAdjustmentResult {
    data object Success : InventoryAdjustmentResult
    data object NotFound : InventoryAdjustmentResult
    data object InvalidQuantity : InventoryAdjustmentResult
}

sealed interface LossRecordResult {
    data object Success : LossRecordResult
    data object ProductNotFound : LossRecordResult
    data object InvalidQuantity : LossRecordResult
    data class InsufficientStock(val available: Int) : LossRecordResult
}
