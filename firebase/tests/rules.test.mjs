// =====================================================================================
// SalonLink — Firestore security rules tests
// -------------------------------------------------------------------------------------
// Covers proposal tests T01 (roles) and T09 (permission isolation), and NFR-04.
// Each test signs in as a pretend user against the LOCAL emulator (no real data is
// touched) and checks that a read/write is allowed or denied.
//
// Run from the project root:
//   firebase emulators:exec --only firestore --project demo-salonlink "npm --prefix firebase/tests test"
// =====================================================================================
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Bytes,
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from "firebase/firestore";

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-salonlink", // "demo-" projects never touch real Firebase
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
  });
});

after(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

// ---------- helpers ----------

/** A Firestore client signed in as `uid` with the given email. */
const as = (uid, email) => env.authenticatedContext(uid, { email }).firestore();

/** Writes data bypassing the rules (like the seed script / console would). */
const seed = (path, data) =>
  env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), path), data));

const customerProfile = (email, extra = {}) => ({
  name: "Kavindu Perera",
  email,
  phone: "0771234567",
  role: "CUSTOMER",
  salonId: null,
  staffId: null,
  createdAt: serverTimestamp(),
  ...extra,
});

// ---------- registration (users/{uid} create) ----------

describe("Registering a profile", () => {
  test("a new user can create their own CUSTOMER profile", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertSucceeds(setDoc(doc(db, "users/kavindu"), customerProfile("kavindu@example.com")));
  });

  test("a user cannot create someone else's profile", async () => {
    const db = as("attacker", "attacker@example.com");
    await assertFails(setDoc(doc(db, "users/kavindu"), customerProfile("attacker@example.com")));
  });

  test("a user cannot make themselves OWNER without an invite", async () => {
    const db = as("sneaky", "sneaky@example.com");
    await assertFails(
      setDoc(doc(db, "users/sneaky"), customerProfile("sneaky@example.com", { role: "OWNER", salonId: "aura" })),
    );
  });

  test("an invited email gets exactly the invited STAFF role", async () => {
    await seed("invites/kasun@aura.lk", { role: "STAFF", salonId: "aura", staffId: "kasun" });
    const db = as("kasun-uid", "kasun@aura.lk");
    await assertSucceeds(
      setDoc(
        doc(db, "users/kasun-uid"),
        customerProfile("kasun@aura.lk", { role: "STAFF", salonId: "aura", staffId: "kasun" }),
      ),
    );
  });

  test("an invited staff member cannot upgrade themselves to OWNER", async () => {
    await seed("invites/kasun@aura.lk", { role: "STAFF", salonId: "aura", staffId: "kasun" });
    const db = as("kasun-uid", "kasun@aura.lk");
    await assertFails(
      setDoc(doc(db, "users/kasun-uid"), customerProfile("kasun@aura.lk", { role: "OWNER", salonId: "aura" })),
    );
  });

  test("an invite for one salon cannot be used to join another salon", async () => {
    await seed("invites/kasun@aura.lk", { role: "STAFF", salonId: "aura", staffId: "kasun" });
    const db = as("kasun-uid", "kasun@aura.lk");
    await assertFails(
      setDoc(
        doc(db, "users/kasun-uid"),
        customerProfile("kasun@aura.lk", { role: "STAFF", salonId: "other-salon", staffId: "kasun" }),
      ),
    );
  });

  test("the profile email must be the signed-in email", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertFails(setDoc(doc(db, "users/kavindu"), customerProfile("someone-else@example.com")));
  });

  test("unknown extra fields are rejected", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertFails(
      setDoc(doc(db, "users/kavindu"), customerProfile("kavindu@example.com", { isAdmin: true })),
    );
  });
});

// ---------- reading and editing profiles ----------

describe("Profile privacy and editing (T09)", () => {
  beforeEach(async () => {
    await seed("users/kavindu", { ...customerProfile("kavindu@example.com"), createdAt: new Date() });
  });

  test("users can read their own profile", async () => {
    await assertSucceeds(getDoc(doc(as("kavindu", "kavindu@example.com"), "users/kavindu")));
  });

  test("users cannot read another customer's profile", async () => {
    await assertFails(getDoc(doc(as("nimal", "nimal@example.com"), "users/kavindu")));
  });

  test("signed-out visitors cannot read profiles", async () => {
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "users/kavindu")));
  });

  test("users can change their name and phone", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertSucceeds(updateDoc(doc(db, "users/kavindu"), { name: "Kavindu P.", phone: "0779998888" }));
  });

  test("users cannot change their own role", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertFails(updateDoc(doc(db, "users/kavindu"), { role: "OWNER" }));
  });
});

