package io.github.bijojp.thingspot

import org.junit.Assert.assertTrue
import org.junit.Test

class StoreProGateTest {
    @Test
    fun `F-Droid build always has Pro unlocked`() {
        assertTrue(StoreProGate.isUnlocked.value)
    }
}
