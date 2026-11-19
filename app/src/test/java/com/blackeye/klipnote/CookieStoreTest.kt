package com.blackeye.klipnote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CookieStoreTest {

    @Test
    fun siteForUrl_mapsKnownHosts() {
        assertEquals(CookieSite.INSTAGRAM, CookieStore.siteForUrl("https://www.instagram.com/p/abc/"))
        assertEquals(CookieSite.INSTAGRAM, CookieStore.siteForUrl("https://instagr.am/p/abc/"))
        assertEquals(CookieSite.YOUTUBE, CookieStore.siteForUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(CookieSite.YOUTUBE, CookieStore.siteForUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(CookieSite.TIKTOK, CookieStore.siteForUrl("https://www.tiktok.com/@user/video/1"))
        assertEquals(CookieSite.FACEBOOK, CookieStore.siteForUrl("https://www.facebook.com/watch/?v=1"))
        assertEquals(CookieSite.FACEBOOK, CookieStore.siteForUrl("https://fb.watch/abc"))
        assertEquals(CookieSite.X, CookieStore.siteForUrl("https://x.com/user/status/1"))
        assertEquals(CookieSite.X, CookieStore.siteForUrl("https://twitter.com/user/status/1"))
    }

    @Test
    fun siteForUrl_blankOrUnknownReturnsNull() {
        assertNull(CookieStore.siteForUrl(null))
        assertNull(CookieStore.siteForUrl(""))
        assertNull(CookieStore.siteForUrl("   "))
        assertNull(CookieStore.siteForUrl("https://example.com/video"))
        assertNull(CookieStore.siteForUrl("not-a-url"))
    }
}
