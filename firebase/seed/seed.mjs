// =====================================================================================
// SalonLink — demo data seed script
// -------------------------------------------------------------------------------------
// Loads the demo salon from the Figma design into Firestore:
//   salons/aura                         Aura Salon, 24 Havelock Road, Colombo 05
//   salons/aura/services/*              Haircut, Beard Trim, Hair Colour, Hair Treatment
//   salons/aura/staff/kasun             Kasun Perera (★ 4.9, 128 reviews, 8 years)
//   salons/aura/staff/nadeesha          a second stylist, so "choose your barber" means something
//   salons/aura/staff/kasun/blocks/*    a daily lunch break for the next 14 days
//   invites/{email}                     the owner invite (+ the test staff invite on the emulator)
//
// WHY the Admin SDK: the security rules (correctly) stop app users from writing the catalogue.
// The Admin SDK runs with full access, like the Firebase console, so it can set things up.
//
// Usage (from the project root):
//   npm --prefix firebase/seed install
//
//   # Local emulator (safe, throwaway). The emulator must be running.
//   npm --prefix firebase/seed run seed:emulator
//
//   # Real project salonlink-4d991. Needs a service-account key saved as
//   # firebase/seed/service-account.json (git-ignored) and an owner email:
//   npm --prefix firebase/seed run seed:live -- --owner-email someone@example.com
//
// Running it again is safe: every document has a fixed id and is overwritten, not duplicated.
// =====================================================================================
import { existsSync, readFileSync } from "node:fs";
import { parseArgs } from "node:util";
import { cert, initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";

const PROJECT_ID = "salonlink-4d991";
const EMULATOR_HOST = "127.0.0.1:8085"; // must match firebase.json
const SALON_ID = "aura";
const SALON_ZONE_OFFSET = "+05:30"; // Asia/Colombo has no daylight saving, so a fixed offset is exact

const { values: args } = parseArgs({
  options: {
    emulator: { type: "boolean", default: false },
    live: { type: "boolean", default: false },
    "owner-email": { type: "string" },
  },
});

if (args.emulator === args.live) {
  console.error("Choose exactly one target: --emulator or --live");
  process.exit(1);
}

// ---------- connect ----------

if (args.emulator) {
  // The Admin SDK talks to the emulator whenever this variable is set. No credentials needed.
  process.env.FIRESTORE_EMULATOR_HOST = EMULATOR_HOST;
  initializeApp({ projectId: PROJECT_ID });
} else {
  const keyPath = new URL("./service-account.json", import.meta.url);
  if (!existsSync(keyPath)) {
    console.error("Missing firebase/seed/service-account.json (Firebase console → Project settings → Service accounts).");
    process.exit(1);
  }
  if (!args["owner-email"]) {
    console.error("--owner-email is required for --live, so the salon owner can register.");
    process.exit(1);
  }
  initializeApp({ credential: cert(JSON.parse(readFileSync(keyPath, "utf8"))), projectId: PROJECT_ID });
}

const db = getFirestore();

// ---------- demo data (values copied from the Figma frames) ----------

/** Same storage format the app reads (FirestoreValues.kt): { MON: ["09:00", "19:00"], ... } */
const hours = (days, open, close) => Object.fromEntries(days.map((d) => [d, [open, close]]));

const salon = {
  name: "Aura Salon",
  address: "24 Havelock Road, Colombo 05",
  area: "Colombo 05",
  phone: "0112345678",
  openingHours: {
    ...hours(["MON", "TUE", "WED", "THU", "FRI", "SAT"], "09:00", "19:00"),
    SUN: ["10:00", "17:00"],
  },
};

const staff = {
  kasun: {
    name: "Kasun Perera",
    bio: "Modern cuts, natural texture and attention to detail for every Sri Lankan hair type. Let’s create your signature look.",
    specialties: ["Fade", "Colour", "Curly Hair", "Beard Styling"],
    rating: 4.9,
    reviewCount: 128,
    yearsExperience: 8,
    photoUrl: null, // the app shows a bundled placeholder until real photos are added
    portfolioUrls: [],
    workingHours: hours(["MON", "TUE", "WED", "THU", "FRI", "SAT"], "09:00", "19:00"),
    active: true,
  },
  nadeesha: {
    name: "Nadeesha Fernando",
    bio: "Colour specialist who loves healthy, glossy hair. Calm chair, honest advice.",
    specialties: ["Colour", "Treatments", "Long Hair"],
    rating: 4.8,
    reviewCount: 86,
    yearsExperience: 6,
    photoUrl: null,
    portfolioUrls: [],
    workingHours: hours(["TUE", "WED", "THU", "FRI", "SAT", "SUN"], "10:00", "17:00"),
    active: true,
  },
};

// Prices are whole rupees (see Money.kt). staffIds = who can do it; [] would mean "anyone".
const services = {
  haircut: {
    name: "Haircut", description: "Consultation, wash, cut & finish", category: "HAIRCUT",
    durationMin: 30, price: 1500, staffIds: ["kasun", "nadeesha"], active: true,
  },
  "beard-trim": {
    name: "Beard Trim", description: "Shape, tidy & finish", category: "BEARD",
    durationMin: 20, price: 800, staffIds: ["kasun"], active: true,
  },
  "hair-colour": {
    name: "Hair Colour", description: "Full colour application", category: "COLOUR",
    durationMin: 90, price: 4500, staffIds: ["kasun", "nadeesha"], active: true,
  },
  "hair-treatment": {
    name: "Hair Treatment", description: "Nourishing treatment", category: "TREATMENT",
    durationMin: 60, price: 4000, staffIds: ["nadeesha"], active: true,
  },
};

/** Salon-local "YYYY-MM-DD" + "HH:mm" → Firestore Timestamp (stored as UTC, like the app does). */
const salonTime = (date, time) => Timestamp.fromDate(new Date(`${date}T${time}:00${SALON_ZONE_OFFSET}`));

/** Kasun's 13:00–13:30 lunch break on each of the next 14 days (shows how blocks remove slots). */
function lunchBlocks() {
  const blocks = {};
  for (let i = 0; i < 14; i++) {
    // Today's date in Sri Lanka, shifted by i days.
    const day = new Date(Date.now() + i * 86_400_000).toLocaleDateString("en-CA", { timeZone: "Asia/Colombo" });
    blocks[`lunch-${day}`] = { start: salonTime(day, "13:00"), end: salonTime(day, "13:30"), reason: "Lunch" };
  }
  return blocks;
}

// Who may register as OWNER / STAFF (see AuthRepository.register and the `invites` rules).
const invites = {};
if (args.emulator) {
  // Matches firebase/emulator-test-accounts.md
  invites["owner.test@salonlink.test"] = { role: "OWNER", salonId: SALON_ID };
  invites["staff.test@salonlink.test"] = { role: "STAFF", salonId: SALON_ID, staffId: "kasun" };
}
if (args["owner-email"]) {
  invites[args["owner-email"].trim().toLowerCase()] = { role: "OWNER", salonId: SALON_ID };
}

// ---------- write everything in one batch (all or nothing) ----------

const batch = db.batch();
const salonRef = db.doc(`salons/${SALON_ID}`);
batch.set(salonRef, salon);
for (const [id, data] of Object.entries(services)) batch.set(salonRef.collection("services").doc(id), data);
for (const [id, data] of Object.entries(staff)) batch.set(salonRef.collection("staff").doc(id), data);
for (const [id, data] of Object.entries(lunchBlocks())) {
  batch.set(salonRef.collection("staff").doc("kasun").collection("blocks").doc(id), data);
}
for (const [email, data] of Object.entries(invites)) batch.set(db.doc(`invites/${email}`), data);

await batch.commit();

console.log(`Seeded ${args.emulator ? "the LOCAL EMULATOR" : `LIVE project ${PROJECT_ID}`}:`);
console.log(`  salon        ${salon.name}`);
console.log(`  services     ${Object.values(services).map((s) => s.name).join(", ")}`);
console.log(`  staff        ${Object.values(staff).map((s) => s.name).join(", ")}`);
console.log(`  blocks       ${Object.keys(lunchBlocks()).length} lunch breaks for Kasun`);
console.log(`  invites      ${Object.keys(invites).join(", ") || "(none)"}`);
