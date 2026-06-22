package com.amg.hisabkitab.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.Button
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.provideContent
import com.amg.hisabkitab.HisabKitabApplication
import com.amg.hisabkitab.MainActivity
import com.amg.hisabkitab.R
import com.amg.hisabkitab.data.local.BillStatus
import com.amg.hisabkitab.data.local.ProductEntity
import com.amg.hisabkitab.data.repository.ShopRepository
import com.amg.hisabkitab.ui.common.money
import com.amg.hisabkitab.ui.navigation.AppLaunchActions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

private val selectedProductIndexKey = intPreferencesKey("selected_product_index")
private val selectedQuantityKey = intPreferencesKey("selected_quantity")
private val productIdParameter = ActionParameters.Key<Long>("product_id")
private val quantityParameter = ActionParameters.Key<Int>("quantity")
private val widgetZone = ZoneId.of("Asia/Kolkata")
private val ink = ColorProvider(Color(0xFF171717))
private val muted = ColorProvider(Color(0xFF5F6368))
private val paper = ColorProvider(Color(0xFFFFFBFE))
private val accent = ColorProvider(Color(0xFF006C4F))
private val warning = ColorProvider(Color(0xFFB3261E))

class InventoryStateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = InventoryStateWidget()
}

class AnalysisChartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AnalysisChartWidget()
}

class CreateBillWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CreateBillWidget()
}

class InventoryStateWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val products = context.databaseProducts()
        val lowStock = products
            .filter { it.stockQuantity <= it.lowStockThreshold }
            .sortedWith(compareBy<ProductEntity> { it.stockQuantity }.thenBy { it.name })
            .take(3)

        provideContent {
            WidgetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = GlanceModifier.size(34.dp)
                    )
                    Spacer(GlanceModifier.width(10.dp))
                    Column {
                        Text("Inventory", style = titleStyle())
                        Text(
                            if (lowStock.isEmpty()) {
                                "Inventory looks good"
                            } else {
                                "${lowStock.size} item${if (lowStock.size == 1) "" else "s"} running low"
                            },
                            style = bodyStyle(if (lowStock.isEmpty()) accent else warning)
                        )
                    }
                }
                Spacer(GlanceModifier.height(12.dp))
                if (lowStock.isEmpty()) {
                    Text("No stock running low", style = bodyStyle(muted))
                } else {
                    lowStock.forEach { product ->
                        Text(
                            "${product.name}: ${product.stockQuantity} left",
                            style = bodyStyle(warning)
                        )
                    }
                }
            }
        }
    }
}

class AnalysisChartWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = (context.applicationContext as HisabKitabApplication).database.dao()
        val bills = dao.allBills()
        val items = dao.allBillItems().groupBy { it.billId }
        val today = LocalDate.now(widgetZone)
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val revenueByDay = days.associateWith { 0L }.toMutableMap()
        bills.filter { it.status == BillStatus.PAID && it.paidAt != null }.forEach { bill ->
            val day = Instant.ofEpochMilli(bill.paidAt ?: 0).atZone(widgetZone).toLocalDate()
            if (day in revenueByDay.keys) {
                revenueByDay[day] = revenueByDay.getValue(day) +
                    items.orEmpty()[bill.id].orEmpty().sumOf { it.sellingPricePaise * it.quantity }
            }
        }
        val values = days.map { revenueByDay.getValue(it) }
        val maxValue = max(values.maxOrNull() ?: 0L, 1L)
        val total = values.sum()

        provideContent {
            WidgetCard {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text("Weekly sales", style = titleStyle())
                        Text(money(total), style = bodyStyle(accent))
                    }
                    Button(text = "Open", onClick = actionStartActivity(openMainIntent()))
                }
                Spacer(GlanceModifier.height(10.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    days.forEachIndexed { index, day ->
                        val barHeight = (12 + (values[index].toFloat() / maxValue * 50)).dp
                        Column(
                            modifier = GlanceModifier.defaultWeight(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .width(18.dp)
                                    .height(barHeight)
                                    .background(accent)
                            ) {}
                            Spacer(GlanceModifier.height(4.dp))
                            Text(day.dayOfWeek.name.take(1), style = captionStyle())
                        }
                    }
                }
            }
        }
    }
}

class CreateBillWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val products = context.databaseProducts().sortedBy { it.name.lowercase() }

        provideContent {
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val quantity = max(prefs[selectedQuantityKey] ?: 1, 1)
            val selectedIndex = (prefs[selectedProductIndexKey] ?: 0).coerceIn(
                0,
                max(products.lastIndex, 0)
            )
            val product = products.getOrNull(selectedIndex)
            val total = product?.sellingPricePaise?.times(quantity) ?: 0L

            WidgetCard {
                Text("Create bill", style = titleStyle())
                Spacer(GlanceModifier.height(8.dp))
                if (product == null) {
                    Text("Add inventory items first", style = bodyStyle(muted))
                    Spacer(GlanceModifier.height(8.dp))
                    Button(
                        text = "Add item",
                        onClick = actionStartActivity(openMainIntent(AppLaunchActions.AddItem))
                    )
                } else {
                    Text(product.name, style = bodyStyle(ink))
                    Text("${money(product.sellingPricePaise)} each", style = captionStyle())
                    Spacer(GlanceModifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            text = "Prev",
                            onClick = actionRunCallback<SelectPreviousProductAction>()
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Button(
                            text = "Next",
                            onClick = actionRunCallback<SelectNextProductAction>()
                        )
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(text = "-", onClick = actionRunCallback<DecreaseQuantityAction>())
                        Text(
                            quantity.toString(),
                            modifier = GlanceModifier.padding(horizontal = 12.dp),
                            style = titleStyle()
                        )
                        Button(text = "+", onClick = actionRunCallback<IncreaseQuantityAction>())
                        Spacer(GlanceModifier.defaultWeight())
                        Text(money(total), style = titleStyle(accent))
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    Button(
                        text = "Open bill",
                        onClick = actionRunCallback<CreateWidgetBillAction>(
                            actionParametersOf(
                                productIdParameter to product.id,
                                quantityParameter to quantity
                            )
                        )
                    )
                }
            }
        }
    }
}

class SelectPreviousProductAction : ProductSelectionAction(-1)
class SelectNextProductAction : ProductSelectionAction(1)

abstract class ProductSelectionAction(private val delta: Int) : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val count = context.databaseProducts().size
        if (count == 0) return
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val mutable = prefs.toMutablePreferences()
            val current = mutable[selectedProductIndexKey] ?: 0
            mutable[selectedProductIndexKey] = (current + delta).floorMod(count)
            mutable
        }
        CreateBillWidget().update(context, glanceId)
    }
}

class IncreaseQuantityAction : QuantityAction(1)
class DecreaseQuantityAction : QuantityAction(-1)

abstract class QuantityAction(private val delta: Int) : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val mutable = prefs.toMutablePreferences()
            mutable[selectedQuantityKey] = max((mutable[selectedQuantityKey] ?: 1) + delta, 1)
            mutable
        }
        CreateBillWidget().update(context, glanceId)
    }
}

class CreateWidgetBillAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val productId = parameters[productIdParameter] ?: return
        val quantity = max(parameters[quantityParameter] ?: 1, 1)
        val dao = (context.applicationContext as HisabKitabApplication).database.dao()
        val product = dao.product(productId) ?: return
        val repository = ShopRepository(dao)
        val billId = repository.createBill(null)
        repository.addProduct(billId, product.id)
        val item = dao.billItemForProduct(billId, product.id)
        if (item != null && quantity > 1) {
            repository.setQuantity(item.id, billId, quantity)
        }
        context.startActivity(openBillIntent(billId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private suspend fun Context.databaseProducts(): List<ProductEntity> =
    (applicationContext as HisabKitabApplication).database.dao().allProducts()

private fun openMainIntent(action: String? = null): Intent =
    Intent()
        .setClassName("com.amg.hisabkitab", MainActivity::class.java.name)
        .apply { if (action != null) setAction(action) }

private fun openBillIntent(billId: Long): Intent =
    openMainIntent(AppLaunchActions.OpenBill)
        .putExtra(MainActivity.EXTRA_OPEN_BILL_ID, billId)

private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other

@Composable
private fun WidgetCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(paper)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
        content = content
    )
}

private typealias ColumnScope = androidx.glance.layout.ColumnScope

private fun titleStyle(color: ColorProvider = ink): TextStyle =
    TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)

private fun bodyStyle(color: ColorProvider): TextStyle =
    TextStyle(color = color, fontSize = 13.sp)

private fun captionStyle(): TextStyle =
    TextStyle(color = muted, fontSize = 11.sp)
