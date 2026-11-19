package com.frayshare.app.core.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Keys {

    fun random(length: Int): ByteArray = ByteArray(length).also { SecureRandom().nextBytes(it) }

    fun ecKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()

    fun sharedKey(privateKey: PrivateKey, peerPublic: ByteArray, salt: ByteArray): ByteArray {
        val public = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(peerPublic))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(public, true)
        return hkdf(agreement.generateSecret(), salt, INFO)
    }

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val prk = hmac(effectiveSalt, ikm)
        var previous = ByteArray(0)
        var okm = ByteArray(0)
        var block = 1
        while (okm.size < LENGTH) {
            previous = hmac(prk, previous + info + byteArrayOf(block.toByte()))
            okm += previous
            block += 1
        }
        return okm.copyOf(LENGTH)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private const val LENGTH = 32
    private val INFO = "fray-channel-v1".toByteArray(Charsets.UTF_8)
}
