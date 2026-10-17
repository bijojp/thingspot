<div align="center">

# ThingSpot

#### Where did I put it?

An offline, privacy-first home inventory for Android.

</div>

---

ThingSpot remembers where your things are. Rooms, shelves, drawers, boxes and bags become **Places** that nest inside each other; your things are **Items** kept in exactly one Place. Search for anything and see its full path (Home → Garage → Toolbox) in seconds. Stick a printed QR **Label** on a box, and scanning it shows what's inside.

> **Status: pre-alpha.** Places, Items, Categories, search, photos, Backups and QR Labels work; polish and the first F-Droid release are next. See [the plan](docs/PLAN.md).

## Promises

- **Your inventory never leaves your phone.** No account, no server, no cloud sync. Android's cloud backup and phone-to-phone transfer are switched off. Data leaves only as a Backup file that *you* move.
- **No limits on your own data.** Unlimited Places and Items. Photos, search, backup, restore and export are free forever.
- **No ads, no analytics, no tracking, no subscription.**
- **Free and open source** under GPL-3.0-or-later.

## Privacy, in code

| File | What it guarantees |
| --- | --- |
| `app/src/main/AndroidManifest.xml` | No `INTERNET` permission; `allowBackup="false"`; camera only for the Label scanner and photos, asked for when used |
| `app/src/main/res/xml/data_extraction_rules.xml` | Every data domain is excluded from cloud backup and device transfer |
| `app/build.gradle.kts` | No Firebase, Google Play Services or analytics SDKs; Play Billing only in the `play` flavour |
| `tools/verify-fdroid-build.sh` | Fails if the F-Droid build gains a proprietary Google library or any permission not in `tools/allowed-permissions.txt` |

## Two builds, one app

| | F-Droid | Google Play |
| --- | --- | --- |
| Price | Free | Free |
| Pro extras (QR Labels, later more) | Always unlocked | One-time purchase |
| Proprietary code | None | Play Billing only |
| Signing key | Developer's own key (reproducible builds) | Same key |

## Building

Requirements: JDK 17 and the Android SDK with `platforms;android-37.0`, `build-tools;36.0.0` and `build-tools;34.0.0`.

```sh
./gradlew assembleFdroidDebug        # F-Droid flavour, debug
./gradlew assemblePlayDebug          # Play flavour, debug
./gradlew assembleFdroidRelease      # unsigned release; signed separately with apksigner 34
./tools/verify-fdroid-build.sh       # checks the F-Droid build's promises
```

Debug builds install side by side with release builds (`.debug` suffix), so testing never touches real data.

## Project docs

- [`CONTEXT.md`](CONTEXT.md): the vocabulary (Place, Item, Category, Label, Backup, Export, Pro)
- [`docs/PLAN.md`](docs/PLAN.md): scope, architecture, milestones, launch checklists
- [`docs/adr/`](docs/adr/): decisions that are hard to reverse, and why

## License

GPL-3.0-or-later, with an additional permission that lets the Play build link Google's Play Billing library. See [`LICENSE`](LICENSE) and [`NOTICE.md`](NOTICE.md).
