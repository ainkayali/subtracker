package com.subtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subtracker.ExchangeRates
import com.subtracker.Subscription
import com.subtracker.formatMoney
import com.subtracker.monthlyAmount
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*

@Composable
fun DashboardScreen(
    subscriptions: List<Subscription>,
    exchangeRates: ExchangeRates,
    budgetLimit: Double,
    onBudgetChange: (Double) -> Unit,
    onRefreshRates: () -> Unit,
    onEdit: (Subscription) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val now = LocalDate.now()
    val sorted = subscriptions.sortedBy { it.nextBilling }
    val totalSpent = sorted.sumOf {
        monthlyAmount(it) * (exchangeRates.ratesToTry[it.currency.uppercase()] ?: 1.0)
    }
    
    val monthName = SimpleDateFormat("MMMM", Locale("tr", "TR")).format(Date()).uppercase(Locale("tr", "TR"))
    val dateTitle = SimpleDateFormat("d MMMM yyyy", Locale("tr", "TR")).format(Date())
    val timeTitle = SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date())

    LazyColumn(
        modifier
            .fillMaxSize()
            .background(Color(0xFFF4F3EF))
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
    ) {
        // 1. Header (Date & Time)
        item {
            HeaderSection(dateTitle, timeTitle, onRefreshRates)
        }

        // 2. Total Summary Card (2nd Screenshot style)
        item {
            TotalSummaryCard(monthName, totalSpent, exchangeRates)
        }

        // 3. Budget Card (1st Screenshot style)
        item {
            BudgetCard(totalSpent, budgetLimit, onBudgetChange)
        }

        // 4. Subscriptions Header
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ABONELİKLER",
                    color = Color(0xFF9A968F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    "${subscriptions.size} / ${subscriptions.size}",
                    color = Color(0xFF9A968F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5. Subscription Cards (1st Screenshot style)
        items(sorted) { sub ->
            EnhancedSubscriptionCard(sub, now) { onEdit(sub) }
        }
        
        if (subscriptions.isEmpty()) {
            item { EmptyState() }
        }
    }
}

@Composable
private fun HeaderSection(date: String, time: String, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {}) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF111111), modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(date, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
            Text(time, fontSize = 16.sp, color = Color(0xFF77736C), fontWeight = FontWeight.Medium)
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Info, null, tint = Color(0xFF111111), modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun TotalSummaryCard(month: String, total: Double, exchangeRates: ExchangeRates) {
    val usdRate = exchangeRates.ratesToTry["USD"] ?: 44.88 // Default from screenshot if not loaded
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "$month · AYI TOPLAMI",
                color = Color(0xFF77736C),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatMoney(total, "TRY"),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF111111)
                )
                Surface(
                    color = Color(0xFFF0EFEB),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "TRY  ↔",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = Color(0xFF77736C)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "1 USD = ${"%.2f".format(usdRate)} ₺",
                fontSize = 13.sp,
                color = Color(0xFF77736C),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BudgetCard(spent: Double, limit: Double, onLimitChange: (Double) -> Unit) {
    val progress = (spent / limit).coerceIn(0.0, 1.0).toFloat()
    val isOverBudget = spent > limit
    val accentColor = if (isOverBudget) Color(0xFFD32F2F) else Color(0xFFE57373)
    
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(limit.toInt().toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bu ay harcama", fontSize = 15.sp, color = Color(0xFF77736C), fontWeight = FontWeight.Medium)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("₺${spent.toInt()} / ", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (isEditing) {
                        BasicTextField(
                            value = editValue,
                            onValueChange = { editValue = it.filter { c -> c.isDigit() } },
                            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(60.dp),
                            singleLine = true,
                            decorationBox = { inner ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    inner()
                                    Text(" ₺", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("✓", Modifier.clickable { onLimitChange(editValue.toDoubleOrNull() ?: limit); isEditing = false }.padding(start = 4.dp), color = Color(0xFF4CAF50))
                                }
                            }
                        )
                    } else {
                        Text("₺${limit.toInt()}", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { isEditing = true })
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Progress Bar
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFFF0F0F0))
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(accentColor))
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem("Yayın", Color(0xFFE57373))
                LegendItem("Yazılım", Color(0xFF4CAF50))
                LegendItem("Diğer", Color(0xFFBDBDBD))
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = Color(0xFF9A968F), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EnhancedSubscriptionCard(sub: Subscription, now: LocalDate, onClick: () -> Unit) {
    val style = brandStyle(sub.name, sub.category)
    val daysUntil = daysUntil(sub.nextBilling, now)
    val statusText = when {
        daysUntil == 0L -> "Bugün ödeniyor"
        daysUntil == 1L -> "Yarın ödeniyor"
        daysUntil < 7 -> "$daysUntil gün sonra"
        else -> formatDateMinimal(sub.nextBilling)
    }
    val statusColor = if (daysUntil <= 0L) Color(0xFFD32F2F) else if (daysUntil < 7) Color(0xFFFFA000) else Color(0xFF9A968F)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left color strip
            Box(Modifier.width(4.dp).fillMaxHeight().background(style.background))
            
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Badge
                Box(
                    modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(style.background),
                    contentAlignment = Alignment.Center
                ) {
                    Text(style.label, color = style.content, fontSize = style.fontSize, fontWeight = FontWeight.Black)
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(Modifier.weight(1f)) {
                    Text(sub.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                        Spacer(Modifier.width(6.dp))
                        Text(statusText, fontSize = 14.sp, color = statusColor, fontWeight = FontWeight.Medium)
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatMoney(sub.amount, sub.currency), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
                    Text("aylık", fontSize = 12.sp, color = Color(0xFF9A968F), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("--- BOŞ ---", color = Color(0xFFCCCCCC))
        Spacer(Modifier.height(8.dp))
        Text("Abonelik eklemek için + tuşuna bas.", color = Color(0xFF999999), textAlign = TextAlign.Center)
    }
}

private data class BrandStyle(
    val label: String, val background: Color, val content: Color = Color.White,
    val round: Boolean = true, val fontSize: androidx.compose.ui.unit.TextUnit = 24.sp
)

private fun brandStyle(name: String, category: String): BrandStyle {
    val normalized = name.lowercase(Locale.US)
    return when {
        "netflix" in normalized -> BrandStyle("N", Color(0xFF111111), Color(0xFFE50914), false, 36.sp)
        "youtube" in normalized -> BrandStyle("▶", Color(0xFFE91E3A), Color.White, false, 24.sp)
        "claude" in normalized -> BrandStyle("Cl", Color(0xFFC26B50), Color.White, false, 28.sp)
        "spotify" in normalized -> BrandStyle("♪", Color(0xFF1DB954), Color.White, false, 30.sp)
        category == "Oyun" -> BrandStyle("G", Color(0xFF6047D7), Color.White, false)
        category == "Yazılım" -> BrandStyle("S", Color(0xFF1F2937), Color.White, false)
        else -> BrandStyle(name.take(1).uppercase(Locale("tr", "TR")), Color(0xFFECE8DF), Color(0xFF111111), false)
    }
}

private fun daysUntil(millis: Long, now: LocalDate): Long {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return ChronoUnit.DAYS.between(now, date)
}

private fun formatDateMinimal(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val monthNames = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
    return "${date.dayOfMonth} ${monthNames[date.monthValue - 1]} ${date.year}"
}
