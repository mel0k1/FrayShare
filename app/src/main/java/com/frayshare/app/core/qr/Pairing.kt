package com.frayshare.app.core.qr

import java.util.Base64

object Pairing {

    const val SCHEME = "fray://"

    data class Invite(val address: String, val port: Int, val key: ByteArray)

    fun build(address: String, port: Int, key: ByteArray): String =
        "$SCHEME$address:$port#${encode(key)}"

    fun parse(text: String): Invite? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(SCHEME)) return null
        val hostPart = trimmed.removePrefix(SCHEME)
        val split = hostPart.indexOf('#')
        if (split < 0) return null
        val addressPort = hostPart.take(split)
        val portSplit = addressPort.lastIndexOf(':')
        if (portSplit <= 0) return null
        val address = addressPort.take(portSplit)
        val port = addressPort.substring(portSplit + 1).toIntOrNull() ?: return null
        if (address.isEmpty() || port !in 1..65535) return null
        val key = runCatching { decode(hostPart.substring(split + 1)) }.getOrNull() ?: return null
        if (key.size != 32) return null
        return Invite(address, port, key)
    }

    fun encode(key: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(key)

    fun decode(text: String): ByteArray =
        Base64.getUrlDecoder().decode(text)
}
