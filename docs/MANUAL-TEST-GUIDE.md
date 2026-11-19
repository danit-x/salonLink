# SalonLink — Manual test guide

A step-by-step checklist for trying every feature by hand on the Android emulator. Each check
says what to do and what you should see. Tick the box when it works; if it doesn't, write down
the step number and what you saw.

> Tests run against the **local Firebase emulators**, not the real project, so you can book,
> cancel and delete freely. Everything resets when the emulators stop.

---

## 0. Before you start

### Is everything running?

You need four things. If the app already opens and you can sign in, skip to **Accounts**.

| # | What | How to start it (Git Bash or PowerShell, in the project folder) |
|---|---|---|
| 1 | Android emulator | Android Studio → Device Manager → ▶ **SalonLink_Pixel** |
| 2 | Firebase emulators | `firebase emulators:start --only auth,firestore --project salonlink-4d991` (leave this window open) |
| 3 | Demo data | `npm --prefix firebase/seed run seed:emulator` (after every emulator restart) |
| 4 | Port forwarding + app | `adb reverse tcp:9099 tcp:9099`, `adb reverse tcp:8085 tcp:8085`, then `./gradlew installDebug -Psalonlink.useEmulators=true` |

After an emulator restart, do steps 3 and 4 again, and register the accounts again (see below).

### Accounts

| Role | Email | Password | Notes |
|---|---|---|---|
| Customer | `customer.test@salonlink.test` | `Customer!Test123` | main tester |
| Customer 2 | `customer2.test@salonlink.test` | `Customer!Test123` | second customer for waitlist / clash tests |
| Staff (Kasun) | `staff.test@salonlink.test` | `Staff!Test123` | invited as Kasun Perera's staff account |
| Owner | `owner.test@salonlink.test` | `Owner!Test123` | owner of Aura Salon |

If an account doesn't exist (fresh emulators), tap **Create an account** and register with
that email and password. Staff and owner emails get their role from the invite the seed made.

### Switching accounts

- **Customer:** My style tab → scroll to the bottom → **Sign out**.
- **Staff:** Salon tab → **Sign out**.
- **Owner:** Salon tab → scroll to the bottom → **Sign out**.

### Good to know

- Salon hours: **9:00–19:00 Colombo time, closed Sunday**. Kasun has a lunch break at 1:00 pm.
  After 19:00 there are no times today, so the booking screen opens on tomorrow.
- **Walk-ins and the live queue only work for today, during opening hours.** Test those
  between 9:00 and 18:30.
- Customers can cancel in the app only **more than 24 hours** before the start. Book 2+ days
  ahead when you want to test cancelling as a customer.

---

## 1. Sign-in and roles (T01)

- [ ] **1.1** Welcome → **I already have an account** → sign in as **Customer**.
  → Explore opens with "Good evening/morning, Test" and four tabs: Explore, Bookings, Concierge, My style.
- [ ] **1.2** Sign out, then sign in with the customer email and a **wrong password**.
  → A red message: "That email and password don't match."
- [ ] **1.3** Sign in as **Staff**. → Staff app: tabs Today, Walk-ins, Salon.
- [ ] **1.4** Sign in as **Owner**. → Owner app: tabs Dashboard, Services, Staff, Salon.
- [ ] **1.5** Close the app completely (swipe it away) and open it again.
  → You are still signed in (no sign-in screen).
- [ ] **1.6** Register a **new** customer with any email (e.g. `you1@test.com`) and a password of 8+ characters.
  → Lands on Explore as a customer. Try a short password or bad email first: you get a message under the field.

## 2. Explore and barber profile (customer)

- [ ] **2.1** Explore shows the bell, search box, category bubbles, "Your next great cut" (Kasun) and "More barbers" (Nadeesha).
- [ ] **2.2** Type `beard` in search. → Only Kasun (he does Beard Trim). Type `xyz` → "No barber matches that yet".
- [ ] **2.3** Tap **Colour**. → Barbers who do colour. Tap it again → filter cleared.
- [ ] **2.4** Tap Kasun's card → **Meet your barber**: photo, rating, specialties, 3 portfolio photos, "Book with Kasun".

## 3. Booking (T02, T03)

- [ ] **3.1** **Book with Kasun** → Step 1: tick **Haircut** and **Beard Trim**.
  → Bottom shows "2 services selected · 50 minutes of you time" and **Rs. 2,300**. Untick both → the button goes grey.
- [ ] **3.2** **Choose date & time** → Step 2: calendar (Sundays and past days greyed), time grid for the selected day.
  → 1:00 pm is crossed out (lunch). Times already booked are crossed out.
- [ ] **3.3** Pick a day **2+ days ahead**, pick a time → **Confirm appointment**.
  → "All booked" ticket with date, time, Haircut & Beard Trim, Rs. 2,300 and a reference like SL-XXXXXX.
- [ ] **3.4** **Add to calendar** opens a calendar app (or says no app can do it). The **share** icon (top right) opens the share sheet.
- [ ] **3.5** Back to Explore → "Upcoming appointment" shows this booking. The bell now has a dot (a "Booking confirmed" notification).
- [ ] **3.6 No double booking:** sign in as **Customer 2**, book Kasun on the **same day**.
  → The time you booked in 3.3 is **crossed out** and can't be tapped.

## 4. My bookings, cancelling, notifications

- [ ] **4.1** Bookings tab → **Upcoming** lists your bookings, soonest first; **History** shows past and cancelled ones.
- [ ] **4.2** On the booking from 3.3 (2+ days ahead) tap **Cancel booking** → confirm.
  → "Booking cancelled." It moves to History. The time is free again on the booking screen.
