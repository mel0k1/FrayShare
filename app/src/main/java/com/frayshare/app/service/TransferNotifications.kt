package com.frayshare.app.service

import com.frayshare.app.core.model.JobState
import com.frayshare.app.core.model.TransferProgress
import com.frayshare.app.core.util.Format

object TransferNotifications {

    data class Content(
        val title: String,
        val text: String,
        val done: Long,
        val total: Long,
        val indeterminate: Boolean,
        val finished: Boolean
    )

    fun content(progress: TransferProgress): Content = when (progress.state) {
        JobState.AWAITING_PEER -> Content(
            title = "Ожидание подключения",
            text = "Приложение работает в фоне и ждёт второе устройство",
            done = 0,
            total = 0,
            indeterminate = true,
            finished = false
        )
        JobState.CONNECTED -> Content(
            title = "Передача: ${progress.peerName}",
            text = "Соединение установлено",
            done = 0,
            total = progress.bytesTotal,
            indeterminate = progress.bytesTotal <= 0,
            finished = false
        )
        JobState.RUNNING -> {
            val done = Format.bytes(progress.bytesDone)
            val total = Format.bytes(progress.bytesTotal)
            val speed = if (progress.speedBps > 0) " · ${Format.bytes(progress.speedBps)}/с" else ""
            Content(
                title = if (progress.peerName.isBlank()) "Передача файлов" else "Передача: ${progress.peerName}",
                text = if (progress.bytesTotal > 0) "$done из $total$speed" else "$done$speed",
                done = progress.bytesDone,
                total = progress.bytesTotal,
                indeterminate = progress.bytesTotal <= 0,
                finished = false
            )
        }
        JobState.COMPLETED -> Content(
            title = "Передача завершена",
            text = "${progress.totalItems} ${Format.plural(progress.totalItems, "файл", "файла", "файлов")} · ${Format.bytes(progress.bytesTotal)}",
            done = progress.bytesTotal,
            total = progress.bytesTotal,
            indeterminate = false,
            finished = true
        )
        JobState.FAILED -> Content(
            title = "Передача прервана",
            text = "Проверьте сеть и попробуйте снова",
            done = 0,
            total = 0,
            indeterminate = false,
            finished = true
        )
        JobState.IDLE -> Content(
            title = "Fray",
            text = "",
            done = 0,
            total = 0,
            indeterminate = false,
            finished = true
        )
    }
}
