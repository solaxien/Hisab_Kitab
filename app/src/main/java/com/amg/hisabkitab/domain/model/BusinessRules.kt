package com.amg.hisabkitab.domain.model

import com.amg.hisabkitab.data.local.BillItemEntity
import com.amg.hisabkitab.data.local.ProductEntity
import com.amg.hisabkitab.data.local.StockShortage

fun billTotalPaise(items: List<BillItemEntity>): Long =
    items.sumOf { it.sellingPricePaise * it.quantity }

fun billProfitPaise(items: List<BillItemEntity>): Long =
    items.sumOf { (it.sellingPricePaise - it.purchasePricePaise) * it.quantity }

fun findStockShortages(
    items: List<BillItemEntity>,
    products: Map<Long, ProductEntity>
): List<StockShortage> = items.mapNotNull { item ->
    val product = products[item.productId] ?: return@mapNotNull null
    if (item.quantity > product.stockQuantity) {
        StockShortage(product.id, product.name, item.quantity, product.stockQuantity)
    } else null
}

fun stockAfterSale(current: Int, sold: Int): Int = (current - sold).coerceAtLeast(0)