// ---------- invites ----------

describe("Invites", () => {
  beforeEach(async () => {
    await seed("users/owner-uid", {
      ...customerProfile("owner@aura.lk", { role: "OWNER", salonId: "aura" }),
      createdAt: new Date(),
    });
    await seed("users/kavindu", { ...customerProfile("kavindu@example.com"), createdAt: new Date() });
  });

  test("an owner can invite staff to their own salon", async () => {
    const db = as("owner-uid", "owner@aura.lk");
    await assertSucceeds(setDoc(doc(db, "invites/new@aura.lk"), { role: "STAFF", salonId: "aura", staffId: "s1" }));
  });

  test("an owner cannot invite another OWNER", async () => {
    const db = as("owner-uid", "owner@aura.lk");
    await assertFails(setDoc(doc(db, "invites/new@aura.lk"), { role: "OWNER", salonId: "aura" }));
  });

  test("an owner cannot invite staff into a different salon", async () => {
    const db = as("owner-uid", "owner@aura.lk");
    await assertFails(setDoc(doc(db, "invites/new@aura.lk"), { role: "STAFF", salonId: "other-salon" }));
  });

  test("a customer cannot create invites", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertFails(setDoc(doc(db, "invites/kavindu@example.com"), { role: "STAFF", salonId: "aura" }));
  });

  test("people can read only their own invite", async () => {
    await seed("invites/kasun@aura.lk", { role: "STAFF", salonId: "aura", staffId: "kasun" });
    await assertSucceeds(getDoc(doc(as("kasun-uid", "kasun@aura.lk"), "invites/kasun@aura.lk")));
    await assertFails(getDoc(doc(as("kavindu", "kavindu@example.com"), "invites/kasun@aura.lk")));
  });
});

// ---------- salon catalogue (v2) ----------

describe("Salon catalogue", () => {
  beforeEach(async () => {
    await seed("salons/aura", { name: "Aura Salon" });
    await seed("salons/aura/services/haircut", { name: "Haircut", durationMin: 30, price: 1500 });
    await seed("salons/aura/staff/kasun", { name: "Kasun Perera" });
    await seed("salons/aura/staff/kasun/blocks/lunch", { reason: "Lunch" });
  });

  test("signed-in users can browse salons, services, staff and blocks", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertSucceeds(getDoc(doc(db, "salons/aura")));
    await assertSucceeds(getDocs(collection(db, "salons/aura/services")));
    await assertSucceeds(getDocs(collection(db, "salons/aura/staff")));
    await assertSucceeds(getDocs(collection(db, "salons/aura/staff/kasun/blocks")));
  });

  test("signed-out visitors cannot browse the catalogue", async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "salons/aura")));
  });

  test("customers cannot change prices or staff (writes are closed until Phase 6)", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertFails(setDoc(doc(db, "salons/aura"), { name: "Hacked" }));
    await assertFails(updateDoc(doc(db, "salons/aura/services/haircut"), { price: 1 }));
    await assertFails(setDoc(doc(db, "salons/aura/staff/fake"), { name: "Fake Barber" }));
  });
});

// ---------- bookings: who can read what (T09) ----------

