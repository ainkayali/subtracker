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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

    val todaySubs = sorted.filter { isSameDay(it.nextBilling, now) }
    val upcomingSubs = sorted.filter { !isSameDay(it.nextBilling, now) && isWithinDays(it.nextBilling, now, 7) }
    val otherSubs = sorted.filter { !isSameDay(it.nextBilling, now) && !isWithinDays(it.nextBilling, now, 7) }

    LazyColumn(
        modifier
            .fillMaxSize()
            .background(Color(0xFFF4F3EF))
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
    ) {
        item {
            BudgetHeader(
                spent = totalSpent,
                limit = budgetLimit,
                onLimitChange = onBudgetChange,
                onRefresh = onRefreshRates
            )
        }

        if (todaySubs.isNotEmpty()) {
            item { SectionHeader("BUGÜN") }
            items(todaySubs) { sub ->
                CompactSubscriptionCard(sub, "Bugün kesilecek", true) { onEdit(sub) }
            }
        }

        if (upcomingSubs.isNotEmpty()) {
            item { SectionHeader("YAKLAŞAN") }
            items(upcomingSubs) { sub ->
                val days = daysUntil(sub.nextBilling, now)
                CompactSubscriptionCard(sub, "$days gün sonra", false, showProgress = true) { onEdit(sub) }
            }
        }

        if (otherSubs.isNotEmpty()) {
            item { SectionHeader("DİĞER") }
            items(otherSubs) { sub ->
                val dateStr = formatDateMinimal(sub.nextBilling)
                CompactSubscriptionCard(sub, dateStr, false) { onEdit(sub) }
            }
        }
        
        if (subscriptions.isEmpty()) {
            item { EmptyState() }
        }
    }
}

@Composable
private fun BudgetHeader(spent: Double, limit: Double, onLimitChange: (Double) -> Unit, onRefresh: () -> Unit) {
    val isOverBudget = spent > limit
    val progress = (spent / limit).coerceIn(0.0, 1.0)
    val color = if (isOverBudget) Color(0xFFD32F2F) else Color(0xFF111111)
    
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(limit.toInt().toString()) }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Bu ay: ₺${spent.toInt()} / ",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            if (isEditing) {
                BasicTextField(
                    value = editValue,
                    onValueChange = { editValue = it.filter { c -> c.isDigit() } },
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(color),
                    modifier = Modifier.width(IntrinsicSize.Min),
                    singleLine = true,
                    onTextLayout = {},
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            innerTextField()
                            Text(" ₺", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "✓",
                                Modifier.clickable { 
                                    onLimitChange(editValue.toDoubleOrNull() ?: limit)
                                    isEditing = false 
                                },
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                )
            } else {
                Text(
                    text = "₺${limit.toInt()}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.clickable { isEditing = true }
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFF999999))
                }
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Progress Bar
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.1f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.toFloat())
                    .fillMaxHeight()
                    .background(color)
            )
        }
        
        Spacer(Modifier.height(4.dp))
        Text(
            text = "%${(progress * 100).toInt()} tamamlandı ${asciiBar(progress)}",
            fontSize = 12.sp,
            color = if (isOverBudget) color else Color(0xFF666666),
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = "--- $title ---",
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFF999999),
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun CompactSubscriptionCard(
    sub: Subscription, 
    status: String, 
    isAlert: Boolean, 
    showProgress: Boolean = false,
    onClick: () -> Unit
) {
    val statusColor = if (isAlert) Color(0xFFD32F2F) else Color(0xFF666666)
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "[ ${sub.name} ]",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111111)
                )
                Text(
                    formatMoney(sub.amount, sub.currency),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111111)
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isAlert) {
                    Text("⚠️ ", fontSize = 13.sp)
                } else if (showProgress) {
                    Text("⏳ ", fontSize = 13.sp)
                } else {
                    Text("📅 ", fontSize = 13.sp)
                }
                Text(
                    text = status,
                    fontSize = 13.sp,
                    color = statusColor
                )
            }
            if (showProgress) {
                val daysLeft = daysUntil(sub.nextBilling, LocalDate.now())
                val cycleDays = when(sub.cycle) {
                    "weekly" -> 7
                    "yearly" -> 365
                    else -> 30
                }
                val progress = 1f - (daysLeft.toFloat() / cycleDays).coerceIn(0f, 1f)
                Text(
                    text = asciiBar(progress.toDouble()),
                    fontSize = 12.sp,
                    color = Color(0xFFBBBBBB),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("--- BOŞ ---", color = Color(0xFFCCCCCC))
        Spacer(Modifier.height(8.dp))
        Text("Abonelik eklemek için + tuşuna bas.", color = Color(0xFF999999), textAlign = TextAlign.Center)
    }
}

private fun asciiBar(progress: Double): String {
    val totalChars = 8
    val filledChars = (progress * totalChars).toInt().coerceIn(0, totalChars)
    val emptyChars = totalChars - filledChars
    return "▓".repeat(filledChars) + "░".repeat(emptyChars)
}

private fun isSameDay(millis: Long, now: LocalDate): Boolean {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.isEqual(now)
}

private fun isWithinDays(millis: Long, now: LocalDate, days: Int): Boolean {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val diff = ChronoUnit.DAYS.between(now, date)
    return diff in 1..days.toLong()
}

private fun daysUntil(millis: Long, now: LocalDate): Long {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return ChronoUnit.DAYS.between(now, date)
}

private fun formatDateMinimal(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val monthNames = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
    return "${date.dayOfMonth} ${monthNames[date.monthValue - 1]}"
}
