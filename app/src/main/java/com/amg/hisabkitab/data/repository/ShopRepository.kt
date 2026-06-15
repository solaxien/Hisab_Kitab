package com.amg.hisabkitab.data.repository

import com.amg.hisabkitab.data.local.BillEntity
import com.amg.hisabkitab.data.local.BillStatus
import com.amg.hisabkitab.data.local.HisabKitabDao
import com.amg.hisabkitab.data.local.LossReason
import com.amg.hisabkitab.data.local.PaymentMode
import com.amg.hisabkitab.data.local.PaymentResult
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

    suspend fun saveProduct(product: ProductEntity): Long {
        val now = System.currentTimeMillis()
        return if (product.id == 0L) {
            dao.insertProduct(product.copy(createdAt = now, updatedAt = now))
        } else {
            dao.updateProduct(product.copy(updatedAt = now))
            product.id
        }
    }

    suspend fun deleteProduct(id: Long) = dao.deleteProduct(id)
    suspend fun restock(id: Long, quantity: Int) =
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

    suspend fun addProduct(billId: Long, productId: Long) {
        val product = dao.product(productId) ?: return
        dao.addProductToBill(billId, product)
    }

    suspend fun addBarcode(billId: Long, barcode: String): Boolean {
        val product = dao.productByBarcode(barcode) ?: return false
        dao.addProductToBill(billId, product)
        return true
    }

    suspend fun setQuantity(itemId: Long, billId: Long, quantity: Int) {
        val item = dao.bill(billId)?.items?.firstOrNull { it.id == itemId } ?: return
        if (quantity <= 0) dao.deleteBillItem(itemId)
        else dao.updateBillItem(item.copy(quantity = quantity))
    }

    suspend fun removeBillItem(id: Long) = dao.deleteBillItem(id)

    suspend fun updateCustomer(billId: Long, name: String) {
        val current = dao.bill(billId)?.bill ?: return
        dao.updateBill(
            current.copy(
                customerName = name.trim().ifBlank { null },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun cancelBill(billId: Long) {
        val current = dao.bill(billId)?.bill ?: return
        if (current.status == BillStatus.ACTIVE) {
            dao.updateBill(current.copy(status = BillStatus.CANCELLED, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun payBill(id: Long, mode: PaymentMode, force: Boolean): PaymentResult =
        dao.payBill(id, mode, force, System.currentTimeMillis())

    suspend fun recordLoss(productId: Long, quantity: Int, reason: LossReason, note: String?) =
        dao.recordLoss(productId, quantity, reason, note, System.currentTimeMillis())

    suspend fun saveSettings(settings: SettingsEntity) = dao.saveSettings(settings)
    suspend fun createBackup(): String = backupCodec.encode()
    suspend fun restoreBackup(raw: String) = backupCodec.restore(raw)
}
