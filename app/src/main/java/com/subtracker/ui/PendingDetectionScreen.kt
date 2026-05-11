package com.subtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subtracker.PendingDetection
import com.subtracker.formatMoney

@Composable
fun PendingDetectionScreen(
    items: List<PendingDetection>,
    onAccept: (PendingDetection) -> Unit,
    onReject: (PendingDetection) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F3EF))) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            Spacer(Modifier.width(4.dp))
            Text("Mail Tespitleri", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF111111))
        }

        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Bekleyen tespit yok", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Maillerden tespit edilen abonelikler burada görünür.",
                    color = Color(0xFF77736C), fontSize = 13.sp
                )
            }
            return
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            items(items, key = { it.id }) { pd ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(pd.provider, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF111111))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${formatMoney(pd.amount, pd.currency)} · ${pd.cycle} · ${pd.dateIso}",
                            fontSize = 13.sp, color = Color(0xFF77736C)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when (pd.kind) {
                                "new_sub" -> "Yeni abonelik olarak ekle"
                                "new_payment" -> "Mevcut aboneliğe ödeme ekle"
                                "amount_change" -> "Tutar değişimi — güncelle"
                                "date_correction" -> "Tarih düzeltmesi"
                                else -> pd.kind
                            },
                            fontSize = 12.sp, color = Color(0xFFFFA000), fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onReject(pd) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Reddet") }
                            Button(
                                onClick = { onAccept(pd) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111))
                            ) { Text("Kabul Et") }
                        }
                    }
                }
            }
        }
    }
}
