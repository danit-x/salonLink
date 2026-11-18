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
import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  collectionGroup,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
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

  test("nobody can create bookings directly yet (booking engine arrives in Phase 3)", async () => {
    const db = as("kavindu", "kavindu@example.com");
    await assertFails(setDoc(doc(db, "salons/aura/bookings/b3"), booking("kavindu")));
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
