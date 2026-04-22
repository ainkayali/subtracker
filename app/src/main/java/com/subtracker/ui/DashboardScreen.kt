package com.subtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subtracker.ExchangeRates
import com.subtracker.Subscription
import com.subtracker.formatMoney
import com.subtracker.monthlyAmount
import com.subtracker.relativeLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    subscriptions: List<Subscription>,
    exchangeRates: ExchangeRates,
    onRefreshRates: () -> Unit,
    onEdit: (Subscription) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val sorted = subscriptions.sortedBy { it.nextBilling }
    val totalTry = sorted.sumOf {
        monthlyAmount(it) * (exchangeRates.ratesToTry[it.currency.uppercase()] ?: 1.0)
    }
    val monthTitle = SimpleDateFormat("MMMM", Locale("tr", "TR"))
        .format(Date())
        .uppercase(Locale("tr", "TR"))
    val dateTitle = SimpleDateFormat("d MMMM yyyy", Locale("tr", "TR")).format(Date())
    val timeTitle = SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date())

    LazyColumn(
        modifier
            .fillMaxSize()
            .background(Color(0xFFF4F3EF))
            .padding(contentPadding)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        item {
            Header(dateTitle = dateTitle, timeTitle = timeTitle, onRefreshRates = onRefreshRates)
        }

        item {
            SummaryCard(
                monthTitle = monthTitle,
                totalTry = totalTry,
                count = sorted.size,
                exchangeRates = exchangeRates
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ABONELİKLER",
                    color = Color(0xFF77736C),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
                Text(
                    "${sorted.size} / ${sorted.size}",
                    color = Color(0xFF77736C),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }

        if (sorted.isEmpty()) {
            item {
                EmptyState()
            }
        } else {
            items(sorted, key = { it.id }) { sub ->
                SubscriptionCard(sub = sub, onClick = { onEdit(sub) })
            }
        }
    }
}

@Composable
private fun Header(dateTitle: String, timeTitle: String, onRefreshRates: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {}) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Color(0xFF1F1F1F), modifier = Modifier.size(34.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                dateTitle,
                color = Color(0xFF171717),
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 30.sp
            )
            Text(
                timeTitle,
                color = Color(0xFF77736C),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
        }
        IconButton(onClick = onRefreshRates) {
            Icon(Icons.Default.Info, "Kurları yenile", tint = Color(0xFF1F1F1F), modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun SummaryCard(monthTitle: String, totalTry: Double, count: Int, exchangeRates: ExchangeRates) {
    val usdRate = exchangeRates.ratesToTry["USD"]
    val rateText = if (usdRate != null) {
        "1 USD = ${"%.4f".format(Locale.US, usdRate)} ₺"
    } else {
        "TCMB kuru bekleniyor"
    }
    val sourceText = listOf(exchangeRates.source, exchangeRates.sourceDate)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            Text(
                "$monthTitle · AYLIK TOPLAMI",
                color = Color(0xFF77736C),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatMoney(totalTry, "TRY"),
                    color = Color(0xFF151515),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 50.sp,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = Color(0xFFF0EFEB),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "TRY  ↔",
                        color = Color(0xFF77736C),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                rateText,
                color = Color(0xFF77736C),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            if (sourceText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    sourceText,
                    color = Color(0xFF9A968F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(
                    "ABONELİKLER",
                    color = Color(0xFF77736C),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
                Text(
                    "$count / $count",
                    color = Color(0xFF77736C),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
private fun SubscriptionCard(sub: Subscription, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandBadge(sub)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    sub.name,
                    color = Color(0xFF161616),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${cycleLabel(sub.cycle)} · ${relativeLabel(sub.nextBilling).lowercase(Locale("tr", "TR"))}",
                    color = Color(0xFF85817B),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                formatMoney(sub.amount, sub.currency),
                color = Color(0xFF161616),
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BrandBadge(sub: Subscription) {
    val style = brandStyle(sub.name, sub.category)
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(if (style.round) CircleShape else RoundedCornerShape(12.dp))
            .background(style.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            style.label,
            color = style.content,
            fontSize = style.fontSize,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("Henüz abonelik yok", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "İlk aboneliğini eklemek için + tuşuna bas.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp
            )
        }
    }
}

private data class BrandStyle(
    val label: String,
    val background: Color,
    val content: Color = Color.White,
    val round: Boolean = true,
    val fontSize: androidx.compose.ui.unit.TextUnit = 24.sp
)

private fun brandStyle(name: String, category: String): BrandStyle {
    val normalized = name.lowercase(Locale.US)
    return when {
        "netflix" in normalized -> BrandStyle("N", Color(0xFFFBF3F1), Color(0xFFE50914), false, 42.sp)
        "spotify" in normalized -> BrandStyle("S", Color(0xFF57D861), Color.White, true, 30.sp)
        "youtube" in normalized -> BrandStyle("▶", Color(0xFFE91E3A), Color.White, true, 26.sp)
        "apple" in normalized -> BrandStyle("♪", Color(0xFFE94D6A), Color.White, false, 34.sp)
        "chatgpt" in normalized || "openai" in normalized -> BrandStyle("◎", Color.White, Color.Black, true, 32.sp)
        "claude" in normalized -> BrandStyle("✳", Color(0xFFF7EFEA), Color(0xFFC26B50), true, 34.sp)
        category == "Oyun" -> BrandStyle("G", Color(0xFF6047D7), Color.White)
        category == "Yazılım" -> BrandStyle("A", Color(0xFF1F2937), Color.White)
        category == "Fatura" -> BrandStyle("₺", Color(0xFF445849), Color.White)
        else -> BrandStyle(name.take(1).uppercase(Locale("tr", "TR")).ifBlank { "?" }, Color(0xFFECE8DF), Color(0xFF1F1F1F))
    }
}

private fun cycleLabel(cycle: String): String = when (cycle) {
    "weekly" -> "Haftalık"
    "yearly" -> "Yıllık"
    else -> "Aylık"
}
