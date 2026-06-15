package com.amg.hisabkitab

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amg.hisabkitab.data.local.BillEntity
import com.amg.hisabkitab.data.local.BillStatus
import com.amg.hisabkitab.data.local.HisabKitabDatabase
import com.amg.hisabkitab.data.local.PaymentMode
import com.amg.hisabkitab.data.local.PaymentResult
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
        val productId = dao.insertProduct(
            ProductEntity(
                name = "Milk", barcode = "8901", sku = "MILK-1",
                purchasePricePaise = 2_500, sellingPricePaise = 3_200,
                stockQuantity = 5, lowStockThreshold = 2, createdAt = 1, updatedAt = 1
            )
        )
        val billId = dao.insertBill(
            BillEntity(billNumber = "INV-1", customerName = null, createdAt = 1, updatedAt = 1)
        )
        dao.addProductToBill(billId, dao.product(productId)!!)
        assertEquals(5, dao.product(productId)!!.stockQuantity)
        assertEquals(BillStatus.ACTIVE, dao.bill(billId)!!.bill.status)

        val result = dao.payBill(billId, PaymentMode.CASH, false, 2)
        assertTrue(result is PaymentResult.Success)
        assertEquals(4, dao.product(productId)!!.stockQuantity)
        assertEquals(BillStatus.PAID, dao.bill(billId)!!.bill.status)
    }
}
