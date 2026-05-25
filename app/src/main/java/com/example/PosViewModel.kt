package com.example

import android.app.Application
import android.widget.Toast
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar

val posJson = Json { 
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true 
}

data class Product(
    val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val costPrice: Double = 0.0,
    val icon: ImageVector,
    val category: String,
    val stock: Int = 0,
    val barcode: String? = null,
    val wholesaleTiers: List<WholesaleTier> = emptyList()
)

data class CartItem(
    val product: Product,
    val quantity: Int
)

data class PosState(
    val products: List<Product> = emptyList(),
    val cart: List<CartItem> = emptyList(),
    val isCheckingOut: Boolean = false,
    val checkoutComplete: Boolean = false,
    val selectedCategory: String = "Semua",
    val searchQuery: String = "",
    val showMainMenuSettings: Boolean = false,
    val showPrinterSettings: Boolean = false,
    val showTaxSettings: Boolean = false,
    val showStoreInfoSettings: Boolean = false,
    val isManagingProducts: Boolean = false,
    val isManagingExpenses: Boolean = false,
    val showBackupSettings: Boolean = false,
    val showSalesVisualization: Boolean = false,
    val showPaymentSelection: Boolean = false,
    val selectedPaymentMethod: String = "Tunai",
    val storeName: String = "Toko Kasir",
    val storeAddress: String = "Jl. Contoh No. 123",
    val storePhone: String = "081234567890",
    val storeLogoUrl: String = "",
    val receiptFooter: String = "Terima kasih atas kunjungan Anda",
    val lastOrderCart: List<CartItem> = emptyList(),
    val lastOrderTotal: Double = 0.0,
    val lastOrderDiscount: Double = 0.0,
    val lastOrderTax: Double = 0.0,
    val lastOrderPaymentMethod: String = "Tunai",
    val lastOrderPaymentAmount: Double = 0.0,
    val lastOrderChange: Double = 0.0,
    val lastOrderTimestamp: Long = 0L,
    val isTaxEnabled: Boolean = true,
    val discountPercentage: Double = 0.0,
    val taxPercentage: Double = 11.0,

    val sales: List<SaleEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),

    // Pre-calculated totals to avoid UI thread overhead
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,

    // Receipt custom layout settings
    val receiptWidth: Int = 32, // 32 chars (58mm) or 48 chars (80mm)
    val headerFontSize: String = "Besar", // options: "Normal", "Besar", "Sangat Besar"
    val headerBold: Boolean = true,
    val selectedPrinterAddress: String? = null,
    val autoPrintEnabled: Boolean = true,
    val printStoreAddress: Boolean = true,
    val printStorePhone: Boolean = true,
    val printStoreLogo: Boolean = true,
    val spacingAfterReceipt: Int = 3, // spacing line count 1 to 5
    val receiptLogoSize: Int = 100, // percentage 20 to 100

    // Drive Backup status
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val isPrinting: Boolean = false,

    // Optimized fields
    val categories: List<String> = listOf("Semua"),
    val filteredProducts: List<Product> = emptyList()
)

@kotlinx.serialization.Serializable
data class BackupPayload(
    val products: List<ProductEntity>,
    val settings: List<SettingEntity>
)

class PosViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.posDao()

    private val _state = MutableStateFlow(PosState())
    val state: StateFlow<PosState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Initial data loading in background
            try {
                withContext(Dispatchers.IO) {
                    loadFromDatabase()
                }
            } catch (e: Throwable) {
                android.util.Log.e("PosViewModel", "Failed to load database in init", e)
            }
            
            // Launch observers in parallel with robust error handlers
            launch(Dispatchers.IO) {
                try {
                    observeProducts()
                } catch (e: Throwable) {
                    android.util.Log.e("PosViewModel", "Failed to observe products", e)
                }
            }
            launch(Dispatchers.IO) {
                try {
                    observeSettings()
                } catch (e: Throwable) {
                    android.util.Log.e("PosViewModel", "Failed to observe settings", e)
                }
            }
            launch(Dispatchers.IO) {
                try {
                    observeSales()
                } catch (e: Throwable) {
                    android.util.Log.e("PosViewModel", "Failed to observe sales", e)
                }
            }
            launch(Dispatchers.IO) {
                try {
                    observeExpenses()
                } catch (e: Throwable) {
                    android.util.Log.e("PosViewModel", "Failed to observe expenses", e)
                }
            }
        }
    }

    private suspend fun loadFromDatabase() {
        // Load settings first
        val dbSettings = dao.getAllSettings()
        applySettingsToState(dbSettings)

        // Then load products
        val dbProducts = dao.getAllProducts()
        if (dbProducts.isEmpty()) {
            val dummyProducts = listOf(
                ProductEntity("p1", "Espresso", "Kuat dan pekat", 25000.0, 15000.0, "LocalCafe", "Kopi"),
                ProductEntity("p2", "Cappuccino", "Kenikmatan klasik", 35000.0, 20000.0, "Coffee", "Kopi"),
                ProductEntity("p3", "Latte", "Kopi susu lembut", 40000.0, 25000.0, "LocalCafe", "Kopi"),
                ProductEntity("p4", "Croissant", "Renyah dan gurih", 30000.0, 18000.0, "BakeryDining", "Roti"),
                ProductEntity("p5", "Bagel", "Bagel krim keju", 25000.0, 15000.0, "BreakfastDining", "Roti"),
                ProductEntity("p6", "Roti Alpukat", "Awal yang sehat", 55000.0, 30000.0, "Restaurant", "Makanan"),
                ProductEntity("p7", "Smoothie", "Ledakan beri", 45000.0, 28000.0, "Blender", "Minuman"),
                ProductEntity("p8", "Muffin", "Kue bluberi", 28000.0, 16000.0, "BakeryDining", "Roti"),
                ProductEntity("p9", "Sandwich", "Kalkun dan Keju", 65000.0, 40000.0, "LunchDining", "Makanan"),
                ProductEntity("p10", "Es Teh", "Lemon segar", 20000.0, 5000.0, "LocalDrink", "Minuman"),
                ProductEntity("p11", "Macaron", "Manis khas Prancis", 20000.0, 12000.0, "Cookie", "Roti"),
                ProductEntity("p12", "Salad", "Sayuran segar", 70000.0, 45000.0, "Eco", "Makanan")
            )
            dao.insertProducts(dummyProducts)
        }

        // Initialize dummy sales if empty
        dao.deleteDummySales()
        
        // Auto-delete transactions older than 2 months (60 days)
        val twoMonthsAgo = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000L
        dao.deleteSalesOlderThan(twoMonthsAgo)
        dao.deleteExpensesOlderThan(twoMonthsAgo)
    }

    private suspend fun observeProducts() {
        dao.getAllProductsFlow().collect { entities ->
            withContext(Dispatchers.Default) {
                val productsList = entities.map { entity ->
                    Product(
                        id = entity.id,
                        name = entity.name,
                        description = entity.description,
                        price = entity.price,
                        costPrice = entity.costPrice,
                        icon = stringToImageVector(entity.iconName),
                        category = entity.category,
                        stock = entity.stock,
                        barcode = entity.barcode,
                        wholesaleTiers = try {
                            entity.wholesaleTiersJson?.let { posJson.decodeFromString<List<WholesaleTier>>(it) } ?: emptyList()
                        } catch (e: Exception) { emptyList() }
                    )
                }
                
                val newCategories = listOf("Semua") + productsList.map { it.category }.distinct()
                
                _state.update { current ->
                    val newFiltered = if (current.selectedCategory == "Semua") {
                        productsList
                    } else {
                        productsList.filter { it.category == current.selectedCategory }
                    }.filter { it.name.contains(current.searchQuery, ignoreCase = true) }
                    
                    // Recalculate totals based on new product data
                    val intermediateCart = current.cart.map { cartItem ->
                        val updatedProduct = productsList.find { it.id == cartItem.product.id }
                        if (updatedProduct != null) cartItem.copy(product = updatedProduct) else cartItem
                    }
                    val adjustedCart = applyWholesalePrices(intermediateCart)
                    
                    val subtotal = adjustedCart.sumOf { it.product.price * it.quantity }
                    val discount = subtotal * (current.discountPercentage / 100.0)
                    val tax = if (current.isTaxEnabled) (subtotal - discount) * (current.taxPercentage / 100.0) else 0.0
                    val total = subtotal - discount + tax

                    current.copy(
                        products = productsList,
                        categories = newCategories,
                        filteredProducts = newFiltered,
                        cart = adjustedCart,
                        subtotal = subtotal,
                        discount = discount,
                        tax = tax,
                        total = total
                    )
                }
            }
        }
    }

    private suspend fun observeSettings() {
        dao.getAllSettingsFlow().collect { settings ->
            applySettingsToState(settings)
        }
    }

    private suspend fun observeSales() {
        dao.getAllSalesFlow().collect { salesList ->
            _state.update { it.copy(sales = salesList) }
        }
    }

    private suspend fun observeExpenses() {
        dao.getAllExpensesFlow().collect { expensesList ->
            _state.update { it.copy(expenses = expensesList) }
        }
    }

    fun applyWholesalePrices(cart: List<CartItem>): List<CartItem> {
        return cart.map { item ->
            val applicableTier = item.product.wholesaleTiers
                .filter { it.minQuantity <= item.quantity }
                .maxByOrNull { it.minQuantity }
            
            if (applicableTier != null) {
                item.copy(product = item.product.copy(price = applicableTier.price))
            } else {
                item
            }
        }
    }

    private fun updateState(update: (PosState) -> PosState) {
        _state.update { current ->
            val intermediateState = update(current)
            val adjustedCart = applyWholesalePrices(intermediateState.cart)
            val newState = intermediateState.copy(cart = adjustedCart)
            
            // Calculate totals whenever cart, discount, or tax settings change
            val subtotal = newState.cart.sumOf { it.product.price * it.quantity }
            val discount = subtotal * (newState.discountPercentage / 100.0)
            val tax = if (newState.isTaxEnabled) (subtotal - discount) * (newState.taxPercentage / 100.0) else 0.0
            val total = subtotal - discount + tax
            
            newState.copy(
                subtotal = subtotal,
                discount = discount,
                tax = tax,
                total = total
            )
        }
    }

    private fun applySettingsToState(settings: List<SettingEntity>) {
        val map = settings.associate { it.key to it.value }
        updateState { current ->
            current.copy(
                storeName = map["storeName"] ?: current.storeName,
                storeAddress = map["storeAddress"] ?: current.storeAddress,
                storePhone = map["storePhone"] ?: current.storePhone,
                storeLogoUrl = map["storeLogoUrl"] ?: current.storeLogoUrl,
                receiptFooter = map["receiptFooter"] ?: current.receiptFooter,
                discountPercentage = map["discountPercentage"]?.toDoubleOrNull() ?: current.discountPercentage,
                taxPercentage = map["taxPercentage"]?.toDoubleOrNull() ?: current.taxPercentage,
                isTaxEnabled = map["isTaxEnabled"]?.toBoolean() ?: current.isTaxEnabled,
                receiptWidth = map["receiptWidth"]?.toIntOrNull() ?: current.receiptWidth,
                headerFontSize = map["headerFontSize"] ?: current.headerFontSize,
                headerBold = map["headerBold"]?.toBoolean() ?: current.headerBold,
                selectedPrinterAddress = map["selectedPrinterAddress"],
                autoPrintEnabled = map["autoPrintEnabled"]?.toBoolean() ?: current.autoPrintEnabled,
                printStoreAddress = map["printStoreAddress"]?.toBoolean() ?: current.printStoreAddress,
                printStorePhone = map["printStorePhone"]?.toBoolean() ?: current.printStorePhone,
                printStoreLogo = map["printStoreLogo"]?.toBoolean() ?: current.printStoreLogo,
                spacingAfterReceipt = map["spacingAfterReceipt"]?.toIntOrNull() ?: current.spacingAfterReceipt,
                receiptLogoSize = map["receiptLogoSize"]?.toIntOrNull() ?: current.receiptLogoSize
            )
        }
    }

    private fun saveSetting(key: String, value: String) {
        viewModelScope.launch {
            dao.insertSetting(SettingEntity(key, value))
        }
    }

    fun selectCategory(category: String) {
        viewModelScope.launch(Dispatchers.Default) {
            updateState { current ->
                val newFiltered = if (category == "Semua") {
                    current.products
                } else {
                    current.products.filter { it.category == category }
                }.filter { it.name.contains(current.searchQuery, ignoreCase = true) }
                
                current.copy(
                    selectedCategory = category,
                    filteredProducts = newFiltered
                )
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(300) // Debounce 300ms
            updateState { current ->
                val newFiltered = if (current.selectedCategory == "Semua") {
                    current.products
                } else {
                    current.products.filter { it.category == current.selectedCategory }
                }.filter { it.name.contains(query, ignoreCase = true) }
                
                current.copy(filteredProducts = newFiltered)
            }
        }
    }

    fun addToCart(product: Product, quantity: Int = 1) {
        viewModelScope.launch(Dispatchers.Default) {
            updateState { currentState ->
                val existingItem = currentState.cart.find { it.product.id == product.id }
                val newCart = if (existingItem != null) {
                    currentState.cart.map {
                        if (it.product.id == product.id) it.copy(quantity = it.quantity + quantity) else it
                    }
                } else {
                    currentState.cart + CartItem(product, quantity)
                }
                currentState.copy(cart = newCart)
            }
        }
    }

    fun removeFromCart(product: Product) {
        viewModelScope.launch(Dispatchers.Default) {
            updateState { currentState ->
                val existingItem = currentState.cart.find { it.product.id == product.id } ?: return@updateState currentState
                val newCart = if (existingItem.quantity > 1) {
                    currentState.cart.map {
                        if (it.product.id == product.id) it.copy(quantity = it.quantity - 1) else it
                    }
                } else {
                    currentState.cart.filter { it.product.id != product.id }
                }
                currentState.copy(cart = newCart)
            }
        }
    }
    
    fun removeAllFromCart(product: Product) {
        viewModelScope.launch(Dispatchers.Default) {
             updateState { currentState ->
                  val newCart = currentState.cart.filter { it.product.id != product.id }
                  currentState.copy(cart = newCart)
             }
        }
    }

    fun clearCart() {
        viewModelScope.launch(Dispatchers.Default) {
            updateState { it.copy(cart = emptyList()) }
        }
    }

    fun checkout(paymentMethod: String, paymentAmount: Double = 0.0) {
        if (_state.value.cart.isEmpty()) return
        
        val currentState = _state.value
        val currentCart = currentState.cart
        val currentTotal = currentState.total
        val currentDiscount = currentState.discount
        val currentTax = currentState.tax
        val change = if (paymentAmount > 0) (paymentAmount - currentTotal).coerceAtLeast(0.0) else 0.0
        
        viewModelScope.launch {
            try {
                _state.update { it.copy(isCheckingOut = true, showPaymentSelection = false) }
                
                // Save to database
                val saleItems = currentCart.map { 
                    SaleItem(it.product.id, it.product.name, it.product.price, it.product.costPrice, it.quantity)
                }
                val saleTimestamp = System.currentTimeMillis()
                val saleEntity = SaleEntity(
                    timestamp = saleTimestamp,
                    totalAmount = currentTotal,
                    discountAmount = currentDiscount,
                    taxAmount = currentTax,
                    paymentMethod = paymentMethod,
                    itemsJson = posJson.encodeToString(saleItems)
                )
                
                withContext(Dispatchers.IO) {
                    dao.insertSale(saleEntity)
                    currentCart.forEach { item ->
                        dao.decreaseStock(item.product.id, item.quantity)
                    }
                }

                updateState { 
                    it.copy(
                        isCheckingOut = false,
                        checkoutComplete = true,
                        lastOrderCart = currentCart,
                        lastOrderTotal = currentTotal,
                        lastOrderDiscount = currentDiscount,
                        lastOrderTax = currentTax,
                        lastOrderPaymentMethod = paymentMethod,
                        lastOrderPaymentAmount = if (paymentAmount > 0) paymentAmount else currentTotal,
                        lastOrderChange = change,
                        lastOrderTimestamp = saleTimestamp,
                        cart = emptyList()
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("PosViewModel", "Checkout failed", e)
                _state.update { it.copy(isCheckingOut = false) }
            }
        }
    }

    fun togglePaymentSelection(show: Boolean) {
        updateState { it.copy(showPaymentSelection = show) }
    }

    fun dismissCheckoutComplete() {
        updateState { it.copy(checkoutComplete = false) }
    }

    fun toggleMainMenuSettings(show: Boolean) {
        updateState { it.copy(showMainMenuSettings = show) }
    }

    fun toggleTaxSettings(show: Boolean) {
        updateState { it.copy(showTaxSettings = show) }
    }

    fun toggleStoreInfoSettings(show: Boolean) {
        updateState { it.copy(showStoreInfoSettings = show) }
    }

    fun updateTaxSettings(discount: Double, tax: Double, isEnabled: Boolean) {
        updateState { it.copy(discountPercentage = discount, taxPercentage = tax, isTaxEnabled = isEnabled) }
        saveSetting("discountPercentage", discount.toString())
        saveSetting("taxPercentage", tax.toString())
        saveSetting("isTaxEnabled", isEnabled.toString())
    }
    
    fun updateStoreInfo(name: String, address: String, phone: String, logoUrl: String, footer: String, printLogo: Boolean) {
        updateState { it.copy(storeName = name, storeAddress = address, storePhone = phone, storeLogoUrl = logoUrl, receiptFooter = footer, printStoreLogo = printLogo) }
        saveSetting("storeName", name)
        saveSetting("storeAddress", address)
        saveSetting("storePhone", phone)
        saveSetting("storeLogoUrl", logoUrl)
        saveSetting("receiptFooter", footer)
        saveSetting("printStoreLogo", printLogo.toString())
    }

    fun updatePrinterPersistent(address: String?, autoPrint: Boolean) {
        updateState { it.copy(selectedPrinterAddress = address, autoPrintEnabled = autoPrint) }
        saveSetting("selectedPrinterAddress", address ?: "")
        saveSetting("autoPrintEnabled", autoPrint.toString())
    }

    fun toggleProductManagement(show: Boolean) {
        updateState { it.copy(isManagingProducts = show) }
    }

    fun toggleExpenseManagement(show: Boolean) {
        updateState { it.copy(isManagingExpenses = show) }
    }

    fun togglePrinterSettings(show: Boolean) {
        updateState { it.copy(showPrinterSettings = show) }
    }

    fun toggleBackupSettings(show: Boolean) {
        updateState { it.copy(showBackupSettings = show) }
    }

    fun toggleSalesVisualization(show: Boolean) {
        updateState { it.copy(showSalesVisualization = show) }
    }

    fun updateReceiptLayout(
        width: Int,
        headerSize: String,
        headerBold: Boolean,
        printAddress: Boolean,
        printPhone: Boolean,
        spacing: Int,
        printLogo: Boolean,
        logoSize: Int
    ) {
        updateState {
            it.copy(
                receiptWidth = width,
                headerFontSize = headerSize,
                headerBold = headerBold,
                printStoreAddress = printAddress,
                printStorePhone = printPhone,
                spacingAfterReceipt = spacing,
                printStoreLogo = printLogo,
                receiptLogoSize = logoSize
            )
        }
        
        viewModelScope.launch {
            dao.insertSettings(listOf(
                SettingEntity("receiptWidth", width.toString()),
                SettingEntity("headerFontSize", headerSize),
                SettingEntity("headerBold", headerBold.toString()),
                SettingEntity("printStoreAddress", printAddress.toString()),
                SettingEntity("printStorePhone", printPhone.toString()),
                SettingEntity("spacingAfterReceipt", spacing.toString()),
                SettingEntity("printStoreLogo", printLogo.toString()),
                SettingEntity("receiptLogoSize", logoSize.toString())
            ))
        }
    }

    fun addProduct(product: Product) {
        viewModelScope.launch {
            val entity = ProductEntity(
                id = product.id,
                name = product.name,
                description = product.description,
                price = product.price,
                costPrice = product.costPrice,
                iconName = imageVectorToString(product.icon),
                category = product.category,
                stock = product.stock,
                barcode = product.barcode,
                wholesaleTiersJson = posJson.encodeToString(product.wholesaleTiers)
            )
            dao.insertProduct(entity)
        }
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            val entity = ProductEntity(
                id = product.id,
                name = product.name,
                description = product.description,
                price = product.price,
                costPrice = product.costPrice,
                iconName = imageVectorToString(product.icon),
                category = product.category,
                stock = product.stock,
                barcode = product.barcode,
                wholesaleTiersJson = posJson.encodeToString(product.wholesaleTiers)
            )
            dao.insertProduct(entity)
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            dao.deleteProductById(productId)
            _state.update { currentState ->
                val newCart = currentState.cart.filter { it.product.id != productId }
                currentState.copy(cart = newCart)
            }
        }
    }

    fun addExpense(title: String, amount: Double, category: String) {
        viewModelScope.launch {
            val expense = ExpenseEntity(
                title = title,
                amount = amount,
                timestamp = System.currentTimeMillis(),
                category = category
            )
            dao.insertExpense(expense)
        }
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch {
            dao.deleteExpenseById(id)
        }
    }

    fun findProductByBarcode(barcode: String) {
        _state.value.products.find { it.barcode == barcode }?.let {
            addToCart(it)
        }
    }

    fun setPrinting(isPrinting: Boolean) {
        _state.update { it.copy(isPrinting = isPrinting) }
    }

    // Local Backup & Restore Actions
    suspend fun getExportJson(): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val dbProducts = dao.getAllProducts()
            val dbSettings = dao.getAllSettings()
            val payload = BackupPayload(products = dbProducts, settings = dbSettings)
            posJson.encodeToString(payload)
        } catch (e: Exception) {
            android.util.Log.e("PosViewModel", "Export error", e)
            null
        }
    }

    suspend fun importFromJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val payload = posJson.decodeFromString<BackupPayload>(jsonString)
            dao.clearProducts()
            dao.clearSettings()
            dao.insertProducts(payload.products)
            dao.insertSettings(payload.settings)
            true
        } catch (e: Exception) {
            android.util.Log.e("PosViewModel", "Import error", e)
            false
        }
    }

    private val iconMap = mapOf(
        "LocalCafe" to Icons.Filled.LocalCafe,
        "Coffee" to Icons.Filled.Coffee,
        "BakeryDining" to Icons.Filled.BakeryDining,
        "BreakfastDining" to Icons.Filled.BreakfastDining,
        "Restaurant" to Icons.Filled.Restaurant,
        "Blender" to Icons.Filled.Blender,
        "LunchDining" to Icons.Filled.LunchDining,
        "LocalDrink" to Icons.Filled.LocalDrink,
        "Cookie" to Icons.Filled.Cookie,
        "Eco" to Icons.Filled.Eco,
        "LocalMall" to Icons.Filled.LocalMall
    )

    private fun stringToImageVector(name: String): ImageVector {
        return iconMap[name] ?: Icons.Filled.LocalMall
    }

    private fun imageVectorToString(icon: ImageVector): String {
        return iconMap.entries.find { it.value == icon }?.key ?: "LocalMall"
    }
}
