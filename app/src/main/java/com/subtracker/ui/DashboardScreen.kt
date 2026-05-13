package com.subtracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subtracker.ExchangeRates
import com.subtracker.PaymentLog
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
    recentPayments: List<PaymentLog>,
    pendingCount: Int,
    onBudgetChange: (Double) -> Unit,
    onRefreshRates: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenBackfill: () -> Unit,
    onClearAll: () -> Unit,
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
    val nowMillis = System.currentTimeMillis()
    val upcomingCount = subscriptions.count { sub ->
        sub.reminderOn && (sub.nextBilling - nowMillis) in 0..(sub.reminderDays.toLong().coerceAtLeast(0L) * 86_400_000L)
    }
    val bellBadgeCount = upcomingCount + pendingCount

    var showSettings by remember { mutableStateOf(false) }

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
            HeaderSection(
                dateTitle,
                timeTitle,
                upcomingCount = bellBadgeCount,
                onBellClick = if (pendingCount > 0) onOpenPending else onOpenNotifications,
                onSettingsClick = { showSettings = true }
            )
        }

        // 2. Total Summary Card (2nd Screenshot style)
        item {
            TotalSummaryCard(monthName, totalSpent, exchangeRates)
        }

        // 3. Budget Card (1st Screenshot style)
        item {
            BudgetCard(totalSpent, budgetLimit)
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
                    "${subscriptions.size}",
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

        if (recentPayments.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SON ÖDEMELER",
                        color = Color(0xFF9A968F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "Tümü →",
                        color = Color(0xFF9A968F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOpenHistory() }
                    )
                }
            }
            items(recentPayments.take(3)) { log ->
                val sub = subscriptions.find { it.id == log.subscriptionId }
                PaymentLogRow(log, sub)
            }
        }
        
        if (subscriptions.isEmpty()) {
            item { EmptyState() }
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentLimit = budgetLimit,
            pendingCount = pendingCount,
            onLimitChange = { onBudgetChange(it); showSettings = false },
            onRefreshRates = onRefreshRates,
            onOpenPending = { showSettings = false; onOpenPending() },
            onOpenBackfill = { showSettings = false; onOpenBackfill() },
            onClearAll = onClearAll,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun HeaderSection(
    date: String,
    time: String,
    upcomingCount: Int,
    onBellClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(date, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
            Text(time, fontSize = 16.sp, color = Color(0xFF77736C), fontWeight = FontWeight.Medium)
        }
        Box {
            IconButton(onClick = onBellClick) {
                Icon(Icons.Default.Notifications, contentDescription = "Bildirimler", tint = Color(0xFF111111), modifier = Modifier.size(28.dp))
            }
            if (upcomingCount > 0) {
                Box(
                    Modifier
                        .size(10.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD32F2F))
                )
            }
        }
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Ayarlar", tint = Color(0xFF111111), modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun TotalSummaryCard(month: String, total: Double, exchangeRates: ExchangeRates) {
    val usdRate = exchangeRates.ratesToTry["USD"]
    val canShowUsd = usdRate != null
    var displayCurrency by remember { mutableStateOf("TRY") }
    val displayTotal = if (displayCurrency == "USD" && usdRate != null) total / usdRate else total
    LaunchedEffect(canShowUsd) {
        if (!canShowUsd) displayCurrency = "TRY"
    }
    
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
                    formatMoney(displayTotal, displayCurrency),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF111111)
                )
                Surface(
                    color = Color(0xFFF0EFEB),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.then(
                        if (canShowUsd) {
                            Modifier.clickable {
                                displayCurrency = if (displayCurrency == "TRY") "USD" else "TRY"
                            }
                        } else {
                            Modifier
                        }
                    )
                ) {
                    Text(
                        if (canShowUsd) "$displayCurrency  ↔" else "TRY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (canShowUsd) Color(0xFF77736C) else Color(0xFFBDBDBD)
                    )
                }
            }
            if (usdRate != null) {
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
}

@Composable
private fun BudgetCard(spent: Double, limit: Double) {
    val progress = if (limit > 0) (spent / limit).coerceIn(0.0, 1.0).toFloat() else 1f
    
    val accentColor = when {
        progress <= 0.7f -> Color(0xFF4CAF50) // Green
        progress <= 0.9f -> Color(0xFFFFA000) // Orange
        else -> Color(0xFFD32F2F) // Red
    }

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
                
                Text(
                    text = "₺${spent.toInt()} / ₺${limit.toInt()}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (progress > 0.9f) Color(0xFFD32F2F) else Color.Unspecified
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Progress Bar
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFFF0F0F0))
            ) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(accentColor))
            }
        }
    }
}

@Composable
fun SettingsDialog(
    currentLimit: Double,
    pendingCount: Int,
    onLimitChange: (Double) -> Unit,
    onRefreshRates: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenBackfill: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Tüm abonelikleri sil?") },
            text = { Text("Geri alınamaz. Ödeme geçmişi de cascade silinir.", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = { onClearAll(); showClearConfirm = false; onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Evet, hepsini sil") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("İptal") } },
            containerColor = Color(0xFFF4F3EF)
        )
    }

    var editValue by remember { mutableStateOf(currentLimit.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ayarlar", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Aylık Bütçe Limiti (₺)", fontSize = 14.sp, color = Color(0xFF77736C))
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it.filter { c -> c.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = { onRefreshRates(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Döviz Kurlarını Yenile", color = Color(0xFF111111)) }

                OutlinedButton(
                    onClick = onOpenPending,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (pendingCount > 0) "Mail Tespitleri ($pendingCount)" else "Mail Tespitleri",
                        color = Color(0xFF111111)
                    )
                }

                OutlinedButton(
                    onClick = onOpenBackfill,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Geçmiş Mailleri Tara", color = Color(0xFF111111)) }

                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFD32F2F))
                ) { Text("Tüm Abonelikleri Sil", color = Color(0xFFD32F2F)) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLimitChange(editValue.toDoubleOrNull() ?: currentLimit) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111))
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = Color(0xFF77736C))
            }
        },
        containerColor = Color(0xFFF4F3EF)
    )
}

@Composable
internal fun EnhancedSubscriptionCard(sub: Subscription, now: LocalDate, onClick: () -> Unit) {
    val style = brandStyle(sub.name, sub.category)
    val daysUntil = daysUntil(sub.nextBilling, now)
    val statusText = when {
        daysUntil < 0L -> "Gecikmiş (${-daysUntil}g)"
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
internal fun PaymentLogRow(log: PaymentLog, sub: Subscription?) {
    val style = sub?.let { brandStyle(it.name, it.category) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(style?.background ?: Color(0xFFECE8DF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    style?.label ?: "?",
                    color = style?.content ?: Color(0xFF111111),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(sub?.name ?: "Abonelik", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF111111))
                Text("${formatDateMinimal(log.paidAt)} · ${daysAgoLabel(log.paidAt)}", fontSize = 12.sp, color = Color(0xFF9A968F))
            }
            Text(
                "+${formatMoney(log.amount, log.currency)}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF4CAF50)
            )
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

private fun daysAgoLabel(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, LocalDate.now())
    return when {
        days <= 0L -> "Bugün"
        days == 1L -> "Dün"
        else -> "$days gün önce"
    }
}
