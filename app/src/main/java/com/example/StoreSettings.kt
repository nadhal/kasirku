package com.example

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun MainSettingsDialog(
    state: PosState,
    onDismiss: () -> Unit,
    onMenuSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Menu Pengaturan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsMenuItem(icon = Icons.Filled.Inventory, text = "1. Manajemen Produk & Stok", onClick = { onMenuSelected("produk") })
                SettingsMenuItem(icon = Icons.Filled.AccountBalanceWallet, text = "2. Manajemen Pengeluaran", onClick = { onMenuSelected("pengeluaran") })
                SettingsMenuItem(icon = Icons.Filled.Store, text = "3. Toko (Nama, alamat, dll)", onClick = { onMenuSelected("toko") })
                SettingsMenuItem(icon = Icons.AutoMirrored.Filled.ReceiptLong, text = "4. Pajak & Diskon", onClick = { onMenuSelected("pajak") })
                SettingsMenuItem(icon = Icons.Filled.Print, text = "5. Printer (Bluetooth & Struk)", onClick = { onMenuSelected("printer") })
                SettingsMenuItem(icon = Icons.Filled.BarChart, text = "6. Grafik Penjualan", onClick = { onMenuSelected("grafik") })
                SettingsMenuItem(icon = Icons.Filled.Backup, text = "7. Cadangan & Pemulihan (File)", onClick = { onMenuSelected("backup") })
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun SettingsMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun StoreInfoDialog(
    state: PosState,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Boolean) -> Unit
) {
    var nameStr by remember { mutableStateOf(state.storeName) }
    var addressStr by remember { mutableStateOf(state.storeAddress) }
    var phoneStr by remember { mutableStateOf(state.storePhone) }
    var logoStr by remember { mutableStateOf(state.storeLogoUrl) }
    var footerStr by remember { mutableStateOf(state.receiptFooter) }
    var printLogo by remember { mutableStateOf(state.printStoreLogo) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val localPath = withContext(Dispatchers.IO) {
                    copyUriToInternalStorage(context, uri)
                }
                if (localPath != null) {
                    logoStr = localPath
                    Toast.makeText(context, "Logo berhasil dipilih!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gagal memuat gambar logo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan Toko") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = nameStr,
                    onValueChange = { nameStr = it },
                    label = { Text("Nama Toko") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = addressStr,
                    onValueChange = { addressStr = it },
                    label = { Text("Alamat Toko") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phoneStr,
                    onValueChange = { phoneStr = it },
                    label = { Text("Nomor Telepon") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                
                // Section: Logo Toko
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Logo Toko",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (logoStr.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = logoStr,
                                    contentDescription = "Logo Toko",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Logo Aktif", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Tersimpan di memori lokal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row {
                                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Ubah", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { logoStr = "" }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Tampilkan di Struk", 
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = printLogo,
                                onCheckedChange = { printLogo = it },
                                modifier = Modifier.testTag("print_logo_switch")
                            )
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { imagePickerLauncher.launch("image/*") },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddAPhoto,
                                    contentDescription = "Pilih Gambar",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    "Pilih Gambar Logo Toko",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Format JPG/PNG dari galeri Anda",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = footerStr,
                    onValueChange = { footerStr = it },
                    label = { Text("Pesan Penutup Struk") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(nameStr, addressStr, phoneStr, logoStr, footerStr, printLogo)
                onDismiss()
            }, shape = RoundedCornerShape(12.dp)) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

fun copyUriToInternalStorage(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val fileName = "store_logo_${System.currentTimeMillis()}.png"
        val file = java.io.File(context.filesDir, fileName)
        
        // Cleanup old logo files
        context.filesDir.listFiles()?.forEach { f ->
            if (f.name.startsWith("store_logo_")) {
                f.delete()
            }
        }
        
        val outputStream = java.io.FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun TaxSettingsDialog(
    state: PosState,
    onDismiss: () -> Unit,
    onSave: (Double, Double, Boolean) -> Unit
) {
    var discountStr by remember { mutableStateOf(state.discountPercentage.toString()) }
    var taxStr by remember { mutableStateOf(state.taxPercentage.toString()) }
    var isTaxEnabled by remember { mutableStateOf(state.isTaxEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pengaturan Pajak & Diskon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Aktifkan Pajak", modifier = Modifier.weight(1f))
                    Switch(checked = isTaxEnabled, onCheckedChange = { isTaxEnabled = it })
                }
                OutlinedTextField(
                    value = taxStr,
                    onValueChange = { taxStr = it },
                    label = { Text("Pajak (%)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = isTaxEnabled
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = discountStr,
                    onValueChange = { discountStr = it },
                    label = { Text("Diskon Global (%)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val d = discountStr.replace(",",".").toDoubleOrNull() ?: 0.0
                val t = taxStr.replace(",",".").toDoubleOrNull() ?: 0.0
                onSave(d, t, isTaxEnabled)
                onDismiss()
            }, shape = RoundedCornerShape(12.dp)) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
fun BackupRestoreDialog(
    state: PosState,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text("Cadangan & Pemulihan (File)")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Amankan data produk dan pengaturan toko Anda dengan mengekspornya ke sebuah file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onExport,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ekspor Data ke File")
                    }

                    ElevatedButton(
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pulihkan Data dari File")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}
