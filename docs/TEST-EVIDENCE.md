# SalonLink — Test evidence (T01–T12)

EC5208 · Group 12 · Results recorded 19 Nov 2026 on the API 37 emulator (`SalonLink_Pixel`, 1080×2400).

## Totals

| Suite | Where | Count | Result | Command |
|---|---|---|---|---|
| Unit tests (JVM) | `app/src/test` | 138 | all pass | `./gradlew :app:testDebugUnitTest` |
| Security-rules tests (Firestore emulator) | `firebase/tests/rules.test.mjs` | 78 | all pass | `firebase emulators:exec --only firestore --project demo-salonlink "npm --prefix firebase/tests test"` |
| Compose UI tests (emulator) | `app/src/androidTest` | 5 | all pass | `./gradlew :app:connectedDebugAndroidTest` |
| Manual end-to-end runs | emulator + local Firebase emulators | see below | as described | `firebase/emulator-test-accounts.md` |

## Proposal tests

| Test | What it checks | Evidence |
|---|---|---|
| **T01** Login and roles | Customers self-register. Staff and owners get their role only from an invite, and nobody can raise their own role. | Rules: *Registering a profile* (8 tests: no self-made OWNER, invite can't be reused for another salon, extra fields refused). Unit: `SignInViewModelTest`, `RegisterViewModelTest`, `UserMappersTest` (unknown role → CUSTOMER). Manual: the customer, staff and owner accounts each open their own shell. |
| **T02** Booking | The 3-step flow creates a booking with a price and duration snapshot. | Unit: `CreateBookingUseCaseTest` "T02 – the Figma booking is created…", `BookingFlowViewModelTest` "T02 – confirm saves the booking…". UI: `BookingFlowScreensTest` (totals, Continue, select + confirm). Manual: booking Fri 20 Nov 10:00 → ticket SL-62EB34; 90 min → 9 `slotLocks` docs. |
| **T03** Conflict | Two phones booking the same time → exactly one booking. | Rules: "T03: two customers racing for the same slot → exactly one wins", "T03: a second booking overlapping a taken slot is refused". Unit: "T03 – a slot taken by another phone…", "T03 – losing the race explains why and clears the time", "T03 – two offers for the same time still give only one booking". UI: `taken_time_is_announced_and_cannot_be_picked`, `a_slot_taken_at_confirm_time_shows_a_clear_error`. |
| **T04–T06** AI concierge | — | **Not tested: Phase 8 (AI concierge) is on hold.** The Concierge tab shows a "coming soon" screen. |
| **T07** Cancellation recovery | A freed slot goes to the first person waiting, who can book it once. | Unit (`WaitlistAndQueueTest`): FIFO offer, time window respected, accept books once (retry-safe), expired offer refused, no-show offer starts on the 10-minute grid, the canceller isn't offered their own time, refused ACCEPTED → entry leaves the line. Rules: "only the customer accepts, and only before the offer expires". **Manual (emulator):** customer 2 booked 10:00 → customer 1 joined the waitlist → owner cancelled → offer + phone notification → "Book it" → confirmed booking. |
| **T08** Live queue | Position and ETA with booked + walk-in customers. | Unit: `QueueEtaCalculator` tests in `WaitlistAndQueueTest` ("T08 – walk-ins and bookings ahead push my start back" and others). Rules: "staff publish the anonymous queue board, customers only read it". |
| **T09** Permission isolation | Customer A can't read customer B's booking, profile or feed. | Rules: *Reading bookings (T09)* incl. "a customer cannot read another customer's booking"; *Profile privacy (T09)*; "a customer cannot write into another customer's feed". |
| **T10** | *Not listed in the plan's test mapping; check against the proposal before submission.* | — |
| **T11** Network failure | A retry after a dropped connection never books twice; offline states are visible. | Unit: "T11 – confirming twice with the same id makes only one booking", "T11 – after a network error, retrying makes exactly one booking". Rules: "T11: checking whether a booking id exists is allowed before it's written", "no duplicates on retry" for the feed. Code: transactions fail fast offline with a clear message; plain writes stop waiting after 8 s (`awaitWrite`). |
| **Phase 9** Style profile, matching, languages | Match score, consent-gated sharing and photos, private profile, language switch. | Unit: `BarberMatcherTest` (5), Discovery "best match is featured, favourite first", BookingFlow "style note only when shared". Rules: *Style profile and photos (v7)* (private, shape, consent + 300 KB limit). Manual: 100 % / 19 % match, photo added (35 KB), booking note "Style: Fade, Beard · Low fade, keep the top", Sinhala/Tamil screenshots `docs/screenshots/p9-*.png`. |
| **T12** Compatibility | Works on a normal and a small phone. | Screenshots in `docs/screenshots/`: `t12-phone-explore.png` (1080×2400, 420 dpi) and `t12-small-*.png` (720×1280, 320 dpi): Explore, upcoming appointment, barber profile, services step. Content scrolls and the main button stays pinned and visible. minSdk 26 (Android 8.0). |

## Code review (Phase 10)

A `/code-review high` pass over Phases 4–10 found 10 issues. 8 are fixed in commit `7a59577`
(misaligned no-show offers, a stale "today" on an all-day tablet, accept-at-expiry, offers
returned to the canceller, duplicate feed listeners, double announcements, the default booking
day, and the wording of the offer hold). The 2 waitlist-rules findings need a server and are
documented as Spark-plan limitations in `docs/ARCHITECTURE.md` §6.