describe("Reading bookings (T09)", () => {
  const booking = (customerId, staffId = "kasun") => ({
    salonId: "aura",
    customerId,
    staffId,
    start: new Date("2026-12-03T12:00:00Z"),
    status: "BOOKED",
  });

  beforeEach(async () => {
    await seed("users/kavindu", { ...customerProfile("kavindu@example.com"), createdAt: new Date() });
    await seed("users/nimal", { ...customerProfile("nimal@example.com"), createdAt: new Date() });
    await seed("users/kasun-uid", {
      ...customerProfile("kasun@aura.lk", { role: "STAFF", salonId: "aura", staffId: "kasun" }),
      createdAt: new Date(),
    });
    await seed("users/other-staff", {
      ...customerProfile("x@other.lk", { role: "STAFF", salonId: "other-salon", staffId: "x" }),
      createdAt: new Date(),
    });
    await seed("salons/aura/bookings/b1", booking("kavindu"));
    await seed("salons/aura/bookings/b2", booking("nimal"));
  });

  test("a customer can read their own booking", async () => {
    await assertSucceeds(getDoc(doc(as("kavindu", "kavindu@example.com"), "salons/aura/bookings/b1")));
  });

  test("a customer cannot read another customer's booking", async () => {
    await assertFails(getDoc(doc(as("kavindu", "kavindu@example.com"), "salons/aura/bookings/b2")));
  });

  test("'My bookings' (collection group) works when filtered to the caller", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertSucceeds(
      getDocs(query(collectionGroup(db, "bookings"), where("customerId", "==", "kavindu"), orderBy("start", "desc"))),
    );
  });

  test("a collection-group query for everyone's bookings is refused", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertFails(getDocs(collectionGroup(db, "bookings")));
    await assertFails(getDocs(query(collectionGroup(db, "bookings"), where("customerId", "==", "nimal"))));
  });

  test("salon staff can read the whole day's bookings of their salon", async () => {
    const db = as("kasun-uid", "kasun@aura.lk");
    await assertSucceeds(getDocs(query(collection(db, "salons/aura/bookings"), where("staffId", "==", "kasun"))));
    await assertSucceeds(getDocs(collection(db, "salons/aura/bookings")));
  });

  test("staff of another salon cannot read this salon's bookings", async () => {
    const db = as("other-staff", "x@other.lk");
    await assertFails(getDocs(collection(db, "salons/aura/bookings")));
  });

  test("a customer cannot list the salon's bookings", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertFails(getDocs(collection(db, "salons/aura/bookings")));
  });

  test("a booking written without its slot locks is refused", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertFails(setDoc(doc(db, "salons/aura/bookings/b3"), booking("kavindu")));
  });
});

// ---------- the booking engine: slot locks (Phase 3, T02 / T03 / T11) ----------

