package com.frayshare.app.core.transfer

import android.content.Context
import com.frayshare.app.core.discovery.Discovery
import com.frayshare.app.core.discovery.DiscoveredPeer
import com.frayshare.app.core.history.HistoryStore
import com.frayshare.app.core.model.HistoryEntry
import com.frayshare.app.core.model.JobDirection
import com.frayshare.app.core.model.JobState
import com.frayshare.app.core.model.TransferItem
import com.frayshare.app.core.model.TransferProgress
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

    private var activeJob: Job? = null

    private fun busy() = activeJob?.isActive == true

    fun loadHistory(context: Context) {
        if (_history.value.isEmpty()) {
            _history.value = HistoryStore.load(context)
        }
    }

    fun startSending(context: Context, peer: DiscoveredPeer, items: List<TransferItem>) {
        if (busy() || items.isEmpty()) return
        Discovery.stop()
        _progress.value = TransferProgress(state = JobState.CONNECTED, totalItems = items.size)
        activeJob = scope.launch {
            val result = Sender.send(context, peer, items) { update -> _progress.value = update }
            finish(context, result, JobDirection.SEND)
        }
    }

    fun startReceiving(context: Context) {
        if (busy()) return
        _progress.value = TransferProgress(state = JobState.AWAITING_PEER)
        activeJob = scope.launch {
            val result = Receiver.receive(context) { update -> _progress.value = update }
            finish(context, result, JobDirection.RECEIVE)
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        Discovery.stop()
        _progress.value = TransferProgress(state = JobState.IDLE)
    }

    private fun finish(context: Context, result: JobResult, direction: JobDirection) {
        if (!scope.isActive) return
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
}
