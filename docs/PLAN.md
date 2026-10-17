# ThingSpot plan

> "Where did I put it?": an offline, privacy-first home inventory for Android.
> Free and open source on F-Droid first, then on Google Play with a one-time Pro unlock.

Vocabulary (Place, Item, Category, Label, Backup, Export, Pro) is defined in [`CONTEXT.md`](../CONTEXT.md). The decisions that are hard to reverse are recorded in [`docs/adr/`](adr/). Agreed on 2026-10-16.

---

## 1. Why this can win

- **There's a gap on F-Droid.** It has no polished, maintained, general-purpose "nested places + search" inventory app. The closest are Personal Stuff (focused on expiry dates), boxorg (brand new) and Flindex (last release in 2022). Homebox, a self-hosted web app with 7.5K GitHub stars, shows FOSS users want this.
- **Play competitors keep getting the same complaints:** item caps on free tiers, subscriptions for "a list app", forced accounts or cloud storage, data loss when changing phones, ads, no export, and weak search or moving. ThingSpot is designed to avoid every one of these.
- **There's proof the model works.** "Find my stuff" is offline, rated 4.8★ with 50K+ installs, and earns from small one-time unlocks. DAVx⁵ and Simple Mobile Tools show that "free on F-Droid, paid on Play" works.

**One-line pitch:** *Find anything in seconds. Your inventory never leaves your phone. No account, no subscription, no limits.*

## 2. Principles (non-negotiable)

1. **Data stays on the device.** No account, no server, no cloud sync, and no Android system backup. Data leaves the phone only as a Backup file the user moves themselves. (ADR 0001)
2. **The user's data is never locked or limited.** Unlimited Places and Items. Photos, search, backup, restore and export are always free. (ADR 0005)
3. **Every Item is always in exactly one Place.** No state, screen or delete flow may leave an Item without a Place.
4. **Search is the product.** Every other screen exists to put data in so that search can get it back out.
5. **Security features are never Pro.**
6. **Nothing proprietary in the F-Droid build.** Proprietary libraries (Play Billing) live only in the `play` build. (ADR 0004)

## 3. Scope

### v1.0 (first F-Droid release)

| Area | What ships |
|---|---|
| Places | Nested tree, as deep as needed. Create, rename, set an icon, move under a new parent (moving a Place inside itself or its own sub-places is blocked). Delete moves its Items and child Places up to the parent; for a top-level Place, the user picks where they go. Its Labels become blank. |
| Items | Name and Place (required); Category, notes and **one photo** (optional). Add, edit, move, delete. No quantity, value or dates. |
| Categories | Optional, at most one per Item, with a colour. Deleting a Category removes it from its Items. |
| Search | Home screen. Matches Item names and notes, Place names and Category names. Ignores case and accents. Every result shows its full path (Home → Garage → Toolbox). "Recently added" list when the search box is empty. |
| Photos | One per Item and one per Place. Taken with the camera or picked from the gallery. Compressed on save. |
| Labels (Pro on Play) | Print sheets of QR Labels as a PDF (A4 or Letter): for chosen Places, or blank. Each Label also shows a short human-readable code. A Place can have several Labels. In-app scanner: a linked Label opens its Place; a blank or unknown Label offers "new Place" or "link to an existing Place". |
| Backups | **Automatic daily** Backup (only if anything changed) to a `ThingSpot` folder inside a folder the user picks once (Documents suggested), keeping the last 7. "Back up now" and **"Share backup"** (Quick Share, USB, email). **Optional password** encrypts the Backup file, with a clear warning that a forgotten password means a lost Backup. **Restore** replaces everything, after automatically saving the current data first. A visible "last backup" status, plus a warning when backups are failing. |
| Export | Spreadsheet (CSV) of Items with their full Place path, Category and notes. |
| First launch | "Restore from a backup file" or "Start fresh", plus an optional one-tap set of starter Places (Home → Bedroom, Kitchen, Living room, Garage). |
| Look and feel | Material 3 with ThingSpot's own brand theme, light and dark. Dynamic colour as an optional setting. English only. |
| Crashes | ACRA: after a crash, a dialog offers to email you the report. The user decides each time. |
| Pro (Play only) | One-time unlock for Labels. Always unlocked in the F-Droid build, which shows a donate link instead. |

### v1.1
- Optional fingerprint/PIN **app lock** (free).
- Fixes and polish from the first users' feedback.

### Later (in rough order; most become Pro extras on Play)
- Quick capture: a "share to ThingSpot" target, a home-screen widget, a quick-settings tile.
- Lending ("lent to Rahul on 3 Oct").
- Extra themes and icons.
- Several photos per Item or Place (the data model already allows it).
- Translations through Hosted Weblate.
- User-controlled file sync, e.g. Syncthing (ADR 0001 keeps this possible).
- iOS through Compose Multiplatform (ADR 0002 keeps this possible).

### Deliberately not doing
Cloud accounts or sync, family or shared inventories, quantities or stock tracking, item-limited free tiers, subscriptions, ads, analytics or tracking, a web version, and importing data from the old web app.

## 4. Architecture

