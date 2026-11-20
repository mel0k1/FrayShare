package com.frayshare.app.core.transfer

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.frayshare.app.core.crypto.BoxChannel
import com.frayshare.app.core.crypto.Keys
import com.frayshare.app.core.discovery.DiscoveredPeer
import com.frayshare.app.core.model.JobState
import com.frayshare.app.core.model.TransferItem
import com.frayshare.app.core.model.TransferProgress
import com.frayshare.app.core.qr.Pairing
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

    @Volatile private var active: Socket? = null

    // Отмена передачи: закрыть сокет, чтобы застрявший read/write немедленно вышел.
    fun abort() {
        runCatching { active?.close() }
    }

    suspend fun send(
        context: Context,
        peer: DiscoveredPeer,
        items: List<TransferItem>,
        onProgress: (TransferProgress) -> Unit
    ): JobResult = withContext(Dispatchers.IO) {
        var sentBytes = 0L
        var sentItems = 0
        var peerName = peer.name
        val totalBytes = items.sumOf { it.sizeBytes }
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(peer.address, peer.port), 8000)
            socket.tcpNoDelay = true
            socket.soTimeout = IDLE_TIMEOUT_MS
            active = socket
            val rawInput = Protocol.input(socket)
            val rawOutput = Protocol.output(socket)
            val (key, remoteName) = handshake(rawInput, rawOutput, peer)
            if (remoteName.isNotBlank()) peerName = remoteName
            val channel = BoxChannel(rawInput, rawOutput, key)
            onProgress(
                TransferProgress(
                    state = JobState.CONNECTED,
                    totalItems = items.size,
                    bytesTotal = totalBytes,
                    peerName = peerName
                )
            )
            channel.send(manifest(items))
            val plan = readPlan(channel, items.size)
            val offsets = plan.offsets
            sentBytes = offsets.sum()
            val segments = if (plan.proto >= Protocol.PROTO) {
                Bundler.group(items.map { it.sizeBytes })
            } else {
                items.indices.map { Segment(it, 1, solo = true) }
            }

            fun writeItem(position: Int, item: TransferItem, offset: Long) {
                if (offset >= item.sizeBytes) return
                val source = openStream(context, item)
                source.use { stream ->
                    skip(stream, offset)
                    val buffer = ByteArray(Protocol.CHUNK)
                    var done = offset
                        var lastEmit = 0L
                        var lastEmitBytes = sentBytes
                        var lastEmitTime = System.currentTimeMillis()
                    while (true) {
                        ensureActive()
                        val read = stream.read(buffer)
                        if (read < 0) break
                        channel.send(buffer.copyOf(read))
                        done += read
                        sentBytes += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 120) {
                            val speed = (sentBytes - lastEmitBytes) * 1000 /
                                maxOf(now - lastEmitTime, 1L)
                            lastEmitBytes = sentBytes
                            lastEmitTime = now
                            lastEmit = now
                            onProgress(
                                running(
                                    position, items.size, item.name,
                                    sentBytes, totalBytes, peerName, speed
                                )
                            )
                        }
                    }
                    if (done != item.sizeBytes) {
                        throw IllegalStateException("size mismatch for ${item.name}")
                    }
                }
            }

            for (segment in segments) {
                ensureActive()
                val last = segment.first + segment.count - 1
                if (segment.solo) {
                    val item = items[segment.first]
                    channel.send(
                        itemHeader(segment.first, item.sizeBytes, offsets[segment.first])
                    )
                    writeItem(segment.first, item, offsets[segment.first])
                    val ack = readJson(channel)
                    if (ack.optString("type") != "ack" || ack.optInt("index") != segment.first) {
                        throw IllegalStateException("bad ack for ${item.name}")
                    }
                    sentItems += 1
                    onProgress(
                        running(segment.first + 1, items.size, item.name, sentBytes, totalBytes, peerName)
                    )
                } else {
                    channel.send(
                        JSONObject()
                            .put("type", "bundle")
                            .put("index", segment.first)
                            .put("count", segment.count)
                            .toString()
                            .toByteArray()
                    )
                    for (position in segment.first..last) {
                        ensureActive()
                        val item = items[position]
                        writeItem(position, item, offsets[position])
                        sentItems += 1
                        onProgress(
                            running(position + 1, items.size, item.name, sentBytes, totalBytes, peerName)
                        )
                    }
                    val ack = readJson(channel)
                    if (ack.optString("type") != "ack" || ack.optInt("index") != last) {
                        throw IllegalStateException("bad bundle ack for $last")
                    }
                }
            }
            channel.send(JSONObject().put("type", "done").toString().toByteArray())
            JobResult(success = true, items = sentItems, bytes = sentBytes, device = peerName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            Log.w(TAG, "send failed: ${e.message}")
            JobResult(success = false, items = sentItems, bytes = sentBytes, device = peerName)
        } finally {
            runCatching { socket.close() }
            if (active === socket) active = null
        }
    }

    private fun itemHeader(index: Int, size: Long, offset: Long): ByteArray = JSONObject()
        .put("type", "item")
        .put("index", index)
        .put("size", size)
        .put("offset", offset)
        .toString()
        .toByteArray()

    private fun handshake(
        rawInput: DataInputStream,
        rawOutput: DataOutputStream,
        peer: DiscoveredPeer
    ): Pair<ByteArray, String> {
        val hello = JSONObject().put("type", "hello").put("device", Build.MODEL)
        val key: ByteArray
        val reply: JSONObject
        if (peer.key != null) {
            hello.put("mode", MODE_PSK)
            Protocol.writeMessage(rawOutput, hello)
            reply = Protocol.readMessage(rawInput)
            requireHello(reply)
            key = peer.key
        } else {
            val pair = Keys.ecKeyPair()
            hello.put("mode", MODE_ECDH).put("pub", Pairing.encode(pair.public.encoded))
            Protocol.writeMessage(rawOutput, hello)
            reply = Protocol.readMessage(rawInput)
            requireHello(reply)
            key = Keys.sharedKey(
                pair.private,
                Pairing.decode(reply.optString("pub")),
                Pairing.decode(reply.optString("salt"))
            )
        }
        return Pair(key, reply.optString("device"))
    }

    private fun requireHello(reply: JSONObject) {
        if (reply.optString("type") != "hello") {
            throw IllegalStateException("peer refused hello")
        }
    }

    private data class PlanReply(val offsets: LongArray, val proto: Int)

    private fun readPlan(channel: BoxChannel, expected: Int): PlanReply {
        val plan = readJson(channel)
        if (plan.optString("type") != "plan") {
            throw IllegalStateException("missing plan")
        }
        val array = plan.optJSONArray("plan") ?: throw IllegalStateException("missing plan")
        val offsets = LongArray(expected)
        for (position in 0 until array.length()) {
            val entry = array.getJSONObject(position)
            val index = entry.getInt("index")
            if (index !in 0 until expected) throw IllegalStateException("bad plan index")
            offsets[index] = entry.getLong("offset")
        }
        return PlanReply(offsets, plan.optInt("proto"))
    }

    private fun running(
        currentItem: Int,
        totalItems: Int,
        currentName: String,
        bytesDone: Long,
        bytesTotal: Long,
        peerName: String,
        speedBps: Long = 0
    ): TransferProgress = TransferProgress(
        state = JobState.RUNNING,
        currentItem = currentItem,
        totalItems = totalItems,
        currentName = currentName,
        bytesDone = bytesDone,
        bytesTotal = bytesTotal,
        peerName = peerName,
        speedBps = speedBps
    )

    private fun manifest(items: List<TransferItem>): ByteArray {
        val payload = JSONObject()
            .put("type", "manifest")
            .put("resume", true)
            .put("proto", Protocol.PROTO)
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
                                .put(
                                    "token",
                                    ResumeToken.of(
                                        item.kind.name,
                                        item.name,
                                        item.sizeBytes,
                                        item.uri ?: item.filePath ?: ""
                                    )
                                )
                        )
                    }
                }
            )
        return payload.toString().toByteArray()
    }

    private fun readJson(channel: BoxChannel): JSONObject =
        JSONObject(String(channel.receive(), Charsets.UTF_8))

    private fun skip(stream: InputStream, amount: Long) {
        var left = amount
        val buffer = ByteArray(Protocol.CHUNK)
        while (left > 0) {
            val step = minOf(left, buffer.size.toLong()).toInt()
            val read = stream.read(buffer, 0, step)
            if (read < 0) throw IllegalStateException("source shorter than offset")
            left -= read
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

    const val MODE_PSK = "psk"
    const val MODE_ECDH = "ecdh"

    private const val IDLE_TIMEOUT_MS = 30_000
    private const val TAG = "Fray"
}
