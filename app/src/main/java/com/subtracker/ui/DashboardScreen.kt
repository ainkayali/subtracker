package com.subtracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subtracker.Subscription
import com.subtracker.formatMoney
import com.subtracker.monthlyAmount
import com.subtracker.relativeLabel

@Composable
fun DashboardScreen(
    subscriptions: List<Subscription>,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val upcoming = subscriptions.filter { it.nextBilling in now..(now + 30L * 86_400_000) }
    val totals = subscriptions.groupBy { it.currency }
        .mapValues { (_, list) -> list.sumOf { monthlyAmount(it) } }
    val currency = totals.keys.firstOrNull() ?: "USD"

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        "Monthly total",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        formatMoney(totals[currency] ?: 0.0, currency),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 42.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(
                            "${subscriptions.size} active",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (upcoming.isNotEmpty()) Text(
                            "Next: ${relativeLabel(upcoming.first().nextBilling)}",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (totals.size > 1) {
            item { Text("By currency", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
            items(totals.entries.toList()) { (cur, amt) ->
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(cur, fontWeight = FontWeight.Medium)
                    Text(formatMoney(amt, cur), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { Text("Upcoming", fontWeight = FontWeight.Bold, fontSize = 17.sp) }

        if (upcoming.isEmpty()) {
            item {
                Text(
                    "Nothing in the next 30 days.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            items(upcoming.take(5)) { sub ->
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        Arrangement.SpaceBetween
                    ) {
                        Text(sub.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            relativeLabel(sub.nextBilling),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
