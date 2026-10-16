package io.github.bijojp.thingspot.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether Pro extras (QR Labels first) are unlocked.
 *
 * The F-Droid build is always unlocked; the Play build asks Play Billing (ADR 0005).
 * Pro never limits or locks the user's own data: Places, Items, photos, search,
 * Backups and Export work the same with or without it.
 */
interface ProGate {
    val isUnlocked: StateFlow<Boolean>
}
