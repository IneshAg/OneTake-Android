package com.onetake.app.ui.profile

import android.app.Application
import com.onetake.app.AppStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadshotManagementTest {

    @Test
    fun headshotStateStartsEmptyAndUpdatesDynamically() {
        val store = AppStore(Application())
        assertEquals(0, store.headshotsTaken)
        assertTrue(store.headshots.isEmpty())
        assertFalse(store.hasHeadshot("front"))
        assertNull(store.headshot("front"))

        store.saveHeadshot("front", "file:///data/user/0/headshots/front.jpg")
        assertEquals(1, store.headshotsTaken)
        assertTrue(store.hasHeadshot("front"))
        assertEquals("file:///data/user/0/headshots/front.jpg", store.headshot("front"))

        store.saveHeadshot("left", "file:///data/user/0/headshots/left.jpg")
        store.saveHeadshot("right", "file:///data/user/0/headshots/right.jpg")
        assertEquals(3, store.headshotsTaken)
        assertTrue(store.hasHeadshot("left"))
        assertTrue(store.hasHeadshot("right"))

        store.clearHeadshots()
        assertEquals(0, store.headshotsTaken)
        assertTrue(store.headshots.isEmpty())
        assertFalse(store.hasHeadshot("front"))
    }
}
