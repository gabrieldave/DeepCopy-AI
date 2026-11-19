package com.blackeye.klipnote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteRouterConfigTest {

    @Test
    fun defaultSnapshot_keepsPaidFallbackLast() {
        val snap = RemoteRouterConfig.defaultSnapshot()
        assertTrue(snap.priority.isNotEmpty())
        assertEquals(SttProvider.PAID_FALLBACK, snap.priority.last())
        assertEquals(
            listOf(
                SttProvider.GROQ_FREE,
                SttProvider.INWORLD,
                SttProvider.ASSEMBLY_AI,
                SttProvider.DEEPGRAM,
                SttProvider.PAID_FALLBACK
            ),
            snap.priority
        )
    }
}
