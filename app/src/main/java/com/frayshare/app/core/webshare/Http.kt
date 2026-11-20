package com.frayshare.app.core.webshare

import java.net.URLDecoder

data class HttpRequest(
    val method: String,
    val path: String,
    val params: Map<String, String>,
    val headers: Map<String, String>
)

object Http {

    fun parse(head: String): HttpRequest? {
        val lines = head.split("\r\n")
        if (lines.isEmpty() || lines[0].isBlank()) return null
        val parts = lines[0].trim().split(" ")
        if (parts.size < 3 || !parts[2].startsWith("HTTP/")) return null
        val target = parts[1]
        val cut = target.indexOf('?')
        val path = if (cut >= 0) target.substring(0, cut) else target
        val query = if (cut >= 0) parseQuery(target.substring(cut + 1)) else emptyMap()
        val headers = mutableMapOf<String, String>()
        for (line in lines.drop(1)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        return HttpRequest(parts[0].uppercase(), path, query, headers)
    }

    fun parseQuery(query: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val key = if (eq >= 0) pair.substring(0, eq) else pair
            val value = if (eq >= 0) pair.substring(eq + 1) else ""
            out[decode(key)] = decode(value)
        }
        return out
    }

    fun decode(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)
    }.getOrDefault(value)

    fun encodeFilename(name: String): String {
        val safe = name.replace("\"", "'").ifBlank { "file" }
        val ascii = safe.map { if (it.code in 32..126) it else '_' }.joinToString("")
        val encoded = StringBuilder()
        for (byte in safe.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            if (code in 33..126 && code != 34) {
                encoded.append(code.toChar())
            } else {
                encoded.append('%')
                val hex = Integer.toHexString(code).uppercase()
                if (hex.length < 2) encoded.append('0')
                encoded.append(hex)
            }
        }
        return "attachment; filename=\"$ascii\"; filename*=UTF-8''$encoded"
    }
}
