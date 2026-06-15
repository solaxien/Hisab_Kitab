package com.amg.hisabkitab.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.amg.hisabkitab.domain.model.findStockShortages
import com.amg.hisabkitab.domain.model.stockAfterSale

@Dao
abstract class HisabKitabDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    abstract fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    abstract suspend fun product(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    abstract suspend fun productByBarcode(barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertProduct(product: ProductEntity): Long

    @Update
    abstract suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    abstract suspend fun deleteProduct(id: Long)

    @Query("UPDATE products SET stockQuantity = stockQuantity + :quantity, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun restock(id: Long, quantity: Int, updatedAt: Long)

    @Transaction
    @Query("SELECT * FROM bills ORDER BY createdAt DESC")
    abstract fun observeBills(): Flow<List<BillWithItems>>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id")
    abstract fun observeBill(id: Long): Flow<BillWithItems?>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id")
    abstract suspend fun bill(id: Long): BillWithItems?

    @Insert
    abstract suspend fun insertBill(bill: BillEntity): Long

    @Update
    abstract suspend fun updateBill(bill: BillEntity)

    @Insert
    abstract suspend fun insertBillItem(item: BillItemEntity): Long

    @Update
    abstract suspend fun updateBillItem(item: BillItemEntity)

    @Query("DELETE FROM bill_items WHERE id = :id")
    abstract suspend fun deleteBillItem(id: Long)

    @Query("SELECT * FROM bill_items WHERE billId = :billId AND productId = :productId LIMIT 1")
    abstract suspend fun billItemForProduct(billId: Long, productId: Long): BillItemEntity?

    @Insert
    abstract suspend fun insertLoss(loss: LossEntryEntity): Long

    @Query("SELECT * FROM loss_entries ORDER BY createdAt DESC")
    abstract fun observeLosses(): Flow<List<LossEntryEntity>>

    @Query("SELECT * FROM settings WHERE id = 1")
    abstract fun observeSettings(): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveSettings(settings: SettingsEntity)

    @Query("SELECT * FROM products")
    abstract suspend fun allProducts(): List<ProductEntity>

    @Query("SELECT * FROM bills")
    abstract suspend fun allBills(): List<BillEntity>

    @Query("SELECT * FROM bill_items")
    abstract suspend fun allBillItems(): List<BillItemEntity>

    @Query("SELECT * FROM loss_entries")
    abstract suspend fun allLosses(): List<LossEntryEntity>

    @Query("SELECT * FROM settings")
    abstract suspend fun allSettings(): List<SettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertBills(bills: List<BillEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertBillItems(items: List<BillItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertLosses(losses: List<LossEntryEntity>)

    @Query("DELETE FROM bill_items")
    abstract suspend fun clearBillItems()

    @Query("DELETE FROM bills")
    abstract suspend fun clearBills()

    @Query("DELETE FROM loss_entries")
    abstract suspend fun clearLosses()

    @Query("DELETE FROM products")
    abstract suspend fun clearProducts()

    @Query("DELETE FROM settings")
    abstract suspend fun clearSettings()

    @Transaction
    open suspend fun replaceAll(
        products: List<ProductEntity>,
        bills: List<BillEntity>,
        billItems: List<BillItemEntity>,
        losses: List<LossEntryEntity>,
        settings: List<SettingsEntity>
    ) {
        clearBillItems()
        clearBills()
        clearLosses()
        clearProducts()
        clearSettings()
        insertProducts(products)
        insertBills(bills)
        insertBillItems(billItems)
        insertLosses(losses)
        settings.forEach { saveSettings(it) }
    }

    @Transaction
    open suspend fun addProductToBill(billId: Long, product: ProductEntity) {
        val existing = billItemForProduct(billId, product.id)
        if (existing == null) {
            insertBillItem(
                BillItemEntity(
                    billId = billId,
                    productId = product.id,
                    productName = product.name,
                    purchasePricePaise = product.purchasePricePaise,
                    sellingPricePaise = product.sellingPricePaise,
                    quantity = 1
                )
            )
        } else {
            updateBillItem(existing.copy(quantity = existing.quantity + 1))
        }
    }

    @Transaction
    open suspend fun payBill(
        billId: Long,
        mode: PaymentMode,
        allowNegativeStock: Boolean,
        now: Long
    ): PaymentResult {
        val current = bill(billId) ?: return PaymentResult.Success
        if (current.bill.status != BillStatus.ACTIVE) return PaymentResult.Success

        val products = current.items.mapNotNull { product(it.productId) }.associateBy { it.id }
        val shortages = findStockShortages(current.items, products)
        if (shortages.isNotEmpty() && !allowNegativeStock) {
            return PaymentResult.InsufficientStock(shortages)
        }

        current.items.forEach { item ->
            val product = product(item.productId) ?: return@forEach
            // "Pay anyway" clamps stock at zero so inventory never displays negative units.
            updateProduct(
                product.copy(
                    stockQuantity = stockAfterSale(product.stockQuantity, item.quantity),
                    updatedAt = now
                )
            )
        }
        updateBill(
            current.bill.copy(
                status = BillStatus.PAID,
                paymentMode = mode,
                paidAt = now,
                updatedAt = now
            )
        )
        return PaymentResult.Success
    }

    @Transaction
    open suspend fun recordLoss(
        productId: Long,
        quantity: Int,
        reason: LossReason,
        note: String?,
        now: Long
    ) {
        val product = product(productId) ?: return
        insertLoss(
            LossEntryEntity(
                productId = product.id,
                productName = product.name,
                quantity = quantity,
                reason = reason,
                note = note,
                purchasePricePaise = product.purchasePricePaise,
                createdAt = now
            )
        )
        updateProduct(
            product.copy(
                stockQuantity = (product.stockQuantity - quantity).coerceAtLeast(0),
                updatedAt = now
            )
        )
    }
}
