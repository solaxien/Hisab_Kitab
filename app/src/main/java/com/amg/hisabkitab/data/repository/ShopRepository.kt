package com.amg.hisabkitab.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.amg.hisabkitab.data.local.BillEntity
import com.amg.hisabkitab.data.local.BillMutationResult
import com.amg.hisabkitab.data.local.HisabKitabDao
import com.amg.hisabkitab.data.local.InventoryAdjustmentResult
import com.amg.hisabkitab.data.local.LossRecordResult
import com.amg.hisabkitab.data.local.LossReason
import com.amg.hisabkitab.data.local.PaymentMode
import com.amg.hisabkitab.data.local.PaymentResult
import com.amg.hisabkitab.data.local.ProductDeleteResult
import com.amg.hisabkitab.data.local.ProductSaveResult
import com.amg.hisabkitab.data.local.ProductEntity
import com.amg.hisabkitab.data.local.SettingsEntity
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicInteger

class ShopRepository(private val dao: HisabKitabDao) {
    private val backupCodec = BackupCodec(dao)
    val products = dao.observeProducts()
    val bills = dao.observeBills()
    val losses = dao.observeLosses()
    val settings = dao.observeSettings().map { it ?: SettingsEntity() }
    private val billSequence = AtomicInteger()

    fun bill(id: Long) = dao.observeBill(id)

    suspend fun saveProduct(product: ProductEntity): ProductSaveResult {
        if (!product.isValidForSave()) return ProductSaveResult.InvalidProduct
        val now = System.currentTimeMillis()
        return try {
            val id = if (product.id == 0L) {
                dao.insertProduct(product.copy(createdAt = now, updatedAt = now))
            } else {
                dao.updateProduct(product.copy(updatedAt = now))
                product.id
            }
            ProductSaveResult.Success(id)
        } catch (_: SQLiteConstraintException) {
            ProductSaveResult.DuplicateSkuOrBarcode
        } catch (_: androidx.sqlite.SQLiteException) {
            ProductSaveResult.DuplicateSkuOrBarcode
        }
    }

    private fun ProductEntity.isValidForSave(): Boolean =
        name.isNotBlank() &&
            barcode.isNotBlank() &&
            purchasePricePaise >= 0 &&
            sellingPricePaise >= 0 &&
            stockQuantity >= 0 &&
            lowStockThreshold >= 0

    suspend fun deleteProduct(id: Long): ProductDeleteResult = dao.deleteProduct(id)
    suspend fun restock(id: Long, quantity: Int): InventoryAdjustmentResult =
        dao.restock(id, quantity, System.currentTimeMillis())

    suspend fun createBill(customerName: String?): Long {
        val now = System.currentTimeMillis()
        val suffix = (now % 100000).toString().padStart(5, '0')
        return dao.insertBill(
            BillEntity(
                billNumber = "INV-$suffix${billSequence.getAndIncrement() % 10}",
                customerName = customerName?.trim()?.ifBlank { null },
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun addProduct(billId: Long, productId: Long): BillMutationResult {
        val product = dao.product(productId) ?: return BillMutationResult.NotFound
        return dao.addProductToBill(billId, product)
    }

    suspend fun addBarcode(billId: Long, barcode: String): Boolean {
        val product = dao.productByBarcode(barcode) ?: return false
        return dao.addProductToBill(billId, product) is BillMutationResult.Success
    }

    suspend fun productByBarcode(barcode: String): ProductEntity? =
        dao.productByBarcode(barcode.trim())

    suspend fun setQuantity(itemId: Long, billId: Long, quantity: Int): BillMutationResult =
        dao.setBillItemQuantity(billId, itemId, quantity)

    suspend fun removeBillItem(id: Long) = dao.deleteActiveBillItem(id)

    suspend fun updateCustomer(billId: Long, name: String): BillMutationResult =
        dao.updateCustomer(
            billId = billId,
            customerName = name.trim().ifBlank { null },
            updatedAt = System.currentTimeMillis()
        )

    suspend fun cancelBill(billId: Long): BillMutationResult =
        dao.cancelBill(billId, System.currentTimeMillis())

    suspend fun payBill(id: Long, mode: PaymentMode, force: Boolean): PaymentResult =
        dao.payBill(id, mode, force, System.currentTimeMillis())

    suspend fun recordLoss(productId: Long, quantity: Int, reason: LossReason, note: String?): LossRecordResult =
        dao.recordLoss(productId, quantity, reason, note, System.currentTimeMillis())

    suspend fun saveSettings(settings: SettingsEntity) = dao.saveSettings(settings)
    suspend fun createBackup(): String = backupCodec.encode()
    suspend fun restoreBackup(raw: String) = backupCodec.restore(raw)
}
