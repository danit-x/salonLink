# Distributing SalonLink to testers (Firebase App Distribution)

Test builds reach phones through **Firebase App Distribution**. It is free, and no store review is needed.
Each new upload notifies testers, who update with one tap in the **App Tester** app.

## One-time setup (already done)

- Gradle plugin `com.google.firebase.appdistribution` (version in `gradle/libs.versions.toml`).
- Tester group **Testers** (alias `testers`) in project `salonlink-4d991`.
- `versionCode` = number of git commits, so every commit is a newer version. Release notes = the
  latest commit's subject line.
- Authentication uses your Firebase CLI login (`firebase login`). No key file is stored in the repo.

## Add testers

In the console: **Release & Monitor → App Distribution → Testers & Groups → Testers → Add testers**.

Or from a terminal:

```powershell
firebase appdistribution:testers:add --group-alias testers person@example.com other@example.com --project salonlink-4d991
```

Each tester gets an email invite. On their Android phone they accept it, install **App Tester** when
prompted, and then install SalonLink from there.

## Ship a new build

Commit your changes first (the version number comes from the commit count), then:

```powershell
.\gradlew assembleDebug appDistributionUploadDebug
```

To send it to a different group: `-Psalonlink.testerGroups=group1,group2`.

## Things to know

- **Same signing key every time.** Android only accepts an update signed with the same key as the
  installed app. Debug builds use the key in `%USERPROFILE%\.android\debug.keystore`, which is
  different on every PC. Always upload from the same machine, or share one keystore across the team
  (or switch to a release build signed via `keystore.properties`).
- Builds point at the **real** Firebase project (never upload a `-Psalonlink.useEmulators=true` build).
- Testers need Google Play services. Most HONOR phones sold outside China have it.
- Updates are not silent: testers get a notification or email and tap **Update**. For fully
  automatic background updates, publish through a Google Play testing track instead.
