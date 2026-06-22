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
    abstract suspend fun deleteProductRow(id: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM bill_items
        INNER JOIN bills ON bills.id = bill_items.billId
        WHERE bill_items.productId = :productId AND bills.status = 'ACTIVE'
        """
    )
    abstract suspend fun activeBillItemReferenceCount(productId: Long): Int

    @Query("UPDATE products SET stockQuantity = :stockQuantity, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun updateProductStock(id: Long, stockQuantity: Int, updatedAt: Long): Int

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

    @Query("SELECT status FROM bills WHERE id = :billId")
    abstract suspend fun billStatus(billId: Long): BillStatus?

    @Query(
        "UPDATE bills SET customerName = :customerName, updatedAt = :updatedAt WHERE id = :billId AND status = 'ACTIVE'"
    )
    abstract suspend fun updateActiveBillCustomer(billId: Long, customerName: String?, updatedAt: Long): Int

    @Query(
        "UPDATE bills SET status = 'CANCELLED', updatedAt = :updatedAt WHERE id = :billId AND status = 'ACTIVE'"
    )
    abstract suspend fun cancelActiveBill(billId: Long, updatedAt: Long): Int

    @Query(
        """
        UPDATE bills
        SET status = 'PAID', paymentMode = :mode, paidAt = :paidAt, updatedAt = :updatedAt
        WHERE id = :billId AND status = 'ACTIVE'
        """
    )
    abstract suspend fun payActiveBill(
        billId: Long,
        mode: PaymentMode,
        paidAt: Long,
        updatedAt: Long
    ): Int

    @Insert
    abstract suspend fun insertBillItem(item: BillItemEntity): Long

    @Update
    abstract suspend fun updateBillItem(item: BillItemEntity)

    @Query("DELETE FROM bill_items WHERE id = :id")
    abstract suspend fun deleteBillItem(id: Long)

    @Query(
        "DELETE FROM bill_items WHERE id = :id AND billId IN (SELECT id FROM bills WHERE status = 'ACTIVE')"
    )
    abstract suspend fun deleteActiveBillItem(id: Long): Int

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
    open suspend fun restock(id: Long, quantity: Int, updatedAt: Long): InventoryAdjustmentResult {
        if (quantity <= 0) return InventoryAdjustmentResult.InvalidQuantity
        val current = product(id) ?: return InventoryAdjustmentResult.NotFound
        val nextStock = current.stockQuantity.toLong() + quantity
        if (nextStock > Int.MAX_VALUE) return InventoryAdjustmentResult.InvalidQuantity
        updateProductStock(id, nextStock.toInt(), updatedAt)
        return InventoryAdjustmentResult.Success
    }

    @Transaction
    open suspend fun deleteProduct(id: Long): ProductDeleteResult {
        if (product(id) == null) return ProductDeleteResult.NotFound
        if (activeBillItemReferenceCount(id) > 0) return ProductDeleteResult.ReferencedByActiveBill
        return if (deleteProductRow(id) > 0) ProductDeleteResult.Success else ProductDeleteResult.NotFound
    }

    @Transaction
    open suspend fun addProductToBill(billId: Long, product: ProductEntity): BillMutationResult {
        val status = billStatus(billId) ?: return BillMutationResult.NotFound
        if (status != BillStatus.ACTIVE) return BillMutationResult.InvalidStatus(status)

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
            val nextQuantity = existing.quantity.toLong() + 1
            if (nextQuantity > Int.MAX_VALUE) return BillMutationResult.InvalidQuantity
            updateBillItem(existing.copy(quantity = nextQuantity.toInt()))
        }
        return BillMutationResult.Success
    }

    @Transaction
    open suspend fun setBillItemQuantity(
        billId: Long,
        itemId: Long,
        quantity: Int
    ): BillMutationResult {
        val current = bill(billId) ?: return BillMutationResult.NotFound
        if (current.bill.status != BillStatus.ACTIVE) {
            return BillMutationResult.InvalidStatus(current.bill.status)
        }
        val item = current.items.firstOrNull { it.id == itemId } ?: return BillMutationResult.NotFound
        if (quantity <= 0) {
            deleteActiveBillItem(itemId)
        } else {
            updateBillItem(item.copy(quantity = quantity))
        }
        return BillMutationResult.Success
    }

    @Transaction
    open suspend fun updateCustomer(
        billId: Long,
        customerName: String?,
        updatedAt: Long
    ): BillMutationResult {
        val updated = updateActiveBillCustomer(billId, customerName, updatedAt)
        if (updated > 0) return BillMutationResult.Success
        val status = billStatus(billId) ?: return BillMutationResult.NotFound
        return BillMutationResult.InvalidStatus(status)
    }

    @Transaction
    open suspend fun cancelBill(billId: Long, updatedAt: Long): BillMutationResult {
        val updated = cancelActiveBill(billId, updatedAt)
        if (updated > 0) return BillMutationResult.Success
        val status = billStatus(billId) ?: return BillMutationResult.NotFound
        return BillMutationResult.InvalidStatus(status)
    }

    @Transaction
    open suspend fun payBill(
        billId: Long,
        mode: PaymentMode,
        allowNegativeStock: Boolean,
        now: Long
    ): PaymentResult {
        val current = bill(billId) ?: return PaymentResult.NotFound
        if (current.bill.status != BillStatus.ACTIVE) {
            return PaymentResult.InvalidStatus(current.bill.status)
        }

        val loadedProducts = current.items.map { it.productId to product(it.productId) }
        val missingProductIds = loadedProducts.filter { it.second == null }.map { it.first }.distinct()
        if (missingProductIds.isNotEmpty()) {
            return PaymentResult.MissingProducts(missingProductIds)
        }
        val products = loadedProducts.map { it.second!! }.associateBy { it.id }
        val shortages = findStockShortages(current.items, products)
        if (shortages.isNotEmpty() && !allowNegativeStock) {
            return PaymentResult.InsufficientStock(shortages)
        }

        current.items.forEach { item ->
            val product = products.getValue(item.productId)
            // "Pay anyway" clamps stock at zero so inventory never displays negative units.
            updateProduct(
                product.copy(
                    stockQuantity = stockAfterSale(product.stockQuantity, item.quantity),
                    updatedAt = now
                )
            )
        }
        if (payActiveBill(billId, mode, now, now) == 0) return PaymentResult.InvalidStatus(current.bill.status)
        return PaymentResult.Success
    }

    @Transaction
    open suspend fun recordLoss(
        productId: Long,
        quantity: Int,
        reason: LossReason,
        note: String?,
        now: Long
    ): LossRecordResult {
        if (quantity <= 0) return LossRecordResult.InvalidQuantity
        val product = product(productId) ?: return LossRecordResult.ProductNotFound
        if (quantity > product.stockQuantity) return LossRecordResult.InsufficientStock(product.stockQuantity)
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
                stockQuantity = product.stockQuantity - quantity,
                updatedAt = now
            )
        )
        return LossRecordResult.Success
    }
}
