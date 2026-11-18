# Testing against the local Firebase emulators

Use the local emulators to create and delete throwaway accounts without touching the real
`salonlink-4d991` project. Everything is wiped when the emulators stop.

1. Start the emulators (from the project root):

   ```
   firebase emulators:start --only auth,firestore --project salonlink-4d991
   ```

2. Forward the emulator ports to the phone or emulator (repeat after every phone/emulator restart):

   ```
   adb reverse tcp:9099 tcp:9099
   adb reverse tcp:8085 tcp:8085
   ```

   Why: Android 17 blocks apps from reaching local-network addresses like 10.0.2.2 without a
   special permission, but the phone's own 127.0.0.1 is allowed.

3. Build and install the app in emulator mode:

   ```
   ./gradlew installDebug -Psalonlink.useEmulators=true
   ```

4. Load the demo data (Aura Salon, Kasun & Nadeesha, 4 services, lunch blocks, test invites).
   Run it again after every emulator restart, since the emulator starts empty:

   ```
   npm --prefix firebase/seed install
   npm --prefix firebase/seed run seed:emulator
   ```

## Test accounts

| Role     | Email                        | Password         | How it gets its role |
|----------|------------------------------|------------------|----------------------|
| Customer | customer.test@salonlink.test | Customer!Test123 | Self-registered (default role) |
| Staff    | staff.test@salonlink.test    | Staff!Test123    | Invite `{role: STAFF, salonId: aura, staffId: kasun}` created by the seed script; register after seeding |
| Owner    | owner.test@salonlink.test    | Owner!Test123    | Invite `{role: OWNER, salonId: aura}` created by the seed script; register after seeding |
