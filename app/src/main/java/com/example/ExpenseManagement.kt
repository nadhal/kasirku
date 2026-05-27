package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseManagementDialog(
    state: PosState,
    onDismiss: () -> Unit,
    onAddExpense: (String, Double, String) -> Unit,
    onDeleteExpense: (Int) -> Unit
) {
    var showAddExpenseForm by remember { mutableStateOf(false) }

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
                        title = { Text("Manajemen Pengeluaran", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Tutup")
                            }
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showAddExpenseForm = true },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Tambah Pengeluaran")
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    val totalExpenses = state.expenses.sumOf { it.amount }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Total Pengeluaran", style = MaterialTheme.typography.labelMedium)
                            Text(CurrencyFormatter.formatRp(totalExpenses), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (state.expenses.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Belum ada catatan pengeluaran", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.expenses) { expense ->
                                ExpenseItemRow(expense, onDeleteExpense)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddExpenseForm) {
        AddExpenseDialog(
            onDismiss = { showAddExpenseForm = false },
            onConfirm = { title, amount, category ->
                onAddExpense(title, amount, category)
                showAddExpenseForm = false
            }
        )
    }
}

@Composable
fun ExpenseItemRow(expense: ExpenseEntity, onDelete: (Int) -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.title, fontWeight = FontWeight.Bold)
                Text(expense.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(dateFormat.format(Date(expense.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(CurrencyFormatter.formatRp(expense.amount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            IconButton(onClick = { onDelete(expense.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AddExpenseDialog(onDismiss: () -> Unit, onConfirm: (String, Double, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Umum") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Pengeluaran") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Keperluan") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all { char -> char.isDigit() }) amount = it },
                    label = { Text("Jumlah (Rp)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Kategori") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountVal = amount.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amountVal > 0) {
                        onConfirm(title, amountVal, category)
                    }
                },
                enabled = title.isNotBlank() && amount.isNotBlank()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
