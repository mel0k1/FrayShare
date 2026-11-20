package com.frayshare.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.frayshare.app.MainActivity
import com.frayshare.app.core.model.JobState
import com.frayshare.app.core.model.TransferProgress
import com.frayshare.app.core.transfer.TransferController
import com.frayshare.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TransferService : Service() {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null
    private lateinit var notifications: NotificationManager
    private var lastState: JobState = JobState.IDLE
    private var lastUpdateAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Передача файлов",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Прогресс передачи файлов между устройствами" }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(notify(TransferController.progress.value))
        if (collectJob == null) {
            collectJob = scope.launch {
                TransferController.progress.collect { update(it) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        collectJob = null
        scope.cancel()
        super.onDestroy()
    }

    // Лимит Android 15 для dataSync-сервисов (6 часов в сутки).
    override fun onTimeout(startId: Int, fgsType: Int) {
        TransferController.cancel()
        stopSelf()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun update(progress: TransferProgress) {
        val finished = progress.state == JobState.COMPLETED || progress.state == JobState.FAILED
        val throttled = progress.state == lastState &&
            progress.state == JobState.RUNNING &&
            SystemClock.elapsedRealtime() - lastUpdateAt < UPDATE_INTERVAL_MS
        if (throttled) return
        lastState = progress.state
        lastUpdateAt = SystemClock.elapsedRealtime()
        notifications.notify(NOTIFICATION_ID, notify(progress))
        if (finished) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        } else if (progress.state == JobState.IDLE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notify(progress: TransferProgress): Notification {
        val content = TransferNotifications.content(progress)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_transfer)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(!content.finished)
        if (content.total > 0) {
            builder.setProgress(
                content.total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                content.done.coerceIn(0, content.total).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                false
            )
        } else if (content.indeterminate) {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        const val CHANNEL = "transfer"
        const val NOTIFICATION_ID = 4747
        const val UPDATE_INTERVAL_MS = 500L
    }
}
