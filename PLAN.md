# SalonLink — Implementation Plan

EC5208 Mobile Application Development · Group 12 · Plan date: 18 Nov 2026 · Final submission: 15 Dec 2026

Source inputs: the submitted proposal (Group 12.pdf) and the Figma file (8 customer screens).

---

## 1. Decisions locked in

| Topic | Decision | Why |
|---|---|---|
| Platform / language | Android, **Kotlin + Jetpack Compose** (Material 3) | Matches proposal §11 exactly. |
| Min / target SDK | minSdk **26** (Android 8.0), target = latest stable | NFR-08. |
| Architecture | **MVVM + Unidirectional Data Flow**, layered `ui → domain → data` (Google "Guide to App Architecture") | Clear separation that is easy to explain in a viva. |
| DI | **Hilt** | Standard, testable, removes manual wiring. |
| Async | Coroutines + `StateFlow` / `Flow` | Firestore real-time listeners become `Flow`s. |
| Navigation | Navigation Compose with **type-safe `@Serializable` routes** | Compile-time safe arguments. |
| Backend | **Firebase Spark (free)**: Auth + Firestore + Security Rules. **No Cloud Functions.** | Cloud Functions require Blaze. Booking safety is achieved with Firestore **transactions + slot-lock docs + rules** (see §5). |
| AI assistant | **ON HOLD (future implementation, decided 18 Nov 2026).** Planned design: Gemini function calling via Firebase AI Logic SDK (Spark-compatible). For now the Concierge tab shows a "coming soon" screen styled from Figma. | No API key inside the APK. AI can only *read* (check availability) and *propose*; the **user's Confirm tap** calls the booking engine (NFR-05). |
| Photos (P3) | Compressed JPEG (≤ ~200 KB) stored in Firestore sub-collection | New projects need Blaze for Cloud Storage. The repository interface lets us swap to Storage later with one class. |
| Push notifications | In-app notification feed (Firestore listener → system notification) + **WorkManager** local reminders | FCM *sending* needs a server; documented as Blaze upgrade path. |
| Images | Coil 3 | Compose-native image loading. |
| Local prefs | DataStore (language, onboarding flag) | |
| Tests | JUnit + MockK + Turbine (unit), Compose UI tests, **Firebase Emulator** + `@firebase/rules-unit-testing` (rules & concurrency) | Covers proposal test plan T01–T12. |

Package name: `com.group12.salonlink` · Project folder: `salonLink/`

---

## 2. Prerequisites (things only you can do — ~1 hour)

**Status 18 Nov 2026:** Firebase project `salonlink-4d991` (Spark). Android app `com.group12.salonlink` registered and `app/google-services.json` in place. Email/Password sign-in enabled. Firestore `(default)` created in **asia-south1 (Mumbai)** in production mode. Firebase CLI 15.36.0 installed. AI Logic **not** enabled (on hold). Android SDK installed at `%LOCALAPPDATA%\Android\Sdk` (platform 37, build-tools 37, emulator, AVD `SalonLink_Pixel`). **Still pending:** `firebase login`.

