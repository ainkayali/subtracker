package com.subtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subtracker.ui.AddEditScreen
import com.subtracker.ui.BackfillScreen
import com.subtracker.ui.DashboardScreen
import com.subtracker.ui.NotificationsScreen
import com.subtracker.ui.PaymentHistoryScreen
import com.subtracker.ui.PendingDetectionScreen
import com.subtracker.ui.SubTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openScreen = intent?.getStringExtra("open_screen")
        val crashFile = java.io.File(filesDir, "last_crash.txt")
        val crashText = if (crashFile.exists()) crashFile.readText().also { crashFile.delete() } else null
        setContent {
            SubTrackerTheme {
                if (crashText != null) {
                    var dismissed by remember { mutableStateOf(false) }
                    if (!dismissed) {
                        AlertDialog(
                            onDismissRequest = { dismissed = true },
                            title = { Text("Önceki çöküş") },
                            text = {
                                Text(
                                    crashText,
                                    modifier = Modifier
                                        .height(400.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            },
                            confirmButton = {
                                Button(onClick = { dismissed = true }) { Text("Tamam") }
                            }
                        )
                    }
                }
                val vm: SubViewModel = viewModel()
                val subs by vm.subscriptions.collectAsState(emptyList())
                val exchangeRates by vm.exchangeRates.collectAsState()
                val budgetLimit by vm.budgetLimit.collectAsState()
                val recentPayments by vm.recentPayments.collectAsState(emptyList())
                val allPayments by vm.allPayments.collectAsState(emptyList())
                val pendingDetections by vm.pendingDetections.collectAsState()
                val pendingCount by vm.pendingCount.collectAsState()
                var screen by remember {
                    mutableStateOf<Screen>(
                        if (openScreen == "pending_detections") Screen.PendingDetections
                        else Screen.Dashboard
                    )
                }
                val context = LocalContext.current
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                when (val current = screen) {
                    Screen.Dashboard -> {
                        Scaffold(
                            floatingActionButton = {
                                FloatingActionButton(
                                    onClick = { screen = Screen.Edit(0L) },
                                    containerColor = Color(0xFF111111),
                                    contentColor = Color.White
                                ) { Icon(Icons.Default.Add, "Abonelik ekle") }
                            },
                            containerColor = Color(0xFFF4F3EF)
                        ) { pad ->
                            DashboardScreen(
                                subscriptions = subs,
                                exchangeRates = exchangeRates,
                                budgetLimit = budgetLimit,
                                recentPayments = recentPayments,
                                pendingCount = pendingCount,
                                onBudgetChange = vm::setBudgetLimit,
                                onRefreshRates = vm::refreshRates,
                                onOpenNotifications = { screen = Screen.Notifications },
                                onOpenHistory = { screen = Screen.PaymentHistory },
                                onOpenPending = { screen = Screen.PendingDetections },
                                onOpenBackfill = { screen = Screen.Backfill },
                                onClearAll = vm::clearAllSubscriptions,
                                onEdit = { screen = Screen.Edit(it.id) },
                                contentPadding = pad
                            )
                        }
                    }
                    is Screen.Edit -> {
                        BackHandler { screen = Screen.Dashboard }
                        AddEditScreen(vm, current.id, onDone = { screen = Screen.Dashboard })
                    }
                    Screen.Notifications -> {
                        BackHandler { screen = Screen.Dashboard }
                        NotificationsScreen(
                            subscriptions = subs,
                            onBack = { screen = Screen.Dashboard },
                            onEdit = { screen = Screen.Edit(it.id) }
                        )
                    }
                    Screen.PaymentHistory -> {
                        BackHandler { screen = Screen.Dashboard }
                        PaymentHistoryScreen(
                            payments = allPayments,
                            subscriptions = subs,
                            onBack = { screen = Screen.Dashboard }
                        )
                    }
                    Screen.PendingDetections -> {
                        BackHandler { screen = Screen.Dashboard }
                        PendingDetectionScreen(
                            items = pendingDetections,
                            onAccept = vm::acceptDetection,
                            onReject = vm::rejectDetection,
                            onBack = { screen = Screen.Dashboard }
                        )
                    }
                    Screen.Backfill -> {
                        BackHandler { screen = Screen.Dashboard }
                        BackfillScreen(
                            triggerBackfill = vm::triggerBackfill,
                            onBack = { screen = Screen.Dashboard }
                        )
                    }
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Dashboard : Screen
    data class Edit(val id: Long) : Screen
    data object Notifications : Screen
    data object PaymentHistory : Screen
    data object PendingDetections : Screen
    data object Backfill : Screen
}
