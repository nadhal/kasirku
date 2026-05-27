package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material.icons.filled.Download

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportingScreen(viewModel: PosViewModel) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Ringkasan", "Transaksi", "Analisis Produk")

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).padding(16.dp)) {
            when (selectedTab) {
                0 -> SummaryDashboardEx(state.sales, state.expenses, posJson)
                1 -> TransactionHistoryTab(state.sales, posJson)
                2 -> ProductAnalysisTab(state.sales, state.products, posJson)
            }
        }
    }
}

// ==========================================
// 1. SUMMARY DASHBOARD
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryDashboardEx(sales: List<SaleEntity>, expenses: List<ExpenseEntity>, json: Json) {
    var dateRangeFilter by remember { mutableIntStateOf(7) } // 0=Hari Ini, 7=7 Hari, 30=30 Hari, -1=Semua
    
    var totalRev by remember { mutableDoubleStateOf(0.0) }
    var totalProfit by remember { mutableDoubleStateOf(0.0) }
    var transactionCount by remember { mutableIntStateOf(0) }
    var avgOrderValue by remember { mutableDoubleStateOf(0.0) }
    var topSelling by remember { mutableStateOf("") }
    var busiestHour by remember { mutableStateOf("") }
    
    val filteredSales = remember(sales, dateRangeFilter) {
        if (dateRangeFilter == -1) sales else {
            val cutoff = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -dateRangeFilter)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
            }.timeInMillis
            sales.filter { it.timestamp >= cutoff }
        }
    }

    LaunchedEffect(filteredSales) {
        withContext(Dispatchers.Default) {
            val rev = filteredSales.sumOf { it.totalAmount - it.taxAmount }
            val itemsCountParams = mutableMapOf<String, Int>()
            val hourCounts = mutableMapOf<Int, Int>()
            var cost = 0.0
            
            filteredSales.forEach { sale ->
                val cal = Calendar.getInstance().apply { timeInMillis = sale.timestamp }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                hourCounts[hour] = (hourCounts[hour] ?: 0) + 1
                
                try {
                    val items = json.decodeFromString<List<SaleItem>>(sale.itemsJson)
                    items.forEach { item ->
                        itemsCountParams[item.productName] = (itemsCountParams[item.productName] ?: 0) + item.quantity
                        cost += (item.costPrice * item.quantity)
                    }
                } catch (e: Exception) {}
            }
            
            totalRev = rev
            transactionCount = filteredSales.size
            avgOrderValue = if (transactionCount > 0) rev / transactionCount else 0.0
            totalProfit = rev - cost
            topSelling = itemsCountParams.entries.maxByOrNull { it.value }?.key ?: "-"
            
            val maxHour = hourCounts.entries.maxByOrNull { it.value }?.key
            busiestHour = if (maxHour != null) "$maxHour:00 - ${maxHour+1}:00" else "-"
        }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SegmentedDateFilter(dateRangeFilter) { dateRangeFilter = it }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard("Pendapatan Kotor", CurrencyFormatter.formatRp(totalRev), modifier = Modifier.weight(1f))
            MetricCard("Laba Kotor (Estimasi)", CurrencyFormatter.formatRp(totalProfit), modifier = Modifier.weight(1f))
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard("Jumlah Transaksi", "$transactionCount", modifier = Modifier.weight(1f))
            MetricCard("Rata-rata Transaksi", CurrencyFormatter.formatRp(avgOrderValue), modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard("Produk Terlaris", topSelling, modifier = Modifier.weight(1f))
            MetricCard("Waktu Ramai", busiestHour, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))
        Text("Tren Pendapatan 7 Hari (Canvas)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        DailyRevenueLineChart(sales)
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ==========================================
// 1b. CANVAS LINE CHART
// ==========================================
@Composable
fun DailyRevenueLineChart(sales: List<SaleEntity>) {
    var dataPoints by remember { mutableStateOf<List<Double>>(emptyList()) }
    var maxVal by remember { mutableDoubleStateOf(1.0) }
    
    LaunchedEffect(sales) {
        withContext(Dispatchers.Default) {
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.MILLISECOND, 0)
            
            val last7 = mutableListOf<Double>()
            for (i in 6 downTo 0) {
                val start = Calendar.getInstance().apply { timeInMillis = today.timeInMillis; add(Calendar.DAY_OF_YEAR, -i) }.timeInMillis
                val end = start + 86400000L - 1 // end of day
                val sub = sales.filter { it.timestamp in start..end }.sumOf { it.totalAmount - it.taxAmount }
                last7.add(sub)
            }
            maxVal = last7.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            dataPoints = last7
        }
    }

    val lineColor = MaterialTheme.colorScheme.primary

    Card(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Box(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            if (dataPoints.isEmpty()) {
                Text("Memuat data...", modifier = Modifier.align(Alignment.Center))
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val stepX = w / (dataPoints.size - 1).coerceAtLeast(1)
                    
                    val path = Path()
                    dataPoints.forEachIndexed { i, amount ->
                        val x = i * stepX
                        val y = h - ((amount / maxVal) * h).toFloat()
                        if (i == 0) path.moveTo(x, y)
                        else path.lineTo(x, y)
                    }
                    
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 4.dp.toPx())
                    )
                    
                    // Draw points
                    dataPoints.forEachIndexed { i, amount ->
                        val x = i * stepX
                        val y = h - ((amount / maxVal) * h).toFloat()
                        drawCircle(color = lineColor, radius = 6.dp.toPx(), center = Offset(x, y))
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. TRANSACTION HISTORY TAB
// ==========================================
@Composable
fun TransactionHistoryTab(sales: List<SaleEntity>, json: Json) {
    var dateRangeFilter by remember { mutableIntStateOf(0) } // 0=Hari, 7=7Hari, 30=30Hari, -1=Semua
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredParams = remember(sales, dateRangeFilter, searchQuery) {
        val cutoff = if (dateRangeFilter == -1) 0L else Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -dateRangeFilter)
            set(Calendar.HOUR_OF_DAY, 0)
        }.timeInMillis
        
        sales.filter { it.timestamp >= cutoff }
             .filter {
                 if (searchQuery.isBlank()) true 
                 else {
                     val tf = SimpleDateFormat("'S'MMddHHmm", Locale.getDefault())
                     val formattedNo = tf.format(Date(it.timestamp))
                     val idMatch = it.id.toString().contains(searchQuery) || formattedNo.contains(searchQuery, ignoreCase = true)
                     val items = try { json.decodeFromString<List<SaleItem>>(it.itemsJson) } catch(e:Exception) { emptyList() }
                     idMatch || items.any { item -> item.productName.contains(searchQuery, ignoreCase = true) }
                 }
             }
             .sortedByDescending { it.timestamp }
    }

    val totalRevenue = remember(filteredParams) {
        filteredParams.sumOf { it.totalAmount - it.taxAmount }
    }

    val context = LocalContext.current
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val writer = outputStream.bufferedWriter()
                    writer.append("No. Struk,Tanggal,Metode Pembayaran,Total,Produk\n")
                    val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val tf = SimpleDateFormat("'S'MMddHHmm", Locale.getDefault())
                    filteredParams.forEach { sale ->
                        val dateStr = df.format(Date(sale.timestamp))
                        val formattedNo = tf.format(Date(sale.timestamp))
                        val items = try { json.decodeFromString<List<SaleItem>>(sale.itemsJson) } catch(e:Exception) { emptyList() }
                        val itemsStr = items.joinToString(" | ") { "${it.productName} (x${it.quantity})" }
                        writer.append("${formattedNo},${dateStr},${sale.paymentMethod},${sale.totalAmount},\"${itemsStr}\"\n")
                    }
                    writer.flush()
                }
                Toast.makeText(context, "Berhasil mengekspor CSV", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal mengekspor: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.weight(1f)) {
                SegmentedDateFilter(dateRangeFilter) { dateRangeFilter = it }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { csvExportLauncher.launch("laporan_penjualan.csv") }) {
                Icon(Icons.Filled.Download, contentDescription = "Export CSV", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cari No Transaksi atau Nama Produk...") }
        )
        Spacer(Modifier.height(8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredParams) { sale ->
                TransactionRow(sale, json)
            }
        }
        
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Pendapatan", fontWeight = FontWeight.Bold)
                Text(CurrencyFormatter.formatRp(totalRevenue), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TransactionRow(sale: SaleEntity, json: Json) {
    val df = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault())
    val dateStr = df.format(Date(sale.timestamp))
    var expand by remember { mutableStateOf(false) }
    
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expand = !expand }) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                val tf = SimpleDateFormat("'S'MMddHHmm", Locale.getDefault())
                val formattedNo = tf.format(Date(sale.timestamp))
                Text("$formattedNo - ${sale.paymentMethod}", fontWeight = FontWeight.Bold)
                Text(CurrencyFormatter.formatRp(sale.totalAmount), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            
            if (expand) {
                Spacer(Modifier.height(8.dp))
                val items = try { json.decodeFromString<List<SaleItem>>(sale.itemsJson) } catch(e:Exception) { emptyList() }
                items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("- ${item.productName} (x${item.quantity})", style = MaterialTheme.typography.bodySmall)
                        Text(CurrencyFormatter.formatRp(item.price * item.quantity), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. PRODUCT ANALYSIS TAB
// ==========================================
@Composable
fun ProductAnalysisTab(sales: List<SaleEntity>, products: List<Product>, json: Json) {
    var dateRangeFilter by remember { mutableIntStateOf(30) }
    
    var statList by remember { mutableStateOf<List<ProductStat>>(emptyList()) }

    LaunchedEffect(sales, products, dateRangeFilter) {
        withContext(Dispatchers.Default) {
            val cutoff = if (dateRangeFilter == -1) 0L else Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -dateRangeFilter)
                set(Calendar.HOUR_OF_DAY, 0)
            }.timeInMillis
            
            val filteredSales = sales.filter { it.timestamp >= cutoff }
            
            val qtyMap = mutableMapOf<String, Int>()
            val revMap = mutableMapOf<String, Double>()
            
            filteredSales.forEach { sale ->
                val items = try { json.decodeFromString<List<SaleItem>>(sale.itemsJson) } catch(e:Exception) { emptyList() }
                items.forEach {
                    qtyMap[it.productId] = (qtyMap[it.productId] ?: 0) + it.quantity
                    revMap[it.productId] = (revMap[it.productId] ?: 0.0) + (it.price * it.quantity)
                }
            }
            
            val totalRev = revMap.values.sum()
            
            val list = products.map { p ->
                val q = qtyMap[p.id] ?: 0
                val r = revMap[p.id] ?: 0.0
                ProductStat(
                    id = p.id,
                    name = p.name,
                    qtySold = q,
                    revenue = r,
                    percent = if (totalRev > 0) (r / totalRev * 100) else 0.0,
                    stock = p.stock
                )
            }.sortedByDescending { it.qtySold }
            
            statList = list
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SegmentedDateFilter(dateRangeFilter) { dateRangeFilter = it }
        Spacer(Modifier.height(16.dp))
        
        Text("Daftar Performa Produk", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Produk", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
                    Text("Terjual", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("Pndptn", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
            }
            items(statList) { stat ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(2f)) {
                        Text(stat.name, fontWeight = FontWeight.SemiBold)
                        Text("Stok: ${stat.stock}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${stat.qtySold}", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Column(modifier = Modifier.weight(1.5f)) { // giving slightly more width for currency
                        Text(CurrencyFormatter.formatRp(stat.revenue), style = MaterialTheme.typography.bodyMedium)
                        Text(String.format("%.1f%%", stat.percent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

data class ProductStat(
    val id: String,
    val name: String,
    val qtySold: Int,
    val revenue: Double,
    val percent: Double,
    val stock: Int
)

// ==========================================
// UTILS
// ==========================================
@Composable
fun SegmentedDateFilter(selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(0 to "Hari Ini", 7 to "7 H", 30 to "30 H", -1 to "Semua").forEach { (valDay, label) ->
            val isSelected = selected == valDay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onSelect(valDay) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
