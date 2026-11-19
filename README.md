# SalonLink

Book your barber in three taps, see the live queue, and never get double-booked.
An Android app for salons, built for **EC5208 Mobile Application Development (Group 12, University of Ruhuna)**.

| Customer | Staff | Owner |
|---|---|---|
| Explore barbers and services (ranked by style match), book in 3 steps, Bookings tab (upcoming / history / cancel ≥ 24 h), waitlist with freed-slot offers, live queue + ETA, notification feed, reminders 24 h and 2 h before, **My style** (style tags, note for the barber with consent, favourite barber, private moodboard), **English / සිංහල / தமிழ்** | Live day schedule, check in → start → complete, no-show, cancel, add walk-ins | Day at a glance (bookings, walk-ins, revenue), services, team (hours, time off, invites), salon details |

Screenshots: [`docs/screenshots/`](docs/screenshots/).

## Tech

- **Kotlin + Jetpack Compose** (Material 3), minSdk 26, target 37
- **MVVM + unidirectional data flow**, Hilt, Coroutines/Flow, type-safe Navigation
- **Firebase Spark (free)**: Authentication + Cloud Firestore + security rules. No Cloud Functions:
  double booking is prevented with Firestore transactions and slot-lock documents checked by the rules.
- Coil 3 (images), WorkManager (reminders)
- Tests: JUnit, MockK, Turbine, Compose UI tests, `@firebase/rules-unit-testing` on the emulator

More detail: [Architecture & diagrams](docs/ARCHITECTURE.md) · [Test evidence T01–T12](docs/TEST-EVIDENCE.md) · [Demo script](docs/DEMO-SCRIPT.md) · [Manual test guide](docs/MANUAL-TEST-GUIDE.md) · [Implementation plan](PLAN.md)

## Run it

**You need:** Android Studio (recent, with JDK 17+), the Android SDK (platform 37), Node.js 20+ and the Firebase CLI.

1. Put `app/google-services.json` in place (Firebase console → Project settings → Your apps). It is git-ignored.
2. Choose a backend:
   - **Local emulators (recommended for testing):**
     ```
     firebase emulators:start --only auth,firestore --project salonlink-4d991
     npm --prefix firebase/seed install
     npm --prefix firebase/seed run seed:emulator
     adb reverse tcp:9099 tcp:9099
     adb reverse tcp:8085 tcp:8085
     ./gradlew installDebug -Psalonlink.useEmulators=true
     ```
     Test accounts and roles: [`firebase/emulator-test-accounts.md`](firebase/emulator-test-accounts.md).
   - **Real project:** `./gradlew installDebug`. Rules and indexes are deployed with
     `firebase deploy --only firestore:rules,firestore:indexes`.
3. Staff and owners register with an email that has an invite (the seed creates them). Everyone else becomes a customer.

## Tests

```
./gradlew :app:testDebugUnitTest            # 138 unit tests (JVM)
./gradlew :app:connectedDebugAndroidTest    # 5 Compose UI tests (emulator/device)
firebase emulators:exec --only firestore --project demo-salonlink "npm --prefix firebase/tests test"   # 78 rules tests
```

## Release APK

The release build is minified and shrunk. To sign it, create a keystore once (keep it and its
passwords out of git) and a `keystore.properties` file in the project root:

```
keytool -genkeypair -v -keystore salonlink-release.jks -alias salonlink -keyalg RSA -keysize 2048 -validity 10000
```

```
storeFile=salonlink-release.jks
storePassword=…
keyAlias=salonlink
keyPassword=…
```

Then run `./gradlew assembleRelease`. The APK is in `app/build/outputs/apk/release/`. Without
`keystore.properties` the release APK is built unsigned.

## Project layout

```
app/src/main/java/com/group12/salonlink/
  core/      design system, navigation, notifications, utils
  domain/    models, booking engine (SlotGrid, AvailabilityCalculator), use cases, queue ETA
  data/      Firestore + Auth repositories, mappers, WorkManager reminders
  feature/   auth, customer (discovery, booking, bookings, queue, notifications), staff, owner
firebase/    firestore.rules, indexes, seed script, rules tests
docs/        architecture, test evidence, demo script, screenshots
```

## Status

Phases 0–7 and 9–11 are done. **Phase 8 (AI concierge) is on hold**, and the Concierge tab shows
"coming soon". Sinhala and Tamil cover the customer app; staff and owner screens are English.
See [PLAN.md](PLAN.md) for each phase's status and the honest limitations of the free Firebase plan.
