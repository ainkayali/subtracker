# SubTracker Modernization Design

**Date:** 2026-05-11
**Repo:** github.com/ainkayali/subtracker
**Baseline commit:** 71789ff (versionCode=5, versionName=1.1.0)

## 1. Scope

Mevcut görsel kimlik (cream + cards + brand badges + black FAB) **değişmiyor**. Görsel polish yok. Eklenenler tamamen fonksiyonel:

1. **Bildirim sistemi** — WorkManager günlük çalışır, `reminderDays` öncesi push atar
2. **Ödeme geçmişi** — yeni Room entity `PaymentLog`. Her `nextBilling` roll-forward log üretir. Dashboard'a "Son ödemeler" section'ı. Tam liste için ayrı ekran.
3. **Bildirim listesi** — header'a 🔔 ikon eklenir; tıklayınca yaklaşan ödemeler ekranı.

**Skip edilenler** (kullanıcı tercihi): grafik/analitik, dark mode, filter/sort/search, export/import, custom kategori, trial/pause.

### Bug + dead code temizliği

- `app/src/main/java/com/subtracker/ui/ListScreen.kt` sil (unreferenced, 115 satır)
- `DashboardScreen.kt:435` ölü back-arrow IconButton sil
- `DashboardScreen.kt:401` "13 / 13" stub → tek sayı "13"
- `DashboardScreen.kt:548-552` BudgetCard hardcoded Yayın/Yazılım/Diğer legend sil (yanlış data, derived değil)
- `DashboardScreen.kt:450` USD fallback `44.88` magic number sil → kur yoksa USD pill disable + kur satırı gizle
- `app/build.gradle.kts:18-22` hardcoded keystore fallback `subtracker123` sil → env yoksa hata fırlat
- `.github/workflows/build.yml` keystore cleanup step ekle (`if: always() rm -f`)
- `AndroidManifest.xml` "zero permissions" yalanı README'de düzelt (INTERNET izni var, sebep TCMB döviz)

## 2. Architecture + Data Model

### Yeni entity: `PaymentLog`

```kotlin
@Entity(
    foreignKeys = [ForeignKey(
        entity = Subscription::class,
        parentColumns = ["id"],
        childColumns = ["subscriptionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("subscriptionId"), Index("paidAt")]
)
data class PaymentLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long,
    val paidAt: Long,             // epochMillis (roll-forward anı)
    val amount: Double,           // snapshot, sub edit edilse log doğru kalır
    val currency: String,
    val cycleAtPayment: String
)
```

### DAO

```kotlin
@Dao interface PaymentDao {
    @Query("SELECT * FROM PaymentLog ORDER BY paidAt DESC LIMIT :limit")
    fun recent(limit: Int = 10): Flow<List<PaymentLog>>

    @Query("SELECT * FROM PaymentLog WHERE subscriptionId = :id ORDER BY paidAt DESC")
    fun forSub(id: Long): Flow<List<PaymentLog>>

    @Query("SELECT * FROM PaymentLog WHERE paidAt >= :startMillis AND paidAt < :endMillis ORDER BY paidAt DESC")
    fun forPeriod(startMillis: Long, endMillis: Long): Flow<List<PaymentLog>>

    @Insert suspend fun insert(log: PaymentLog)
}
```

### Database migration

- `AppDb` version 1 → 2
- `exportSchema = true` (build config `room.schemaLocation` = `$projectDir/schemas`)
- Migration `1→2`:
  ```sql
  CREATE TABLE IF NOT EXISTS PaymentLog (
      id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
      subscriptionId INTEGER NOT NULL,
      paidAt INTEGER NOT NULL,
      amount REAL NOT NULL,
      currency TEXT NOT NULL,
      cycleAtPayment TEXT NOT NULL,
      FOREIGN KEY(subscriptionId) REFERENCES Subscription(id) ON DELETE CASCADE
  );
  CREATE INDEX IF NOT EXISTS index_PaymentLog_subscriptionId ON PaymentLog(subscriptionId);
  CREATE INDEX IF NOT EXISTS index_PaymentLog_paidAt ON PaymentLog(paidAt);
  ```

### `SubViewModel.init` refactor

Mevcut while-loop roll-forward'u `RoomDatabase.withTransaction` içinde her iteration için bir `PaymentLog` insert eder. Atomik.

```kotlin
db.withTransaction {
    dao.all().filter { it.nextBilling <= now }.forEach { sub ->
        var next = sub.nextBilling
        while (next <= now) {
            paymentDao.insert(PaymentLog(
                subscriptionId = sub.id,
                paidAt = next,
                amount = sub.amount,
                currency = sub.currency,
                cycleAtPayment = sub.cycle
            ))
            next = addCycle(next, sub.cycle)
        }
        dao.update(sub.copy(nextBilling = next))
    }
}
```

### Notification layer — `Notifier.kt` (yeni dosya)

