package com.subtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subtracker.BackfillResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun BackfillScreen(
    triggerBackfill: (Int) -> Flow<BackfillResult>,
    onBack: () -> Unit
) {
    var months by remember { mutableStateOf("12") }
    var state by remember { mutableStateOf<BackfillResult?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Color(0xFFF4F3EF))) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            Spacer(Modifier.width(4.dp))
            Text("Geçmiş Tarama", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF111111))
        }

        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Fastmail INBOX'unu tarayıp eksik abonelik ve ödemeleri tespit eder. " +
                "Her tespit Mail Tespitleri ekranında onay bekler.",
                fontSize = 13.sp, color = Color(0xFF77736C)
            )
            OutlinedTextField(
                value = months,
                onValueChange = { if (it.all { c -> c.isDigit() }) months = it },
                label = { Text("Kaç ay geriye git? (1-24)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val n = months.toIntOrNull()?.coerceIn(1, 24) ?: 12
                    scope.launch {
                        triggerBackfill(n).collect { state = it }
                    }
                },
                enabled = state !is BackfillResult.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111))
            ) {
                Text(
                    if (state is BackfillResult.Loading) "Taranıyor..." else "Başlat",
                    fontWeight = FontWeight.SemiBold
                )
            }
            when (val s = state) {
                BackfillResult.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                        Text("Server'da JMAP + DeepSeek çalışıyor, 30-60 sn sürebilir...",
                            fontSize = 12.sp, color = Color(0xFF77736C))
                    }
                }
                is BackfillResult.Done -> Text(
                    "${s.inserted} tespit eklendi — Mail Tespitleri ekranına bak.",
                    color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold
                )
                is BackfillResult.Error -> Text("Hata: ${s.message}", color = Color(0xFFD32F2F))
                null -> {}
            }
        }
    }
}
