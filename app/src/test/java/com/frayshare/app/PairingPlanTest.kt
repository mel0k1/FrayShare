package com.frayshare.app

import com.frayshare.app.core.qr.Pairing
import com.frayshare.app.core.transfer.Plan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {

    @Test
    fun inviteSurvivesRoundTrip() {
        val key = com.frayshare.app.core.crypto.Keys.random(32)
        val payload = Pairing.build("192.168.1.24", 47470, key)
        val invite = Pairing.parse(payload)
        assertEquals("192.168.1.24", invite?.address)
        assertEquals(47470, invite?.port)
        assertArrayEquals(key, invite?.key)
    }

    @Test
    fun urlSafeKeyRoundTrips() {
        val key = com.frayshare.app.core.crypto.Keys.random(32)
        assertTrue(Pairing.encode(key).none { it == '+' || it == '/' || it == '=' })
        val payload = Pairing.build("fe80::1", 80, key)
        assertArrayEquals(key, Pairing.parse(payload)?.key)
    }

    @Test
    fun foreignTextRejected() {
        assertNull(Pairing.parse("hello world"))
        assertNull(Pairing.parse("https://example.org#abc"))
        assertNull(Pairing.parse("fray://192.168.1.1:47470"))
        assertNull(Pairing.parse("fray://192.168.1.1:99999#abcdef"))
        assertNull(Pairing.parse("fray://:47470#abcdef"))
        assertNull(Pairing.parse("fray://192.168.1.1:47470#short"))
    }
}

class PlanTest {

    @Test
    fun freshTransferStartsAtZero() {
        val plan = Plan.compute(listOf(100L, 0L), listOf(0L, 0L))
        assertEquals(listOf(0L, 0L), plan.map { it.offset })
        assertEquals(listOf(0, 1), plan.map { it.index })
    }

    @Test
    fun partialFileResumesFromStored() {
        val plan = Plan.compute(listOf(500L), listOf(120L))
        assertEquals(120L, plan[0].offset)
    }

    @Test
    fun completeFileSkipped() {
        val plan = Plan.compute(listOf(500L), listOf(500L))
        assertEquals(500L, plan[0].offset)
    }

    @Test
    fun staleLargerFileRestarts() {
        val plan = Plan.compute(listOf(500L), listOf(700L))
        assertEquals(0L, plan[0].offset)
    }

    @Test
    fun emptySizeFileSkipped() {
        val plan = Plan.compute(listOf(0L), listOf(0L))
        assertEquals(0L, plan[0].offset)
    }
}