**Phase 9 status (19 Nov 2026): DONE.** "My style" tab: style tags, a note for the barber, a consent switch (when on, new bookings carry "Style: Fade, Beard · note" in the booking note the barber already sees; photos are never shared), favourite barber ranked by a 0–100 match score (`BarberMatcher`: 80 × share of tags covered by the barber's specialties + 20 × rating/5), and a private moodboard (system photo picker, no storage permission; JPEGs shrunk to ≤ ~200 KB and stored as bytes in `users/{uid}/photos`, gated by a one-time consent that the rules check). Explore puts the favourite first, then best match, and shows "92% match for you". Language switch English / සිංහල / தமிழ் / phone default, saved on the phone and applied from `attachBaseContext` (Android 8–17): every customer screen is translated; staff/owner screens and dates/times stay English. Rules v7 (private style profile + photos, consent + size checks). 138 unit + 78 rules tests pass. Verified on the emulator: tags → 100 % / 19 % match, consent → photo added (35 KB), booking carried the note, Sinhala and Tamil Explore. Rules v7 + indexes **deployed** to `salonlink-4d991` (19 Nov 2026; includes v4–v6).

**Phase 11 status (19 Nov 2026): DONE (signing key still to create).** `README.md`, `docs/ARCHITECTURE.md` (layer, ER, booking-sequence and status-machine diagrams in Mermaid, Spark limitations), `docs/TEST-EVIDENCE.md` (T01–T12 table with test names), `docs/DEMO-SCRIPT.md` (10-minute walkthrough + troubleshooting). Release build is minified (4.2 MB) and starts cleanly; optional signing from a git-ignored `keystore.properties`. **Pending (user):** create the release keystore and run `assembleRelease`; T10 needs checking against the proposal.

**Phase 10 status (19 Nov 2026): DONE.** 5 Compose UI tests of the booking flow pass on the emulator (totals/continue, TalkBack checkbox semantics, taken time can't be picked, select + confirm, slot-taken error). Offline hardening: plain writes stop waiting after 8 s (`awaitWrite`), transactions fail fast with NETWORK. Accessibility pass: every icon-only button is labelled; cards/time chips have checkbox/radio roles. T12 screenshots at 1080×2400 and 720×1280. Phase 7 verified end to end on the emulator (cancel → offer → phone notification → Book it). `/code-review high` over Phases 4–10: 8 of 10 findings fixed (misaligned no-show offers, staff "today" not rolling over at midnight, accept at expiry, canceller re-offered, duplicate listeners…), 2 documented as Spark limitations (§10). 131 unit + 75 rules + 5 UI tests pass.

**Phase 7 status (19 Nov 2026): DONE (verified on the emulator in Phase 10).** Waitlist: join for a day from Date & time; a cancellation or no-show offers the freed time to the first matching customer (FIFO, 10-minute hold); the customer accepts in the Bookings tab through the normal booking engine (no double booking even if two people accept); offers also appear in the feed and as phone notifications. Expired offers go back in line on the next freed slot (no server timer on Spark). Live queue: staff devices publish anonymous per-barber boards; a pure `QueueEtaCalculator` gives position and ETA (booked + walk-ins); reached from the booking ticket and the Explore "Queue" action on the day. Rules v6 + collection-group index for "my waitlist". 128 unit tests + 75 rules tests pass. Rules v6 / index deployed with v7.

**Phase 6 status (19 Nov 2026): DONE.** Staff: live day schedule with one-tap status buttons derived from the status table (check in → start → complete, no-show after the start, cancel with confirm; no-show frees the slots), walk-in form (10-minute steps from the current slot, same booking engine). Owner: "Today at a glance" (bookings, completed, walk-ins, revenue + expected, barber filter, add walk-in), services CRUD (retire instead of delete), team list + staff editor (profile, working hours, time off, app invite) and salon details/opening hours. Status changes write a feed entry to the customer (staff rule from v4). Rules v5: owner-only catalogue writes with field validation (whole 10-min durations ≤ 160, ratings can't be set by the owner). 117 unit tests + 68 rules tests pass. Checked on the emulator as owner: check-in (customer feed entry written), walk-in added, editors load. Rules v5 deployed with v7.

**Phase 5 status (19 Nov 2026): DONE.** Bookings tab (Upcoming / History, cancel with a confirm dialog, also from the booking ticket). Customers can cancel in the app up to 24 h before the start (`BookingPolicy`, enforced again in rules v4); later they call the salon, staff can still cancel. In-app notification feed `users/{uid}/notifications` (bell with unread dot on Explore): "booking confirmed / cancelled" written by the use cases (best-effort, deterministic ids so retries can't duplicate), and staff may write status updates about their own salon's bookings (used in Phase 6). Salon-originated feed entries also become phone notifications while the app runs. WorkManager reminders 24 h and 2 h before each active booking (`ReminderPlanner`, pure + tested; `BookingReminderWorker` re-reads the booking first so a cancelled one stays silent; Hilt worker factory). 112 unit tests + 61 rules tests pass; Bookings tab checked on the emulator. Rules v4 deployed with v7.

> Tip: the rules tests can run against an already-running emulator: `FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 npm --prefix firebase/tests test` (they use the separate `demo-salonlink` project).

**Phase 4 status (19 Nov 2026): DONE.** Customer booking journey built from the Figma frames: Discovery (search, category bubbles, best-rated featured barber, upcoming appointment, Book / Book Again), Barber profile, Service selection (step 1/3), Date & time (step 2/3, live calendar + time grid with taken times struck through) and Booking confirmation (ticket, add to calendar, share). The two flow steps share one `BookingFlowViewModel` scoped to a nested `BookingFlowRoute` graph (assisted injection for route arguments). Coil 3.6.3 loads photos; Kasun's Figma portrait + 3 portfolio shots are bundled in `assets/demo` and referenced by the seed as `file:///android_asset/...` (no Cloud Storage on Spark). Figma items without a working feature yet (bell, favourites, distance, Queue/Waitlist, review quote) are left out on purpose. 97 unit tests pass. **Verified on the API 37 emulator against the local Firebase emulators:** a real booking went through the Firestore transaction + rules v3 (90 min → 9 `slotLocks` docs), appeared as "Upcoming appointment · Today", Book Again preselected its services, and the grid then struck out every overlapping start. Still not verified: Firestore's 20-document limit with a 16-slot booking on the live project. **Pending:** seed the live project again (new photo URLs).

**Phase 3 status (19 Nov 2026): DONE (engine + rules; no UI yet).** `SlotGrid` (10-min slots numbered from the epoch), pure `AvailabilityCalculator` (salon ∩ barber hours, blocks, taken slots, past), `ObserveAvailabilityUseCase`, `CreateBookingUseCase` (idempotent by client UUID), `CancelBookingUseCase`, Firestore transactions in `FirestoreBookingRepository`, rules v3 with the slot-lock "domino chain" + status table, `slotLocks` index. 80 unit tests + 53 rules tests pass (incl. T03 race, tampered client skipping a lock, T11 retry). **Not yet verified:** the real Firestore transaction on a device (Phase 4 UI will exercise it) and Firestore's 20-document rules limit for 16-slot bookings (the emulator does not enforce that limit). Rules v3 + `slotLocks` index **deployed** to `salonlink-4d991` (19 Nov 2026).

**Phase 2 status (19 Nov 2026): DONE.** Domain models (Salon, WeeklyHours, SalonService, Staff, StaffBlock, Booking, BookingStatus with its transition table), `SalonRepository` + `BookingRepository` (read side, live Flows) with Firestore implementations and mappers, Firestore rules v2 (catalogue readable by signed-in users; bookings readable only by their customer or the salon's staff/owner, incl. the "My bookings" collection-group query), composite indexes, and `firebase/seed/seed.mjs` (Aura Salon, Kasun Perera + Nadeesha Fernando, the 4 Figma services, lunch blocks, owner/staff invites). 53 unit tests + 30 rules tests pass; seed verified on the local emulator. Booking writes are Phase 3. Rules v2 + indexes **deployed** to `salonlink-4d991` (19 Nov 2026). **Pending:** seed the live project (needs a service-account key + the owner's email).

**Phase 1 status (19 Nov 2026): DONE.** Firebase email sign-in/registration, invite-based STAFF/OWNER roles, role-based root routing, Firestore rules v1 **deployed** to `salonlink-4d991`. 28 unit tests + 19 rules tests pass. Verified end to end on the emulator against the local Firebase emulators (see `firebase/emulator-test-accounts.md`): register → Customer home, wrong password → error, sign-out/sign-in, invited email → Staff home, session restored on relaunch.

> Android 17 note: apps can't reach 10.0.2.2 without ACCESS_LOCAL_NETWORK, so emulator builds use 127.0.0.1 + `adb reverse`.

**Phase 0 status (19 Nov 2026): DONE.** Builds, 6/6 unit tests pass, and it runs on the API 37 emulator: Welcome → Sign-in preview → Customer / Staff / Owner shells.

> Build note for this PC: the Windows `PATH` contains a stray `"` (after `C:\Program Files\nodejs`). It breaks Gradle's unit-test runner (and `cmd.exe` finding `node`). Fix: System Properties → Environment Variables → Path → remove the `"`.

1. **Install Android Studio** (latest stable). On first launch let it install: Android SDK, Platform-Tools, an emulator image (Pixel, latest API, x86_64). This machine currently has **no Android SDK**.
2. **Create a Firebase project** (Spark plan) at console.firebase.google.com:
   - Add Android app → package `com.group12.salonlink` → download `google-services.json` → put it in `salonLink/app/`.
   - Authentication → enable **Email/Password**.
   - Firestore → create database (production mode; our rules will be deployed).
   - **AI Logic** → enable with **Gemini Developer API** provider.
3. `npm install -g firebase-tools` then `firebase login` (for emulator + rules deploy).
4. Create a GitHub repo for the team (NFR-11). I will `git init` locally; you push.

---

## 3. Project structure (single module, package-by-feature)

```
salonLink/
├─ app/src/main/java/com/group12/salonlink/
│  ├─ SalonLinkApp.kt            // @HiltAndroidApp
│  ├─ MainActivity.kt            // single activity, hosts NavHost
│  ├─ core/
│  │  ├─ designsystem/           // Theme.kt, Color.kt, Type.kt, Shapes.kt + reusable components
│  │  │                          //   (GradientButton, SalonCard, StatusChip, StepProgress, BottomBar…)
│  │  ├─ navigation/             // Routes.kt (type-safe), SalonLinkNavHost.kt, role-based graphs
│  │  ├─ util/                   // Result wrapper, DateTime helpers (Asia/Colombo), Money formatter
│  │  └─ di/                     // Hilt modules (Firebase, repositories, dispatchers)
│  ├─ domain/
│  │  ├─ model/                  // User, Role, Salon, Service, Staff, Booking, BookingStatus, WaitlistEntry…
│  │  ├─ repository/             // interfaces only (AuthRepository, BookingRepository…)
│  │  └─ usecase/                // CheckAvailability, CreateBooking, CancelBooking, UpdateStatus,
│  │                             //   AddWalkIn, ComputeQueueEta, OfferFreedSlot, MatchBarber…
│  ├─ data/
│  │  ├─ firestore/              // DTOs + mappers, Firestore paths
│  │  ├─ repository/             // Firestore implementations of domain interfaces
│  │  ├─ ai/                     // GeminiConcierge, tool declarations, tool dispatcher
│  │  └─ notifications/          // NotificationHelper, ReminderWorker
│  └─ feature/
│     ├─ auth/                   // Splash, Welcome, Login, Register
│     ├─ customer/discovery/     // Figma: Discovery
│     ├─ customer/barber/        // Figma: Barber profile
│     ├─ customer/booking/       // Figma: Services → Date & time → Confirmation (shared BookingFlowViewModel)
│     ├─ customer/bookings/      // Upcoming / History / Cancel
│     ├─ customer/concierge/     // Figma: AI concierge
│     ├─ customer/queue/         // Figma: Live queue + waitlist offer
│     ├─ customer/style/         // Figma: Style profile (P3)
│     ├─ staff/                  // Today schedule, booking detail + status, walk-in
│     └─ owner/                  // Dashboard, services CRUD, staff + hours/blocks, salon details
├─ firebase/
│  ├─ firestore.rules            // role & salon boundary rules (NFR-04)
│  ├─ firestore.indexes.json
│  ├─ seed/                      // Node script seeding Aura Salon, Kasun Perera, services (from Figma)
│  └─ tests/                     // rules + concurrency tests (emulator)
└─ docs/                         // architecture diagram, test evidence, demo script
```

Every ViewModel exposes one `UiState` data class + one `onEvent(UiEvent)` function. That pattern repeats on every screen, so you can explain one screen during the demo and the examiner sees the same structure everywhere.

---

## 4. Firestore data model

```
users/{uid}                     name, email, phone, role(CUSTOMER|STAFF|OWNER), salonId?, language,
                                styleProfile{tags, preferredStyle, preferredStaffId, notes}
users/{uid}/photos/{id}         jpegBlob, label(PREVIOUS_RESULT|INSPIRATION), caption, createdAt   (P3)
users/{uid}/notifications/{id}  title, body, type, bookingId?, read, createdAt
salons/{salonId}                name, address, phone, openingHours
  /services/{id}                name, description, category, durationMin, price, staffIds[], active
  /staff/{id}                   uid, name, bio, specialties[], rating, reviewCount, photoUrl,
                                yearsExperience, portfolioUrls[], workingHours{MON:[09:00,19:00]…}
  /staff/{id}/blocks/{id}       start, end, reason                                        (P2)
  /bookings/{id}                customerId, customerName, staffId, staffName, services[snapshot],
                                start, end, totalPrice, status, type(APPOINTMENT|WALK_IN),
                                createdAt, updatedAt, checkedInAt?
  /slotLocks/{staffId}_{slot}   bookingId, staffId, slot             (anti-double-booking, §5)
  /waitlist/{id}                customerId, serviceIds, staffId?, date, windowStart, windowEnd,
                                status(WAITING|OFFERED|ACCEPTED|EXPIRED), offer{start,end,expiresAt}
staffInvites/{email}            salonId, staffId   (owner invites staff; rules allow role=STAFF on signup)
```

`BookingStatus`: BOOKED → CHECKED_IN → WAITING → IN_SERVICE → COMPLETED, plus CANCELLED and MISSED. The allowed transitions live in one place (`BookingStatus.canTransitionTo`) and are unit-tested.

---

## 5. Booking engine — how double-booking is prevented without Cloud Functions

1. The calendar is split into **10-minute slots**, numbered as minutes-since-1970 ÷ 10 (`SlotGrid`). A 50-minute booking at 17:30 occupies 5 slots. Max booking = 16 slots (rules read limit).
2. `CreateBookingUseCase` runs **one Firestore transaction**:
   - reads every `slotLocks/{staff_date_time}` doc for the interval → if any exists → `SlotTakenException`;
   - re-validates working hours and blocks;
   - writes the booking doc + all slot-lock docs atomically.
3. Two phones booking the same slot at once → Firestore retries one transaction, which then sees the locks and fails. This gives **at most one booking** (T03, NFR-03).
4. **Idempotency:** the booking ID is a client-generated UUID created once per confirm screen, so a retry after a network drop rewrites the same doc and never creates a duplicate (T11).
5. **Security rules** use `getAfter()`/`existsAfter()` as a *domino chain*: a booking needs its first lock in the same write, each lock needs the next one, up to the last slot. Locks have no update rule, so an existing lock can't be overwritten. A hacked client can't skip the engine.
   Customers see availability by reading the anonymous `slotLocks` (no names), never other people's bookings (T09). Working hours/blocks are checked client-side just before the transaction (rules can't evaluate them cheaply); a block added in that same instant is a documented race.
6. Cancel deletes the locks in the same transaction, then runs `OfferFreedSlotUseCase` (waitlist, P2).

---

## 6. Screens

**From Figma (customer):** Discovery · Barber profile · Service selection (step 1/3) · Date & time (2/3) · Booking confirmation · AI concierge · Live queue (+ earlier-slot offer) · Style profile. Bottom bar: Explore / Bookings / Concierge / My style.

**Not in Figma: I'll design these in the same visual language** (cream background, indigo→violet gradient buttons, coral accents, rounded 24 dp cards):
- Auth: Splash, Welcome, Login, Register (customer self-register; staff via invite).
- Customer: My Bookings (Upcoming / History tabs, cancel with confirm dialog), Notifications.
- Staff: Today's schedule (timeline + status chips), Booking detail (status stepper), Add walk-in (the form shown on the Figma queue screen moves here, because only staff should add walk-ins).
- Owner: Dashboard (today's bookings, completed, revenue, walk-ins), Services CRUD, Staff list + working hours + blocked times, Salon details, Invite staff.

---

## 7. Phases, effort, and which Claude model to use

Effort = estimated hands-on hours (with me coding). **Model / effort** = what to select in Claude Code for that phase. Rule of thumb: **Opus** for correctness-critical logic (engine, rules, AI safety), **Sonnet** for screen building and CRUD, **Haiku** for mechanical work.

| # | Phase | Main output | Priority | Effort | Claude model · effort | Target dates |
|---|---|---|---|---|---|---|
| 0 | Scaffold | Gradle (version catalog), Hilt, Firebase wiring, theme from Figma tokens, nav shell, CI-ready `./gradlew build` | — | 3 h | **Opus 5.5 · high** (architecture decisions last the whole project) | 19 Nov |
| 1 | Auth & roles | Login/Register, role routing, invites, `firestore.rules` v1 | P1 | 4 h | **Sonnet 5.5 · medium** (UI); Opus · high for the rules file | 19–20 Nov |
| 2 | Data layer & seed | Domain models, repositories, mappers, seed script (Aura Salon, Kasun, 4 services) | P1 | 3 h | **Sonnet 5.5 · medium** | 20 Nov |
| 3 | Booking engine | Availability calculator, slot locks, create/cancel transactions + unit tests | P1/P2 | 5 h | **Opus 5.5 · xhigh** (the riskiest code in the project: concurrency + time zones) | 21–22 Nov |
| 4 | Customer booking UI | Discovery, Barber profile, Services, Date & time, Confirmation (pixel-close to Figma) | P1 | 6 h | **Sonnet 5.5 · medium** | 22–24 Nov |
| 5 | My bookings & notifications | Upcoming/History/Cancel, in-app notification feed, WorkManager reminders | P1 | 3 h | **Sonnet 5.5 · medium** | 24 Nov |
| 6 | Staff & owner | Schedule, status updates, walk-ins, daily dashboard, services/staff CRUD, hours & blocks | P1/P2 | 7 h | **Sonnet 5.5 · high** (many screens, role rules) | 25–27 Nov |
| 7 | Waitlist & live queue | Join waitlist, freed-slot offer with 10-min expiry + atomic accept, live queue + ETA ring | P2 | 5 h | **Opus 5.5 · high** (race between two waitlisted customers) | 28–30 Nov |
| 8 | AI concierge — **ON HOLD** | Firebase AI Logic + function calling (`listServices`, `listStaff`, `checkAvailability`, `proposeBooking`), chat UI, proposal card, Confirm → engine | P2 | 5 h | **Opus 5.5 · high** (prompt + tool safety: T04–T06) | 1–3 Dec |
| 9 | Style profile & matching | Profile tags, moodboard photos (add/delete, consent), note for barber, barber-matching score, EN/සිං/தமிழ் switch | P3 | 5 h | **Sonnet 5.5 · medium** | 4–7 Dec |
| 10 | Testing & hardening | Rules tests, concurrency test, Compose UI test of booking flow, error/offline states, a11y, 2 screen sizes | All | 5 h | **Sonnet 5.5 · medium** to write tests, then `/code-review high` on **Opus 5.5** | 8–11 Dec |
| 11 | Docs & demo | README, architecture & ER diagrams, test evidence table (T01–T12), demo script, signed APK | — | 3 h | **Haiku 5.5 · low** for docs; Sonnet for the demo script | 12–14 Dec |
| | **Total** | | | **~54 h** | | Buffer: 15 Dec |

Tip: switch model per phase in the app's model picker. Keep **one Claude session per phase**, so each session's context stays small and the git history maps cleanly to phases (useful as contribution evidence).

---

## 8. Coding standards (for evaluation readability)

- **Comments that teach:** KDoc on every public class and function. A short `// WHY:` comment wherever a decision isn't obvious, such as why a transaction is used or why a slot is 10 minutes. Every screen file starts with a header naming its Figma frame and the user story / FR it covers (e.g. `// Covers: US-03, FR-03 · Figma: "Service selection"`).
- **No hard-coded strings or colours in screens:** text goes in `strings.xml` (localizable), colours and spacing in the design system.
- **Stateless composables + previews:** each screen = `XxxRoute` (wires the ViewModel) + `XxxScreen(state, onEvent)` (pure UI with `@Preview`). The UI can then be demoed and tested without Firebase.
- Errors flow as a sealed `UiState`/`Result` and are always shown to the user (NFR-10). No silent catches.
- Time is always stored in UTC and displayed in `Asia/Colombo`.
- Secrets: `google-services.json` and the seed service-account key are git-ignored.
- One feature per commit using conventional commit messages (`feat(booking): …`).

---

## 9. Test plan mapping

| Proposal test | How it's covered |
|---|---|
| T01 Login/roles | Compose UI test per role + rules tests |
| T02 Booking | `CreateBookingUseCase` unit test + UI test of the 3-step flow |
| T03 Conflict | Emulator test: 2 parallel transactions on the same slot → exactly 1 succeeds |
| T04–T06 AI | Tool-dispatcher unit tests with scripted model replies; assert no booking is created before Confirm |
| T07 Cancellation recovery | Emulator test: cancel → offer → accept revalidated |
| T08 Live queue | `ComputeQueueEtaUseCase` unit tests (mixed booked + walk-in) |
| T09 Permission isolation | Rules test: customer A reads customer B's booking → denied |
| T11 Network failure | Idempotent retry test + offline UI state |
| T12 Compatibility | Run on 2 emulator sizes (phone + small phone), screenshots in docs |

---

## 10. Out of scope / honest limitations

- AI hairstyle *image generation* preview (proposal says "may"): stretch goal only.
- Server-sent FCM push and Cloud Functions require the Blaze plan. The code is structured so they can be added later without UI changes.
- Single salon in the demo data, but every query is scoped by `salonId` (NFR-09).
- **Waitlist without a server (Spark):** the cancelling device (customer or staff) picks who gets a freed slot, so every signed-in user can read waitlist entries (they hold a uid, never a name) and can write an offer onto a waiting entry. A fake offer can't create a bad booking (accepting goes through the booking engine), but it can send a misleading notification. An offer is a 10-minute *first chance*, not a reservation: the time stays bookable by others. With Cloud Functions (Blaze) the offer would be made server-side and the waitlist made private.
