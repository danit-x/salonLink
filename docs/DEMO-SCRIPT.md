# SalonLink — Demo script (about 10 minutes)

EC5208 · Group 12. Run this against the **local Firebase emulators** so the demo can create and cancel
bookings freely (setup: `firebase/emulator-test-accounts.md`).

## Before the demo (10 minutes beforehand)

1. Start the emulators: `firebase emulators:start --only auth,firestore --project salonlink-4d991`
2. Seed: `npm --prefix firebase/seed run seed:emulator`
3. Forward ports: `adb reverse tcp:9099 tcp:9099` and `adb reverse tcp:8085 tcp:8085`
4. Install: `./gradlew installDebug -Psalonlink.useEmulators=true`
5. Register the four accounts (or create them with the Admin SDK): customer, customer 2, staff, owner.
6. Pick a **demo day = tomorrow** (salon hours 9:00–19:00, Sunday closed). Allow notifications.

Tip: switching accounts is fastest with *Settings → Apps → SalonLink → Clear storage*, or
`adb shell pm clear com.group12.salonlink`.

## 1. Customer books (T02, about 3 min) · sign in as **customer 2**

| Do | Say |
|---|---|
| Explore: point at the greeting, categories, "Your next great cut" (Kasun) | "Discovery from the Figma file, live from Firestore." |
| Tap **See times → Book with Kasun** | "Real barber profile: specialties, portfolio, 24-hour cancellation policy." |
| Tick **Haircut** + **Beard Trim** | "Total and duration update as you tap: Rs. 2,300, 50 minutes." |
| **Choose date & time**, pick tomorrow, then **10:00 am**, then **Confirm appointment** | "One Firestore transaction writes the booking and one lock per 10 minutes. The rules check the lock chain, so no double booking even without a server." |
| Ticket appears, then **View my booking** | "Pay at the salon; reminders are planned 24 h and 2 h before." |

## 2. Double booking is impossible (T03, about 1 min) · sign in as **customer**

| Do | Say |
|---|---|
| Book with Kasun, Haircut, tomorrow | "10:00 is struck through: this phone only sees anonymous locks, never the other customer's name (T09)." |
| (Optional) show `rules.test.mjs` "two customers racing for the same slot → exactly one wins" | "Proven on the emulator with parallel transactions." |

## 3. Waitlist recovery (T07, about 3 min) · still **customer**

| Do | Say |
|---|---|
| On tomorrow, tap **Join the waitlist for this day** | "No time suits me, so I wait for a cancellation." |
| Sign in as **owner**, Dashboard, **>** to tomorrow, **Cancel** on customer 2's 10:00, Confirm | "Staff can cancel any time; the freed time goes to the first person waiting." |
| Sign in as **customer**: a phone notification appears; Bookings tab shows **A time freed up … Book it** | "A 10-minute first chance. Accepting goes through the same booking engine." |
| Tap **Book it** | "Booked. If two people tried, only one would win." |

## 4. Staff day (about 2 min) · sign in as **owner** (or **staff**)

| Do | Say |
|---|---|
| Dashboard: Bookings / Completed / Walk-ins / Revenue | "Owner's day at a glance, with a barber filter." |
| On today: **Check in**, then **Start**, then **Complete** on a booking | "Buttons come from the status table, so the UI can't offer a move the rules would refuse. The customer gets 'You're checked in'." |
| **Add a walk-in** (name, service, next free time) | "Walk-ins use the same engine, so they can't collide with online bookings." |
| Customer side: Explore, **Queue** | "Live queue: position and estimated start, from an anonymous board the staff app publishes." |
| Services / Staff / Salon tabs | "The owner manages services, working hours, time off, invites and opening hours." |

## 5. Wrap-up (about 1 min)

- Architecture: MVVM + UDF, Hilt, Compose; Firebase Spark only (`docs/ARCHITECTURE.md`).
- Tests: 131 unit + 75 rules + 5 UI tests (`docs/TEST-EVIDENCE.md`).
- Honest limits: AI concierge on hold; no server push on Spark; waitlist offers are made by the cancelling device.

## If something goes wrong

| Symptom | Fix |
|---|---|
| App stuck on a spinner after launch | Emulators not running or `adb reverse` lost after an emulator restart. Redo steps 1–3. |
| No times shown | It's Sunday, after 19:00, or a lunch break. Pick another day. |
| "System UI isn't responding" on the emulator | Tap **Wait**: the emulator is slow right after boot. |
