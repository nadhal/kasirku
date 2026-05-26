package com.example

import android.os.Bundle
import android.os.Build
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.bluetooth.BluetoothDevice
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<PosViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = java.io.StringWriter()
            val pw = java.io.PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = sw.toString()
            try {
                getSharedPreferences("crash_pref", MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", stackTrace)
                    .commit()
            } catch (e: Exception) {
                // Ignore SharedPreferences failure on crash
            }
            android.util.Log.e("CRITICAL_CRASH", "Uncaught crash on thread ${thread.name}: ${throwable.localizedMessage}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val prefs = getSharedPreferences("crash_pref", MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null)

        setContent {
            MyApplicationTheme {
                if (lastCrash != null) {
                    CrashScreen(
                        stackTrace = lastCrash,
                        onClear = {
                            prefs.edit().remove("last_crash").commit()
                            val intent = intent
                            finish()
                            startActivity(intent)
                        }
                    )
                } else {
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isExpanded = configuration.screenWidthDp >= 840
                    PosScreen(viewModel = viewModel, isExpanded = isExpanded)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(viewModel: PosViewModel, isExpanded: Boolean) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showBottomSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val bluetoothPrinter = remember { BluetoothPrinter(context) }
    DisposableEffect(Unit) {
        onDispose {
            bluetoothPrinter.close()
        }
    }
    var connectedPrinter by remember { mutableStateOf<BluetoothDevice?>(null) }
    val scope = rememberCoroutineScope()

    // Format helper for Rupiah is now in CurrencyFormatter.formatRp

    // Persistent Printer Connection
    LaunchedEffect(state.selectedPrinterAddress) {
        if (state.selectedPrinterAddress != null && connectedPrinter == null) {
            val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(android.Manifest.permission.BLUETOOTH)
            }
            
            val hasBluetoothPermission = permissions.all { perm ->
                context.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (hasBluetoothPermission) {
                try {
                    val devices = withContext(Dispatchers.IO) {
                        bluetoothPrinter.getPairedDevices()
                    }
                    val targetDevice = devices.find { it.address == state.selectedPrinterAddress }
                    if (targetDevice != null) {
                        connectedPrinter = targetDevice
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error auto-connecting printer", e)
                }
            }
        }
    }

    // Auto Print Logic - More robust
    LaunchedEffect(state.checkoutComplete) {
        if (state.checkoutComplete && state.autoPrintEnabled && connectedPrinter != null && !state.isPrinting) {
            val activePrinter = connectedPrinter
            if (activePrinter != null) {
                // Short delay to let the UI finish transition from checkout to success state
                kotlinx.coroutines.delay(800)
                if (!state.checkoutComplete) return@LaunchedEffect // Guard against rapid dismiss
                
                viewModel.setPrinting(true)
                try {
                    // Perform all IO steps in a single IO block
                    withContext(Dispatchers.IO) {
                        val connected = bluetoothPrinter.connect(activePrinter)
                        try {
                            if (connected) {
                                bluetoothPrinter.printReceipt(
                                    cartInfo = state.lastOrderCart, 
                                    total = state.lastOrderTotal, 
                                    discount = state.lastOrderDiscount, 
                                    tax = state.lastOrderTax,
                                    paymentMethod = state.lastOrderPaymentMethod,
                                    paymentAmount = state.lastOrderPaymentAmount,
                                    changeAmount = state.lastOrderChange,
                                    storeName = state.storeName,
                                    storeAddress = state.storeAddress,
                                    storePhone = state.storePhone,
                                    receiptFooter = state.receiptFooter,
                                    receiptWidth = state.receiptWidth,
                                    headerFontSize = state.headerFontSize,
                                    headerBold = state.headerBold,
                                    printAddress = state.printStoreAddress,
                                    printPhone = state.printStorePhone,
                                    spacingAfterReceipt = state.spacingAfterReceipt,
                                    storeLogoUrl = state.storeLogoUrl,
                                    printLogo = state.printStoreLogo,
                                    logoSize = state.receiptLogoSize,
                                    orderTimestamp = state.lastOrderTimestamp
                                )
                            }
                        } finally {
                            bluetoothPrinter.disconnect()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Auto print error", e)
                } finally {
                    viewModel.setPrinting(false)
                }
            }
        }
    }



    var selectedDestination by remember { mutableIntStateOf(0) }

    val destinations = listOf(
        "Transaksi" to Icons.Filled.ShoppingCart,
        "Produk" to Icons.Filled.Inventory,
        "Laporan" to Icons.Filled.Insights
    )

    Row(modifier = Modifier.fillMaxSize()) {
        if (isExpanded) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                header = {
                    IconButton(onClick = { /* menu */ }) {
                        Icon(Icons.Filled.Menu, contentDescription = null)
                    }
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                destinations.forEachIndexed { index, (label, icon) ->
                    NavigationRailItem(
                        selected = selectedDestination == index,
                        onClick = { 
                            selectedDestination = index
                            if (index == 1) {
                                viewModel.toggleProductManagement(true)
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        alwaysShowLabel = false
                    )
                }
            }
        }

        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
                    // Store Info Header Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.storeLogoUrl.isNotBlank()) {
                            AsyncImage(
                                model = state.storeLogoUrl,
                                contentDescription = "Logo Toko",
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                contentScale = ContentScale.Fit,
                                error = rememberVectorPainter(Icons.Filled.Store),
                                placeholder = rememberVectorPainter(Icons.Filled.Store)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Store,
                                    contentDescription = "Logo Toko",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.storeName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (state.storeAddress.isNotBlank() || state.storePhone.isNotBlank()) {
                                Text(
                                    text = listOfNotNull(
                                        state.storeAddress.takeIf { it.isNotBlank() },
                                        state.storePhone.takeIf { it.isNotBlank() }?.let { "Telp: $it" }
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        var settingsExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { settingsExpanded = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Menu Pengaturan")
                            }
                            DropdownMenu(
                                expanded = settingsExpanded,
                                onDismissRequest = { settingsExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Pengaturan Toko") },
                                    onClick = { settingsExpanded = false; viewModel.toggleStoreInfoSettings(true) },
                                    leadingIcon = { Icon(Icons.Filled.Store, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Pajak & Diskon") },
                                    onClick = { settingsExpanded = false; viewModel.toggleTaxSettings(true) },
                                    leadingIcon = { Icon(Icons.Filled.ReceiptLong, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Pengeluaran") },
                                    onClick = { settingsExpanded = false; viewModel.toggleExpenseManagement(true) },
                                    leadingIcon = { Icon(Icons.Filled.AccountBalanceWallet, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Printer") },
                                    onClick = { settingsExpanded = false; viewModel.togglePrinterSettings(true) },
                                    leadingIcon = { Icon(Icons.Filled.Print, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Backup (File)") },
                                    onClick = { settingsExpanded = false; viewModel.toggleBackupSettings(true) },
                                    leadingIcon = { Icon(Icons.Filled.Backup, null) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text("Cari produk / barcode", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                Row {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { 
                                            viewModel.updateSearchQuery("") 
                                            keyboardController?.hide()
                                        }) {
                                            Icon(Icons.Filled.Clear, contentDescription = "Hapus pencarian")
                                        }
                                    }
                                    IconButton(onClick = {
                                        Toast.makeText(context, "Fitur scanner sedang dalam perbaikan.", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan Barcode")
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            singleLine = true
                        )
                        if (isExpanded && state.cart.isNotEmpty()) {

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(24.dp))
                                    .clickable { viewModel.clearCart() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.RemoveShoppingCart, contentDescription = "Kosongkan Keranjang", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                        var categoryMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(24.dp))
                                    .clickable { categoryMenuExpanded = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Category, contentDescription = "Kategori", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            
                            DropdownMenu(
                                expanded = categoryMenuExpanded,
                                onDismissRequest = { categoryMenuExpanded = false },
                                offset = androidx.compose.ui.unit.DpOffset(x = 0.dp, y = 8.dp),
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            ) {
                                state.categories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = category,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (state.selectedCategory == category) FontWeight.Bold else FontWeight.Normal
                                            ) 
                                        },
                                        onClick = {
                                            viewModel.selectCategory(category)
                                            categoryMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            if (state.selectedCategory == category) {
                                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!isExpanded) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        destinations.forEachIndexed { index, (label, icon) ->
                            NavigationBarItem(
                                selected = selectedDestination == index,
                        onClick = { 
                                    selectedDestination = index
                                    if (index == 1) {
                                        viewModel.toggleProductManagement(true)
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (selectedDestination == 2) {
                Box(Modifier.padding(innerPadding).fillMaxSize()) {
                    ReportingScreen(viewModel)
                }
            } else {
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                if (isExpanded) {
                    Row(Modifier.fillMaxSize()) {
                        ProductSection(
                            state = state,
                            isExpanded = isExpanded,
                            onCategorySelected = viewModel::selectCategory,
                            onProductClick = { p, q -> viewModel.addToCart(p, q) },
                            modifier = Modifier.weight(0.65f)
                        )
                        CartPanel(
                            state = state,
                            onAdd = { viewModel.addToCart(it, 1) },
                            onRemove = viewModel::removeFromCart,
                            onClear = viewModel::clearCart,
                            onCheckout = { viewModel.togglePaymentSelection(true) },
                            modifier = Modifier.weight(0.35f).padding(16.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                        )
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (state.cart.isNotEmpty()) {
                            val cartTotalItems by remember {
                                derivedStateOf { state.cart.sumOf { it.quantity } }
                            }
                            Surface(
                                modifier = Modifier.padding(16.dp).clickable { showBottomSheet = true },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("TOTAL KERANJANG", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(CurrencyFormatter.formatRp(state.total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text("Bayar", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        ProductSection(
                            state = state,
                            isExpanded = isExpanded,
                            onCategorySelected = viewModel::selectCategory,
                            onProductClick = { p, q -> viewModel.addToCart(p, q) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (state.isCheckingOut) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(enabled = false, onClick = {}),
                        color = Color.Black.copy(alpha = 0.3f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(16.dp))
                                    Text("Memproses Pembayaran...")
                                }
                            }
                        }
                    }
                }
            }
            } // Close the Box
        }
    }

    if (showBottomSheet && !isExpanded) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CartPanel(
                state = state,
                onAdd = { viewModel.addToCart(it, 1) },
                onRemove = viewModel::removeFromCart,
                onClear = viewModel::clearCart,
                onCheckout = {
                    scope.launch {
                        sheetState.hide()
                        showBottomSheet = false
                        viewModel.togglePaymentSelection(true)
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
            )
        }
    }

    if (state.checkoutComplete) {
        val context = LocalContext.current

        AlertDialog(
            onDismissRequest = viewModel::dismissCheckoutComplete,
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) },
            title = { Text("Pembayaran Berhasil") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val timestamp = state.lastOrderTimestamp
                    val txId = remember(timestamp) {
                        try {
                            SimpleDateFormat("'S'MMddHHmm", Locale.getDefault()).format(Date(timestamp))
                        } catch (e: Exception) {
                            "S" + timestamp.toString()
                        }
                    }
                    val dateFormatted = remember(timestamp) {
                        try {
                            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
                        } catch (e: Exception) {
                            "-"
                        }
                    }
                    
                    Text(
                        text = "Pesanan telah berhasil diproses.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("No. Struk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(txId, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Waktu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(dateFormatted, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Metode Bayar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(state.lastOrderPaymentMethod, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Tagihan", style = MaterialTheme.typography.bodyMedium)
                                Text(CurrencyFormatter.formatRp(state.lastOrderTotal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Bayar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(CurrencyFormatter.formatRp(state.lastOrderPaymentAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Kembalian", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(CurrencyFormatter.formatRp(state.lastOrderChange), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::dismissCheckoutComplete,
                    modifier = Modifier.testTag("new_order_button")
                ) {
                    Text("Pesanan Baru")
                }
            },
            dismissButton = {
                val activePrinter = connectedPrinter
                if (activePrinter != null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                viewModel.setPrinting(true)
                                try {
                                    val connected = bluetoothPrinter.connect(activePrinter)
                                    if (connected) {
                                        bluetoothPrinter.printReceipt(
                                            cartInfo = state.lastOrderCart, 
                                            total = state.lastOrderTotal, 
                                            discount = state.lastOrderDiscount, 
                                            tax = state.lastOrderTax,
                                            paymentMethod = state.lastOrderPaymentMethod,
                                            paymentAmount = state.lastOrderPaymentAmount,
                                            changeAmount = state.lastOrderChange,
                                            storeName = state.storeName,
                                            storeAddress = state.storeAddress,
                                            storePhone = state.storePhone,
                                            receiptFooter = state.receiptFooter,
                                            receiptWidth = state.receiptWidth,
                                            headerFontSize = state.headerFontSize,
                                            headerBold = state.headerBold,
                                            printAddress = state.printStoreAddress,
                                            printPhone = state.printStorePhone,
                                            spacingAfterReceipt = state.spacingAfterReceipt,
                                            storeLogoUrl = state.storeLogoUrl,
                                            printLogo = state.printStoreLogo,
                                            logoSize = state.receiptLogoSize,
                                            orderTimestamp = state.lastOrderTimestamp
                                        )
                                        Toast.makeText(context, "Struk dicetak", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Gagal terhubung ke printer", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "Error printing receipt", e)
                                    Toast.makeText(context, "Gagal cetak: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    bluetoothPrinter.disconnect()
                                    viewModel.setPrinting(false)
                                }
                            }
                        }, 
                        enabled = !state.isPrinting,
                        modifier = Modifier.testTag("print_receipt_button")
                    ) {
                        if (state.isPrinting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Cetak Struk")
                    }
                }
            }
        )
    }

    if (state.showPrinterSettings) {
        PrinterSettingsDialog(
            state = state,
            onDismiss = { viewModel.togglePrinterSettings(false) },
            printer = bluetoothPrinter,
            connectedPrinter = connectedPrinter,
            onTestPrint = { device ->
                connectedPrinter = device
                // test print
                val sampleCart = listOf(
                    CartItem(Product(id = "0", name = "Test Print", description = "", price = 1000.0, icon = Icons.Filled.Print, category = "Test"), 1)
                )
                try {
                    bluetoothPrinter.printReceipt(
                        cartInfo = sampleCart,
                        total = 1000.0,
                        storeName = state.storeName,
                        storeAddress = state.storeAddress,
                        storePhone = state.storePhone,
                        storeLogoUrl = state.storeLogoUrl,
                        printLogo = state.printStoreLogo,
                        receiptFooter = state.receiptFooter,
                        receiptWidth = state.receiptWidth,
                        headerFontSize = state.headerFontSize,
                        headerBold = state.headerBold,
                        printAddress = state.printStoreAddress,
                        printPhone = state.printStorePhone,
                        spacingAfterReceipt = state.spacingAfterReceipt,
                        logoSize = state.receiptLogoSize
                    )
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error during test receipt print", e)
                }
            },
            onUpdateLayout = { width, size, bold, addr, phone, spacing, printLogo, logoSize ->
                viewModel.updateReceiptLayout(width, size, bold, addr, phone, spacing, printLogo, logoSize)
            },
            onUpdatePrinterPersistent = { addr, autoPrint ->
                viewModel.updatePrinterPersistent(addr, autoPrint)
            }
        )
    }

    if (state.showPaymentSelection) {
        val keyboard = LocalSoftwareKeyboardController.current
        PaymentSelectionDialog(
            total = state.total,
            onDismiss = { viewModel.togglePaymentSelection(false) },
            onSelect = { method, amount -> 
                keyboard?.hide()
                scope.launch {
                    kotlinx.coroutines.delay(100)
                    viewModel.checkout(method, amount)
                }
            }
        )
    }

    if (state.isManagingProducts) {
        ProductManagementDialog(
            state = state,
            onDismiss = { 
                viewModel.toggleProductManagement(false)
                selectedDestination = 0
            },
            onAddProduct = { viewModel.addProduct(it) },
            onUpdateProduct = { viewModel.updateProduct(it) },
            onDeleteProduct = { viewModel.deleteProduct(it) }
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.getExportJson()
                if (json != null) {
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { 
                                it.write(json.toByteArray())
                            }
                        }
                        Toast.makeText(context, "Data berhasil diekspor!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal mengekspor data: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { 
                            it.reader().readText()
                        }
                    }
                    if (json != null) {
                        val success = viewModel.importFromJson(json)
                        if (success) {
                            Toast.makeText(context, "Data berhasil dipulihkan!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Gagal memulihkan data. Format tidak sesuai.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal memulihkan data: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (state.showBackupSettings) {
        BackupRestoreDialog(
            state = state,
            onDismiss = { viewModel.toggleBackupSettings(false) },
            onExport = { 
                viewModel.toggleBackupSettings(false)
                exportLauncher.launch("kasirku_backup.json") 
            },
            onImport = { 
                viewModel.toggleBackupSettings(false)
                importLauncher.launch(arrayOf("application/json")) 
            }
        )
    }

    if (state.showStoreInfoSettings) {
        StoreInfoDialog(
            state = state,
            onDismiss = { viewModel.toggleStoreInfoSettings(false) },
            onSave = { name, address, phone, logoUrl, footer, printLogo -> 
                viewModel.updateStoreInfo(name, address, phone, logoUrl, footer, printLogo) 
            }
        )
    }

    if (state.showTaxSettings) {
        TaxSettingsDialog(
            state = state,
            onDismiss = { viewModel.toggleTaxSettings(false) },
            onSave = { discount, tax, isEnabled -> viewModel.updateTaxSettings(discount, tax, isEnabled) }
        )
    }

    if (state.isManagingExpenses) {
        ExpenseManagementDialog(
            state = state,
            onDismiss = { viewModel.toggleExpenseManagement(false) },
            onAddExpense = { title, amount, category -> viewModel.addExpense(title, amount, category) },
            onDeleteExpense = { id -> viewModel.deleteExpense(id) }
        )
    }
}

@Composable
fun PaymentSelectionDialog(
    total: Double,
    onDismiss: () -> Unit,
    onSelect: (String, Double) -> Unit
) {
    var selectedMethod by remember { mutableStateOf("Tunai") }
    var cashPaid by remember { mutableStateOf("") }
    
    val amountToPay = total
    val change = remember(cashPaid, amountToPay) {
        val paid = cashPaid.toDoubleOrNull() ?: 0.0
        if (paid > amountToPay) paid - amountToPay else 0.0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Pembayaran", 
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Total: ${CurrencyFormatter.formatRp(amountToPay)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentMethodSmallCard(
                        title = "Tunai",
                        icon = Icons.Filled.Payments,
                        isSelected = selectedMethod == "Tunai",
                        onClick = { selectedMethod = "Tunai" },
                        modifier = Modifier.weight(1f)
                    )
                    PaymentMethodSmallCard(
                        title = "QRIS",
                        icon = Icons.Filled.QrCodeScanner,
                        isSelected = selectedMethod == "QRIS",
                        onClick = { selectedMethod = "QRIS" },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                if (selectedMethod == "Tunai") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cashPaid,
                            onValueChange = { if (it.all { char -> char.isDigit() }) cashPaid = it },
                            label = { Text("Uang Dibayar (Rp)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            singleLine = true,
                            prefix = { Text("Rp ") }
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val suggestions = listOf(amountToPay.toInt(), 50000, 100000)
                            suggestions.distinct().sorted().forEach { suggestion ->
                                val label = if (suggestion.toDouble() == amountToPay) "Uang Pas" else CurrencyFormatter.formatRp(suggestion.toDouble())
                                FilterChip(
                                    selected = false,
                                    onClick = { cashPaid = suggestion.toString() },
                                    label = { Text(label) }
                                )
                            }
                        }
                        
                        if (change > 0) {
                            Text(
                                "Kembalian: ${CurrencyFormatter.formatRp(change)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val amount = cashPaid.toDoubleOrNull() ?: amountToPay
                    onSelect(selectedMethod, amount)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMethod != "Tunai" || (cashPaid.toDoubleOrNull() ?: 0.0) >= amountToPay || cashPaid.isEmpty()
            ) {
                Text("Konfirmasi Pembayaran")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Batal")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun PaymentMethodSmallCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PaymentMethodCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = color,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = iconColor
            )
            Spacer(Modifier.width(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = iconColor
            )
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductSection(
    state: PosState,
    isExpanded: Boolean,
    onCategorySelected: (String) -> Unit,
    onProductClick: (Product, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = "Kategori",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Kategori: ${state.selectedCategory}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        val cartQuantities = remember(state.cart) {
            if (state.cart.isEmpty()) emptyMap() 
            else state.cart.associate { it.product.id to it.quantity }
        }

        val columns = if (isExpanded) 3 else 2

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(
                items = state.filteredProducts, 
                key = { it.id },
                contentType = { "product" }
            ) { product ->
                val cartQuantity = cartQuantities[product.id] ?: 0
                ProductCard(
                    product = product,
                    cartQuantity = cartQuantity,
                    onClick = { qty -> onProductClick(product, qty) }
                )
            }
        }
    }
}

@Composable
fun ProductCard(product: Product, cartQuantity: Int, onClick: (Int) -> Unit) {
    var quantity by remember { mutableIntStateOf(1) }

    Surface(
        onClick = { onClick(quantity); quantity = 1 },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().testTag("product_card_${product.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (cartQuantity > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = cartQuantity.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = product.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = CurrencyFormatter.formatRp(product.price),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )

                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    onClick = { onClick(1) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Add, 
                            contentDescription = "Tambah",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CartPanel(
    state: PosState,
    onAdd: (Product) -> Unit,
    onRemove: (Product) -> Unit,
    onClear: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var cartSearchQuery by remember { mutableStateOf("") }
    
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Keranjang", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (state.cart.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        
        if (state.cart.isNotEmpty()) {
            TextField(
                value = cartSearchQuery,
                onValueChange = { cartSearchQuery = it },
                placeholder = { Text("Cari di keranjang...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (cartSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { cartSearchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )
        }
        
        HorizontalDivider()

        if (state.cart.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Keranjang kosong", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val filteredCart = remember(state.cart, cartSearchQuery) {
                if (cartSearchQuery.isBlank()) state.cart
                else state.cart.filter { it.product.name.contains(cartSearchQuery, ignoreCase = true) }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredCart, key = { it.product.id }) { item ->
                    CartItemRow(item, onAdd = { onAdd(item.product) }, onRemove = { onRemove(item.product) })
                    HorizontalDivider()
                }
            }
        }

        Surface(shadowElevation = 8.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", style = MaterialTheme.typography.bodyLarge)
                    Text(CurrencyFormatter.formatRp(state.subtotal), style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(8.dp))
                
                if (state.discountPercentage > 0.0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Diskon (${state.discountPercentage}%)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                        Text("- ${CurrencyFormatter.formatRp(state.discount)}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                
                if (state.isTaxEnabled && state.taxPercentage > 0.0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pajak (${state.taxPercentage}%)", style = MaterialTheme.typography.bodyLarge)
                        Text(CurrencyFormatter.formatRp(state.tax), style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(CurrencyFormatter.formatRp(state.total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        keyboardController?.hide()
                        onCheckout()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = state.cart.isNotEmpty()
                ) {
                    Icon(Icons.Filled.Payments, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Bayar ${CurrencyFormatter.formatRp(state.total)}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun CartItemRow(item: CartItem, onAdd: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(CurrencyFormatter.formatRp(item.product.price), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(24.dp))
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Kurangi", modifier = Modifier.size(16.dp))
            }
            Text("${item.quantity}", modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
            IconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah", modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = CurrencyFormatter.formatRp(item.product.price * item.quantity),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashScreen(stackTrace: String, onClear: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostik Sistem: Error Terdeteksi", color = MaterialTheme.colorScheme.onErrorContainer) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(48.dp)
                    )
                    Column {
                        Text(
                            text = "Aplikasi Berhenti Tak Terduga",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Kami telah mencatat detail kendala di bawah ini untuk membantu memperbaikinya.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Text(
                text = "Stack Trace / Log Error:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    val scrollState = androidx.compose.foundation.rememberScrollState()
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = stackTrace,
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        )
                    }
                }
            }

            Button(
                onClick = onClear,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Hapus Log & Mulai Ulang", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
