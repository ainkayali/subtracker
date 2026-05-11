package com.subtracker.ui

import androidx.compose.foundation.background
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
import com.subtracker.Subscription
import java.time.LocalDate

@Composable
fun NotificationsScreen(
    subscriptions: List<Subscription>,
    onBack: () -> Unit,
    onEdit: (Subscription) -> Unit
) {
    val now = LocalDate.now()
    val nowMillis = System.currentTimeMillis()
    val upcoming = subscriptions
        .filter { it.reminderOn }
        .filter { sub ->
            val delta = sub.nextBilling - nowMillis
            delta in 0..(sub.reminderDays.toLong().coerceAtLeast(0L) * 86_400_000L)
        }
        .sortedBy { it.nextBilling }

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
            Text("Bildirimler", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF111111))
        }

        if (upcoming.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Yakın hatırlatıcı yok", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF111111))
                Spacer(Modifier.height(6.dp))
                Text(
                    "Hatırlatıcı açık abonelik yaklaştığında burada görünecek.",
                    color = Color(0xFF77736C),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                items(upcoming, key = { it.id }) { sub ->
                    EnhancedSubscriptionCard(sub, now) { onEdit(sub) }
                }
            }
        }
    }
}
