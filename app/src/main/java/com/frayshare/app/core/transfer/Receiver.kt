package com.frayshare.app.core.transfer

import android.content.Context
import android.os.Build
import com.frayshare.app.core.crypto.BoxChannel
import com.frayshare.app.core.crypto.Keys
import com.frayshare.app.core.model.JobState
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
import java.net.ServerSocket
import java.net.Socket

object Receiver {

    suspend fun receive(
        context: Context,
        pairingKey: ByteArray?,
        onProgress: (TransferProgress) -> Unit
    ): JobResult = withContext(Dispatchers.IO) {
        var receivedBytes = 0L
        var receivedItems = 0
        var device = ""
        val server = ServerSocket(Protocol.PORT)
        try {
            onProgress(TransferProgress(state = JobState.AWAITING_PEER))
            val socket = server.accept()
            try {
                socket.tcpNoDelay = true
                val rawInput = Protocol.input(socket)
                val rawOutput = Protocol.output(socket)
                val channel = handshake(rawInput, rawOutput, pairingKey)
                val manifest = readJson(channel)
                device = manifest.optString("device")
                val entries = manifest.optJSONArray("items")?.let { array ->
                    (0 until array.length()).map { array.getJSONObject(it) }
                } ?: emptyList()
                val names = entries.map { it.optString("name") }
                val sizes = entries.map { it.optLong("size") }
                val staging = Staging(context)
                val plan = Plan.compute(sizes, names.map { staging.stored(it) })
                channel.send(planMessage(plan))
                val totalBytes = sizes.sumOf { it }
                val resumeBytes = plan.sumOf { it.offset }
                receivedBytes = resumeBytes
                onProgress(
                    TransferProgress(
                        state = JobState.CONNECTED,
                        totalItems = entries.size,
                        bytesTotal = totalBytes,
                        bytesDone = receivedBytes,
                        peerName = device
                    )
                )
                val segments = if (manifest.optInt("proto") >= Protocol.PROTO) {
                    Bundler.group(sizes)
                } else {
                    entries.indices.map { Segment(it, 1, solo = true) }
                }

                fun receiveInto(position: Int, size: Long, offset: Long, name: String) {
                    if (offset >= size) return
                    val sink = staging.sink(name, offset == 0L)
                    sink.use { target ->
                        var done = offset
                        var lastEmit = 0L
                        var lastEmitBytes = receivedBytes
                        var lastEmitTime = 0L
                        while (done < size) {
                            ensureActive()
                            val frame = channel.receive()
                            target.write(frame)
                            done += frame.size
                            receivedBytes += frame.size
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 120) {
                                val speed = (receivedBytes - lastEmitBytes) * 1000 /
                                    maxOf(now - lastEmitTime, 1L)
                                lastEmitBytes = receivedBytes
                                lastEmitTime = now
                                lastEmit = now
                                onProgress(
                                    running(
                                        position + 1, entries.size, name,
                                        receivedBytes, totalBytes, device, speed
                                    )
                                )
                            }
                        }
                    }
                }

                fun ack(index: Int) {
                    channel.send(
                        JSONObject().put("type", "ack").put("index", index).toString().toByteArray()
                    )
                }

                for (segment in segments) {
                    ensureActive()
                    if (segment.solo) {
                        val position = segment.first
                        val header = readJson(channel)
                        if (header.optString("type") != "item" || header.optInt("index") != position) {
                            throw IllegalStateException("bad item header")
                        }
                        val size = header.optLong("size")
                        val offset = header.optLong("offset")
                        if (size != sizes[position] || offset != plan[position].offset) {
                            throw IllegalStateException("plan mismatch for ${names[position]}")
                        }
                        receiveInto(position, size, offset, names[position])
                        staging.finalize(names[position])
                        ack(position)
                        receivedItems += 1
                        onProgress(
                            running(position + 1, entries.size, names[position], receivedBytes, totalBytes, device)
                        )
                    } else {
                        val header = readJson(channel)
                        val validHeader = header.optString("type") == "bundle" &&
                            header.optInt("index") == segment.first &&
                            header.optInt("count") == segment.count
                        if (!validHeader) {
                            throw IllegalStateException("bad bundle header")
                        }
                        for (position in segment.first until segment.first + segment.count) {
                            receiveInto(position, sizes[position], plan[position].offset, names[position])
                            staging.finalize(names[position])
                            receivedItems += 1
                            onProgress(
                                running(position + 1, entries.size, names[position], receivedBytes, totalBytes, device)
                            )
                        }
                        ack(segment.first + segment.count - 1)
                    }
                }
                JobResult(success = true, items = receivedItems, bytes = receivedBytes, device = device)
            } finally {
                runCatching { socket.close() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            JobResult(success = false, items = receivedItems, bytes = receivedBytes, device = device)
        } finally {
            runCatching { server.close() }
        }
    }

    private fun handshake(
        rawInput: DataInputStream,
        rawOutput: DataOutputStream,
        pairingKey: ByteArray?
    ): BoxChannel {
        val hello = Protocol.readMessage(rawInput)
        if (hello.optString("type") != "hello") {
            throw IllegalStateException("expected hello")
        }
        val key: ByteArray
        when (hello.optString("mode")) {
            Sender.MODE_PSK -> {
                key = pairingKey ?: throw IllegalStateException("no pairing session")
            }
            Sender.MODE_ECDH -> {
                val pair = Keys.ecKeyPair()
                val salt = Keys.random(16)
                Protocol.writeMessage(
                    rawOutput,
                    JSONObject()
                        .put("type", "hello")
                        .put("device", Build.MODEL)
                        .put("pub", Pairing.encode(pair.public.encoded))
                        .put("salt", Pairing.encode(salt))
                )
                key = Keys.sharedKey(
                    pair.private,
                    Pairing.decode(hello.optString("pub")),
                    salt
                )
            }
            else -> throw IllegalStateException("unknown mode ${hello.optString("mode")}")
        }
        if (hello.optString("mode") == Sender.MODE_PSK) {
            Protocol.writeMessage(
                rawOutput,
                JSONObject().put("type", "hello").put("device", Build.MODEL)
            )
        }
        return BoxChannel(rawInput, rawOutput, key)
    }

    private fun planMessage(plan: List<ItemPlan>): ByteArray {
        val array = JSONArray()
        plan.forEach { entry ->
            array.put(JSONObject().put("index", entry.index).put("offset", entry.offset))
        }
        return JSONObject()
            .put("type", "plan")
            .put("proto", Protocol.PROTO)
            .put("plan", array)
            .toString()
            .toByteArray()
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

    private fun readJson(channel: BoxChannel): JSONObject =
        JSONObject(String(channel.receive(), Charsets.UTF_8))
}
