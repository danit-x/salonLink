# SalonLink — Architecture

EC5208 Mobile Application Development · Group 12

The diagrams are Mermaid. GitHub and Android Studio (Markdown preview) draw them directly.

## 1. Layers

SalonLink follows Google's *Guide to App Architecture*: **UI → domain → data**, with MVVM and
unidirectional data flow. Each screen is a stateless `XxxScreen(state, onEvent)` with a
`@Preview`, plus a ViewModel that exposes one `UiState` and one `onEvent()` function.

```mermaid
flowchart TB
    subgraph UI["UI layer (feature/*)"]
        S["Compose screens<br/>stateless: XxxScreen(state, onEvent)"]
        VM["ViewModels<br/>StateFlow&lt;UiState&gt; + onEvent()"]
        S -- "events (taps)" --> VM
        VM -- "UiState" --> S
    end

    subgraph Domain["Domain layer (domain/*): pure Kotlin, unit-tested"]
        UC["Use cases<br/>CreateBooking · CancelBooking · UpdateBookingStatus<br/>OfferFreedSlot · AcceptWaitlistOffer · ObserveAvailability"]
        CALC["Pure calculators<br/>SlotGrid · AvailabilityCalculator · BookingPolicy<br/>QueueEtaCalculator · ReminderPlanner"]
        RI["Repository interfaces"]
        UC --> CALC
        UC --> RI
    end

    subgraph Data["Data layer (data/*)"]
        FR["Firestore repositories<br/>(Flows from snapshot listeners, transactions)"]
        AR["FirebaseAuthRepository"]
        WM["WorkManager reminder scheduler"]
    end

    subgraph Firebase["Firebase Spark (free plan)"]
        AUTH["Authentication<br/>(email + password)"]
        FS["Cloud Firestore<br/>+ security rules v6"]
    end

    VM --> UC
    VM --> RI
    RI -. "implemented by (Hilt)" .-> FR
    RI -. "implemented by (Hilt)" .-> AR
    RI -. "implemented by (Hilt)" .-> WM
    FR --> FS
    AR --> AUTH
    AR --> FS
```

| Package | What lives there |
|---|---|
| `core/designsystem` | Theme (Figma tokens), gradient buttons, cards, `SalonPhoto`, `FormBanner` |
| `core/navigation` | Type-safe `@Serializable` routes, role shells (customer / staff / owner) |
| `core/notifications` | `SystemNotifier`: notification channels for reminders and salon updates |
| `domain/model` | `Booking`, `BookingStatus` (with its transition table), `Salon`, `Staff`, `WaitlistEntry`… |
| `domain/booking` | `SlotGrid`, `AvailabilityCalculator`, `BookingPolicy` (60-day window, 24 h cancel rule) |
| `domain/usecase` | Booking engine and waitlist use cases |
| `data/firestore` | Mappers, `FirestoreSchema` (field names), Flow helpers, `awaitWrite()` |
| `data/repository` | Firestore and Firebase Auth implementations |
| `feature/*` | One folder per screen: `XxxScreen.kt` + `XxxViewModel.kt` |

**Why no Cloud Functions:** they need the paid Blaze plan. Everything a server would usually
guarantee is done with **Firestore transactions and security rules** instead (see §3).

## 2. Firestore data model (ER diagram)

```mermaid
erDiagram
    USER ||--o{ NOTIFICATION : "has feed"
    USER ||--o{ BOOKING : "books (customerId)"
    USER ||--o{ WAITLIST_ENTRY : "waits (customerId)"
    INVITE |o--o| USER : "gives role on sign-up"
    SALON ||--o{ SERVICE : offers
    SALON ||--o{ STAFF : employs
    STAFF ||--o{ BLOCK : "time off"
    SALON ||--o{ BOOKING : holds
    STAFF ||--o{ BOOKING : serves
    BOOKING ||--|{ SLOT_LOCK : "locks 1 per 10 min"
    SALON ||--o{ WAITLIST_ENTRY : has
    STAFF ||--o| QUEUE_BOARD : "live queue"

    USER {
        string uid PK "users/{uid}"
        string name
        string email
        string role "CUSTOMER | STAFF | OWNER"
        string salonId "staff/owner only"
        string staffId "staff only"
    }
    INVITE {
        string email PK "invites/{email}"
        string role
        string salonId
        string staffId
    }
    NOTIFICATION {
        string id PK "deterministic: type + booking"
        string type "BOOKING_CONFIRMED | BOOKING_CANCELLED | STATUS_CHANGED | WAITLIST_OFFER"
        string bookingId
        timestamp start
        bool read
    }
    SALON {
        string id PK "salons/{id}"
        string name
        string address
        map openingHours "MON: [09:00, 19:00]"
    }
    SERVICE {
        string id PK
        string name
        string category
        int durationMin "multiple of 10, max 160"
        int price "LKR"
        list staffIds
        bool active
    }
    STAFF {
        string id PK
        string name
        list specialties
        double rating
        map workingHours
        bool active
    }
    BLOCK {
        string id PK
        timestamp start
        timestamp end
        string reason
    }
    BOOKING {
        string id PK "client UUID (idempotent)"
        string customerId
        string staffId
        list services "snapshot: name, duration, price"
        timestamp start
        timestamp end
        int startSlot
        int endSlot
        string status "BOOKED ... COMPLETED / CANCELLED / MISSED"
        string type "APPOINTMENT | WALK_IN"
    }
    SLOT_LOCK {
        string id PK "{staffId}_{slot}"
        string bookingId
        int slot "10-minute slot number"
    }
    WAITLIST_ENTRY {
        string id PK
        string customerId
        string staffId
        string date "salon-local day"
        timestamp windowStart
        timestamp windowEnd
        string status "WAITING | OFFERED | ACCEPTED | LEFT"
        map offer "start, end, expiresAt (10 min)"
    }
    QUEUE_BOARD {
        string staffId PK "salons/{id}/queue/{staffId}"
        list entries "anonymous: start, duration, status"
        timestamp updatedAt
    }
```

