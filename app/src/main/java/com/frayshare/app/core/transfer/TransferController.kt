package com.frayshare.app.core.transfer

import android.content.Context
import android.os.PowerManager
import com.frayshare.app.core.crypto.Keys
import com.frayshare.app.core.direct.WifiDirect
import com.frayshare.app.core.discovery.Discovery
import com.frayshare.app.core.discovery.LocalIp
import com.frayshare.app.core.discovery.DiscoveredPeer
import com.frayshare.app.core.history.HistoryStore
import com.frayshare.app.core.model.HistoryEntry
import com.frayshare.app.core.model.JobDirection
import com.frayshare.app.core.model.JobState
import com.frayshare.app.core.model.TransferItem
import com.frayshare.app.core.model.TransferProgress
import com.frayshare.app.core.qr.Pairing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object TransferController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _progress = MutableStateFlow(TransferProgress())
    val progress: StateFlow<TransferProgress> = _progress.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _invite = MutableStateFlow<String?>(null)
    val invite: StateFlow<String?> = _invite.asStateFlow()

    private var activeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private fun busy() = activeJob?.isActive == true

    fun loadHistory(context: Context) {
        if (_history.value.isEmpty()) {
            _history.value = HistoryStore.load(context)
        }
    }

    fun startSending(context: Context, peer: DiscoveredPeer, items: List<TransferItem>) {
        if (busy() || items.isEmpty()) return
        Discovery.stop()
        WifiDirect.stopScanning()
        holdCpu(context)
        _progress.value = TransferProgress(state = JobState.CONNECTED, totalItems = items.size)
        activeJob = scope.launch {
            val result = Sender.send(context, peer, items) { update -> _progress.value = update }
            finish(context, result, JobDirection.SEND)
        }
    }

    fun startReceiving(context: Context) {
        if (busy()) return
        val key = Keys.random(32)
        val address = LocalIp.first()
        _invite.value = address?.let { Pairing.build(it, Protocol.PORT, key) }
        holdCpu(context)
        _progress.value = TransferProgress(state = JobState.AWAITING_PEER)
        activeJob = scope.launch {
            val result = Receiver.receive(context, key) { update -> _progress.value = update }
            _invite.value = null
            finish(context, result, JobDirection.RECEIVE)
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        Discovery.stop()
        WifiDirect.shutdown()
        releaseCpu()
        _invite.value = null
        _progress.value = TransferProgress(state = JobState.IDLE)
    }

    private fun holdCpu(context: Context) {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Fray:transfer")
            .also { it.acquire(WAKE_LIMIT_MS) }
    }

    private fun releaseCpu() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    private fun finish(context: Context, result: JobResult, direction: JobDirection) {
        if (!scope.isActive) return
        releaseCpu()
        WifiDirect.shutdown()
        _progress.value = _progress.value.copy(
            state = if (result.success) JobState.COMPLETED else JobState.FAILED
        )
        if (result.items > 0 || !result.success) {
            val entry = HistoryEntry(
                id = System.currentTimeMillis(),
                direction = direction,
                title = result.device,
                itemCount = result.items,
                bytes = result.bytes,
                finishedAt = System.currentTimeMillis(),
                success = result.success
            )
            _history.value = listOf(entry) + _history.value
            HistoryStore.append(context, entry)
        }
    }

    private const val WAKE_LIMIT_MS = 60L * 60 * 1000
}
