package com.frayshare.app.core.crypto

import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BoxChannel(
    private val input: DataInputStream,
    private val output: DataOutputStream,
    key: ByteArray
) {
    private val secret = SecretKeySpec(key, "AES")
    private val marker = ByteArray(4).also { SecureRandom().nextBytes(it) }
    private var remoteMarker: ByteArray? = null
    private var counter = 0L

    fun send(plain: ByteArray) {
        require(plain.isNotEmpty() && plain.size <= MAX_PLAIN) { "bad plain size ${plain.size}" }
        val iv = buildIv(marker, counter)
        counter += 1
        val sealed = cipher(false, iv).doFinal(plain)
        output.writeInt(sealed.size)
        output.write(marker)
        output.writeLong(counter - 1)
        output.write(sealed)
        output.flush()
    }

    fun receive(): ByteArray {
        val sealedSize = input.readInt()
        require(sealedSize in TAG + 1..TAG + MAX_PLAIN) { "bad sealed size $sealedSize" }
        val peer = ByteArray(4)
        input.readFully(peer)
        val counterValue = input.readLong()
        require(counterValue == counter) { "channel counter mismatch" }
        counter += 1
        val known = remoteMarker
        if (known == null) {
            remoteMarker = peer.copyOf()
        } else {
            require(peer.contentEquals(known)) { "channel marker mismatch" }
        }
        val sealed = ByteArray(sealedSize)
        input.readFully(sealed)
        return cipher(true, buildIv(peer, counterValue)).doFinal(sealed)
    }

    private fun buildIv(prefix: ByteArray, value: Long): ByteArray {
        val iv = ByteArray(12)
        System.arraycopy(prefix, 0, iv, 0, 4)
        for (index in 0 until 8) {
            iv[4 + index] = (value ushr ((7 - index) * 8)).toByte()
        }
        return iv
    }

    private fun cipher(decrypt: Boolean, iv: ByteArray): Cipher {
        val instance = Cipher.getInstance("AES/GCM/NoPadding")
        instance.init(
            if (decrypt) Cipher.DECRYPT_MODE else Cipher.ENCRYPT_MODE,
            secret,
            GCMParameterSpec(TAG * 8, iv)
        )
        return instance
    }

    companion object {
        const val TAG = 16
        const val MAX_PLAIN = 1 shl 20
    }
}