Privacy: customers never read other people's bookings (T09). They see availability through the
**anonymous** `slotLocks` and the live queue through the **anonymous** queue boards, which hold
no names.

## 3. Booking engine: no double booking without a server

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant VM as BookingFlowViewModel
    participant UC as CreateBookingUseCase
    participant TX as Firestore transaction
    participant R as Security rules

    C->>VM: Confirm 17:30 (Haircut + Beard, 50 min)
    VM->>UC: BookingRequest(id = UUID made once per screen)
    UC->>UC: AvailabilityCalculator.check (hours, breaks, past, 10-min grid)
    UC->>TX: runTransaction
    TX->>TX: read slotLocks kasun_17:30 ... kasun_18:10 (5 docs)
    alt any lock exists
        TX-->>UC: SLOT_TAKEN (someone was faster)
        UC-->>VM: "Someone just booked that time"
    else all free
        TX->>R: write booking + 5 locks atomically
        R->>R: domino chain: booking needs lock 1, lock n needs lock n+1 (getAfter)
        R-->>TX: allowed
        TX-->>UC: committed
        UC-->>VM: booking (+ "Booking confirmed" feed entry, best effort)
    end
```

- **T03, two phones at once:** Firestore runs the transactions one after the other. The second
  one sees the locks and fails, so at most one booking can exist.
- **T11, network drop:** the booking id is a UUID created once per Confirm screen. A retry
  rewrites the same document and never makes a second booking.
- **Cancel / no-show:** one transaction marks the booking and deletes its locks. Then
  `OfferFreedSlotUseCase` offers the time to the first matching waitlisted customer (FIFO,
  10 minutes). Accepting goes through the same engine, so two people can't both win it.

## 4. Booking status machine

```mermaid
stateDiagram-v2
    [*] --> BOOKED : customer books / staff add walk-in
    BOOKED --> CHECKED_IN : staff check in
    BOOKED --> IN_SERVICE : start straight away
    CHECKED_IN --> WAITING
    CHECKED_IN --> IN_SERVICE : start
    WAITING --> IN_SERVICE : start
    IN_SERVICE --> COMPLETED : complete
    BOOKED --> CANCELLED : customer (>= 24 h before) or staff
    CHECKED_IN --> CANCELLED : staff
    WAITING --> CANCELLED : staff
    BOOKED --> MISSED : no-show (after the start)
    COMPLETED --> [*]
    CANCELLED --> [*]
    MISSED --> [*]
```

The table lives in one place (`BookingStatus.canTransitionTo`) and is copied into the rules. Staff
screens show only the buttons the table allows (`Booking.staffActions`), so the UI never offers a
move the rules would refuse.

## 5. Background work and notifications

| What | How | Why this way |
|---|---|---|
| Reminders 24 h and 2 h before | WorkManager (`BookingReminderWorker`, Hilt worker), re-planned on every change to "my bookings" | Works offline and survives restarts. The worker re-reads the booking, so a cancelled one stays silent. |
| Salon updates (checked in, completed, waitlist offer) | Firestore feed `users/{uid}/notifications`, turned into phone notifications while the app runs | Sending FCM push needs a server (Blaze). |
| Live queue | The staff/owner app publishes an anonymous board per barber whenever today's bookings change. Customers compute position and ETA with `QueueEtaCalculator`. | No server to compute it. |
| Offline writes | Transactions fail fast with a NETWORK error. Plain writes wait at most 8 s, then count as queued on the phone (`awaitWrite`). | No spinner stuck forever while offline. |

## 6. Known limitations (Spark plan)

- The waitlist offer is made by the cancelling device, so waitlist entries are readable by any
  signed-in user (they hold a uid, never a name), and an offer is a first chance, not a
  reservation. With Cloud Functions this would move to the server.
- No server push: notifications appear while the app runs, and reminders are local.
- AI concierge (Phase 8) is on hold. The Concierge tab shows a "coming soon" screen.
