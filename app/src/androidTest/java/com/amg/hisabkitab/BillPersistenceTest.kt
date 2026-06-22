package com.amg.hisabkitab

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amg.hisabkitab.data.local.BillEntity
import com.amg.hisabkitab.data.local.BillStatus
import com.amg.hisabkitab.data.local.HisabKitabDatabase
import com.amg.hisabkitab.data.local.InventoryAdjustmentResult
import com.amg.hisabkitab.data.local.LossRecordResult
import com.amg.hisabkitab.data.local.LossReason
import com.amg.hisabkitab.data.local.PaymentMode
import com.amg.hisabkitab.data.local.PaymentResult
import com.amg.hisabkitab.data.local.ProductDeleteResult
import com.amg.hisabkitab.data.local.ProductEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BillPersistenceTest {
    private lateinit var db: HisabKitabDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HisabKitabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun activeBillPersistsAndStockChangesOnlyOnPayment() = runBlocking {
        val dao = db.dao()
        val productId = insertProduct()
        val billId = insertBill()
        dao.addProductToBill(billId, dao.product(productId)!!)
        assertEquals(5, dao.product(productId)!!.stockQuantity)
        assertEquals(BillStatus.ACTIVE, dao.bill(billId)!!.bill.status)

        val result = dao.payBill(billId, PaymentMode.CASH, false, 2)
        assertTrue(result is PaymentResult.Success)
        assertEquals(4, dao.product(productId)!!.stockQuantity)
        assertEquals(BillStatus.PAID, dao.bill(billId)!!.bill.status)
    }

    @Test
    fun paymentFailsWhenBillReferencesDeletedProduct() = runBlocking {
        val dao = db.dao()
        val productId = insertProduct()
        val billId = insertBill()
        dao.addProductToBill(billId, dao.product(productId)!!)
        dao.deleteProductRow(productId)

        val result = dao.payBill(billId, PaymentMode.CASH, false, 2)

        assertTrue(result is PaymentResult.MissingProducts)
        assertEquals(BillStatus.ACTIVE, dao.bill(billId)!!.bill.status)
    }

    @Test
    fun productDeleteIsBlockedWhileReferencedByActiveBill() = runBlocking {
        val dao = db.dao()
        val productId = insertProduct()
        val billId = insertBill()
        dao.addProductToBill(billId, dao.product(productId)!!)

        val result = dao.deleteProduct(productId)

        assertTrue(result is ProductDeleteResult.ReferencedByActiveBill)
        assertEquals(productId, dao.product(productId)!!.id)
    }

    @Test
    fun paidBillsRejectLaterCustomerAndItemChanges() = runBlocking {
        val dao = db.dao()
        val productId = insertProduct()
        val billId = insertBill()
        dao.addProductToBill(billId, dao.product(productId)!!)
        val itemId = dao.bill(billId)!!.items.single().id
        dao.payBill(billId, PaymentMode.CASH, false, 2)

        dao.updateCustomer(billId, "Late Edit", 3)
        dao.setBillItemQuantity(billId, itemId, 5)

        val bill = dao.bill(billId)!!
        assertEquals(BillStatus.PAID, bill.bill.status)
        assertEquals(null, bill.bill.customerName)
        assertEquals(1, bill.items.single().quantity)
    }

    @Test
    fun lossGreaterThanAvailableStockIsRejected() = runBlocking {
        val dao = db.dao()
        val productId = insertProduct()

        val result = dao.recordLoss(productId, 6, LossReason.DAMAGED, null, 2)

        assertTrue(result is LossRecordResult.InsufficientStock)
        assertEquals(5, dao.product(productId)!!.stockQuantity)
        assertTrue(dao.allLosses().isEmpty())
    }

    @Test
    fun restockRejectsInvalidAndOverflowQuantities() = runBlocking {
        val dao = db.dao()
        val productId = insertProduct()

        val invalid = dao.restock(productId, 0, 2)
        assertTrue(invalid is InventoryAdjustmentResult.InvalidQuantity)
        assertEquals(5, dao.product(productId)!!.stockQuantity)

        dao.updateProduct(dao.product(productId)!!.copy(stockQuantity = Int.MAX_VALUE))
        val overflow = dao.restock(productId, 1, 3)

        assertTrue(overflow is InventoryAdjustmentResult.InvalidQuantity)
        assertEquals(Int.MAX_VALUE, dao.product(productId)!!.stockQuantity)
    }

    @Test
    fun multipleProductsMayUseBlankSku() = runBlocking {
        insertProduct(barcode = "8901", sku = "")
        insertProduct(barcode = "8902", sku = "")

        assertEquals(2, db.dao().allProducts().size)
    }

    private suspend fun insertProduct(
        barcode: String = "8901",
        sku: String = "MILK-1"
    ): Long = db.dao().insertProduct(
        ProductEntity(
            name = "Milk", barcode = barcode, sku = sku,
            purchasePricePaise = 2_500, sellingPricePaise = 3_200,
            stockQuantity = 5, lowStockThreshold = 2, createdAt = 1, updatedAt = 1
        )
    )

    private suspend fun insertBill(): Long = db.dao().insertBill(
        BillEntity(billNumber = "INV-1", customerName = null, createdAt = 1, updatedAt = 1)
    )
}
