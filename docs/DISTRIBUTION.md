# Distributing SalonLink to testers (CI/CD + Firebase App Distribution)

Test builds reach phones through **Firebase App Distribution**. It is free (Spark plan), and no
store review is needed. **GitHub Actions** builds, tests and uploads every push to `main`
automatically. When a tester opens SalonLink and a newer build exists, the app shows a
**"New version available → Update"** dialog, which downloads and installs it.

## How it works

```
git push (main) ──► GitHub Actions (.github/workflows/android.yml)
                      1. unit tests
                      2. signed "beta" APK (release key)
                      3. upload to App Distribution, group "testers"
                                 │
tester opens SalonLink ◄─────────┘  MainActivity.checkForTesterUpdate()
  → "Update" dialog → download → install
```

- **Build type `beta`** = the release build (minified, signed with `salonlink-release.jks`) plus
  the full App Distribution SDK that shows the update dialog. The `release` build (for Google Play)
  never contains that SDK, because Play forbids apps that update themselves.
- `versionCode` = number of git commits, so every commit is a newer version. Release notes = the
  latest commit's subject line.
- Pull requests only run the unit tests; nothing is uploaded.

## One-time setup (already done)

- Tester group **Testers** (alias `testers`) in project `salonlink-4d991`.
- GitHub repository secrets (Settings → Secrets and variables → Actions):
  `GOOGLE_SERVICES_JSON_B64`, `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `FIREBASE_SA_JSON_B64`.
  If you change the keystore password or the service-account key, update the secret too, e.g.:

  ```powershell
  gh secret set KEYSTORE_PASSWORD -R danit-x/salonLink
  ```

## Add testers

In the console: **Release & Monitor → App Distribution → Testers & Groups → Testers → Add testers**.

Or from a terminal:

```powershell
firebase appdistribution:testers:add --group-alias testers person@example.com --project salonlink-4d991
```

Each tester gets an email invite. On their Android phone they accept it and install SalonLink.
The first time the update check runs, the app asks them to **sign in with that same Google
account** (opens the browser once). After that, new builds just show the Update dialog.

## Ship a new build

Push to `main`. Follow it in the repo's **Actions** tab; each run also keeps the APK as a download
for 14 days. To rebuild without a new commit: Actions → **Android CI/CD** → **Run workflow**.

By hand (same result, from this PC):

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS = "firebase\seed\service-account.json"
.\gradlew assembleBeta appDistributionUploadBeta
```

To send it to a different group: `-Psalonlink.testerGroups=group1,group2`.

## Things to know

- **Same signing key every time.** Android only installs an update signed with the same key as the
  installed app. All beta builds use `salonlink-release.jks`. A phone that has an older **debug**
  build installed must uninstall it once before the first beta build will install.
- **Keep the keystore and its password backed up** outside this folder. Losing either means no
  more updates for installed apps.
- Builds point at the **real** Firebase project.
- Testers need Google Play services. Most HONOR phones sold outside China have it.
- Updates are not silent: the tester taps **Update** and confirms the install. Android asks once
  to allow installing apps from SalonLink ("Install unknown apps").
