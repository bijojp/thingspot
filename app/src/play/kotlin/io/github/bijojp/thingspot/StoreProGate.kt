package io.github.bijojp.thingspot

import io.github.bijojp.thingspot.domain.ProGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Play build: Pro is a one-time purchase through Play Billing (ADR 0005).
 * Billing arrives with Labels (plan, week 5); until then Pro stays locked here.
 */
object StoreProGate : ProGate {
    override val isUnlocked: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
}
