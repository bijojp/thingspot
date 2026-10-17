# Backup file format

A ThingSpot Backup is a single `.thingspot` file. Your data shouldn't depend on this app existing, so the format is documented here and readable with standard tools.

## Without a password: a ZIP

Rename the file to `.zip` and open it with any unzip tool. Inside:

| Entry | Contents |
| --- | --- |
| `manifest.json` | `format` (`"thingspot-backup"`), `formatVersion` (currently `1`), `appVersion`, `createdAt` (Unix ms), and counts of `places`, `items`, `categories`, `photos` |
| `data.json` | `places`, `items`, `categories` and `photos` arrays (fields below) |
| `photos/<fileName>` | JPEG photos, named by the `fileName` in `data.json` |

`data.json` fields (all times are Unix milliseconds; IDs are UUID strings):

- **places**: `id`, `name`, `icon` (an emoji), `parentId` (null for a top-level Place), `createdAt`, `updatedAt`
- **items**: `id`, `name`, `placeId`, `categoryId` (or null), `notes`, `createdAt`, `updatedAt`
- **categories**: `id`, `name`, `color` (ARGB as a number, e.g. `4284193013` = `0xFF5B9CF5`), `createdAt`, `updatedAt`
- **photos**: `id`, `itemId` or `placeId` (exactly one), `fileName`, `createdAt`
- **labels** (optional; absent in older backups): `code` (8 characters, e.g. `K7Q3MX9P`), `placeId`, `createdAt`. A Label's QR code holds `THINGSPOT:L:<code>`.

When reading, ThingSpot ignores fields it doesn't know about. It refuses a `formatVersion` newer than it understands.

## With a password: encrypted

The whole ZIP above is encrypted. The file starts with the 8 ASCII bytes `TSPOTENC`, then:

| Bytes | Field |
| --- | --- |
| 1 | encryption version (`1`) |
| 16 | salt |
| 4 | PBKDF2 iteration count (big-endian; currently 600,000) |
| 8 | nonce prefix |

The key is PBKDF2-HMAC-SHA256(password, salt, iterations), producing 256 bits.

The ZIP is then split into 1 MiB chunks, and each chunk is stored as a 4-byte big-endian length followed by its AES-256-GCM ciphertext and 16-byte tag:

- **Nonce:** the 8-byte prefix plus the chunk index as 4 bytes (big-endian).
- **Associated data:** the 37-byte header, the chunk index (4 bytes) and a final byte: `1` for the last chunk, `0` otherwise.

So changing, reordering or cutting off any part of the file makes decryption fail.

The reference implementation is `core/data/src/main/kotlin/io/github/bijojp/thingspot/data/backup/BackupCrypto.kt`.

## Where Backups are kept

- **Automatic backups:** saved to `<folder you chose>/ThingSpot/thingspot-backup-YYYY-MM-DD-HHmmss.thingspot`. The newest 7 are kept.
- **Safety copy:** before every restore, a copy of the current data is saved as `thingspot-before-restore-….thingspot`. It goes in the same folder, or in app storage if no folder is set.
- **Shared Backups:** made fresh each time and go only where you send them.
