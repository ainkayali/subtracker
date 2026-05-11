# Fastmail Mail-Based Subscription Detection — Design Spec

**Date:** 2026-05-11
**Project:** SubTracker (github.com/ainkayali/subtracker)
**Baseline:** v1.2.0 (tag)
**Parent issue:** Suggestion #7 (mail tabanlı abonelik tespit + backfill)

---

## 1. Scope

SubTracker'a 2 mail-tabanlı feature ekle:

1. **Real-time tespit** — Fastmail JMAP push → OCI webhook → DeepSeek body parsing → FCM push → Android notification → user confirm → DB upsert.
2. **Backfill** — kullanıcı son 12 ay maillerini taratıp eksik abonelikleri/payment log'ları oluşturur, fark gerekirse tutar/tarih düzeltir.

Sadece **tek kullanıcı için** (Alper). Multi-tenant değil.

### Out of scope (V2)

- Multi-user / shared infra
- Mail klasör seçimi (sadece INBOX taranır)
- Mail silme / cevaplama / yönlendirme — read-only
- Auto-create silent (user confirm zorunlu)
- Push fail-over: FCM çalışmazsa cihazlı polling fallback yok (push edemezse, app açılışta `Email/changes` çağırır)

---

## 2. Architecture

```
┌──────────────────┐ JMAP push   ┌───────────────────────────┐
│ Fastmail JMAP    │────────────▶│ subhook.dafre.org (OCI)    │
└──────────────────┘             │  - JMAP token              │
        ▲                        │  - DeepSeek API key        │
        │ JMAP API               │  - FCM Server Key          │
        │ Email/changes          │  - SQLite (state, dedup)   │
        │ Email/get              │                            │
        └────────────────────────┤                            │
                                 │  Pipeline:                 │
                                 │   1. Email/changes (delta) │
                                 │   2. Email/get (body)      │
                                 │   3. Pre-filter rules.json │
                                 │   4. DeepSeek extract JSON │
                                 │   5. FCM data message      │
                                 └──────────┬─────────────────┘
                                            │ FCM
                                            ▼
                              ┌────────────────────────────────┐
                              │ SubTracker (Android)           │
                              │  - FCM service                 │
                              │  - MailWorker (consume FCM)    │
                              │  - PendingDetectionScreen      │
                              │  - DB upsert (Sub + Payment)   │
                              └────────────────────────────────┘
```

### 2.1 Backend service: `subhook`

**Lokasyon:** `/home/alper/web/subhook/` (Pancar paterni)
**Dil:** Node.js (Express)
**Çalışma:** user-systemd (`~/.config/systemd/user/subhook.service`, `linger=yes`)
**Port:** localhost only, Cloudflare tunnel ingress `subhook.dafre.org`
**State:** SQLite (`subhook.db`) — son JMAP state, FCM device token, gönderilmiş email ID dedup

### 2.2 Android: yeni katman

- **`FirebaseMessagingService`** subclass — FCM data message yakala, `MailDetectionWorker` enqueue
- **`MailDetectionWorker`** — payload'u parse, DB ile match, notification göster
- **`PendingDetection` Room entity** — onaylanmamış tespitler için staging
- **`PendingDetectionScreen`** Compose — onay UI
- **`BackfillScreen`** Compose — "Geçmiş maillerden senkronize et" butonu + sonuç listesi
- **`SettingsRepository.subhookAuthToken`** — HMAC shared secret (EncryptedSharedPreferences)
- **`SettingsRepository.fcmToken`** — son kayıtlı FCM token

---

## 3. Data Flow

### 3.1 Realtime (push) flow

1. Fastmail mailbox state değişir
2. Fastmail PushSubscription (önceden kayıtlı) → POST `https://subhook.dafre.org/jmap-push` (server-server, no client)
3. subhook handler:
   - `Email/changes` ile son state'ten beri yeni email ID'leri al
   - Her ID için `Email/get` (subject, from, body)
   - Pre-filter: from domain whitelist'te VEYA subject regex eşleşiyor mu?
   - Geçenleri **DeepSeek** API'a sırayla gönder:
     ```
     System: "Sen abonelik mailini ayrıştıran asistansın. Yanıt JSON: {provider, amount, currency, date_iso, cycle, confidence}"
     User: "<from>\n<subject>\n<plaintext body, ilk 3KB>"
     ```
   - LLM JSON dön → schema validate (zod)
   - Dedup tablo'da email ID kontrolü → daha önce işlendiyse skip
   - FCM data message gönder cihaza:
     ```json
     {
       "type": "detection",
       "email_id": "...",
       "provider": "Spotify",
       "amount": 99.0,
       "currency": "TRY",
       "date_iso": "2026-05-11",
       "cycle": "monthly",
       "confidence": 0.92,
       "raw_subject": "..."
     }
     ```
