package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.shape.shader.fromComponent
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.column.ColumnChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesVisualizationScreen(
    viewModel: PosViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Ringkasan", "Grafik Penjualan", "Produk Terlaris")
    val json = remember { Json { ignoreUnknownKeys = true; coerceInputValues = true } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grafik Penjualan") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.sales.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Belum ada data penjualan", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    when (selectedTab) {
                        0 -> SummaryDashboard(state.sales, state.expenses, json)
                        1 -> Column {
                            DailySalesChart(state.sales)
                            Spacer(Modifier.height(16.dp))
                            WeeklySalesChart(state.sales)
                        }
                        2 -> TopProductsChart(state.sales, json)
                    }
                }
            }
        }
    }
}

@Composable
fun DailySalesChart(sales: List<SaleEntity>) {
    val modelProducer = remember { ChartEntryModelProducer() }
    
    var dailyData by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }

    LaunchedEffect(sales) {
        withContext(Dispatchers.Default) {
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            
            val last7Days = mutableListOf<Pair<String, Double>>()
            val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
            
            for (i in 6 downTo 0) {
                val date = Calendar.getInstance().apply { timeInMillis = today.timeInMillis }.apply { add(Calendar.DAY_OF_YEAR, -i) }
                val dayKey = dateFormat.format(date.time)
                
                val startOfDay = date.timeInMillis
                val endOfDay = Calendar.getInstance().apply { 
                    timeInMillis = date.timeInMillis
                    add(Calendar.DAY_OF_YEAR, 1)
                    add(Calendar.MILLISECOND, -1)
                }.timeInMillis
                
                val totalForDay = sales.filter { it.timestamp in startOfDay..endOfDay }.sumOf { it.totalAmount - it.taxAmount }
                last7Days.add(dayKey to totalForDay)
            }
            dailyData = last7Days
        }
    }

    LaunchedEffect(dailyData) {
        val entries = dailyData.mapIndexed { index, entry -> 
            entryOf(x = index.toFloat(), y = entry.second.toFloat()) 
        }
        modelProducer.setEntries(entries)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Penjualan 7 Hari Terakhir",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Chart(
                chart = columnChart(),
                chartModelProducer = modelProducer,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = AxisValueFormatter { value, _ ->
                        dailyData.getOrNull(value.toInt())?.first ?: ""
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val total = dailyData.sumOf { it.second }
            Text(
                "Total: ${CurrencyFormatter.formatRp(total)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun WeeklySalesChart(sales: List<SaleEntity>) {
    val modelProducer = remember { ChartEntryModelProducer() }
    
    var weeklyData by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }

    LaunchedEffect(sales) {
        withContext(Dispatchers.Default) {
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            
            today.set(Calendar.DAY_OF_WEEK, today.firstDayOfWeek)
            
            val last4Weeks = mutableListOf<Pair<String, Double>>()
            val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
            
            for (i in 3 downTo 0) {
                val startOfWeek = Calendar.getInstance().apply { timeInMillis = today.timeInMillis }.apply { add(Calendar.WEEK_OF_YEAR, -i) }
                val endOfWeek = Calendar.getInstance().apply { timeInMillis = startOfWeek.timeInMillis }.apply { add(Calendar.DAY_OF_YEAR, 6) }
                
                val weekLabel = "${dateFormat.format(startOfWeek.time)}-${dateFormat.format(endOfWeek.time)}"
                
                val startMillis = startOfWeek.timeInMillis
                val endMillis = Calendar.getInstance().apply { 
                    timeInMillis = endOfWeek.timeInMillis
                    add(Calendar.DAY_OF_YEAR, 1)
                    add(Calendar.MILLISECOND, -1)
                }.timeInMillis
                
                val totalForWeek = sales.filter { it.timestamp in startMillis..endMillis }.sumOf { it.totalAmount - it.taxAmount }
                last4Weeks.add(weekLabel to totalForWeek)
            }
            weeklyData = last4Weeks
        }
    }

    LaunchedEffect(weeklyData) {
        val entries = weeklyData.mapIndexed { index, entry -> 
            entryOf(x = index.toFloat(), y = entry.second.toFloat()) 
        }
        modelProducer.setEntries(entries)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Penjualan 4 Minggu Terakhir",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Chart(
                chart = lineChart(),
                chartModelProducer = modelProducer,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = AxisValueFormatter { value, _ ->
                        weeklyData.getOrNull(value.toInt())?.first ?: ""
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val total = weeklyData.sumOf { it.second }
            Text(
                "Total: ${CurrencyFormatter.formatRp(total)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryDashboard(sales: List<SaleEntity>, expenses: List<ExpenseEntity>, json: Json) {
    var totalRevenue by remember { mutableDoubleStateOf(0.0) }
    var totalExpenses by remember { mutableDoubleStateOf(0.0) }
    var totalProfit by remember { mutableDoubleStateOf(0.0) }
    var methodCounts by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var showDatePicker by remember { mutableStateOf(false) }
    var selectedStartDate by remember { mutableStateOf<Long?>(null) }
    var selectedEndDate by remember { mutableStateOf<Long?>(null) }

    val filteredSales = remember(sales, selectedStartDate, selectedEndDate) {
        sales.filter { sale ->
            val start = selectedStartDate ?: Long.MIN_VALUE
            // Add almost 24 hours to include the whole end day
            val end = selectedEndDate?.plus(86399999L) ?: Long.MAX_VALUE
            sale.timestamp in start..end
        }
    }

    val filteredExpenses = remember(expenses, selectedStartDate, selectedEndDate) {
        expenses.filter { expense ->
            val start = selectedStartDate ?: Long.MIN_VALUE
            val end = selectedEndDate?.plus(86399999L) ?: Long.MAX_VALUE
            expense.timestamp in start..end
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = selectedStartDate,
            initialSelectedEndDateMillis = selectedEndDate
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedStartDate = datePickerState.selectedStartDateMillis
                    selectedEndDate = datePickerState.selectedEndDateMillis
                    showDatePicker = false
                }) {
                    Text("Terapkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    selectedStartDate = null
                    selectedEndDate = null
                    showDatePicker = false 
                }) {
                    Text("Reset")
                }
            }
        ) {
            DateRangePicker(
                state = datePickerState,
                modifier = Modifier.weight(1f),
                title = { Text(text = "Pilih Rentang Tanggal", modifier = Modifier.padding(16.dp)) },
                headline = { 
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(text = "Pilih tanggal mulai & akhir")
                    }
                },
                showModeToggle = false
            )
        }
    }

    LaunchedEffect(filteredSales, filteredExpenses) {
        isLoading = true
        try {
            withContext(Dispatchers.Default) {
                val revenue = filteredSales.sumOf { it.totalAmount - it.taxAmount }
                val exps = filteredExpenses.sumOf { it.amount }
                
                val profit = filteredSales.sumOf { sale ->
                    try {
                        val items = json.decodeFromString<List<SaleItem>>(sale.itemsJson)
                        if (items.isEmpty()) {
                            (sale.totalAmount - sale.taxAmount) * 0.3
                        } else {
                            items.sumOf { (it.price - it.costPrice) * it.quantity }
                        }
                    } catch (e: Exception) {
                        (sale.totalAmount - sale.taxAmount) * 0.3
                    }
                }

                val methodsMap = filteredSales.groupBy { it.paymentMethod }
                    .mapValues { it.value.sumOf { s -> s.totalAmount - s.taxAmount } }
                val sortedMethods = methodsMap.entries
                    .sortedByDescending { it.value }
                    .map { it.key to it.value }

                totalRevenue = revenue
                totalExpenses = exps
                totalProfit = profit
                methodCounts = sortedMethods
            }
        } catch (e: Exception) {
            android.util.Log.e("SalesViz", "Error in SummaryDashboard", e)
        } finally {
            isLoading = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dateLabel = if (selectedStartDate != null && selectedEndDate != null) {
                val format = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                "${format.format(Date(selectedStartDate!!))} - ${format.format(Date(selectedEndDate!!))}"
            } else if (selectedStartDate != null) {
                val format = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                format.format(Date(selectedStartDate!!))
            } else {
                "Semua Waktu"
            }
            
            Text(
                "Rentang: $dateLabel", 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            OutlinedButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pilih Tanggal")
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        
        // Summary Cards
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryCard(
                title = "Total Pendapatan",
                amount = totalRevenue,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Total Pengeluaran",
                amount = totalExpenses,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        }
        
        SummaryCard(
            title = "Estimasi Laba Kotor",
            amount = totalProfit,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth()
        )

        // Payment Methods
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Metode Pembayaran", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                
                methodCounts.forEach { (method, total) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(method)
                        Text(CurrencyFormatter.formatRp(total), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    val progress = if (totalRevenue > 0) (total / totalRevenue).toFloat().coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, amount: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
            Text(CurrencyFormatter.formatRp(amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TopProductsChart(sales: List<SaleEntity>, json: Json) {
    var topProducts by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(sales) {
        isLoading = true
        try {
            withContext(Dispatchers.Default) {
                val productQuantities = mutableMapOf<String, Int>()
                sales.forEach { sale ->
                    try {
                        val items = json.decodeFromString<List<SaleItem>>(sale.itemsJson)
                        items.forEach { item ->
                            productQuantities[item.productName] = (productQuantities[item.productName] ?: 0) + item.quantity
                        }
                    } catch (e: Exception) {}
                }
                topProducts = productQuantities.entries
                    .sortedByDescending { it.value }
                    .take(10)
                    .map { it.key to it.value }
            }
        } catch (e: Exception) {
            android.util.Log.e("SalesViz", "Error in TopProductsChart", e)
        } finally {
            isLoading = false
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("10 Produk Terlaris", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (topProducts.isEmpty()) {
                Text("Belum ada data produk terjual", style = MaterialTheme.typography.bodyMedium)
            } else {
                topProducts.forEach { (name, qty) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("$qty unit", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

