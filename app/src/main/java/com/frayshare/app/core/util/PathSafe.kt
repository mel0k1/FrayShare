package com.frayshare.app.core.util

object PathSafe {
    private val BAD = Regex("[\\\\:*?\"<>|\u0000-\u001F]")

    fun clean(name: String): String {
        val segments = name.split('/')
            .map { BAD.replace(it, "").trim().take(120) }
            .filter { it.isNotBlank() && it != "." && it != ".." }
        return if (segments.isEmpty()) "файл" else segments.joinToString("/")
    }
}
