package com.frayshare.app.core.share

import com.frayshare.app.core.model.DataKind
import com.frayshare.app.core.model.TransferItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareInTest {

    @Test
    fun mapsMimeToKind() {
        assertEquals(DataKind.PHOTO, ShareIn.kindOf("image/jpeg"))
        assertEquals(DataKind.VIDEO, ShareIn.kindOf("video/mp4"))
        assertEquals(DataKind.FILE, ShareIn.kindOf("application/pdf"))
        assertEquals(DataKind.FILE, ShareIn.kindOf(""))
    }

    @Test
    fun namesTextFiles() {
        assertEquals("Ссылка.txt", ShareIn.textFileName("https://example.com/page?x=1"))
        assertEquals("Ссылка.txt", ShareIn.textFileName("www.example.com"))
        assertEquals("Текст.txt", ShareIn.textFileName("просто заметка"))
        assertEquals("Текст.txt", ShareIn.textFileName("ftp://example.com/file"))
    }

    @Test
    fun buildsItemsFromTextsAndFiles() {
        val calls = mutableListOf<Pair<String, String>>()
        val items = ShareIn.buildItems(
            listOf("https://example.com", "заметка", "  ", "вторая заметка"),
            listOf(
                ShareIn.SharedFile("content://media/1", null, "photo.jpg", 100, "image/jpeg"),
                ShareIn.SharedFile(null, "/tmp/doc.pdf", "doc.pdf", 50, "application/pdf")
            )
        ) { name, content ->
            calls.add(name to content)
            TransferItem("text-${calls.size}", name, DataKind.FILE, content.length.toLong())
        }
        assertEquals(5, items.size)
        assertEquals(listOf("Ссылка.txt" to "https://example.com"), calls.take(1))
        assertEquals("Текст.txt", calls[1].first)
        assertEquals("Текст.txt (2)", calls[2].first)
        assertEquals("photo.jpg", items[3].name)
        assertEquals(DataKind.PHOTO, items[3].kind)
        assertEquals(100L, items[3].sizeBytes)
        assertEquals("doc.pdf", items[4].name)
        assertEquals(DataKind.FILE, items[4].kind)
    }

    @Test
    fun blankTextsAreSkipped() {
        val items = ShareIn.buildItems(listOf("   "), emptyList()) { name, _ ->
            TransferItem("t", name, DataKind.FILE, 0)
        }
        assertTrue(items.isEmpty())
    }

    @Test
    fun fileWithoutNameGetsFallback() {
        val items = ShareIn.buildItems(
            emptyList(),
            listOf(ShareIn.SharedFile("content://x", null, "", 10, "application/octet-stream"))
        ) { _, _ -> throw IllegalStateException("не должен вызываться") }
        assertEquals("Файл", items[0].name)
    }
}
