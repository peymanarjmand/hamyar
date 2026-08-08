package com.hesabyar.garage.ui

import com.hesabyar.garage.data.AppSecuritySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAccessStateTest {
    @Test
    fun `security must load before any app content can be unlocked`() {
        assertSame(AppAccessState.Loading, resolveAppAccessState(null, sessionUnlocked = false, sessionId = 1))
        assertSame(AppAccessState.Loading, resolveAppAccessState(null, sessionUnlocked = true, sessionId = 1))
    }

    @Test
    fun `lock stays optional and an enabled lock starts closed`() {
        assertSame(
            AppAccessState.Unlocked,
            resolveAppAccessState(AppSecuritySettings(lockEnabled = false), sessionUnlocked = false, sessionId = 2),
        )

        val locked = resolveAppAccessState(
            AppSecuritySettings(lockEnabled = true, recoveryQuestion = "پرسش"),
            sessionUnlocked = false,
            sessionId = 7,
        )
        assertTrue(locked is AppAccessState.Locked)
        locked as AppAccessState.Locked
        assertEquals(7L, locked.sessionId)
        assertEquals("پرسش", locked.settings.recoveryQuestion)
    }

    @Test
    fun `successful authentication unlocks only the current process session`() {
        val settings = AppSecuritySettings(lockEnabled = true)
        assertSame(AppAccessState.Unlocked, resolveAppAccessState(settings, sessionUnlocked = true, sessionId = 3))
        assertTrue(resolveAppAccessState(settings, sessionUnlocked = false, sessionId = 4) is AppAccessState.Locked)
    }

    @Test
    fun `background timeout locks at the exact configured boundary`() {
        assertTrue(shouldLockAfterBackground(timeoutSeconds = 0, elapsedMillis = 0))
        assertTrue(!shouldLockAfterBackground(timeoutSeconds = 30, elapsedMillis = 29_999))
        assertTrue(shouldLockAfterBackground(timeoutSeconds = 30, elapsedMillis = 30_000))
    }

    @Test
    fun `one-time recovery code never renders over the locked gate`() {
        val locked = AppAccessState.Locked(AppSecuritySettings(lockEnabled = true), sessionId = 9)

        assertTrue(!shouldDisplayRecoveryCode(AppAccessState.Loading, "1234"))
        assertTrue(!shouldDisplayRecoveryCode(locked, "1234"))
        assertTrue(!shouldDisplayRecoveryCode(AppAccessState.Unlocked, null))
        assertTrue(shouldDisplayRecoveryCode(AppAccessState.Unlocked, "1234"))
    }
}
