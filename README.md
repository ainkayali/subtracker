# SubTracker

Minimal Android subscription tracker built with Kotlin, Jetpack Compose, Material 3, and Room.

## Features

- Add, edit, and delete subscriptions
- Track monthly subscription spend against a budget
- Convert foreign-currency subscriptions with TCMB exchange rates
- Keep an automatic payment history when overdue subscriptions roll forward
- Send local reminder notifications before upcoming billing dates

## Permissions

- `INTERNET` is used only for TCMB exchange rates: `https://www.tcmb.gov.tr/kurlar/today.xml`
- `POST_NOTIFICATIONS` is used on Android 13+ for subscription reminders

## Development

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Release builds require these environment variables:

- `KEYSTORE_PATH`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

## License

No license has been selected yet.
