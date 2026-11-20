package com.frayshare.app.service

import com.frayshare.app.core.model.JobState
import com.frayshare.app.core.model.TransferProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferNotificationsTest {

    @Test
    fun awaitingPeerIsIndeterminate() {
        val content = TransferNotifications.content(TransferProgress(state = JobState.AWAITING_PEER))
        assertEquals("Ожидание подключения", content.title)
        assertTrue(content.indeterminate)
        assertFalse(content.finished)
    }

    @Test
    fun runningShowsBytesAndSpeed() {
        val content = TransferNotifications.content(
            TransferProgress(
                state = JobState.RUNNING,
                peerName = "Pixel",
                bytesDone = 1024L * 1024,
                bytesTotal = 4L * 1024 * 1024,
                speedBps = 512L * 1024
            )
        )
        assertTrue(content.title.contains("Pixel"))
        assertTrue(content.text.contains("из"))
        assertTrue(content.text.contains("/с"))
        assertFalse(content.indeterminate)
        assertFalse(content.finished)
    }

    @Test
    fun runningWithoutTotalIsIndeterminate() {
        val content = TransferNotifications.content(
            TransferProgress(state = JobState.RUNNING, bytesDone = 100, bytesTotal = 0)
        )
        assertTrue(content.indeterminate)
        assertFalse(content.text.contains("из"))
    }

    @Test
    fun completedIsFinishedWithCounts() {
        val content = TransferNotifications.content(
            TransferProgress(
                state = JobState.COMPLETED,
                totalItems = 3,
                bytesDone = 300,
                bytesTotal = 300
            )
        )
        assertTrue(content.finished)
        assertTrue(content.text.contains("3 файла"))
        assertEquals(300L, content.done)
    }

    @Test
    fun failedIsFinished() {
        val content = TransferNotifications.content(TransferProgress(state = JobState.FAILED))
        assertTrue(content.finished)
        assertEquals("Передача прервана", content.title)
    }
}
