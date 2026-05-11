package com.subtracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.subtracker.detect.DetectionPayload
import com.subtracker.detect.DetectionReconciler
import com.subtracker.detect.SubhookClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class SubViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as App).db
    private val dao = db.dao()
    private val paymentDao = db.paymentDao()
    private val pendingDao = db.pendingDao()
    private val rateRepository = ExchangeRateRepository(app)
    private val settingsRepository = SettingsRepository(app)

    val subscriptions = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _exchangeRates = MutableStateFlow(ExchangeRates())
    val exchangeRates: StateFlow<ExchangeRates> = _exchangeRates

    val budgetLimit = settingsRepository.budgetLimit
    val recentPayments = paymentDao.recent(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allPayments = paymentDao.recent(500)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingDetections = pendingDao.pending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingCount = pendingDao.pendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            rollForwardPastDueSubscriptions(db, System.currentTimeMillis())
        }
        refreshRates()
    }

    fun setBudgetLimit(limit: Double) {
        settingsRepository.setBudgetLimit(limit)
    }

    fun save(sub: Subscription) = viewModelScope.launch {
        if (sub.id == 0L) dao.insert(sub) else dao.update(sub)
    }

    fun remove(sub: Subscription) = viewModelScope.launch { dao.delete(sub) }

    fun removePayment(log: PaymentLog) = viewModelScope.launch { paymentDao.delete(log) }

    suspend fun byId(id: Long) = dao.byId(id)

    fun refreshRates() = viewModelScope.launch {
        _exchangeRates.value = rateRepository.load()
    }

    fun clearAllSubscriptions() = viewModelScope.launch {
        db.withTransaction {
            dao.all().forEach { dao.delete(it) }
        }
    }

    fun acceptDetection(pd: PendingDetection) = viewModelScope.launch {
        db.withTransaction {
            applyDetectionInTransaction(db, pd)
            pendingDao.setStatus(pd.id, "accepted")
        }
    }

    fun rejectDetection(pd: PendingDetection) = viewModelScope.launch {
        pendingDao.setStatus(pd.id, "rejected")
    }

    fun triggerBackfill(months: Int): Flow<BackfillResult> = flow {
        emit(BackfillResult.Loading(0, 0))
        try {
            val client = SubhookClient(
                BuildConfig.SUBHOOK_BASE_URL,
                BuildConfig.SUBHOOK_HMAC_SECRET
            )
            val jobId = client.submitBackfill(months)
            while (true) {
                kotlinx.coroutines.delay(3000)
                val job = client.backfillStatus(jobId)
                if (job.status == "running") {
                    emit(BackfillResult.Loading(job.processed, job.total))
                    continue
                }
                if (job.status == "error") {
                    emit(BackfillResult.Error(job.error ?: "job error"))
                    return@flow
                }
                if (job.status == "done") {
                    emit(applyBackfillDetectionsInTransaction(db, job.detections, System.currentTimeMillis()))
                    return@flow
                }
            }
        } catch (t: Throwable) {
            emit(BackfillResult.Error(t.message ?: "unknown"))
        }
    }
}

internal suspend fun rollForwardPastDueSubscriptions(db: AppDb, nowMillis: Long) {
    db.withTransaction {
        val dao = db.dao()
        val paymentDao = db.paymentDao()
        dao.all().filter { it.nextBilling <= nowMillis && it.cycle != "one-time" && it.cycle != "unknown" }.forEach { sub ->
            var next = sub.nextBilling
            while (next <= nowMillis) {
                paymentDao.insert(
                    PaymentLog(
                        subscriptionId = sub.id,
                        paidAt = next,
                        amount = sub.amount,
                        currency = sub.currency,
                        cycleAtPayment = sub.cycle
                    )
                )
                next = addCycle(next, sub.cycle)
            }
            dao.update(sub.copy(nextBilling = next))
        }
    }
}

