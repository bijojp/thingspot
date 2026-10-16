# ThingSpot

An Android app (formerly the "Where Is It?" web prototype) that answers one question: "where did I put it?" It records physical things and the places they are kept, and keeps that record on the user's device only.

## Language

### Places and Items

**Place**:
Somewhere things are kept: a room, shelf, drawer, box, bag or car. Places nest; each Place has at most one parent Place, and one with no parent is a top-level Place. Moving a Place moves everything inside it.
_Avoid_: Location (reads as GPS), Vault, Storage, Container, Spot

**Item**:
One physical object, or one bundle kept together (e.g. "AA batteries"), that is always in exactly one Place. An Item never holds anything; whatever holds other things is a Place.
_Avoid_: Thing, Object, Stock

**Category**:
An optional, coloured grouping of Items by kind (e.g. Documents, Electronics), independent of where they are kept. An Item has at most one Category.
_Avoid_: Tag, Type, Group, Label

**Label**:
A printed QR sticker with its own code, meant to be stuck on a physical Place (a box, bin or drawer). A Label is either blank or linked to one Place, and a Place may have several Labels (e.g. one on each side of a box). Scanning a linked Label opens its Place; scanning a blank one asks which Place to link it to. Labels are only ever linked to Places, never to Items.
_Avoid_: Tag, Sticker, Code

### Keeping data safe

**Backup**:
A single file holding a complete copy of everything (Places, Items, Categories, Labels and photos) at one moment, optionally protected by a password. Restoring a Backup replaces all current data.
_Avoid_: Export, Snapshot, Sync

**Export**:
A spreadsheet copy of the inventory for reading or using elsewhere. It is one-way and cannot be restored.
_Avoid_: Backup

### Product

**Pro**:
A one-time paid unlock for extra features, sold only in the Play version; the F-Droid version always has it unlocked. Pro never limits or locks the user's own data.
_Avoid_: Premium, Plus, Subscription
