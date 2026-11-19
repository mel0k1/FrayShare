package com.frayshare.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PathSafeTest {

    @Test
    fun keepsPlainNames() {
        assertEquals("привет/мир.txt", PathSafe.clean("привет/мир.txt"))
    }

    @Test
    fun stripsReservedCharacters() {
        assertEquals("Отчёт итог2024.txt", PathSafe.clean("Отчёт: итог*2024?.txt"))
    }

    @Test
    fun dropsDotSegments() {
        assertEquals("a/файл", PathSafe.clean("a/./../файл"))
    }

    @Test
    fun collapsesExtraSlashes() {
        assertEquals("a/b", PathSafe.clean("a///b"))
    }

    @Test
    fun fallsBackOnEmpty() {
        assertEquals("файл", PathSafe.clean(""))
        assertEquals("файл", PathSafe.clean("///"))
        assertEquals("файл", PathSafe.clean(".."))
    }

    @Test
    fun stripsTraversalAndControl() {
        assertEquals("безопасно", PathSafe.clean("../../безопасно"))
        assertEquals("имя", PathSafe.clean("им\u0000я"))
    }
}
