package com.subtracker.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subtracker.Subscription
import com.subtracker.SubViewModel
import com.subtracker.formatDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    vm: SubViewModel,
    subId: Long,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(subId == 0L) }
    var sub by remember { mutableStateOf<Subscription?>(null) }

    LaunchedEffect(subId) {
        if (subId != 0L) {
            sub = vm.byId(subId)
            if (sub == null) { onDone(); return@LaunchedEffect }
            loaded = true
        }
    }
    if (!loaded) return

    val s = sub
    var name by remember { mutableStateOf(s?.name ?: "") }
    var amount by remember { mutableStateOf(s?.amount?.toString() ?: "") }
    var currency by remember { mutableStateOf(s?.currency ?: "USD") }
    var cycle by remember { mutableStateOf(s?.cycle ?: "monthly") }
    var nextBilling by remember { mutableStateOf(s?.nextBilling ?: System.currentTimeMillis()) }
    var category by remember { mutableStateOf(s?.category ?: "Other") }
    var notes by remember { mutableStateOf(s?.notes ?: "") }
    var reminderOn by remember { mutableStateOf(s?.reminderOn ?: true) }
    var reminderDays by remember { mutableStateOf(s?.reminderDays?.toString() ?: "1") }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = nextBilling)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { nextBilling = it }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(datePickerState) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onDone) { Icon(Icons.Default.ArrowBack, "Back") }
            Spacer(Modifier.width(4.dp))
            Text(
                if (s != null) "Edit" else "Add subscription",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            if (s != null) {
                IconButton(onClick = {
                    scope.launch { vm.remove(s); onDone() }
                }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                name, { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    amount, { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    currency, { currency = it.uppercase().take(3) },
                    label = { Text("Currency") },
                    modifier = Modifier.width(100.dp),
                    singleLine = true
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Billing cycle",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("weekly", "monthly", "yearly").forEach { c ->
                        FilterChip(
                            selected = cycle == c,
                            onClick = { cycle = c },
                            label = { Text(c.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
                    .padding(vertical = 4.dp),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Next billing date",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(formatDate(nextBilling), fontWeight = FontWeight.SemiBold)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Category",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Streaming", "Software", "Utilities", "Gaming", "Other").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            OutlinedTextField(
                notes, { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().height(90.dp),
                maxLines = 3
            )

            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Column {
                    Text("Reminder", fontWeight = FontWeight.SemiBold)
                    Text(
                        "$reminderDays days before",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(reminderOn, { reminderOn = it })
            }

            if (reminderOn) {
                OutlinedTextField(
                    reminderDays,
                    { if (it.all { c -> c.isDigit() }) reminderDays = it },
                    label = { Text("Days before") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (name.isBlank() || amt <= 0 || currency.length != 3) {
                        Toast.makeText(context, "Name, amount and currency are required", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        vm.save(Subscription(
                            id = s?.id ?: 0,
                            name = name.trim(),
                            amount = amt,
                            currency = currency.uppercase(),
                            cycle = cycle,
                            nextBilling = nextBilling,
                            category = category,
                            notes = notes.trim(),
                            reminderOn = reminderOn,
                            reminderDays = reminderDays.toIntOrNull() ?: 1
                        ))
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) { Text(if (s != null) "Save changes" else "Add subscription", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(24.dp))
        }
    }
}
