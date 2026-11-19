package com.frayshare.app.core.transfer

import android.content.Context
import android.net.Uri
import android.os.Build
import com.frayshare.app.core.discovery.DiscoveredPeer
import com.frayshare.app.core.model.JobState
import com.frayshare.app.core.model.TransferItem
import com.frayshare.app.core.model.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

object Sender {

    suspend fun send(
        context: Context,
        peer: DiscoveredPeer,
        items: List<TransferItem>,
        onProgress: (TransferProgress) -> Unit
    ): JobResult = withContext(Dispatchers.IO) {
        var sentBytes = 0L
        val totalBytes = items.sumOf { it.sizeBytes }
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(peer.address, peer.port), 8000)
            socket.tcpNoDelay = true
            val output = Protocol.output(socket)
            val input = Protocol.input(socket)
            onProgress(
                TransferProgress(
                    state = JobState.CONNECTED,
                    totalItems = items.size,
                    bytesTotal = totalBytes,
                    peerName = peer.name
                )
            )
            val manifest = JSONObject()
                .put("type", "manifest")
                .put("device", Build.MODEL)
                .put(
                    "items",
                    JSONArray().apply {
                        items.forEach { item ->
                            put(
                                JSONObject()
                                    .put("name", item.name)
                                    .put("kind", item.kind.name)
                                    .put("size", item.sizeBytes)
                            )
                        }
                    }
                )
            Protocol.writeMessage(output, manifest)
            items.forEachIndexed { index, item ->
                ensureActive()
                Protocol.writeMessage(
                    output,
                    JSONObject()
                        .put("type", "item")
                        .put("index", index)
                        .put("size", item.sizeBytes)
                )
                val source = openStream(context, item)
                source.use { stream ->
                    val buffer = ByteArray(Protocol.CHUNK)
                    var done = 0L
                    var lastEmit = 0L
                    while (true) {
                        ensureActive()
                        val read = stream.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        sentBytes += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 120) {
                            lastEmit = now
                            onProgress(
                                TransferProgress(
                                    state = JobState.RUNNING,
                                    currentItem = index + 1,
                                    totalItems = items.size,
                                    currentName = item.name,
                                    bytesDone = sentBytes,
                                    bytesTotal = totalBytes,
                                    peerName = peer.name
                                )
                            )
                        }
                    }
                    if (done != item.sizeBytes) {
                        throw IllegalStateException("size mismatch for ${item.name}")
                    }
                }
                output.flush()
                val ack = Protocol.readMessage(input)
                if (ack.optString("type") != "ack") {
                    throw IllegalStateException("bad ack for ${item.name}")
                }
            }
            Protocol.writeMessage(output, JSONObject().put("type", "done"))
            JobResult(success = true, items = items.size, bytes = sentBytes, device = peer.name)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            JobResult(success = false, items = 0, bytes = sentBytes, device = peer.name)
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun openStream(context: Context, item: TransferItem): InputStream {
        val uri = item.uri
        if (uri != null) {
            return context.contentResolver.openInputStream(Uri.parse(uri))
                ?: throw IllegalStateException("cannot open ${item.name}")
        }
        val path = item.filePath ?: throw IllegalStateException("no source for ${item.name}")
        return FileInputStream(File(path))
    }
}
