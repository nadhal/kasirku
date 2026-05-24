package com.example

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManagementDialog(
    state: PosState,
    onDismiss: () -> Unit,
    onAddProduct: (Product) -> Unit,
    onUpdateProduct: (Product) -> Unit,
    onDeleteProduct: (String) -> Unit
) {
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showForm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
            color = MaterialTheme.colorScheme.background
        ) {
            AnimatedContent(
                targetState = showForm,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ProductFormTransition"
            ) { isFormVisible ->
                if (isFormVisible) {
                    ProductForm(
                        initialProduct = editingProduct,
                        existingCategories = state.products.map { it.category }.distinct(),
                        onSave = { product ->
                            if (editingProduct == null) {
                                onAddProduct(product)
                            } else {
                                onUpdateProduct(product)
                            }
                            showForm = false
                            editingProduct = null
                        },
                        onCancel = {
                            showForm = false
                            editingProduct = null
                        }
                    )
                } else {
                    ProductList(
                        products = state.products,
                        onClose = onDismiss,
                        onAddClick = { showForm = true },
                        onEditClick = {
                            editingProduct = it
                            showForm = true
                        },
                        onDeleteClick = onDeleteProduct
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductList(
    products: List<Product>,
    onClose: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Product) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("Semua") }
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    val categories = remember(products) {
        listOf("Semua") + products.map { it.category }.distinct().sorted()
    }

    val filteredList = products.filter { product ->
        val matchesSearch = product.name.contains(searchQuery, ignoreCase = true) || 
                            product.description.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategoryFilter == "Semua" || product.category == selectedCategoryFilter
        matchesSearch && matchesCategory
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            "Manajemen Produk", 
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Kelola katalog, harga, & ikon produk", 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Tambah")
                    Spacer(Modifier.width(8.dp))
                    Text("Tambah", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
        ) {
            // Search Bar & Filter Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari produk berdasarkan nama...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )

                    Spacer(Modifier.height(10.dp))

                    // Horizontal Category Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(categories) { category ->
                            val isSelected = selectedCategoryFilter == category
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategoryFilter = category },
                                label = { Text(category) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Quick Stats Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Total Produk", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text("${products.size} Item", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Total Kategori", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text("${categories.size - 1} Kategori", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // List of Products
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "Produk tidak ditemukan",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (products.isEmpty()) "Tambahkan produk pertama menggunakan tombol + Tambah di kanan bawah." 
                            else "Coba bersihkan pencarian atau ubah filter kategori Anda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredList, key = { it.id }) { product ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Circle Avatars tinted nicely
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = product.icon,
                                        contentDescription = product.name,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = product.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }

                                        if (product.description.isNotBlank()) {
                                            Text(
                                                text = product.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = CurrencyFormatter.formatRp(product.price),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Surface(
                                            color = if (product.stock <= 5) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) 
                                                    else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Stok: ${product.stock}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = if (product.stock <= 5) MaterialTheme.colorScheme.error 
                                                        else MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.width(8.dp))

                                // Action Buttons
                                Row {
                                    IconButton(
                                        onClick = { onEditClick(product) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { productToDelete = product },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Hapus",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        productToDelete?.let { product ->
            AlertDialog(
                onDismissRequest = { productToDelete = null },
                icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Konfirmasi Hapus") },
                text = {
                    Text("Apakah Anda yakin ingin menghapus produk \"${product.name}\"? Tindakan ini tidak dapat dibatalkan.")
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            onDeleteClick(product.id)
                            productToDelete = null
                        }
                    ) {
                        Text("Hapus", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { productToDelete = null }) {
                        Text("Batal")
                    }
                },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
            )
        }
    }
}

data class SelectableIcon(val name: String, val icon: ImageVector, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductForm(
    initialProduct: Product?,
    existingCategories: List<String>,
    onSave: (Product) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var category by remember { mutableStateOf(initialProduct?.category ?: "") }
    var price by remember { mutableStateOf(initialProduct?.price?.let { if (it == 0.0) "" else it.toInt().toString() } ?: "") }
    var costPrice by remember { mutableStateOf(initialProduct?.costPrice?.let { if (it == 0.0) "" else it.toInt().toString() } ?: "") }
    var stock by remember { mutableStateOf(initialProduct?.stock?.toString() ?: "") }
    var description by remember { mutableStateOf(initialProduct?.description ?: "") }
    var barcode by remember { mutableStateOf(initialProduct?.barcode ?: "") }
    var wholesaleTiers by remember { mutableStateOf(initialProduct?.wholesaleTiers ?: emptyList()) }
    
    val context = LocalContext.current
    val scanner = remember {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            GmsBarcodeScanning.getClient(context, options)
        } catch (e: Throwable) {
            android.util.Log.e("ProductMgmt", "Failed to initialize barcode scanner", e)
            null
        }
    }
    
    // Selectable product icons available
    val presetIcons = remember {
        listOf(
            SelectableIcon("LocalCafe", Icons.Filled.LocalCafe, "Kafe"),
            SelectableIcon("Coffee", Icons.Filled.Coffee, "Kopi"),
            SelectableIcon("BakeryDining", Icons.Filled.BakeryDining, "Roti"),
            SelectableIcon("BreakfastDining", Icons.Filled.BreakfastDining, "Sarapan"),
            SelectableIcon("Restaurant", Icons.Filled.Restaurant, "Makanan"),
            SelectableIcon("Blender", Icons.Filled.Blender, "Jus"),
            SelectableIcon("LunchDining", Icons.Filled.LunchDining, "Makan Siang"),
            SelectableIcon("LocalDrink" , Icons.Filled.LocalDrink, "Minuman"),
            SelectableIcon("Cookie", Icons.Filled.Cookie, "Kue"),
            SelectableIcon("Eco", Icons.Filled.Eco, "Salad"),
            SelectableIcon("LocalMall", Icons.Filled.LocalMall, "Belanja")
        )
    }

    var selectedIconName by remember { 
        mutableStateOf(
            initialProduct?.let { p ->
                val matching = presetIcons.find { getIconName(it.icon) == getIconName(p.icon) }
                matching?.name ?: "LocalMall"
            } ?: "LocalMall"
        )
    }

    val selectedIcon = remember(selectedIconName) {
        presetIcons.find { it.name == selectedIconName }?.icon ?: Icons.Filled.LocalMall
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (initialProduct == null) "Tambah Produk Baru" else "Edit Produk",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header visual preview of the product
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = selectedIcon,
                                contentDescription = "Preview",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = name.ifBlank { "Nama Produk Baru" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (category.isNotBlank()) "Kategori: $category" else "Belum ditentukan kategori",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (price.isNotBlank()) {
                                    val amount = price.toDoubleOrNull() ?: 0.0
                                    CurrencyFormatter.formatRp(amount)
                                } else "Rp 0",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Stok: ${if (stock.isBlank()) "0" else stock}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (stock.toIntOrNull() ?: 0 <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Interactive Icon Selector Group
            item {
                Text(
                    "Pilih Ikon Produk", 
                    style = MaterialTheme.typography.titleSmall, 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    LazyRow(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(presetIcons) { preset ->
                            val isSelected = selectedIconName == preset.name
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedIconName = preset.name }
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .padding(8.dp)
                                    .width(55.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = preset.icon,
                                        contentDescription = preset.label,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Input fields
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Produk") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    Column {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Kategori") },
                            leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        
                        // Category suggestions row helper
                        if (existingCategories.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Rekomendasi Kategori:", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            LazyRow(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(existingCategories) { cat ->
                                    SuggestionChip(
                                        onClick = { category = cat },
                                        label = { Text(cat) },
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Harga Jual") },
                        leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null) },
                        prefix = { Text("Rp ") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = costPrice,
                        onValueChange = { costPrice = it },
                        label = { Text("Harga Modal (Untuk Analisis Laba)") },
                        leadingIcon = { Icon(Icons.Filled.PriceCheck, contentDescription = null) },
                        prefix = { Text("Rp ") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode / SKU") },
                        leadingIcon = { Icon(Icons.Filled.QrCode, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val activeScanner = scanner
                                if (activeScanner != null) {
                                    try {
                                        activeScanner.startScan()
                                            .addOnSuccessListener { result ->
                                                result.rawValue?.let { barcode = it }
                                            }
                                            .addOnFailureListener { e ->
                                                android.util.Log.e("ProductMgmt", "Scanner failed", e)
                                                Toast.makeText(context, "Gagal scan: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Scanner tidak tersedia", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Scanner tidak tersedia di perangkat ini", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = stock,
                        onValueChange = { if (it.all { char -> char.isDigit() }) stock = it },
                        label = { Text("Jumlah Stok") },
                        leadingIcon = { Icon(Icons.Filled.Inventory, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Deskripsi Singkat (Opsional)") },
                        leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            }

            // Wholesale Tiers Editor
            item {
                Text(
                    "Harga Grosir / Bundling",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        wholesaleTiers.forEachIndexed { index, tier ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = tier.minQuantity.toString(),
                                    onValueChange = { newVal ->
                                        val qty = newVal.toIntOrNull() ?: 0
                                        wholesaleTiers = wholesaleTiers.toMutableList().apply {
                                            this[index] = tier.copy(minQuantity = qty)
                                        }
                                    },
                                    label = { Text("Min. Qty") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = tier.price.toInt().toString(),
                                    onValueChange = { newVal ->
                                        val p = newVal.toDoubleOrNull() ?: 0.0
                                        wholesaleTiers = wholesaleTiers.toMutableList().apply {
                                            this[index] = tier.copy(price = p)
                                        }
                                    },
                                    label = { Text("Harga Satuan") },
                                    prefix = { Text("Rp ") },
                                    modifier = Modifier.weight(1.5f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                IconButton(onClick = {
                                    wholesaleTiers = wholesaleTiers.toMutableList().apply { removeAt(index) }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        
                        TextButton(
                            onClick = {
                                wholesaleTiers = wholesaleTiers + WholesaleTier(minQuantity = 2, price = (price.toDoubleOrNull() ?: 0.0) * 0.9)
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Tambah Tingkat Harga")
                        }
                    }
                }
            }

            // Save changes button action
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        keyboardController?.hide()
                        val priceValue = price.toDoubleOrNull() ?: 0.0
                        val costPriceValue = costPrice.toDoubleOrNull() ?: 0.0
                        val stockValue = stock.toIntOrNull() ?: 0
                        val product = Product(
                            id = initialProduct?.id ?: UUID.randomUUID().toString(),
                            name = name.trim().takeIf { it.isNotBlank() } ?: "Produk Baru",
                            category = category.trim().takeIf { it.isNotBlank() } ?: "Umum",
                            price = priceValue,
                            costPrice = costPriceValue,
                            description = description.trim(),
                            icon = selectedIcon,
                            stock = stockValue,
                            barcode = barcode.trim().takeIf { it.isNotBlank() },
                            wholesaleTiers = wholesaleTiers.sortedBy { it.minQuantity }
                        )
                        onSave(product)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = name.isNotBlank() && price.isNotBlank() && price.toDoubleOrNull() != null,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (initialProduct == null) "Tambahkan ke Katalog" else "Simpan Perubahan", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

// Helper to reliably recognize and map Icons string representation
private fun getIconName(icon: ImageVector): String {
    return when (icon) {
        Icons.Filled.LocalCafe -> "LocalCafe"
        Icons.Filled.Coffee -> "Coffee"
        Icons.Filled.BakeryDining -> "BakeryDining"
        Icons.Filled.BreakfastDining -> "BreakfastDining"
        Icons.Filled.Restaurant -> "Restaurant"
        Icons.Filled.Blender -> "Blender"
        Icons.Filled.LunchDining -> "LunchDining"
        Icons.Filled.LocalDrink -> "LocalDrink"
        Icons.Filled.Cookie -> "Cookie"
        Icons.Filled.Eco -> "Eco"
        Icons.Filled.LocalMall -> "LocalMall"
        else -> "LocalMall"
    }
}