Android-only for now, in Kotlin with Jetpack Compose. The domain and data code doesn't depend on Android UI, so it could later move to Kotlin Multiplatform (ADR 0002).

```
:core:domain   Pure Kotlin (no Android). Place, Item, Category and Label models;
               the tree rules (move-cycle check, delete moves contents up, Labels go blank);
               search and ranking; the ProGate interface.
:core:data     Room database (via its KMP-capable API: coroutines and Flow, no LiveData);
               repositories; the Backup format (zip + JSON + photos, optional encryption);
               CSV export.
:app           Compose UI, navigation, WorkManager (daily Backup), camera and photo picker,
               QR scanning, PDF Label sheets and printing, ACRA, dependency injection.
  src/fdroid/  ProGate is always unlocked; donate link.
  src/play/    ProGate backed by the Play Billing Library (the only proprietary code).
```

**Libraries** (all accepted by F-Droid; no Google Play Services, Firebase or ML Kit anywhere):
- Compose (BOM), Material 3, Navigation Compose
- Room, kotlinx.serialization, Coroutines
- WorkManager
- CameraX + **zxing-cpp** for scanning; ZXing core (or qrcode-kotlin) to generate QR codes
- Coil 3 for images
- Koin for dependency injection (lightweight and multiplatform-ready)
- ACRA (dialog + mail sender)
- Play Billing Library: **`play` flavour only**

**Data design:**
- Every record has a random UUID plus `createdAt` and `updatedAt` timestamps, so future sync is possible.
- A Label's code is its own random short code, separate from the Place ID. QR content is an app-specific string such as `thingspot:L:<code>`, read only by ThingSpot's scanner.
- Photos are stored in the app's private storage, compressed to about 1600 px JPEG.

**Backup file format:**
- **The zip:** contains `manifest.json` (format version, app version, created-at), `data.json` and `photos/`.
- **With a password:** the whole zip is wrapped in AES-256-GCM, using a key derived from the password with PBKDF2-SHA256 (600k iterations) and a random salt. The header records the format version.
- **Restore:** reads current and older format versions.
- **Writing:** the user picks a folder once with the system folder picker (Documents suggested); ThingSpot keeps a lasting grant to it and writes into a `ThingSpot` subfolder. This needs no storage permission on any Android version, and keeping the last 7 still works after a reinstall (the user re-picks the folder). Restore uses the system file picker. Format details: [`docs/backup-format.md`](backup-format.md).

**Build and release (ADR 0003):**
- `fdroid` and `play` product flavours.
- `dependenciesInfo { includeInApk = false; includeInBundle = false }`.
- Sign with `apksigner` from **build-tools 34**.
- Deterministic (or removed) `baseline.prof`.
- No foojay toolchain plugin.
- Release builds come only from clean, tagged commits (`v1.0.0`); versionCode increases with every release.
- **CI (GitHub Actions):** build, unit tests, lint, and a reproducibility check on every tag (build twice from scratch, compare with `diffoscope`/`apksigcopier`).

**Tests:**
- Unit tests for every tree rule: delete moves contents up, no move cycles, freeing Labels, "an Item always has a Place".
- Search ranking tests.
- Backup round trips, plain and encrypted, including restoring the previous format version.
- Room migration tests from v1 onwards.
- A few Compose UI tests for the main flows: add a Place, add an Item, search, move, scan.

## 5. Milestones (20+ h/week; starting Mon 19 Oct 2026)