4. Cihaz FCM data message'i yakala (foreground/background ikisi de) → `MailDetectionWorker` enqueue
5. Worker:
   - Local DB'de provider adıyla match var mı? (case-insensitive contains)
   - Yoksa → `PendingDetection.kind = "new_sub"`
   - Varsa, son `PaymentLog` aynı dönem mi? → tutar fark mı? → `kind = "amount_change"` veya `"new_payment"`
   - Tarih farklıysa → `kind = "date_correction"`
   - PendingDetection row ekle
   - System notification: `"${provider} ${amount} ${currency} algılandı — onayla?"`
6. Tap → app `PendingDetectionScreen` aç → user onayla/reddet → DB upsert

### 3.2 Backfill flow

1. Kullanıcı Settings > "Geçmiş maillerden senkronize et"
2. Cihaz: `POST https://subhook.dafre.org/backfill { months: 12 }` (HMAC imzalı)
3. subhook handler:
   - `Email/query` (filter: receivedAt >= now-12mo, in INBOX)
   - Pre-filter (whitelist + subject regex)
   - Her geçen mail için `Email/get` + DeepSeek extract
   - Tüm sonuçları array olarak HTTP response döndür (stream chunks veya tek JSON)
4. Cihaz: array'i alır → her birini local DB ile karşılaştır → `PendingDetection` row'ları oluştur (her biri user onayı bekler)
5. BackfillScreen'de tüm pending detections listesi göster — "Tümünü kabul" / "Tek tek onayla" / "İptal"

### 3.3 Provider whitelist (server `rules.json`)

```json
{
  "domains": [
    "spotify.com", "netflix.com", "apple.com", "google.com",
    "microsoft.com", "openai.com", "anthropic.com", "claude.ai",
    "github.com", "adobe.com", "amazon.com", "youtube.com",
    "turkcell.com.tr", "vodafone.com.tr", "turktelekom.com.tr",
    "ttnet.net.tr", "vivense.com", "getir.com"
  ],
  "subject_keywords": [
    "invoice", "receipt", "payment", "subscription", "renewal",
    "fatura", "ödeme", "abonelik", "yenileme", "tahsilat"
  ]
}
```

Server boot'ta yüklenir. Genişletilebilir — `git pull` ile.

### 3.4 DeepSeek prompt + schema

```
SYSTEM:
Bir abonelik/fatura mailini ayrıştırıyorsun. Yanıt: SADECE geçerli JSON.

Şema:
{
  "is_subscription_payment": boolean,  // gerçekten abonelik ödemesi mi?
  "provider": string,                  // Spotify, Netflix, Türk Telekom...
  "amount": number,                    // örn 99.0
  "currency": string,                  // ISO 4217: TRY, USD, EUR
  "date_iso": string,                  // YYYY-MM-DD
  "cycle": "monthly" | "yearly" | "weekly" | "one-time" | "unknown",
  "confidence": number                 // 0.0-1.0
}

is_subscription_payment=false ise diğer alanlar null olabilir.
Tutar bulunamazsa 0 değil, null.

USER:
From: ${from}
Subject: ${subject}
Body (first 3KB):
${body}
```

`confidence < 0.6` ise → bildirim atma, sadece dedup tablo'ya skip kaydı.

---

## 4. Components — Detail

### 4.1 Backend `subhook` (Node.js)

**Dosyalar:**

