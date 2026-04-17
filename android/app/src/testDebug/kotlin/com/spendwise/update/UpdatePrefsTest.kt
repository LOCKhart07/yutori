package com.spendwise.update

import androidx.test.core.app.ApplicationProvider
import com.spendwise.ui.TestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class UpdatePrefsTest {
    private val prefs: UpdatePrefs get() =
        UpdatePrefs(ApplicationProvider.getApplicationContext())

    @Test
    fun `defaults are zero false-for-toggle-on and null`() {
        val p = prefs
        assertEquals(0L, p.lastCheckAt)
        assertTrue(p.checkOnOpenEnabled)
        assertNull(p.dismissedTag)
        assertNull(p.lastSeenVersionName)
    }

    @Test
    fun `lastCheckAt round-trips`() {
        val p = prefs
        p.lastCheckAt = 1_700_000_000_000L
        assertEquals(1_700_000_000_000L, prefs.lastCheckAt)
    }

    @Test
    fun `checkOnOpenEnabled round-trips`() {
        val p = prefs
        p.checkOnOpenEnabled = false
        assertEquals(false, prefs.checkOnOpenEnabled)
    }

    @Test
    fun `dismissedTag can be cleared by assigning null`() {
        val p = prefs
        p.dismissedTag = "v0.3.0"
        assertEquals("v0.3.0", prefs.dismissedTag)
        p.dismissedTag = null
        assertNull(prefs.dismissedTag)
    }

    @Test
    fun `lastSeenVersionName round-trips and clears`() {
        val p = prefs
        p.lastSeenVersionName = "0.3.0"
        assertEquals("0.3.0", prefs.lastSeenVersionName)
        p.lastSeenVersionName = null
        assertNull(prefs.lastSeenVersionName)
    }
}
