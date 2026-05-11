package com.subtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subtracker.PaymentLog
import com.subtracker.Subscription
import com.subtracker.formatMoney
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaymentHistoryScreen(
    payments: List<PaymentLog>,
    subscriptions: List<Subscription>,
    onBack: () -> Unit
) {
    val zoneId = ZoneId.systemDefault()
    val grouped = payments.groupBy {
        YearMonth.from(Instant.ofEpochMilli(it.paidAt).atZone(zoneId).toLocalDate())
    }.toSortedMap(compareByDescending { it })

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F3EF))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            Spacer(Modifier.width(4.dp))
            Text("Ödeme geçmişi", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF111111))
        }

        if (payments.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Henüz ödeme yok", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF111111))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Abonelikler vade geldikçe burada listelenir.",
                    color = Color(0xFF77736C),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                grouped.forEach { (month, logs) ->
                    stickyHeader(key = month.toString()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF4F3EF))
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val monthLabel = month.month.getDisplayName(TextStyle.FULL, Locale("tr", "TR"))
                            Text(
                                "${monthLabel.replaceFirstChar { it.uppercase(Locale("tr", "TR")) }} ${month.year}",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color(0xFF9A968F)
                            )
                            val firstCurrency = logs.first().currency
                            val label = if (logs.all { it.currency == firstCurrency }) {
                                "${formatMoney(logs.sumOf { it.amount }, firstCurrency)} toplam"
                            } else {
                                "${logs.size} ödeme"
                            }
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF77736C))
                        }
                    }
                    items(logs, key = { it.id }) { log ->
                        val sub = subscriptions.find { it.id == log.subscriptionId }
                        PaymentLogRow(log, sub)
                    }
                }
            }
        }
    }
}