- `ReminderWorker : CoroutineWorker` — PeriodicWork, 24h interval, flex 1h
- `App.onCreate()`:
    - Channel `subscription_reminders` (IMPORTANCE_DEFAULT)
    - `WorkManager.getInstance(this).enqueueUniquePeriodicWork("reminder_check", KEEP, request)`
- Worker logic:
  ```kotlin
  val now = System.currentTimeMillis()
  dao.all()
      .filter { it.reminderOn }
      .filter { (it.nextBilling - now) in 0..(it.reminderDays * 86_400_000L) }
      .forEach { sub -> fireNotification(sub) }
  ```
- Tap action: `MainActivity` PendingIntent (no deeplink, basit)
- Android 13+ runtime POST_NOTIFICATIONS izni: `MainActivity.onCreate`'de `rememberLauncherForActivityResult(RequestPermission)` ile iste

### Manifest

`<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` ekle.

### Yeni navigation state

`MainActivity.kt`:

```kotlin
sealed class Screen {
    data object Dashboard : Screen()
    data class Edit(val id: Long) : Screen()
    data object Notifications : Screen()
    data object PaymentHistory : Screen()
}

var screen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
```

`BackHandler` her non-Dashboard screen'de `Dashboard`'a döner.

### Dependency additions (`app/build.gradle.kts`)

```kotlin
implementation("androidx.work:work-runtime-ktx:2.9.0")
testImplementation("junit:junit:4.13.2")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
testImplementation("androidx.room:room-testing:2.6.1")
testImplementation("androidx.arch.core:core-testing:2.2.0")
testImplementation("androidx.work:work-testing:2.9.0")
```

### Modul yapı sonu

```
java/com/subtracker/
  App.kt                       # + WorkManager init + channel
  Db.kt                        # + PaymentLog + PaymentDao + Migration_1_2
  ExchangeRates.kt
  MainActivity.kt              # navigation state machine
  Notifier.kt                  # YENI
  Settings.kt
  SubViewModel.kt              # + recentPayments StateFlow, log yazar
  Utils.kt
  ui/
    AddEditScreen.kt
    DashboardScreen.kt         # cleanup + bell + son odemeler section
    NotificationsScreen.kt     # YENI
    PaymentHistoryScreen.kt    # YENI
    Theme.kt
  # ListScreen.kt SILINDI
```

## 3. UI Changes (visual identity preserved)

### `DashboardScreen.kt` patch'leri

**HeaderSection (435-446):**
- Sol back-arrow IconButton SİL
- Sağ tarafta `Row { BellIconButton, SettingsIconButton }`
- BellIconButton: badge dotu kırmızı `if (upcomingCount > 0)`. Tıkla → `onNavigateNotifications()`

**BudgetCard (504-555):**
- Legend Row (548-552) SİL

**TotalSummaryCard (449-502):**
- `val usdRate = exchangeRates.ratesToTry["USD"]`
- `usdRate == null` ise USD pill `enabled = false`, kur satırı render etme

**Abonelikler header (388-407):**
- `"${size} / ${size}"` → `"$size"`

**Yeni section: SON ÖDEMELER**

Subscriptions list'in altında, dashboard'un en sonunda:

- `paymentDao.recent(3)` collect
- Boşsa render etme
- Section label "SON ÖDEMELER" + "Tümü →" (tıkla → PaymentHistoryScreen)
- 3 satır, mevcut white rounded card stilinde:
    - Sol: küçük brand badge (28dp, `brandStyle()` reuse)
    - Orta: sub adı + tarih ("8 May · 3 gün önce")
    - Sağ: `+₺X` yeşil (#4CAF50)

### `NotificationsScreen.kt` — yeni

- Top-bar: AddEditScreen paterni (back arrow + "Bildirimler")
- LazyColumn `upcomingReminders` — derived from `subscriptions`:
  ```kotlin
  subscriptions.filter { it.reminderOn }
      .map { it to (it.nextBilling - now) }
      .filter { (_, delta) -> delta in 0..(it.reminderDays * 86_400_000L) }
      .sortedBy { it.second }
  ```
- Mevcut `EnhancedSubscriptionCard` reuse, "X gün sonra" vurgusu büyük
- Boş state: "Yakın hatırlatıcı yok"

### `PaymentHistoryScreen.kt` — yeni

- Top-bar: AddEditScreen paterni (back arrow + "Ödeme geçmişi")
- LazyColumn `paymentDao.recent(100)` (sonra pagination)
- Sticky header: ay grubu — `"Mayıs 2026 · ₺2.175 toplam"`
- Satır: brand badge + isim + tarih (formatDateMinimal) + tutar (formatMoney)
- Filtre yok (basit tut, sonra eklenir)

### Theme dokunulmuyor

`Theme.kt` aynı.

### Visual diff özet

| Element | Eski | Yeni |
|---|---|---|
| Header sol | ölü back-arrow | boş |
| Header sağ | ⚙ | 🔔(dot) + ⚙ |
| Bütçe legend | 3 hardcoded label | yok |
| USD pill (kur yokken) | "44.88" fallback | disabled |
| Abonelik header | "13 / 13" | "13" |
| Dashboard sonu | (yok) | SON ÖDEMELER (max 3) |
| Yeni ekran | — | Bildirimler |
| Yeni ekran | — | Ödeme Geçmişi |

## 4. Test + Security + Roll-out

### Security cleanup

**`app/build.gradle.kts`** signing config — fallback'ları sil:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(System.getenv("KEYSTORE_PATH") ?: error("KEYSTORE_PATH required"))
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: error("KEYSTORE_PASSWORD required")
        keyAlias = System.getenv("KEY_ALIAS") ?: error("KEY_ALIAS required")
        keyPassword = System.getenv("KEY_PASSWORD") ?: error("KEY_PASSWORD required")
    }
}
```

Debug build etkilenmez, CI zaten env veriyor.

**`.github/workflows/build.yml`** — keystore cleanup:

```yaml
- name: Clean keystore
  if: always()
  run: rm -f app/release.keystore
