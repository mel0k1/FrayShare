package com.frayshare.app

import com.frayshare.app.core.util.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun bytesFormatsSmallValues() {
        assertEquals("512 B", Format.bytes(512))
    }

    @Test
    fun bytesFormatsKilobytes() {
        assertEquals("1.5 KB", Format.bytes(1536))
    }

    @Test
    fun bytesFormatsGigabytes() {
        assertEquals("2.0 GB", Format.bytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun percentClamps() {
        assertEquals(1f, Format.percent(200, 100))
        assertEquals(0f, Format.percent(10, 0))
    }

    @Test
    fun pluralPicksRussianForms() {
        assertEquals("файл", Format.plural(1, "файл", "файла", "файлов"))
        assertEquals("файла", Format.plural(3, "файл", "файла", "файлов"))
        assertEquals("файлов", Format.plural(11, "файл", "файла", "файлов"))
        assertEquals("файлов", Format.plural(25, "файл", "файла", "файлов"))
    }
}
