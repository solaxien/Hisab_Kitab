package com.amg.hisabkitab.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amg.hisabkitab.data.repository.ShopRepository
import com.amg.hisabkitab.ui.scanner.BarcodeScannerScreen
import com.amg.hisabkitab.ui.screens.AnalyticsScreen
import com.amg.hisabkitab.ui.screens.BillDetailScreen
import com.amg.hisabkitab.ui.screens.BillsScreen
import com.amg.hisabkitab.ui.screens.HomeScreen
import com.amg.hisabkitab.ui.screens.InventoryScreen
import com.amg.hisabkitab.ui.screens.SettingsScreen
import com.amg.hisabkitab.ui.viewmodel.BillsViewModel
import com.amg.hisabkitab.ui.viewmodel.DashboardViewModel
import com.amg.hisabkitab.ui.viewmodel.InventoryViewModel
import com.amg.hisabkitab.ui.viewmodel.SettingsViewModel
import com.amg.hisabkitab.ui.viewmodel.shopViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun HisabKitabApp(
    repository: ShopRepository,
    settingsMessage: String?,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClearSettingsMessage: () -> Unit
) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val inventory: InventoryViewModel = viewModel(
        factory = shopViewModelFactory<InventoryViewModel>(repository)
    )
    val bills: BillsViewModel = viewModel(
        factory = shopViewModelFactory<BillsViewModel>(repository)
    )
    val dashboard: DashboardViewModel = viewModel(
        factory = shopViewModelFactory<DashboardViewModel>(repository)
    )
    val settings: SettingsViewModel = viewModel(
        factory = shopViewModelFactory<SettingsViewModel>(repository)
    )

    val inventoryState by inventory.state.collectAsState()
    val billsState by bills.state.collectAsState()
    val dashboardState by dashboard.dashboard.collectAsState()
    val analyticsState by dashboard.analytics.collectAsState()
    val productList by bills.products.collectAsState()
    val settingsState by settings.settings.collectAsState()

    fun navigateMain(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun createBill() {
        scope.launch {
            val id = bills.createBill(null)
            nav.navigate("bill/$id")
        }
    }

    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable("home") {
            HomeScreen(
                state = dashboardState,
                onNavigate = ::navigateMain,
                onSettings = { nav.navigate("settings") },
                onCreateBill = ::createBill,
                onOpenBill = { nav.navigate("bill/$it") }
            )
        }
        composable("analytics") {
            AnalyticsScreen(
                state = analyticsState,
                products = productList,
                onNavigate = ::navigateMain,
                onSettings = { nav.navigate("settings") },
                onPeriod = dashboard::setPeriod,
                onCustomRange = dashboard::setCustomRange,
                onRecordLoss = dashboard::recordLoss
            )
        }
        composable("bills") {
            BillsScreen(
                state = billsState,
                onNavigate = ::navigateMain,
                onSettings = { nav.navigate("settings") },
                onQuery = bills::setQuery,
                onFilter = bills::setFilter,
                onCreate = ::createBill,
                onOpen = { nav.navigate("bill/$it") }
            )
        }
        composable("inventory") {
            InventoryScreen(
                state = inventoryState,
                onNavigate = ::navigateMain,
                onSettings = { nav.navigate("settings") },
                onQuery = inventory::setQuery,
                onFilter = inventory::setFilter,
                onSort = inventory::setSort,
                onSave = inventory::save,
                onDelete = inventory::delete,
                onRestock = inventory::restock,
                onScan = { nav.navigate("scanner/0") }
            )
        }
        composable(
            route = "bill/{billId}",
            arguments = listOf(navArgument("billId") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("billId") ?: return@composable
            val bill by bills.bill(id).collectAsState(initial = null)
            BillDetailScreen(
                bill = bill,
                products = productList,
                shortages = billsState.paymentShortages,
                onBack = { nav.popBackStack() },
                onAddProduct = { bills.addProduct(id, it) },
                onScan = { nav.navigate("scanner/$id") },
                onCustomerChange = { bills.updateCustomer(id, it) },
                onQuantity = { item, quantity -> bills.setQuantity(item, id, quantity) },
                onRemove = bills::removeItem,
                onCancelBill = { bills.cancel(id); nav.popBackStack() },
                onPay = { mode -> bills.pay(id, mode) { nav.popBackStack() } },
                onForcePay = { bills.forcePayment { nav.popBackStack() } },
                onDismissShortage = bills::clearPaymentWarning
            )
        }
        composable(
            route = "scanner/{billId}",
            arguments = listOf(navArgument("billId") { type = NavType.LongType })
        ) { entry ->
            val billId = entry.arguments?.getLong("billId") ?: 0
            BarcodeScannerScreen(
                onBack = { nav.popBackStack() },
                onDetected = { barcode ->
                    if (billId == 0L) {
                        inventory.setQuery(barcode)
                        nav.popBackStack()
                    } else {
                        bills.addBarcode(billId, barcode) {
                            nav.popBackStack()
                        }
                    }
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                settings = settingsState,
                message = settingsMessage,
                onBack = { nav.popBackStack() },
                onSave = settings::save,
                onBackup = onBackup,
                onRestore = onRestore,
                onClearMessage = onClearSettingsMessage
            )
        }
    }
}
