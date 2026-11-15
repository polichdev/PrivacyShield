package com.example.privacyshield.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProtectionControllerTest {

    @Before
    fun setup() {
        ProtectionController.updateState(ProtectionState.Disconnected)
    }

    @Test
    fun stateTransitionsProperly() {
        assertEquals(ProtectionState.Disconnected, ProtectionController.state.value)

        ProtectionController.updateState(ProtectionState.Connecting)
        assertEquals(ProtectionState.Connecting, ProtectionController.state.value)

        ProtectionController.updateState(ProtectionState.Connected)
        assertEquals(ProtectionState.Connected, ProtectionController.state.value)
        assertTrue(ProtectionController.getUptimeMillis() >= 0)

        ProtectionController.updateState(ProtectionState.Disconnecting)
        assertEquals(ProtectionState.Disconnecting, ProtectionController.state.value)

        ProtectionController.updateState(ProtectionState.Disconnected)
        assertEquals(ProtectionState.Disconnected, ProtectionController.state.value)
        assertEquals(0L, ProtectionController.getUptimeMillis())
    }

    @Test
    fun errorStateContainsMessage() {
        val errorMsg = "Permission revoked by user"
        ProtectionController.updateState(ProtectionState.Error(errorMsg))

        val currentState = ProtectionController.state.value
        assertTrue(currentState is ProtectionState.Error)
        assertEquals(errorMsg, (currentState as ProtectionState.Error).message)
    }
}