describe("Booking engine (slot locks)", () => {
  const SLOT_MS = 10 * 60 * 1000;
  // 14 days from now, on the 10-minute grid. WHY not a fixed date: the rules refuse bookings in
  // the past and late cancellations (< 24 h), so a hard-coded date would break these tests later.
  const START = new Date(Math.ceil((Date.now() + 14 * 86_400_000) / SLOT_MS) * SLOT_MS);
  const START_SLOT = START.getTime() / SLOT_MS;

  /** A valid booking document for `slots` 10-minute slots starting at START + offset. */
  const bookingDoc = ({ customerId = "kavindu", slots = 5, offset = 0, type = "APPOINTMENT" } = {}) => {
    const startSlot = START_SLOT + offset;
    return {
      salonId: "aura",
      customerId,
      customerName: "Kavindu Perera",
      staffId: "kasun",
      staffName: "Kasun Perera",
      services: [{ serviceId: "haircut", name: "Haircut", durationMin: slots * 10, price: 1500 }],
      start: new Date(startSlot * SLOT_MS),
      end: new Date((startSlot + slots) * SLOT_MS),
      startSlot,
      endSlot: startSlot + slots,
      totalPrice: 1500,
      status: "BOOKED",
      type,
      note: null,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      checkedInAt: null,
    };
  };

  const lockId = (slot) => `kasun_${slot}`;

  /**
   * Writes the booking and its locks atomically, like the app's transaction does.
   * `skip` = slot numbers whose lock we "forget" (to imitate a tampered client).
   */
  const book = (db, id, data, { skip = [] } = {}) => {
    const batch = writeBatch(db);
    batch.set(doc(db, `salons/aura/bookings/${id}`), data);
    for (let s = data.startSlot; s < data.endSlot; s++) {
      if (skip.includes(s)) continue;
      batch.set(doc(db, `salons/aura/slotLocks/${lockId(s)}`), { bookingId: id, staffId: "kasun", slot: s });
    }
    return batch.commit();
  };

  /** Cancels like the app: status → CANCELLED and delete the locks, in one write. */
  const cancel = (db, id, data) => {
    const batch = writeBatch(db);
    batch.update(doc(db, `salons/aura/bookings/${id}`), { status: "CANCELLED", updatedAt: serverTimestamp() });
    for (let s = data.startSlot; s < data.endSlot; s++) batch.delete(doc(db, `salons/aura/slotLocks/${lockId(s)}`));
    return batch.commit();
  };

  const kavindu = () => as("kavindu", "kavindu@example.com");
  const nimal = () => as("nimal", "nimal@example.com");
  const kasunStaff = () => as("kasun-uid", "kasun@aura.lk");

  beforeEach(async () => {
    await seed("salons/aura", { name: "Aura Salon" });
    await seed("salons/aura/staff/kasun", { name: "Kasun Perera" });
    await seed("users/kavindu", { ...customerProfile("kavindu@example.com"), createdAt: new Date() });
    await seed("users/nimal", { ...customerProfile("nimal@example.com"), createdAt: new Date() });
    await seed("users/kasun-uid", {
      ...customerProfile("kasun@aura.lk", { role: "STAFF", salonId: "aura", staffId: "kasun" }),
      createdAt: new Date(),
    });
  });

  test("T02: a customer can book with all slot locks", async () => {
    await assertSucceeds(book(kavindu(), "b1", bookingDoc()));
  });

  test("anyone signed in can read which slots are taken (but not whose)", async () => {
    await book(kavindu(), "b1", bookingDoc());
    const db = nimal();
    await assertSucceeds(
      getDocs(query(collection(db, "salons/aura/slotLocks"), where("staffId", "==", "kasun"), where("slot", ">=", START_SLOT))),
    );
    await assertFails(getDoc(doc(db, "salons/aura/bookings/b1")));
  });

  test("T03: a second booking overlapping a taken slot is refused", async () => {
    await book(kavindu(), "b1", bookingDoc());
    // Nimal tries 17:50–18:20, which overlaps Kavindu's 17:30–18:20.
    await assertFails(book(nimal(), "b2", bookingDoc({ customerId: "nimal", offset: 2, slots: 3 })));
  });

  test("T03: two customers racing for the same slot → exactly one wins", async () => {
    const results = await Promise.allSettled([
      book(kavindu(), "race-a", bookingDoc()),
      book(nimal(), "race-b", bookingDoc({ customerId: "nimal" })),
    ]);
    assert.equal(results.filter((r) => r.status === "fulfilled").length, 1);
  });

  test("the next booking may start exactly when the previous one ends", async () => {
    await book(kavindu(), "b1", bookingDoc());
    await assertSucceeds(book(nimal(), "b2", bookingDoc({ customerId: "nimal", offset: 5, slots: 3 })));
  });

  test("a tampered client that skips a middle lock is refused (domino chain)", async () => {
    await assertFails(book(kavindu(), "b1", bookingDoc(), { skip: [START_SLOT + 2] }));
  });

  test("a tampered client that skips the last lock is refused", async () => {
    await assertFails(book(kavindu(), "b1", bookingDoc(), { skip: [START_SLOT + 4] }));
  });

  test("a lock can't be created on its own, without its booking", async () => {
    const db = kavindu();
    await assertFails(
      setDoc(doc(db, `salons/aura/slotLocks/${lockId(START_SLOT)}`), { bookingId: "ghost", staffId: "kasun", slot: START_SLOT }),
    );
  });

  test("a customer cannot book on someone else's behalf", async () => {
    await assertFails(book(kavindu(), "b1", bookingDoc({ customerId: "nimal" })));
  });

  test("a customer cannot book in the past", async () => {
    const past = { ...bookingDoc(), start: new Date("2020-01-01T00:00:00Z") };
    past.startSlot = past.start.getTime() / SLOT_MS;
    past.endSlot = past.startSlot + 5;
    past.end = new Date(past.endSlot * SLOT_MS);
    await assertFails(book(kavindu(), "b1", past));
  });

  test("a start time off the 10-minute grid is refused", async () => {
    const odd = { ...bookingDoc(), start: new Date(START.getTime() + 60_000) };
    await assertFails(book(kavindu(), "b1", odd));
  });

  test("a customer cannot create a booking that is already CHECKED_IN", async () => {
    await assertFails(book(kavindu(), "b1", { ...bookingDoc(), status: "CHECKED_IN" }));
  });

  test("the longest allowed booking (16 slots) fits Firestore's 20-document rules limit", async () => {
    await assertSucceeds(book(kavindu(), "long", bookingDoc({ slots: 16 })));
  });

  test("bookings longer than 16 slots are refused", async () => {
    await assertFails(book(kavindu(), "too-long", bookingDoc({ slots: 17 })));
  });

  test("T11: checking whether a booking id exists is allowed before it's written", async () => {
    await assertSucceeds(getDoc(doc(kavindu(), "salons/aura/bookings/not-yet")));
  });

  test("staff can add a walk-in for someone without an account", async () => {
    await assertSucceeds(book(kasunStaff(), "w1", bookingDoc({ customerId: null, type: "WALK_IN" })));
  });

  test("a customer cannot create a WALK_IN", async () => {
    await assertFails(book(kavindu(), "w1", bookingDoc({ type: "WALK_IN" })));
  });

  test("a customer can cancel their own booking, which frees the slots", async () => {
    const data = bookingDoc();
    await book(kavindu(), "b1", data);
    await assertSucceeds(cancel(kavindu(), "b1", data));
    // The time is free again: Nimal can take it.
    await assertSucceeds(book(nimal(), "b2", bookingDoc({ customerId: "nimal" })));
  });

  test("a customer cannot cancel someone else's booking", async () => {
    const data = bookingDoc();
    await book(kavindu(), "b1", data);
    await assertFails(cancel(nimal(), "b1", data));
  });

  test("locks cannot be deleted while the booking is still active", async () => {
    await book(kavindu(), "b1", bookingDoc());
    await assertFails(deleteDoc(doc(kavindu(), `salons/aura/slotLocks/${lockId(START_SLOT)}`)));
  });

  test("a customer cannot change the time or price of an existing booking", async () => {
    await book(kavindu(), "b1", bookingDoc());
    await assertFails(updateDoc(doc(kavindu(), "salons/aura/bookings/b1"), { totalPrice: 1, updatedAt: serverTimestamp() }));
  });

  test("a customer cannot cancel after check-in; staff can move the status along", async () => {
    const data = bookingDoc();
    await book(kavindu(), "b1", data);
    const ref = (db) => doc(db, "salons/aura/bookings/b1");
    await assertSucceeds(updateDoc(ref(kasunStaff()), { status: "CHECKED_IN", checkedInAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref(kavindu()), { status: "CANCELLED", updatedAt: serverTimestamp() }));
    // Not allowed by the status table: CHECKED_IN → COMPLETED skips IN_SERVICE.
    await assertFails(updateDoc(ref(kasunStaff()), { status: "COMPLETED", updatedAt: serverTimestamp() }));
  });

  test("bookings can never be deleted", async () => {
    await book(kavindu(), "b1", bookingDoc());
    await assertFails(deleteDoc(doc(kasunStaff(), "salons/aura/bookings/b1")));
  });

  test("a customer cannot cancel in the app less than 24 h before; staff still can", async () => {
    // Seeded directly: starts in 2 hours (creating it through the rules is fine too, this is quicker).
    const soonSlot = Math.ceil((Date.now() + 2 * 3600_000) / SLOT_MS);
    const soon = { ...bookingDoc(), start: new Date(soonSlot * SLOT_MS), end: new Date((soonSlot + 5) * SLOT_MS), startSlot: soonSlot, endSlot: soonSlot + 5, createdAt: new Date(), updatedAt: new Date() };
    await seed("salons/aura/bookings/late", soon);
    const ref = (db) => doc(db, "salons/aura/bookings/late");
    await assertFails(updateDoc(ref(kavindu()), { status: "CANCELLED", updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(ref(kasunStaff()), { status: "CANCELLED", updatedAt: serverTimestamp() }));
  });
});

// ---------- users/{uid}/notifications (Phase 5) ----------

describe("Notification feed", () => {
  const note = (extra = {}) => ({
    type: "BOOKING_CONFIRMED",
    salonId: "aura",
    bookingId: "b1",
    title: "Haircut",
    staffName: "Kasun Perera",
    start: new Date(Date.now() + 86_400_000 * 3),
    status: null,
    read: false,
    createdAt: serverTimestamp(),
    ...extra,
  });
  const path = (uid, id = "booking_confirmed_b1") => `users/${uid}/notifications/${id}`;
  const kavindu = () => as("kavindu", "kavindu@example.com");
  const kasunStaff = () => as("kasun-uid", "kasun@aura.lk");

  beforeEach(async () => {
    await seed("users/kavindu", { ...customerProfile("kavindu@example.com"), createdAt: new Date() });
    await seed("users/kasun-uid", {
      ...customerProfile("kasun@aura.lk", { role: "STAFF", salonId: "aura", staffId: "kasun" }),
      createdAt: new Date(),
    });
    await seed("salons/aura/bookings/b1", { customerId: "kavindu", staffId: "kasun", status: "BOOKED" });
  });

  test("a customer writes and reads their own feed", async () => {
    await assertSucceeds(setDoc(doc(kavindu(), path("kavindu")), note()));
    await assertSucceeds(getDoc(doc(kavindu(), path("kavindu"))));
  });

  test("nobody else can read a customer's feed", async () => {
    await seed(path("kavindu"), { ...note(), createdAt: new Date() });
    await assertFails(getDoc(doc(as("nimal", "nimal@example.com"), path("kavindu"))));
  });

  test("an existing entry cannot be written again (no duplicates on retry)", async () => {
    await assertSucceeds(setDoc(doc(kavindu(), path("kavindu")), note()));
    await assertFails(setDoc(doc(kavindu(), path("kavindu")), note()));
  });

  test("the only allowed change is marking as read", async () => {
    await seed(path("kavindu"), { ...note(), createdAt: new Date() });
    await assertSucceeds(updateDoc(doc(kavindu(), path("kavindu")), { read: true }));
    await assertFails(updateDoc(doc(kavindu(), path("kavindu")), { title: "Free haircut!" }));
  });

  test("salon staff can notify the customer of THEIR booking about a status change", async () => {
    await assertSucceeds(setDoc(doc(kasunStaff(), path("kavindu", "status_changed_checked_in_b1")), note({ type: "STATUS_CHANGED", status: "CHECKED_IN" })));
  });

  test("staff cannot write into the feed of someone who doesn't own the booking", async () => {
    await seed("users/nimal", { ...customerProfile("nimal@example.com"), createdAt: new Date() });
    await assertFails(setDoc(doc(kasunStaff(), path("nimal", "status_changed_checked_in_b1")), note({ type: "STATUS_CHANGED", status: "CHECKED_IN" })));
  });

  test("a customer cannot write into another customer's feed", async () => {
    await assertFails(setDoc(doc(kavindu(), path("nimal")), note()));
  });
});

// ---------- default deny ----------

describe("Everything else", () => {
  test("collections without rules are locked", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertFails(setDoc(doc(db, "secrets/x"), { a: 1 }));
    await assertFails(getDoc(doc(db, "secrets/x")));
  });
});

// ---------- Owner management of the catalogue (v5, Phase 6) ----------

describe("Owner manages the salon", () => {
  const owner = () => as("owner-uid", "owner@aura.lk");
  const staffUser = () => as("kasun-uid", "kasun@aura.lk");
  const service = (extra = {}) => ({
    name: "Haircut", description: "Cut & finish", category: "HAIRCUT",
    durationMin: 30, price: 1500, staffIds: ["kasun"], active: true, ...extra,
  });
  const staffDoc = (extra = {}) => ({
    name: "Amali Silva", bio: "", specialties: ["Fade"], rating: 0, reviewCount: 0, yearsExperience: 2,
    photoUrl: null, portfolioUrls: [], workingHours: { MON: ["09:00", "17:00"] }, active: true, ...extra,
  });

  beforeEach(async () => {
    await seed("salons/aura", { name: "Aura Salon", address: "24 Havelock Road", area: "Colombo 05", phone: null, openingHours: {} });
    await seed("salons/other", { name: "Other Salon", address: "", area: "", phone: null, openingHours: {} });
    await seed("users/owner-uid", { ...customerProfile("owner@aura.lk", { role: "OWNER", salonId: "aura" }), createdAt: new Date() });
    await seed("users/kasun-uid", { ...customerProfile("kasun@aura.lk", { role: "STAFF", salonId: "aura", staffId: "kasun" }), createdAt: new Date() });
    await seed("salons/aura/staff/kasun", { ...staffDoc({ name: "Kasun Perera" }), rating: 4.9, reviewCount: 128 });
  });

  test("the owner adds and edits a service", async () => {
    await assertSucceeds(setDoc(doc(owner(), "salons/aura/services/s1"), service()));
    await assertSucceeds(setDoc(doc(owner(), "salons/aura/services/s1"), service({ price: 1800, active: false })));
  });

  test("a service must be whole 10-minute slots and at most 160 minutes", async () => {
    await assertFails(setDoc(doc(owner(), "salons/aura/services/s1"), service({ durationMin: 25 })));
    await assertFails(setDoc(doc(owner(), "salons/aura/services/s1"), service({ durationMin: 170 })));
  });

  test("staff and customers cannot change services; owners cannot touch another salon", async () => {
    await assertFails(setDoc(doc(staffUser(), "salons/aura/services/s1"), service()));
    await assertFails(setDoc(doc(as("kavindu", "kavindu@example.com"), "salons/aura/services/s1"), service()));
    await assertFails(setDoc(doc(owner(), "salons/other/services/s1"), service()));
  });

  test("services are retired, never deleted", async () => {
    await seed("salons/aura/services/s1", service());
    await assertFails(deleteDoc(doc(owner(), "salons/aura/services/s1")));
  });

  test("the owner adds staff starting at a zero rating, and cannot fake ratings", async () => {
    await assertSucceeds(setDoc(doc(owner(), "salons/aura/staff/amali"), staffDoc()));
    await assertFails(setDoc(doc(owner(), "salons/aura/staff/fake"), staffDoc({ rating: 5, reviewCount: 999 })));
    await assertSucceeds(setDoc(doc(owner(), "salons/aura/staff/kasun"), { ...staffDoc({ name: "Kasun Perera", bio: "New bio" }), rating: 4.9, reviewCount: 128 }));
    await assertFails(setDoc(doc(owner(), "salons/aura/staff/kasun"), { ...staffDoc({ name: "Kasun Perera" }), rating: 5, reviewCount: 128 }));
  });

  test("the owner blocks time and removes it again; staff cannot", async () => {
    const block = { start: new Date("2027-01-05T07:30:00Z"), end: new Date("2027-01-05T08:00:00Z"), reason: "Lunch" };
    await assertSucceeds(setDoc(doc(owner(), "salons/aura/staff/kasun/blocks/b1"), block));
    await assertFails(setDoc(doc(owner(), "salons/aura/staff/kasun/blocks/b2"), { ...block, end: block.start }));
    await assertFails(setDoc(doc(staffUser(), "salons/aura/staff/kasun/blocks/b3"), block));
    await assertSucceeds(deleteDoc(doc(owner(), "salons/aura/staff/kasun/blocks/b1")));
  });

  test("the owner edits the salon details, staff cannot", async () => {
    const details = { name: "Aura Salon & Spa", address: "24 Havelock Road", area: "Colombo 05", phone: "0112345678", openingHours: { MON: ["09:00", "19:00"] } };
    await assertSucceeds(setDoc(doc(owner(), "salons/aura"), details));
    await assertFails(setDoc(doc(staffUser(), "salons/aura"), details));
  });
});

// ---------- Waitlist + live queue (v6, Phase 7) ----------

describe("Waitlist and live queue", () => {
  const kavindu = () => as("kavindu", "kavindu@example.com");
  const nimal = () => as("nimal", "nimal@example.com");
  const kasunStaff = () => as("kasun-uid", "kasun@aura.lk");
  const day = new Date(Date.now() + 3 * 86_400_000);
  const entry = (extra = {}) => ({
    customerId: "kavindu", staffId: "kasun", serviceIds: ["haircut"], durationMin: 30,
    date: day.toISOString().slice(0, 10),
    windowStart: new Date(day.getTime() - 4 * 3600_000), windowEnd: new Date(day.getTime() + 4 * 3600_000),
    status: "WAITING", offer: null, createdAt: serverTimestamp(), ...extra,
  });
  const offer = (minutes = 10) => ({
    start: day, end: new Date(day.getTime() + 30 * 60_000), staffId: "kasun",
    expiresAt: new Date(Date.now() + minutes * 60_000),
  });
  const ref = (db) => doc(db, "salons/aura/waitlist/w1");

  beforeEach(async () => {
    await seed("users/kasun-uid", { ...customerProfile("kasun@aura.lk", { role: "STAFF", salonId: "aura", staffId: "kasun" }), createdAt: new Date() });
  });

  test("a customer joins the waitlist for themselves only", async () => {
    await assertSucceeds(setDoc(ref(kavindu()), entry()));
    await assertFails(setDoc(doc(kavindu(), "salons/aura/waitlist/w2"), entry({ customerId: "nimal" })));
    await assertFails(setDoc(doc(kavindu(), "salons/aura/waitlist/w3"), entry({ status: "OFFERED" })));
  });

  test("someone else's cancellation can pass a freed slot as a short offer", async () => {
    await seed("salons/aura/waitlist/w1", { ...entry(), createdAt: new Date() });
    await assertSucceeds(updateDoc(ref(nimal()), { status: "OFFERED", offer: offer(10) }));
  });

  test("an offer can't last longer than ~10 minutes or change who it's for", async () => {
    await seed("salons/aura/waitlist/w1", { ...entry(), createdAt: new Date() });
    await assertFails(updateDoc(ref(nimal()), { status: "OFFERED", offer: offer(60) }));
    await assertFails(updateDoc(ref(nimal()), { status: "OFFERED", offer: offer(10), customerId: "nimal" }));
  });

  test("a live offer can't be overwritten by another one", async () => {
    await seed("salons/aura/waitlist/w1", { ...entry({ status: "OFFERED", offer: offer(10) }), createdAt: new Date() });
    await assertFails(updateDoc(ref(nimal()), { status: "OFFERED", offer: offer(9) }));
  });

  test("only the customer accepts, and only before the offer expires", async () => {
    await seed("salons/aura/waitlist/w1", { ...entry({ status: "OFFERED", offer: offer(10) }), createdAt: new Date() });
    await assertFails(updateDoc(ref(nimal()), { status: "ACCEPTED" }));
    await assertSucceeds(updateDoc(ref(kavindu()), { status: "ACCEPTED" }));
    await seed("salons/aura/waitlist/w2", { ...entry({ status: "OFFERED", offer: offer(-1) }), createdAt: new Date() });
    await assertFails(updateDoc(doc(kavindu(), "salons/aura/waitlist/w2"), { status: "ACCEPTED" }));
  });

  test("'my waitlist' works as a collection-group query filtered to the caller", async () => {
    await seed("salons/aura/waitlist/w1", { ...entry(), createdAt: new Date() });
    await assertSucceeds(getDocs(query(collectionGroup(kavindu(), "waitlist"), where("customerId", "==", "kavindu"))));
  });

  test("staff publish the anonymous queue board, customers only read it", async () => {
    const board = { entries: [{ bookingId: "b1", start: day, durationMin: 30, status: "BOOKED", type: "APPOINTMENT", since: null }], updatedAt: serverTimestamp() };
    await assertSucceeds(setDoc(doc(kasunStaff(), "salons/aura/queue/kasun"), board));
    await assertSucceeds(getDoc(doc(kavindu(), "salons/aura/queue/kasun")));
    await assertFails(setDoc(doc(kavindu(), "salons/aura/queue/kasun"), board));
  });
});

// ---------- Phase 9: style profile and moodboard (v7) ----------
describe("Style profile and photos (v7)", () => {
  const me = () => as("kavindu", "kavindu@example.com");
  const other = () => as("nimal", "nimal@example.com");
  const profile = (extra = {}) => ({
    tags: ["FADE", "BEARD"], note: "Low fade, keep the top", preferredStaffId: "kasun",
    shareWithBarber: true, photoConsent: true, updatedAt: serverTimestamp(), ...extra,
  });
  const photo = (size = 1000, extra = {}) => ({
    jpeg: Bytes.fromUint8Array(new Uint8Array(size)), label: "INSPIRATION", createdAt: serverTimestamp(), ...extra,
  });

  test("a customer saves and reads their own style profile; nobody else can", async () => {
    await assertSucceeds(setDoc(doc(me(), "users/kavindu/style/profile"), profile()));
    await assertSucceeds(getDoc(doc(me(), "users/kavindu/style/profile")));
    await assertFails(getDoc(doc(other(), "users/kavindu/style/profile")));
    await assertFails(setDoc(doc(other(), "users/kavindu/style/profile"), profile()));
  });

  test("the profile shape is checked (note length, extra fields, doc id)", async () => {
    await assertFails(setDoc(doc(me(), "users/kavindu/style/profile"), profile({ note: "x".repeat(151) })));
    await assertFails(setDoc(doc(me(), "users/kavindu/style/profile"), profile({ role: "OWNER" })));
    await assertFails(setDoc(doc(me(), "users/kavindu/style/other"), profile()));
  });

  test("photos need consent, stay private and are size-limited", async () => {
    await seed("users/kavindu/style/profile", { ...profile(), photoConsent: false, updatedAt: new Date() });
    await assertFails(setDoc(doc(me(), "users/kavindu/photos/p1"), photo()));

    await seed("users/kavindu/style/profile", { ...profile(), updatedAt: new Date() });
    await assertSucceeds(setDoc(doc(me(), "users/kavindu/photos/p1"), photo()));
    await assertFails(setDoc(doc(me(), "users/kavindu/photos/p2"), photo(300001)));
    await assertFails(setDoc(doc(me(), "users/kavindu/photos/p3"), photo(1000, { label: "SELFIE" })));
    await assertFails(getDoc(doc(other(), "users/kavindu/photos/p1")));
    await assertFails(deleteDoc(doc(other(), "users/kavindu/photos/p1")));
    await assertSucceeds(deleteDoc(doc(me(), "users/kavindu/photos/p1")));
  });
});

