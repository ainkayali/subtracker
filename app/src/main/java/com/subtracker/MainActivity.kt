package com.subtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subtracker.ui.AddEditScreen
import com.subtracker.ui.DashboardScreen
import com.subtracker.ui.ListScreen
import com.subtracker.ui.SubTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SubTrackerTheme {
                val vm: SubViewModel = viewModel()
                val subs by vm.subscriptions.collectAsState(emptyList())
                var tab by remember { mutableIntStateOf(0) }
                var editingId by remember { mutableStateOf<Long?>(null) }

                if (editingId != null) {
                    BackHandler { editingId = null }
                    AddEditScreen(vm, editingId!!, onDone = { editingId = null })
                } else {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(containerColor = Color(0xFFF7F1E8)) {
                                NavigationBarItem(
                                    selected = tab == 0,
                                    onClick = { tab = 0 },
                                    icon = { Icon(Icons.Default.Home, null) },
                                    label = { Text("Dashboard") }
                                )
                                NavigationBarItem(
                                    selected = tab == 1,
                                    onClick = { tab = 1 },
                                    icon = { Icon(Icons.Default.List, null) },
                                    label = { Text("Subscriptions") }
                                )
                            }
                        },
                        floatingActionButton = {
                            FloatingActionButton(
                                onClick = { editingId = 0L },
                                containerColor = Color(0xFF445849)
                            ) { Icon(Icons.Default.Add, "Add", tint = Color.White) }
                        },
                        containerColor = Color(0xFFF7F1E8)
                    ) { pad ->
                        when (tab) {
                            0 -> DashboardScreen(subs, Modifier.padding(pad))
                            1 -> ListScreen(subs, { editingId = it.id }, Modifier.padding(pad))
                        }
                    }
                }
            }
        }
    }
}