- [ ] **4.3** A booking **less than 24 h** away shows "Less than 24 hours to go: please call the salon to cancel" instead of a cancel button.
- [ ] **4.4** Bell on Explore → **Notifications**: "Booking confirmed" / "Booking cancelled" entries. **Mark all read** removes the dot.

## 5. Waitlist (T07): needs 2 customers + owner

- [ ] **5.1** As **Customer 2**, book Kasun **tomorrow at 10:00** (or any free time).
- [ ] **5.2** Sign in as **Customer** → book Kasun → Haircut → pick the same day → scroll under the times → **Join the waitlist for this day**.
  → "You're on the waitlist for …". Bookings tab shows a **Waitlist** card "Waiting for …".
- [ ] **5.3** Sign in as **Owner** → Dashboard → **>** to that day → on Customer 2's booking tap **Cancel** → **Confirm**.
- [ ] **5.4** Sign in as **Customer** within 10 minutes.
  → A phone notification "An earlier time is free", and Bookings shows **"A time freed up: …"** with **Book it** / **No thanks**.
- [ ] **5.5** Tap **Book it**. → The booking appears under Upcoming, Confirmed.
- [ ] **5.6** (optional) Repeat, but wait 10+ minutes before 5.4: the offer is gone, and the entry goes back to waiting.

## 6. Staff and owner

Do these **today between 9:00 and 18:30** so there are free times today.

- [ ] **6.1** **Owner** → Dashboard: Bookings / Completed / Walk-ins / Revenue cards, barber filter (Everyone / Kasun / Nadeesha), **<** **>** to change day, "Back to today".
- [ ] **6.2** **Add a walk-in** (bottom right): guest name, barber, services, a start time → **Add walk-in**.
  → It appears on today's list with a **Walk-in** tag; the Walk-ins number goes up.
- [ ] **6.3** On a today booking: **Check in** → **Start** → **Complete**. Status chip changes each time; Completed and Revenue go up.
  If it was a customer's booking, that customer gets "You're checked in" / "All done" in their notifications.
- [ ] **6.4** **No-show** (only shown after the start time) and **Cancel** ask first ("Mark as no-show?" / "Cancel this booking?").
- [ ] **6.5** **Services** tab: add a service (duration in steps of 10, e.g. 40), edit a price, switch "Visible to customers" off.
  → As a customer, the hidden service no longer appears in Step 1.
- [ ] **6.6** **Staff** tab → Kasun: edit bio, working hours, add **time off** for a day.
  → As a customer, that time is blocked on the booking screen.
- [ ] **6.7** **Salon** tab: change opening hours or phone → **Save** → "Saved."
- [ ] **6.8** Sign in as **Staff** → Today shows only **Kasun's** bookings; Walk-ins tab adds a walk-in for Kasun.

## 7. Live queue (T08): today, during opening hours

- [ ] **7.1** As **Customer**, book Kasun **today**. Add 1–2 walk-ins before that time as **Owner/Staff**, and keep the owner Dashboard open on today (it publishes the queue).
- [ ] **7.2** As **Customer**: open the booking ticket → **See live queue** (or Explore → **Queue**).
  → "You're #N", expected start time, and who is ahead (walk-ins / booked, no names).
- [ ] **7.3** As **Owner**, **Start** the person ahead. → The customer's position and ETA update within about 30 seconds.

## 8. My style, matching, languages (Phase 9)

- [ ] **8.1** **My style** tab: tick **Fade** and **Beard**.
  → Favourite barber list: **Kasun 100% match**, **Nadeesha 19% match**.
- [ ] **8.2** Write a note (e.g. "Low fade, keep the top"), switch **Share my style with my barber** on → **Save my style** → "Saved."
- [ ] **8.3** Tap Nadeesha as favourite → Save → Explore: Nadeesha is now the big card; Kasun shows "% match" under More barbers.
- [ ] **8.4** Book Kasun (any time). Sign in as **Owner**, open that day.
  → The booking shows **"Note: Style: Fade, Beard · Low fade, keep the top"**. Switch sharing off and book again → no note.
- [ ] **8.5** Moodboard → **Inspiration** → **I agree** (first time only) → pick a photo → **Done**.
  → The photo appears with an "Inspiration" label. The **✕** deletes it.
  (To have a photo to pick: drag any JPG onto the emulator window, or use the camera app.)
- [ ] **8.6** Language → **සිංහල**. → The screen reloads in Sinhala; tabs, Explore and booking screens are Sinhala.
  Close and reopen the app → still Sinhala. Try **தமிழ்** too.
  Staff/owner screens stay English on purpose; dates and times stay in English format.
- [ ] **8.7** Set the language back to **English** (or Phone default) when you're done.

## 9. Error handling (T11)

- [ ] **9.1** Stop the Firebase emulators (Ctrl+C in their window), then try to **confirm a booking**.
  → A red message "No connection. Check your internet and try again." No spinner stuck forever.
- [ ] **9.2** Start the emulators again (they will be empty: seed again, re-register accounts).

## 10. Small screen (T12)

- [ ] **10.1** In a terminal: `adb shell wm size 720x1280` and `adb shell wm density 320`.
  → Explore, booking steps and My style still fit; content scrolls; main buttons stay visible.
- [ ] **10.2** Restore: `adb shell wm size reset` and `adb shell wm density reset`.

---

### Not testable (by design)

- **Concierge tab:** AI concierge is on hold. It shows a "coming soon" screen.
- **Push notifications while the app is closed:** needs a server (Blaze plan). Notifications appear while the app runs; reminders (24 h / 2 h before) are scheduled locally.

### Reporting a problem

Note: step number, account used, what you did, what you expected, what you saw (a screenshot
helps: the camera icon in the emulator side bar).