| Week | Milestone | Done when |
|---|---|---|
| 0 (now) | **Groundwork** | See section 6 "Before writing code". |
| 1 | **Skeleton + reproducible "hello world" release** | Public repo `bijojp/thingspot` (GPL-3.0 + Play exception). Gradle multi-module setup, both flavours, CI green. Release signing key created and backed up offline. **A signed hello-world release reproduces in F-Droid's own build tool, run locally in Docker** (this is the biggest technical risk, so prove it first). Backup-folder approach confirmed. |
| 2 | **Domain + data** | Places, Items, Categories and Labels in Room, all tree rules covered by tests, search implemented and tested. |
| 3 | **Core screens** | Search home, Place tree, Place detail, Item add/edit/move/delete, Categories, delete flows, theme, first launch with starter Places. Usable every day; **start using it yourself.** |
| 4 | **Photos + Backups + Export** | One photo per Item/Place. Daily Backup, Share, Restore with safety copy, optional password, CSV export, backup-status warning. |
| 5 | **Labels + Pro + polish + submit** | Printable Label sheets, blank Labels, scanner and linking flow. ProGate wired in both flavours. ACRA. Accessibility pass (TalkBack, large fonts), works smoothly with 5,000 Items, empty states. Store metadata, screenshots, README, privacy policy. **Tag `v1.0.0` → open the merge request to `fdroiddata`.** |
| 6–9 | **Play launch (while F-Droid reviews)** | See section 8. |
| 7+ | **v1.1** | App lock plus fixes from feedback, released to F-Droid (and Play once it's live). |

F-Droid review is done by volunteers and often takes a month or more. Keep building v1.1 meanwhile. **Optionally**, publish the same signed APK on GitHub Releases and ask IzzyOnDroid (a third-party F-Droid-compatible repo) to list it. Early adopters get it within days, and since it's the same key, there's no conflict when the official F-Droid build arrives.

## 6. Before writing code (week 0 checklist)

- [ ] Log the `gh` CLI in as **bijojp** (it's currently `bijopanicker`). Create the repo `bijojp/thingspot`.
- [ ] Do a manual trademark search for **ThingSpot** at WIPO Global Brand Database and IP India. The US was already checked and is clear.
- [ ] *(Optional, recommended)* Register `thingspot.app` to protect the brand (about $15/yr), even though the app ID doesn't use it.
- [ ] Generate the release signing key. Store **two offline copies** plus the password in a password manager. A lost key can't be recovered and blocks registering with Google.
- [ ] Pick a donation platform (GitHub Sponsors or Liberapay) and **check it pays out to India**. Add `FUNDING.yml`.
- [ ] Design the brand basics: icon (adaptive and monochrome), main colour, wordmark.

## 7. F-Droid launch checklist

- [ ] App ID `io.github.bijojp.thingspot` (permanent).
- [ ] `LICENSE` (GPL-3.0-or-later) plus the section 7 Play-library exception. Licence headers or an SPDX identifier.
- [ ] Store listing in `fastlane/metadata/android/en-US/`:
  - `title.txt` (≤50 chars)
  - `short_description.txt` (≤80 chars, no full stop at the end)
  - `full_description.txt`
  - `changelogs/<versionCode>.txt`
  - `images/icon.png`, `featureGraphic.png` and `phoneScreenshots/`
- [ ] No warning labels expected (no network, no tracking, no proprietary code in the `fdroid` flavour). Run F-Droid's scanner locally to confirm.
- [ ] Reproducible build set up in the recipe: `Binaries:` pointing at the GitHub Release APK, `AllowedAPKSigningKeys:` with your key's SHA-256.
- [ ] `metadata/io.github.bijojp.thingspot.yml` passes `fdroid lint` and `fdroid build` locally. Open the MR on `fdroiddata` with CI green.
- [ ] Turn on auto-update (`UpdateCheckMode: Tags`).
- [ ] Announce: F-Droid forum, r/fossdroid, r/androidapps, and Mastodon (#FDroid, #FOSS).

## 8. Google Play launch checklist (weeks 6–9)

- [ ] Personal Play developer account: $25, government ID matching your payment profile, a real Android device, phone verification. Note: **your legal name and country are shown publicly.**
- [ ] **Google's developer verification:** in the Play Console, register `io.github.bijojp.thingspot` with your key's SHA-256. Do this before global enforcement in 2027, and ideally before F-Droid installs pass 50, so you can skip the proof-of-ownership step.
- [ ] Create the app, then choose **"Provide a copy of your app signing key"** (upload your own key, not a Google-generated one). This must happen before the first open-testing or production release (ADR 0003).
- [ ] Target **API 36** (required for new apps since 31 Aug 2026).
- [ ] **Closed test: at least 12 testers opted in continuously for at least 14 days.** Testers who opt out and back in don't count. Recruit early: friends, colleagues, F-Droid early adopters, r/AndroidClosedTesting. Push 1–2 updates during the test. Then fill in the production-access questionnaire (about 7 days of review).
- [ ] **Data safety form:** "No data collected or shared" (everything is processed on the device).
- [ ] **Privacy policy:** a public page (e.g. GitHub Pages under bijojp), linked in the Play Console **and inside the app**. It must exist even though nothing is collected.
- [ ] Content rating questionnaire, store listing, screenshots, feature graphic.
- [ ] Pro product: a one-time in-app product, about **$3.99**, using Play's local-currency pricing (it lowers the price in markets like India). Test the purchase and restore flow with licence testers.
- [ ] **Play build rules:** no self-updating, no update-checker that points to F-Droid or GitHub, and no downloading of code.

## 9. Risks and what we do about them

| Risk | What we do |
|---|---|
| The build won't reproduce (Compose `baseline.prof`, newer build tools, timestamps) | Prove it with hello-world in week 1, before any features. Re-check on every tag in CI. |
| F-Droid review takes months | Submit at the end of week 5 and keep building. Optional GitHub Releases + IzzyOnDroid for early users. |
| Users lose data (backups stay on the phone) | Backup status always visible, "Share backup" offered after big changes and from settings, restore offered on first launch. The Play listing explains how backups work. |
| Google developer verification hits F-Droid users in 2027 | Register the package and key when the Play account opens. One key covers both stores. |
| Signing key lost | Two offline copies and a password manager entry, created in week 0. |
| Name conflict outside the US | WIPO and IP India check in week 0, before announcing anything. |
| Contributors' GPL code shipped next to the proprietary Play Billing library | The section 7 exception is in the licence from the first commit, so every contribution comes under it. |
| Scope creep before v1 | Anything not in the v1 table goes in "Later". Ship, then listen. |

## 10. Decide later (not blocking v1)
- The exact Pro price and which features come next in Pro.
- What v1.2 contains, once you have feedback.
- Whether and when to start translations (Weblate).
- Whether to pursue iOS (revisit after about 6 months of Android data).
