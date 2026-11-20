package com.frayshare.app.core.webshare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTest {

    @Test
    fun parsesRequestLineAndHeaders() {
        val head = "GET /api/download?id=5&t=abc HTTP/1.1\r\n" +
            "Host: 192.168.1.5:47472\r\n" +
            "Content-Length: 12\r\n\r\n"
        val request = Http.parse(head)
        assertTrue(request != null)
        request!!
        assertEquals("GET", request.method)
        assertEquals("/api/download", request.path)
        assertEquals("5", request.params["id"])
        assertEquals("abc", request.params["t"])
        assertEquals("192.168.1.5:47472", request.headers["host"])
        assertEquals("12", request.headers["content-length"])
    }

    @Test
    fun parsesPostWithoutParams() {
        val request = Http.parse("POST / HTTP/1.1\r\n\r\n")
        assertTrue(request != null)
        assertEquals("POST", request!!.method)
        assertEquals("/", request.path)
        assertEquals(0, request.params.size)
    }

    @Test
    fun decodesPercentEncoding() {
        val request = Http.parse("GET /?name=%D1%84%D0%BE%D1%82%D0%BE%20%D0%BB%D0%B5%D1%82%D0%BE.jpg&t=a%2Bb HTTP/1.1\r\n\r\n")
        assertTrue(request != null)
        assertEquals("фото лето.jpg", request!!.params["name"])
        assertEquals("a+b", request.params["t"])
    }

    @Test
    fun rejectsGarbage() {
        assertNull(Http.parse("not a request"))
        assertNull(Http.parse(""))
        assertNull(Http.parse("GET\r\n\r\n"))
    }

    @Test
    fun queryWithoutValueAndEmptyPairs() {
        val query = Http.parseQuery("flag&&name=")
        assertEquals("", query["flag"])
        assertEquals("", query["name"])
    }

    @Test
    fun encodesFilenameForHeader() {
        val header = Http.encodeFilename("отчёт 2026.jpg")
        assertTrue(header.startsWith("attachment; filename=\"_____ 2026.jpg\""))
        assertTrue(header.contains("filename*=UTF-8''%D0"))
        assertTrue(Http.encodeFilename("plain.jpg").contains("filename=\"plain.jpg\""))
    }
}
