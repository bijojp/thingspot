package io.github.bijojp.thingspot

import io.github.bijojp.thingspot.domain.ProGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** F-Droid build: Pro is always unlocked. Do not "fix" this (ADR 0005). */
object StoreProGate : ProGate {
    override val isUnlocked: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}
