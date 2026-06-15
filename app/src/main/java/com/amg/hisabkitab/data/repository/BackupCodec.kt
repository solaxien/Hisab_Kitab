package com.amg.hisabkitab.data.repository

import com.amg.hisabkitab.data.local.BillEntity
import com.amg.hisabkitab.data.local.BillItemEntity
import com.amg.hisabkitab.data.local.BillStatus
import com.amg.hisabkitab.data.local.HisabKitabDao
import com.amg.hisabkitab.data.local.LossEntryEntity
import com.amg.hisabkitab.data.local.LossReason
import com.amg.hisabkitab.data.local.PaymentMode
import com.amg.hisabkitab.data.local.ProductEntity
import com.amg.hisabkitab.data.local.SettingsEntity
import org.json.JSONArray
import org.json.JSONObject

class BackupCodec(private val dao: HisabKitabDao) {
    suspend fun encode(): String = JSONObject().apply {
        put("format", "hisabkitab-backup")
        put("version", 1)
        put("createdAt", System.currentTimeMillis())
        put("products", JSONArray(dao.allProducts().map(::productJson)))
        put("bills", JSONArray(dao.allBills().map(::billJson)))
        put("billItems", JSONArray(dao.allBillItems().map(::billItemJson)))
        put("losses", JSONArray(dao.allLosses().map(::lossJson)))
        put("settings", JSONArray(dao.allSettings().map(::settingsJson)))
    }.toString()

    suspend fun restore(raw: String) {
        val root = JSONObject(raw)
        require(root.optString("format") == "hisabkitab-backup") { "Not a HisabKitab backup" }
        require(root.optInt("version") == 1) { "Unsupported backup version" }
        val products = root.getJSONArray("products").objects(::product)
        val bills = root.getJSONArray("bills").objects(::bill)
        val items = root.getJSONArray("billItems").objects(::billItem)
        val losses = root.getJSONArray("losses").objects(::loss)
        val settings = root.getJSONArray("settings").objects(::settings)
        require(products.map { it.id }.toSet().size == products.size) { "Duplicate product IDs" }
        require(bills.map { it.id }.toSet().size == bills.size) { "Duplicate bill IDs" }
        val productIds = products.map { it.id }.toSet()
        val billIds = bills.map { it.id }.toSet()
        require(items.all { it.productId in productIds && it.billId in billIds }) {
            "Backup contains broken bill references"
        }
        require(losses.all { it.productId in productIds }) { "Backup contains broken loss references" }
        dao.replaceAll(products, bills, items, losses, settings)
    }

    private fun productJson(p: ProductEntity) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("barcode", p.barcode); put("sku", p.sku)
        put("purchase", p.purchasePricePaise); put("selling", p.sellingPricePaise)
        put("stock", p.stockQuantity); put("threshold", p.lowStockThreshold)
        put("createdAt", p.createdAt); put("updatedAt", p.updatedAt)
    }
    private fun billJson(b: BillEntity) = JSONObject().apply {
        put("id", b.id); put("number", b.billNumber); put("customer", b.customerName)
        put("createdAt", b.createdAt); put("updatedAt", b.updatedAt); put("status", b.status.name)
        put("paymentMode", b.paymentMode?.name); put("paidAt", b.paidAt)
    }
    private fun billItemJson(i: BillItemEntity) = JSONObject().apply {
        put("id", i.id); put("billId", i.billId); put("productId", i.productId)
        put("name", i.productName); put("purchase", i.purchasePricePaise)
        put("selling", i.sellingPricePaise); put("quantity", i.quantity)
    }
    private fun lossJson(l: LossEntryEntity) = JSONObject().apply {
        put("id", l.id); put("productId", l.productId); put("name", l.productName)
        put("quantity", l.quantity); put("reason", l.reason.name); put("note", l.note)
        put("purchase", l.purchasePricePaise); put("createdAt", l.createdAt)
    }
    private fun settingsJson(s: SettingsEntity) = JSONObject().apply {
        put("id", s.id); put("shopName", s.shopName); put("ownerName", s.ownerName)
        put("phone", s.phone); put("address", s.address)
        put("notifications", s.stockNotifications); put("pinEnabled", s.pinEnabled)
    }

    private fun product(o: JSONObject) = ProductEntity(
        o.getLong("id"), o.getString("name"), o.getString("barcode"), o.getString("sku"),
        o.getLong("purchase"), o.getLong("selling"), o.getInt("stock"), o.getInt("threshold"),
        o.getLong("createdAt"), o.getLong("updatedAt")
    )
    private fun bill(o: JSONObject) = BillEntity(
        o.getLong("id"), o.getString("number"), o.nullableString("customer"),
        o.getLong("createdAt"), o.getLong("updatedAt"), BillStatus.valueOf(o.getString("status")),
        o.nullableString("paymentMode")?.let(PaymentMode::valueOf),
        if (o.isNull("paidAt")) null else o.getLong("paidAt")
    )
    private fun billItem(o: JSONObject) = BillItemEntity(
        o.getLong("id"), o.getLong("billId"), o.getLong("productId"), o.getString("name"),
        o.getLong("purchase"), o.getLong("selling"), o.getInt("quantity")
    )
    private fun loss(o: JSONObject) = LossEntryEntity(
        o.getLong("id"), o.getLong("productId"), o.getString("name"), o.getInt("quantity"),
        LossReason.valueOf(o.getString("reason")), o.nullableString("note"),
        o.getLong("purchase"), o.getLong("createdAt")
    )
    private fun settings(o: JSONObject) = SettingsEntity(
        o.getInt("id"), o.getString("shopName"), o.getString("ownerName"),
        o.getString("phone"), o.getString("address"), o.getBoolean("notifications"),
        o.getBoolean("pinEnabled")
    )
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else getString(key)

private fun <T> JSONArray.objects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }
