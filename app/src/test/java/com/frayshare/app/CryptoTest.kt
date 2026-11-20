package com.frayshare.app

import com.frayshare.app.core.crypto.BoxChannel
import com.frayshare.app.core.crypto.Keys
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class BoxChannelTest {

    @Test
    fun framesSurviveRoundTrip() {
        val shared = key()
        val wire = ByteArrayOutputStream()
        val alice = BoxChannel(dataIn(ByteArray(0)), dataOut(wire), shared)
        val messages = List(50) { "message-$it".repeat(it + 1).toByteArray() }
        messages.forEach { alice.send(it) }
        val bob = BoxChannel(dataIn(wire.toByteArray()), dataOut(ByteArrayOutputStream()), shared)
        messages.forEach { assertArrayEquals(it, bob.receive()) }
    }

    @Test
    fun largeFramesPass() {
        val shared = key()
        val wire = ByteArrayOutputStream()
        val alice = BoxChannel(dataIn(ByteArray(0)), dataOut(wire), shared)
        val payload = ByteArray(BoxChannel.MAX_PLAIN) { it.toByte() }
        alice.send(payload)
        val bob = BoxChannel(dataIn(wire.toByteArray()), dataOut(ByteArrayOutputStream()), shared)
        assertArrayEquals(payload, bob.receive())
    }

    @Test
    fun wrongKeyFails() {
        val wire = ByteArrayOutputStream()
        BoxChannel(dataIn(ByteArray(0)), dataOut(wire), key()).send("hello".toByteArray())
        val bob = BoxChannel(dataIn(wire.toByteArray()), dataOut(ByteArrayOutputStream()), Keys.random(32))
        assertThrows(Exception::class.java) { bob.receive() }
    }

    @Test
    fun tamperedFrameFails() {
        val shared = key()
        val wire = ByteArrayOutputStream()
        BoxChannel(dataIn(ByteArray(0)), dataOut(wire), shared).send("hello".toByteArray())
        val bytes = wire.toByteArray().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        val bob = BoxChannel(dataIn(bytes), dataOut(ByteArrayOutputStream()), shared)
        assertThrows(Exception::class.java) { bob.receive() }
    }

    @Test
    fun oversizedPlainRejected() {
        val alice = BoxChannel(dataIn(ByteArray(0)), dataOut(ByteArrayOutputStream()), key())
        assertThrows(IllegalArgumentException::class.java) {
            alice.send(ByteArray(BoxChannel.MAX_PLAIN + 1))
        }
    }

    @Test
    fun emptyPlainRejected() {
        val alice = BoxChannel(dataIn(ByteArray(0)), dataOut(ByteArrayOutputStream()), key())
        assertThrows(IllegalArgumentException::class.java) { alice.send(ByteArray(0)) }
    }

    private fun key(): ByteArray = Keys.random(32)

    private fun dataOut(target: ByteArrayOutputStream): DataOutputStream =
        DataOutputStream(target)

    private fun dataIn(source: ByteArray): DataInputStream =
        DataInputStream(ByteArrayInputStream(source))
}

class KeysTest {

    @Test
    fun ecdhPartiesAgreeOnKey() {
        val alice = Keys.ecKeyPair()
        val bob = Keys.ecKeyPair()
        val salt = Keys.random(16)
        val first = Keys.sharedKey(alice.private, bob.public.encoded, salt)
        val second = Keys.sharedKey(bob.private, alice.public.encoded, salt)
        assertEquals(32, first.size)
        assertArrayEquals(first, second)
        assertTrue(first.any { it != 0.toByte() })
    }

    @Test
    fun differentSaltsGiveDifferentKeys() {
        val alice = Keys.ecKeyPair()
        val bob = Keys.ecKeyPair()
        val first = Keys.sharedKey(alice.private, bob.public.encoded, Keys.random(16))
        val second = Keys.sharedKey(alice.private, bob.public.encoded, Keys.random(16))
        assertTrue(!first.contentEquals(second))
    }

    @Test
    fun hkdfMatchesRfc5869Vector() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = byteArrayOf(
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0a, 0x0b, 0x0c
        )
        val info = byteArrayOf(
            0xf0.toByte(), 0xf1.toByte(), 0xf2.toByte(), 0xf3.toByte(),
            0xf4.toByte(), 0xf5.toByte(), 0xf6.toByte(), 0xf7.toByte(),
            0xf8.toByte(), 0xf9.toByte()
        )
        val expected = byteArrayOf(
            0x3c.toByte(), 0xb2.toByte(), 0x5f.toByte(), 0x25.toByte(),
            0xfa.toByte(), 0xac.toByte(), 0xd5.toByte(), 0x7a.toByte(),
            0x90.toByte(), 0x43.toByte(), 0x4f.toByte(), 0x64.toByte(),
            0xd0.toByte(), 0x36.toByte(), 0x2f.toByte(), 0x2a.toByte(),
            0x2d.toByte(), 0x2d.toByte(), 0x0a.toByte(), 0x90.toByte(),
            0xcf.toByte(), 0x1a.toByte(), 0x5a.toByte(), 0x4c.toByte(),
            0x5d.toByte(), 0xb0.toByte(), 0x2d.toByte(), 0x56.toByte(),
            0xec.toByte(), 0xc4.toByte(), 0xc5.toByte(), 0xbf.toByte()
        )
        assertArrayEquals(expected, Keys.hkdf(ikm, salt, info))
    }
}