```
/home/alper/web/subhook/
  package.json
  src/
    server.js               # Express app + route'lar
    jmap.js                 # Fastmail JMAP client (fetch + bearer)
    deepseek.js             # DeepSeek API client (chat completion + JSON parse)
    fcm.js                  # Firebase Admin SDK gönderimi
    db.js                   # SQLite migrations + queries
    rules.js                # rules.json loader + pre-filter
    auth.js                 # HMAC verify (device requests)
    routes/
      jmapPush.js           # POST /jmap-push (Fastmail callback)
      backfill.js           # POST /backfill (device-initiated)
      register.js           # POST /register-fcm-token (device sends FCM token)
      health.js             # GET /health
  rules.json
  subhook.db                # SQLite (gitignored)
  .env                      # FASTMAIL_TOKEN, DEEPSEEK_KEY, FCM_KEY, HMAC_SECRET
```

**Env vars (`.env`):**
```
FASTMAIL_JMAP_URL=https://api.fastmail.com/jmap/api/
FASTMAIL_TOKEN=fmu1-xxx
DEEPSEEK_API_KEY=sk-xxx
DEEPSEEK_MODEL=deepseek-chat
FCM_PROJECT_ID=subtracker-fcm
FCM_SERVICE_ACCOUNT_JSON=/home/alper/web/subhook/firebase-sa.json
DEVICE_HMAC_SECRET=<32-byte hex>
SUBHOOK_PORT=8723
SUBHOOK_BIND=127.0.0.1
```

**Database schema (`subhook.db`):**

```sql
CREATE TABLE jmap_state (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  state TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE fcm_devices (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  token TEXT NOT NULL UNIQUE,
  device_name TEXT,
  registered_at INTEGER NOT NULL
);

CREATE TABLE processed_emails (
  email_id TEXT PRIMARY KEY,
  processed_at INTEGER NOT NULL,
  filtered_out INTEGER NOT NULL,  -- 0 if sent to LLM, 1 if pre-filter skipped
  is_subscription INTEGER,         -- nullable, set after LLM
  detection_json TEXT              -- nullable, the parsed JSON
);

CREATE INDEX idx_processed_at ON processed_emails(processed_at);
```

**Endpoints:**

| Method | Path | Auth | Body |
|---|---|---|---|
| POST | `/jmap-push` | Fastmail signature header verify | JMAP push payload |
| POST | `/backfill` | HMAC (device) | `{ months: int }` |
| POST | `/register-fcm-token` | HMAC (device) | `{ token, device_name? }` |
| GET | `/health` | none | — |

### 4.2 Android — yeni dosyalar

```
app/src/main/java/com/subtracker/
  fcm/
    SubTrackerFcmService.kt        # FirebaseMessagingService — onMessageReceived
    MailDetectionWorker.kt         # parse FCM payload, DB diff, notification
  detect/
    PendingDetection.kt            # @Entity
    PendingDetectionDao.kt
    DetectionReconciler.kt         # local DB ile match + diff hesabı
    SubhookClient.kt               # OkHttp + HMAC
  ui/
    PendingDetectionScreen.kt      # tek detection onayla/red ekranı
    BackfillScreen.kt              # backfill tetik + sonuç listesi
```

**Yeni Room entity:**

```kotlin
@Entity
data class PendingDetection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val emailId: String,                  // dedup
    val kind: String,                     // new_sub | new_payment | amount_change | date_correction
    val targetSubId: Long?,               // matched subscription id, null if kind=new_sub
    val provider: String,
    val amount: Double,
    val currency: String,
    val dateIso: String,                  // YYYY-MM-DD
    val cycle: String,
    val confidence: Double,
    val rawSubject: String,
    val createdAt: Long,
    val status: String = "pending"        // pending | accepted | rejected
)
```

DB migration v2 → v3.

### 4.3 FCM project

- Yeni Firebase project: `subtracker-mail`
- Android app paket: `com.subtracker`
- `google-services.json` indir → `app/google-services.json`
- Build.gradle: `id("com.google.gms.google-services")` plugin
- Server Admin SDK service account JSON Firebase Console'dan
- Plan: V1 tek cihaz, V2 multi-device topic-based

---

## 5. Security + Privacy

### 5.1 Token storage

| Token | Yer | Korunma |
|---|---|---|
| Fastmail JMAP token | OCI subhook `.env` | dosya `0600` alper:alper |
| DeepSeek API key | OCI subhook `.env` | aynı |
| FCM server key (SA JSON) | OCI subhook | aynı |
| HMAC shared secret | OCI `.env` + Android EncryptedSharedPreferences | EncryptedSharedPreferences AES-256-GCM, AndroidKeyStore master key |
| FCM device token | Android EncryptedSharedPreferences + OCI db | aynı |

