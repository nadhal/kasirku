package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsDialog(
    state: PosState,
    onDismiss: () -> Unit,
    printer: BluetoothPrinter,
    connectedPrinter: BluetoothDevice?,
    onTestPrint: suspend (BluetoothDevice) -> Unit,
    onUpdateLayout: (width: Int, headerSize: String, headerBold: Boolean, printAddress: Boolean, printPhone: Boolean, spacing: Int, printLogo: Boolean, logoSize: Int) -> Unit,
    onUpdatePrinterPersistent: (address: String?, autoPrint: Boolean) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var connectingDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var localSpacing by remember(state.spacingAfterReceipt) { mutableIntStateOf(state.spacingAfterReceipt) }
    var localLogoSize by remember(state.receiptLogoSize) { mutableIntStateOf(state.receiptLogoSize) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            hasPermission = true
            scope.launch {
                try {
                    devices = printer.getPairedDevices()
                } catch(e: Exception) {
                    // Ignore
                }
            }
        } else {
            Toast.makeText(context, "Izin Bluetooth diperlukan untuk deteksi printer termal", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        val isAlreadyGranted = permissions.all { perm ->
            context.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (isAlreadyGranted) {
            hasPermission = true
            try {
                devices = printer.getPairedDevices()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Pengaturan Printer & Struk") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Tutup")
                            }
                        }
                    )
                }
            ) { padding ->
                LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(
                                        "KUSTOMISASI LAYOUT STRUK",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    // Width Selector
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Lebar Kertas", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(
                                                selected = state.receiptWidth == 32,
                                                onClick = { onUpdateLayout(32, state.headerFontSize, state.headerBold, state.printStoreAddress, state.printStorePhone, state.spacingAfterReceipt, state.printStoreLogo, state.receiptLogoSize) },
                                                label = { Text("58mm") }
                                            )
                                            FilterChip(
                                                selected = state.receiptWidth == 48,
                                                onClick = { onUpdateLayout(48, state.headerFontSize, state.headerBold, state.printStoreAddress, state.printStorePhone, state.spacingAfterReceipt, state.printStoreLogo, state.receiptLogoSize) },
                                                label = { Text("80mm") }
                                            )
                                        }
                                    }

                                    HorizontalDivider()

                                    // Header size
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Ukuran Nama Toko", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf("Normal", "Besar", "Sangat Besar").forEach { size ->
                                                FilterChip(
                                                    selected = state.headerFontSize == size,
                                                    onClick = { onUpdateLayout(state.receiptWidth, size, state.headerBold, state.printStoreAddress, state.printStorePhone, state.spacingAfterReceipt, state.printStoreLogo, state.receiptLogoSize) },
                                                    label = { Text(size) }
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider()

                                    // Header Bold
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Nama Toko Tebal (Bold)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = state.headerBold,
                                            onCheckedChange = { onUpdateLayout(state.receiptWidth, state.headerFontSize, it, state.printStoreAddress, state.printStorePhone, state.spacingAfterReceipt, state.printStoreLogo, state.receiptLogoSize) }
                                        )
                                    }

                                    HorizontalDivider()

                                    // Print address
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Cetak Alamat Toko", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = state.printStoreAddress,
                                            onCheckedChange = { onUpdateLayout(state.receiptWidth, state.headerFontSize, state.headerBold, it, state.printStorePhone, state.spacingAfterReceipt, state.printStoreLogo, state.receiptLogoSize) }
                                        )
                                    }

                                    HorizontalDivider()

                                    // Print phone
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Cetak No Telepon Toko", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = state.printStorePhone,
                                            onCheckedChange = { onUpdateLayout(state.receiptWidth, state.headerFontSize, state.headerBold, state.printStoreAddress, it, state.spacingAfterReceipt, state.printStoreLogo, state.receiptLogoSize) }
                                        )
                                    }

                                    HorizontalDivider()

                                    // Print store logo setting
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Cetak Logo Toko (Struk)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = state.printStoreLogo,
                                            onCheckedChange = { onUpdateLayout(state.receiptWidth, state.headerFontSize, state.headerBold, state.printStoreAddress, state.printStorePhone, state.spacingAfterReceipt, it, state.receiptLogoSize) }
                                        )
                                    }

                                    HorizontalDivider()

                                    // Spacing
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Text("Spasi Kosong Akhir Struk", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                            Text("${localSpacing} baris", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Slider(
                                            value = localSpacing.toFloat(),
                                            onValueChange = { localSpacing = it.toInt() },
                                            onValueChangeFinished = {
                                                onUpdateLayout(state.receiptWidth, state.headerFontSize, state.headerBold, state.printStoreAddress, state.printStorePhone, localSpacing, state.printStoreLogo, state.receiptLogoSize)
                                            },
                                            valueRange = 1f..5f,
                                            steps = 3
                                        )
                                    }

                                    HorizontalDivider()

                                    // Logo Size
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Text("Ukuran Logo", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                            Text("${localLogoSize}%", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Slider(
                                            value = localLogoSize.toFloat(),
                                            onValueChange = { localLogoSize = it.toInt() },
                                            onValueChangeFinished = {
                                                onUpdateLayout(state.receiptWidth, state.headerFontSize, state.headerBold, state.printStoreAddress, state.printStorePhone, state.spacingAfterReceipt, state.printStoreLogo, localLogoSize)
                                            },
                                            valueRange = 20f..100f,
                                            steps = 7
                                        )
                                    }

                                    HorizontalDivider()

                                    // Auto Print Setting
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Cetak Otomatis", style = MaterialTheme.typography.bodyMedium)
                                            Text("Cetak struk langsung setelah checkout", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = state.autoPrintEnabled,
                                            onCheckedChange = { onUpdatePrinterPersistent(state.selectedPrinterAddress, it) }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "TEST PRINTER & PILIH PRINTER AKTIF",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (!isScanning) {
                                            scope.launch {
                                                isScanning = true
                                                try {
                                                    devices = printer.getPairedDevices()
                                                    kotlinx.coroutines.delay(1500)
                                                    devices = printer.getPairedDevices()
                                                    Toast.makeText(context, "Pencarian selesai. Ditemukan ${devices.size} printer.", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    // Ignore
                                                } finally {
                                                    isScanning = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isScanning && hasPermission,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    if (isScanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Search,
                                            contentDescription = "Cari",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isScanning) "Mencari..." else "Cari Printer",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        if (!hasPermission) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            "Akses Bluetooth diperlukan untuk mendeteksi printer termal di dekat Anda.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = {
                                                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                                                } else {
                                                    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
                                                }
                                                try {
                                                    permissionLauncher.launch(permissions)
                                                } catch (e: Exception) {
                                                    android.util.Log.e("PrinterSettingsDialog", "Error launching permissions", e)
                                                }
                                            }
                                        ) {
                                            Text("Izinkan Akses Bluetooth")
                                        }
                                    }
                                }
                            }
                        } else if (isScanning) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                        Text(
                                            "Sedang memindai perangkat printer...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else if (devices.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("Tidak ada perangkat Bluetooth dipasangkan", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            items(devices) { device ->
                                val isConnected = device.address == connectedPrinter?.address
                                DeviceItem(
                                    device = device,
                                    isConnecting = connectingDevice == device,
                                    isConnected = isConnected,
                                    onClick = {
                                        scope.launch {
                                            connectingDevice = device
                                            try {
                                                val connected = printer.connect(device)
                                                if (connected) {
                                                    Toast.makeText(context, "Terhubung & Disimpan: ${getDeviceName(device)}", Toast.LENGTH_SHORT).show()
                                                    onUpdatePrinterPersistent(device.address, state.autoPrintEnabled)
                                                    onTestPrint(device)
                                                } else {
                                                    Toast.makeText(context, "Gagal terhubung", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("PrinterSettingsDialog", "Error connecting/printing", e)
                                                Toast.makeText(context, "Gagal: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                connectingDevice = null
                                                printer.disconnect()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@SuppressLint("MissingPermission")
private fun getDeviceName(device: BluetoothDevice): String {
    return try {
        device.name ?: device.address
    } catch(e: Exception) {
        device.address
    }
}

@Composable
fun DeviceItem(
    device: BluetoothDevice,
    isConnecting: Boolean,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isConnected) 
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
        else 
            null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth, 
                contentDescription = null, 
                tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(getDeviceName(device), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (isConnected) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = androidx.compose.ui.graphics.Color(0xFF2D9C5E), // Custom polished green
                            contentColor = androidx.compose.ui.graphics.Color.White
                        ) {
                            Text(
                                "READY",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(device.address, style = MaterialTheme.typography.labelSmall)
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (isConnected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle, 
                    contentDescription = "Printer Ready", 
                    tint = androidx.compose.ui.graphics.Color(0xFF2D9C5E)
                )
            } else {
                Icon(Icons.Filled.Print, contentDescription = "Test Print", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
