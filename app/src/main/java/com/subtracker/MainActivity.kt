package com.subtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subtracker.ui.AddEditScreen
import com.subtracker.ui.DashboardScreen
import com.subtracker.ui.SubTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SubTrackerTheme {
                val vm: SubViewModel = viewModel()
                val subs by vm.subscriptions.collectAsState(emptyList())
                var editingId by remember { mutableStateOf<Long?>(null) }

                if (editingId != null) {
                    BackHandler { editingId = null }
                    AddEditScreen(vm, editingId!!, onDone = { editingId = null })
                } else {
                    Scaffold(
                        floatingActionButton = {
                            FloatingActionButton(
                                onClick = { editingId = 0L },
                                containerColor = Color(0xFF111111),
                                contentColor = Color.White
                            ) { Icon(Icons.Default.Add, "Abonelik ekle") }
                        },
                        containerColor = Color(0xFFF4F3EF)
                    ) { pad ->
                        DashboardScreen(
                            subscriptions = subs,
                            onEdit = { editingId = it.id },
                            contentPadding = pad
                        )
                    }
                }
            }
        }
    }
}