Cihazda Fastmail token YOK. App'i biri ele geçirse Fastmail'e erişim olmaz.

### 5.2 HMAC auth (device → subhook)

Her device request:

```
Authorization: HMAC-SHA256 ts=<unix-millis>,sig=<base64-sha256-hmac>
```

Server: ts < 60s drift, replay window 5min.

### 5.3 Mail body maruziyet

- Sadece pre-filter (whitelist domain VEYA subject keyword) geçen mailler DeepSeek'e iletilir
- İlk 3KB body — kalanı gönderilmez
- Mail body cihazda NEVER saklanmaz — sadece parsed JSON saklanır
- DeepSeek retention policy: kullanıcı kabul ettiyse okay (Alper personal)

### 5.4 Cloudflare tunnel ingress

`subhook.dafre.org` → `localhost:8723` (subhook)

Cloudflare DNS API ile CNAME otomatik:

```bash
cloudflared'a /etc/cloudflared/config.yml ingress eklenir:
- hostname: subhook.dafre.org
  service: http://localhost:8723
```

### 5.5 PostMortem switches

| Risk | Switch |
|---|---|
| DeepSeek hesap dolar limiti | `MAX_LLM_CALLS_PER_DAY=200` env, server reddeder |
| Fastmail token expire | health endpoint expose, monitoring uyarır |
| FCM token rotation | App'te token refresh listener var, otomatik /register-fcm-token çağrılır |
| Subhook down | App app-açılışta backfill paterni ile fallback yap (push gelmediyse last sync state'ten devam) |

---

## 6. Testing

### 6.1 Backend

- Unit: `jmap.js`, `deepseek.js`, `rules.js` (mock fetch)
- Integration: `routes/jmapPush.js` end-to-end with fake JMAP body
- Manual: real Fastmail mail tetiği

### 6.2 Android

- Unit: `DetectionReconciler` (match logic, diff calculation)
- Integration: `MailDetectionWorker` Robolectric + FCM payload fixture
- Manual: real FCM push üzerinden uçtan uca

---

## 7. Roll-out

### PR1 — Android base infrastructure (DB v3)
- `PendingDetection` entity + DAO + migration 2→3
- `PendingDetectionScreen` (basit, FCM olmadan dummy data)
- `SubhookClient` skeleton (HMAC, no real endpoint)
- Unit test

### PR2 — Backend `subhook` ilk versiyon
- `/home/alper/web/subhook/` skeleton
- `/health` + `/register-fcm-token` çalışır
- systemd user service
- Cloudflare tunnel ingress
- Firebase project create + service account download (Alper)

### PR3 — JMAP integration (server)
- Fastmail JMAP client çalışır (Email/changes + Email/get)
- rules.json pre-filter
- DeepSeek client + JSON schema validate
- `/jmap-push` endpoint live
- Fastmail PushSubscription register script (one-off)

### PR4 — FCM integration (server + Android)
- `SubTrackerFcmService` Android
- FCM admin SDK server
- End-to-end: server fake push test → Android notification

### PR5 — Backfill UI + endpoint
- `BackfillScreen` Compose
- `/backfill` server endpoint
- Reconciler diff logic
- Tüm-kabul / tek-tek onay

### PR6 — Manual smoke + v1.3.0 release tag

---

## 8. Risks / Open

- **Cost:** DeepSeek pricing. Whitelist + 3KB body → tahmini günlük < 50 LLM çağrısı = düşük. Yine de günlük cap.
- **Fastmail JMAP push reliability:** Fastmail SLO yok bireysel için. Push fail durumunda backfill fallback.
- **DeepSeek hallucination:** confidence < 0.6 ise iletme. User onay her zaman zorunlu.
- **Türkçe mail parsing:** DeepSeek-chat Türkçe mail iyi parse eder bence ama prompt'ta açıkça Türkçe örnek vermek mantıklı.
- **App icon ve provider name mismatch:** "Spotify Premium 1 Aylık" gibi varyasyonlar — case-insensitive contains + ilk kelime fallback.
- **GDPR / Fastmail TOS:** Alper kendi mail'ini kendi okuyor — sorun yok.

---

**Spec end.**
