package com.subtracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as App).db.dao()

    val subscriptions = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.all().filter { it.nextBilling <= now }.forEach { sub ->
                var next = sub.nextBilling
                while (next <= now) next = addCycle(next, sub.cycle)
                dao.update(sub.copy(nextBilling = next))
            }
        }
    }

    fun save(sub: Subscription) = viewModelScope.launch {
        if (sub.id == 0L) dao.insert(sub) else dao.update(sub)
    }

    fun remove(sub: Subscription) = viewModelScope.launch { dao.delete(sub) }

    suspend fun byId(id: Long) = dao.byId(id)
}
