package com.amg.hisabkitab.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BillStatus { ACTIVE, PAID, CANCELLED }
enum class PaymentMode { CASH, UPI }
enum class LossReason { EXPIRED, DAMAGED, UNSOLD, MANUAL_CORRECTION, OTHER }

@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = true), Index(value = ["sku"])]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val barcode: String,
    val sku: String,
    val purchasePricePaise: Long,
    val sellingPricePaise: Long,
    val stockQuantity: Int,
    val lowStockThreshold: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "bills", indices = [Index("status"), Index("createdAt")])
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billNumber: String,
    val customerName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val status: BillStatus = BillStatus.ACTIVE,
    val paymentMode: PaymentMode? = null,
    val paidAt: Long? = null
)

@Entity(
    tableName = "bill_items",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("billId"), Index("productId")]
)
data class BillItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val productId: Long,
    val productName: String,
    val purchasePricePaise: Long,
    val sellingPricePaise: Long,
    val quantity: Int
)

@Entity(
    tableName = "loss_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("productId"), Index("createdAt")]
)
data class LossEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val reason: LossReason,
    val note: String?,
    val purchasePricePaise: Long,
    val createdAt: Long
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "HisabKitab Shop",
    val ownerName: String = "Shop Owner",
    val phone: String = "",
    val address: String = "",
    val stockNotifications: Boolean = true,
    val pinEnabled: Boolean = false
)