```

**README.md ekle** — INTERNET izni açıklaması ("zero permissions" yalanını düzelt).

### Test

`app/src/test/java/com/subtracker/`:

1. **`UtilsTest.kt`**
    - `monthlyAmount` weekly/monthly/yearly
    - `addCycle` ay sonu edge: 31 Ocak + 1 ay → 28/29 Şubat
    - `relativeLabel` sınır değerleri: 0, 1, 6, 7, 29, 30, 364, 365

2. **`ExchangeRateRepositoryTest.kt`**
    - TCMB XML parse (fixture XML → expected rates map)
    - `loadCached()` empty/corrupt prefs → null
    - `save() + loadCached()` round-trip

3. **`SubViewModelTest.kt`** (`Room.inMemoryDatabaseBuilder`)
    - Roll-forward: geç billing → `nextBilling` ilerletildi + `PaymentLog` insert edildi
    - 3 cycle geçmişse 3 log
    - `byId` non-existent → null
    - Sub silinince cascade ile log'ları silinir

4. **`ReminderWorkerTest.kt`** (`WorkManagerTestInitHelper`)
    - `reminderOn=true`, `nextBilling - now == reminderDays * 86400000` → notification fire
    - `reminderOn=false` → fire etmez
    - `nextBilling - now > reminderDays * 86400000` → fire etmez

CI step:
```yaml
- name: Unit tests
  run: ./gradlew testReleaseUnitTest
```

### Manual smoke checklist

- [ ] Debug APK kurulur, Dashboard açılır
- [ ] Abonelik ekle → list'te görünür
- [ ] Bildirim izni iste (Android 13+) — onaylanır, channel açılır
- [ ] `reminderDays=0`, `nextBilling=now` → Worker tetikle → notification görün
- [ ] Roll-forward: `nextBilling=geçmiş` → app aç → ileri sarıldı + Son Ödemeler'de görün
- [ ] Bell ikon: upcoming reminder varsa dot kırmızı → tıkla → Bildirimler ekranı
- [ ] Payment History: "Tümü →" → tam liste + ay başlıkları
- [ ] Subscription sil → cascade ile log'lar gitti
- [ ] Migration 1→2: v5 APK ile data oluştur, v6 üzerine kur, crash yok, eski abonelikler kaldı

### Roll-out PR sırası

1. **PR1 — Cleanup** (versionCode=6): dead code + back arrow + 13/13 + legend + magic number + README + keystore env zorunlu. Düşük risk, küçük diff.
2. **PR2 — Migration + PaymentLog** (versionCode=7): entity + DAO + roll-forward refactor + test.
3. **PR3 — Notifier** (versionCode=8): WorkManager + channel + ReminderWorker + permission + test.
4. **PR4 — UI** (versionCode=9): bell + Son Ödemeler section + Notifications/PaymentHistory screens + navigation state machine.

Tag `v1.2.0` → CI APK release son PR sonrası.

### Bilinen riskler

- **Android 13+ notification permission** — runtime prompt gerekir, kullanıcı reddederse `reminderOn` her şeyi açık tutar ama bildirim atmaz. UI'da uyarı satırı gerekebilir (V2).
- **WorkManager Periodic minimum 15 dakika**. 24h interval fine.
- **Migration testi şart** — `MigrationTestHelper` ile assert; yanlış migration = data silinir.
- **TCMB hafta sonu/tatil** — XML stale. Cache TTL 24h, miss → fetch. Hafta sonu Cuma kuru kullanır.
- **Roll-forward + log ilk açılış**: eski yüklü kullanıcılarda migration'dan sonra ilk açılışta geç kalmış billing'ler bir anda çok log üretebilir. Limit: per-sub roll-forward count'u log'a yaz, sınırsız ama kullanıcıyı uyar gerekirse.

---

**Status:** brainstorm complete. Next step: writing-plans skill ile implementation plan oluştur.