internal fun parseIsoDateMillis(iso: String): Long {
    if (iso.isBlank()) return System.currentTimeMillis()
    val d = LocalDate.parse(iso)
    return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

internal fun normalizedCycleFor(raw: String): String = when (raw.lowercase()) {
    "monthly", "weekly", "yearly" -> raw.lowercase()
    "one-time" -> "one-time"
    else -> "unknown"
}

internal fun shouldAutoApply(pd: PendingDetection): Boolean {
    if (pd.kind != "new_sub" && pd.kind != "new_payment") return false
    val cycle = normalizedCycleFor(pd.cycle)
    return cycle == "monthly" || cycle == "weekly" || cycle == "yearly"
}

internal suspend fun applyBackfillDetectionsInTransaction(
    db: AppDb,
    detections: List<DetectionPayload>,
    now: Long
): BackfillResult.Done {
    var autoApplied = 0
    var pendingCount = 0
    db.withTransaction {
        val dao = db.dao()
        val pendingDao = db.pendingDao()
        for (p in detections) {
            if (pendingDao.byEmailId(p.email_id) != null) continue
            val pd = DetectionReconciler.reconcile(p, dao.all(), now)
            if (shouldAutoApply(pd)) {
                applyDetectionInTransaction(db, pd)
                pendingDao.insert(pd.copy(status = "accepted"))
                autoApplied++
            } else {
                if (pendingDao.insert(pd) > 0) pendingCount++
            }
        }
    }
    return BackfillResult.Done(autoApplied + pendingCount, autoApplied, pendingCount)
}

internal suspend fun applyDetectionInTransaction(db: AppDb, pd: PendingDetection) {
    val dao = db.dao()
    val paymentDao = db.paymentDao()
    when (pd.kind) {
        "new_sub" -> {
            val cycle = normalizedCycleFor(pd.cycle)
            val paidAt = parseIsoDateMillis(pd.dateIso)
            val nextBilling = if (cycle == "one-time" || cycle == "unknown") paidAt else addCycle(paidAt, cycle)
            val newId = dao.insert(Subscription(
                name = pd.provider, amount = pd.amount, currency = pd.currency,
                cycle = cycle, nextBilling = nextBilling, category = "Diğer"
            ))
            paymentDao.insert(PaymentLog(
                subscriptionId = newId, paidAt = paidAt,
                amount = pd.amount, currency = pd.currency, cycleAtPayment = cycle
            ))
        }
        "new_payment" -> pd.targetSubId?.let { sid ->
            val paidAt = parseIsoDateMillis(pd.dateIso)
            val cycle = normalizedCycleFor(pd.cycle)
            paymentDao.insert(PaymentLog(
                subscriptionId = sid, paidAt = paidAt,
                amount = pd.amount, currency = pd.currency,
                cycleAtPayment = cycle
            ))
            advanceSubscriptionAfterPayment(db, sid, paidAt, cycle)
        }
        "amount_change" -> pd.targetSubId?.let { sid ->
            val paidAt = parseIsoDateMillis(pd.dateIso)
            val cycle = normalizedCycleFor(pd.cycle)
            val sub = dao.byId(sid) ?: return@let
            dao.update(sub.copy(amount = pd.amount, currency = pd.currency))
            paymentDao.insert(PaymentLog(
                subscriptionId = sid, paidAt = paidAt,
                amount = pd.amount, currency = pd.currency,
                cycleAtPayment = cycle
            ))
            advanceSubscriptionAfterPayment(db, sid, paidAt, cycle)
        }
        "date_correction" -> pd.targetSubId?.let { sid ->
            val sub = dao.byId(sid) ?: return@let
            dao.update(sub.copy(nextBilling = parseIsoDateMillis(pd.dateIso)))
        }
    }
}

private suspend fun advanceSubscriptionAfterPayment(db: AppDb, subId: Long, paidAt: Long, cycle: String) {
    if (cycle == "one-time" || cycle == "unknown") return
    val dao = db.dao()
    val sub = dao.byId(subId) ?: return
    val next = addCycle(paidAt, cycle)
    if (next > sub.nextBilling) {
        dao.update(sub.copy(nextBilling = next))
    }
}

sealed class BackfillResult {
    data class Loading(val processed: Int, val total: Int) : BackfillResult()
    data class Done(val inserted: Int, val autoApplied: Int = 0, val pending: Int = 0) : BackfillResult()
    data class Error(val message: String) : BackfillResult()
}
