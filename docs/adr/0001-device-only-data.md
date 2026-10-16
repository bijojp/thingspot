# Inventory data stays on the device

All inventory data lives in a local database on the phone. There is no account, no server and no cloud sync. To protect against a lost or replaced phone, the app makes automatic backups to a folder the user picks and also supports manual export. We chose this over cloud sync for three reasons: privacy is the main pitch to an F-Droid-first audience, sync would require a server, accounts and real privacy-policy work, and Firebase is banned on F-Droid. IDs and timestamps are designed so user-controlled file sync (e.g. Syncthing) can be added later without a data migration.

Android's own system backup and phone-to-phone transfer are also turned off. Data leaves the phone only as a backup file that the user puts somewhere themselves. This keeps the promise simple and absolute, but makes the app's own backup and restore flow the only protection when a user changes phones, so it has to be excellent.
