package com.amg.hisabkitab

import com.amg.hisabkitab.data.local.BillItemEntity
import com.amg.hisabkitab.data.local.ProductEntity
import com.amg.hisabkitab.domain.model.billProfitPaise
import com.amg.hisabkitab.domain.model.billTotalPaise
import com.amg.hisabkitab.domain.model.findStockShortages
import com.amg.hisabkitab.domain.model.stockAfterSale
import org.junit.Assert.assertEquals
import org.junit.Test

class BusinessRulesTest {
    private val item = BillItemEntity(
        id = 1, billId = 1, productId = 9, productName = "Milk",
        purchasePricePaise = 2_500, sellingPricePaise = 3_200, quantity = 3
    )

    @Test
    fun totalsAndProfitUseCapturedBillPrices() {
        assertEquals(9_600, billTotalPaise(listOf(item)))
        assertEquals(2_100, billProfitPaise(listOf(item)))
    }

    @Test
    fun shortageReportsRequestedAndAvailableStock() {
        val product = ProductEntity(
            id = 9, name = "Milk", barcode = "8901", sku = "MILK-1",
            purchasePricePaise = 2_700, sellingPricePaise = 3_500,
            stockQuantity = 2, lowStockThreshold = 3, createdAt = 0, updatedAt = 0
        )
        val shortage = findStockShortages(listOf(item), mapOf(9L to product)).single()
        assertEquals(3, shortage.requested)
        assertEquals(2, shortage.available)
    }

    @Test
    fun forcedPaymentNeverCreatesNegativeDisplayedStock() {
        assertEquals(0, stockAfterSale(current = 2, sold = 3))
        assertEquals(7, stockAfterSale(current = 10, sold = 3))
    }
}
