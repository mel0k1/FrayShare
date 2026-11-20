package com.frayshare.app.core.transfer

import java.security.MessageDigest

/**
 * Стабильный отпечаток элемента передачи для докачки: приёмник сравнивает его
 * с сохранённым у себя и начинает с нуля, если файл под тем же именем другой.
 */
object ResumeToken {

    fun of(kind: String, name: String, size: Long, source: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val payload = "$kind\n$name\n$size\n$source"
        return digest.digest(payload.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
    }
}
