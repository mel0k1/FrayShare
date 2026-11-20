package com.frayshare.app.core.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeTokenTest {

    @Test
    fun sameSourceGivesSameToken() {
        val a = ResumeToken.of("PHOTO", "IMG_1.jpg", 100500, "content://media/1")
        val b = ResumeToken.of("PHOTO", "IMG_1.jpg", 100500, "content://media/1")
        assertEquals(a, b)
    }

    @Test
    fun differentSourceChangesToken() {
        val a = ResumeToken.of("PHOTO", "IMG_1.jpg", 100500, "content://media/1")
        val otherFile = ResumeToken.of("PHOTO", "IMG_1.jpg", 100500, "content://media/2")
        val otherSize = ResumeToken.of("PHOTO", "IMG_1.jpg", 100501, "content://media/1")
        val otherKind = ResumeToken.of("VIDEO", "IMG_1.jpg", 100500, "content://media/1")
        assertNotEquals(a, otherFile)
        assertNotEquals(a, otherSize)
        assertNotEquals(a, otherKind)
    }

    @Test
    fun tokenIsCompactHex() {
        val token = ResumeToken.of("FILE", "Текст.txt", 0, "/cache/share_text_1.txt")
        assertTrue(token.matches(Regex("[0-9a-f]{16}")))
    }
}
