package com.frayshare.app.core.transfer

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.frayshare.app.core.model.JobState
import com.frayshare.app.core.model.TransferProgress
import com.frayshare.app.core.util.Mime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.ServerSocket

object Receiver {

    suspend fun receive(
        context: Context,
        onProgress: (TransferProgress) -> Unit
    ): JobResult = withContext(Dispatchers.IO) {
        var receivedBytes = 0L
        var receivedItems = 0
        var device = ""
        val server = ServerSocket(Protocol.PORT)
        try {
            onProgress(TransferProgress(state = JobState.AWAITING_PEER))
            server.soTimeout = 0
            val socket = server.accept()
            try {
                socket.tcpNoDelay = true
                val input = Protocol.input(socket)
                val output = Protocol.output(socket)
                val manifest = Protocol.readMessage(input)
                device = manifest.optString("device")
                val entries = manifest.optJSONArray("items")?.let { array ->
                    (0 until array.length()).map { array.getJSONObject(it) }
                } ?: emptyList()
                val totalBytes = entries.sumOf { it.optLong("size") }
                onProgress(
                    TransferProgress(
                        state = JobState.CONNECTED,
                        totalItems = entries.size,
                        bytesTotal = totalBytes,
                        peerName = device
                    )
                )
                for (index in entries.indices) {
                    ensureActive()
                    val header = Protocol.readMessage(input)
                    if (header.optString("type") != "item") {
                        throw IllegalStateException("bad item header")
                    }
                    val size = header.optLong("size")
                    val name = entries[index].optString("name")
                    val target = saveTarget(context, name)
                    target.use { sink ->
                        val buffer = ByteArray(Protocol.CHUNK)
                        var done = 0L
                        var lastEmit = 0L
                        while (done < size) {
                            ensureActive()
                            val step = minOf(Protocol.CHUNK.toLong(), size - done).toInt()
                            val read = input.read(buffer, 0, step)
                            if (read < 0) throw IllegalStateException("stream ended early")
                            sink.write(buffer, 0, read)
                            done += read
                            receivedBytes += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 120) {
                                lastEmit = now
                                onProgress(
                                    TransferProgress(
                                        state = JobState.RUNNING,
                                        currentItem = index + 1,
                                        totalItems = entries.size,
                                        currentName = name,
                                        bytesDone = receivedBytes,
                                        bytesTotal = totalBytes,
                                        peerName = device
                                    )
                                )
                            }
                        }
                    }
                    receivedItems += 1
                    Protocol.writeMessage(
                        output,
                        JSONObject().put("type", "ack").put("index", index)
                    )
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

    private fun saveTarget(context: Context, name: String): OutputStream {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, Mime.of(name))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Fray")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("cannot create $name")
            return resolver.openOutputStream(uri)
                ?: throw IllegalStateException("cannot open $name")
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Fray")
        dir.mkdirs()
        return FileOutputStream(File(dir, name))
    }
}
