package com.frayshare.app.core.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundlerTest {

    @Test
    fun mixesSolosAndBundles() {
        val sizes = listOf(600_000L, 5_000L, 3_000L, 200_000L, 1_000L)
        val segments = Bundler.group(sizes)
        assertEquals(
            listOf(
                Segment(0, 1, solo = true),
                Segment(1, 2, solo = false),
                Segment(3, 1, solo = true),
                Segment(4, 1, solo = false)
            ),
            segments
        )
    }

    @Test
    fun respectsFileLimit() {
        val sizes = List(300) { 1_000L }
        val segments = Bundler.group(sizes, maxFiles = 256)
        assertEquals(2, segments.size)
        assertEquals(256, segments[0].count)
        assertEquals(44, segments[1].count)
    }

    @Test
    fun respectsByteLimit() {
        val sizes = List(30) { 100_000L }
        val segments = Bundler.group(sizes, maxBytes = 1_000_000L)
        assertEquals(3, segments.size)
        assertEquals(listOf(10, 10, 10), segments.map { it.count })
    }

    @Test
    fun bigFilesStaySolo() {
        val segments = Bundler.group(List(3) { 500_000L })
        assertEquals(3, segments.size)
        assertTrue(segments.all { it.solo })
    }

    @Test
    fun emptyListIsEmpty() {
        assertEquals(0, Bundler.group(emptyList()).size)
    }

    @Test
    fun zeroSizedFilesBundle() {
        val segments = Bundler.group(listOf(0L, 0L, 10L))
        assertEquals(1, segments.size)
        assertEquals(3, segments[0].count)
    }
}
