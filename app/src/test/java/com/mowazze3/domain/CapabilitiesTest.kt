package com.mowazze3.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {
    @Test
    fun `no level claims what no engine really does`() {
        PermissionLevel.entries.forEach { level ->
            assertFalse("$level block", level.capabilities().blockClient)
            assertFalse("$level limit", level.capabilities().limitSpeed)
            assertFalse("$level per-device usage", level.capabilities().perClientUsage)
        }
    }

    @Test
    fun `only no root offers the upgrade sheet`() {
        assertTrue(PermissionLevel.NO_ROOT.canUpgradeForControl)
        assertFalse(PermissionLevel.SHIZUKU.canUpgradeForControl)
        assertFalse(PermissionLevel.ROOT.canUpgradeForControl)
    }
}
