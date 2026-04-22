package com.subtracker

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    
    private val _budgetLimit = MutableStateFlow(prefs.getFloat("budget_limit", 2000f).toDouble())
    val budgetLimit: StateFlow<Double> = _budgetLimit

    fun setBudgetLimit(limit: Double) {
        prefs.edit().putFloat("budget_limit", limit.toFloat()).apply()
        _budgetLimit.value = limit
    }
}
