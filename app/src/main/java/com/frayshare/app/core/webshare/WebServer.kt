package com.frayshare.app.core.webshare

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Size
import com.frayshare.app.core.model.DataKind
import com.frayshare.app.core.model.TransferItem
import com.frayshare.app.core.source.Sources
import com.frayshare.app.core.transfer.Protocol
import com.frayshare.app.core.transfer.Staging
import com.frayshare.app.core.util.Mime
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

class WebServer private constructor(
    private val context: Context,
    private val server: ServerSocket,
    private val token: String
) {
    private val pool = Executors.newCachedThreadPool()
    private val lock = Any()
    @Volatile private var stopped = false
    private var entries: List<TransferItem> = emptyList()

    val url: String get() = "http://${host()}:$PORT/#t=$token"

    private fun host(): String = com.frayshare.app.core.discovery.LocalIp.first() ?: "127.0.0.1"

    private fun acceptLoop() {
        while (!stopped) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            pool.execute { runCatching { handle(socket) } }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 30000
            socket.tcpNoDelay = true
            val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 8192))
            val raw = BufferedOutputStream(socket.getOutputStream(), Protocol.CHUNK)
            val request = Http.parse(readHead(input))
            when {
                request == null -> respond(raw, 400, "Bad Request", "bad request")
                request.method == "GET" && request.path == "/" -> servePage(raw)
                !authorized(request) -> respond(raw, 401, "Unauthorized", "нужен ключ доступа из ссылки")
                request.method == "GET" && request.path == "/api/list" -> serveList(raw)
                request.method == "GET" && request.path == "/api/download" -> serveDownload(request, raw)
                request.method == "GET" && request.path == "/api/thumb" -> serveThumb(request, raw)
                request.method == "POST" && request.path == "/api/upload" -> serveUpload(request, input, raw)
                else -> respond(raw, 404, "Not Found", "нет такого пути")
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun readHead(input: DataInputStream): String {
        val head = StringBuilder()
        var previous = 0
        var current = 0
        while (head.length < 16384) {
            val byte = input.read()
            if (byte < 0) break
            current = byte
            head.append(byte.toChar())
            if (previous == '\r'.code && current == '\n'.code) {
                val text = head.toString()
                if (text.endsWith("\r\n\r\n")) break
            }
            previous = current
        }
        return head.toString()
    }

    private fun authorized(request: HttpRequest): Boolean {
        val supplied = request.params["t"] ?: return false
        return MessageDigest.isEqual(
            supplied.toByteArray(Charsets.UTF_8),
            token.toByteArray(Charsets.UTF_8)
        )
    }

    private fun servePage(raw: BufferedOutputStream) {
        val page = runCatching { context.assets.open(PAGE).use { it.readBytes() } }
            .getOrElse { ByteArray(0) }
        if (page.isEmpty()) {
            respond(raw, 500, "Server Error", "страница не найдена")
        } else {
            respond(
                raw, 200, "OK", page,
                listOf("Content-Type: text/html; charset=utf-8", "Cache-Control: no-store")
            )
        }
    }

    private fun serveList(raw: BufferedOutputStream) {
        val catalog = Sources.load(context)
        val groups = listOf(
            "Фото" to catalog.photos,
            "Видео" to catalog.videos,
            "Загрузки" to catalog.files
        )
        val json = WebListing.json(Build.MODEL, groups)
        synchronized(lock) { entries = groups.flatMap { it.second } }
        respond(
            raw, 200, "OK", json.toByteArray(Charsets.UTF_8),
            listOf("Content-Type: application/json; charset=utf-8", "Cache-Control: no-store")
        )
    }

    private fun serveDownload(request: HttpRequest, raw: BufferedOutputStream) {
        val item = entry(request) ?: return respond(raw, 404, "Not Found", "файл не найден")
        val stream = open(item) ?: return respond(raw, 404, "Not Found", "файл недоступен")
        stream.use { source ->
            raw.write(head(200, "OK", listOf(
                "Content-Type: ${Mime.of(item.name)}",
                // Актуальный размер: снимок из /api/list мог устареть.
                "Content-Length: ${actualSize(item)}",
                "Content-Disposition: ${Http.encodeFilename(item.name)}",
                "Cache-Control: no-store"
            )))
            val buffer = ByteArray(Protocol.CHUNK)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                raw.write(buffer, 0, read)
            }
            raw.flush()
        }
    }

    private fun actualSize(item: TransferItem): Long {
        val uri = item.uri
        if (uri != null) {
            return runCatching {
                context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use {
                    it.statSize
                } ?: item.sizeBytes
            }.getOrDefault(item.sizeBytes)
        }
        val path = item.filePath ?: return item.sizeBytes
        return runCatching { java.io.File(path).length() }.getOrDefault(item.sizeBytes)
    }

    private fun serveThumb(request: HttpRequest, raw: BufferedOutputStream) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return respond(raw, 404, "Not Found", "миниатюры недоступны")
        }
        val item = entry(request) ?: return respond(raw, 404, "Not Found", "файл не найден")
        if (item.kind != DataKind.PHOTO && item.kind != DataKind.VIDEO) {
            return respond(raw, 404, "Not Found", "нет миниатюры")
        }
        val uri = item.uri ?: return respond(raw, 404, "Not Found", "нет миниатюры")
        val bitmap = runCatching {
            context.contentResolver.loadThumbnail(Uri.parse(uri), Size(320, 320), null)
        }.getOrNull() ?: return respond(raw, 404, "Not Found", "нет миниатюры")
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
        bitmap.recycle()
        respond(
            raw, 200, "OK", bytes,
            listOf("Content-Type: image/jpeg", "Cache-Control: private, max-age=86400")
        )
    }

    private fun serveUpload(request: HttpRequest, input: DataInputStream, raw: BufferedOutputStream) {
        val name = request.params["name"]
        if (name.isNullOrBlank()) return respond(raw, 400, "Bad Request", "нет имени файла")
        val length = request.headers["content-length"]?.toLongOrNull()
        if (length == null || length < 0 || length > MAX_UPLOAD) {
            return respond(raw, 400, "Bad Request", "недопустимый размер")
        }
        val staging = Staging(context)
        try {
            staging.sink(name, truncate = true).use { sink ->
                var left = length
                val buffer = ByteArray(Protocol.CHUNK)
                while (left > 0) {
                    val step = minOf(left, buffer.size.toLong()).toInt()
                    input.readFully(buffer, 0, step)
                    sink.write(buffer, 0, step)
                    left -= step
                }
            }
            staging.finalize(name)
        } catch (e: Exception) {
            // Недогруженный .fraypart для веб-загрузок мусор — убираем сразу.
            staging.deletePart(name)
            respond(raw, 500, "Server Error", "не удалось сохранить файл")
            return
        }
        respond(raw, 200, "OK", "ok")
    }

    private fun entry(request: HttpRequest): TransferItem? {
        val id = request.params["id"]?.toIntOrNull() ?: return null
        synchronized(lock) {
            if (id !in entries.indices) return null
            return entries[id]
        }
    }

    private fun open(item: TransferItem): InputStream? {
        val uri = item.uri
        if (uri != null) {
            return runCatching { context.contentResolver.openInputStream(Uri.parse(uri)) }.getOrNull()
        }
        val path = item.filePath ?: return null
        return runCatching { FileInputStream(java.io.File(path)) }.getOrNull()
    }

    private fun respond(
        raw: BufferedOutputStream,
        status: Int,
        reason: String,
        text: String
    ) = respond(raw, status, reason, text.toByteArray(Charsets.UTF_8), listOf("Content-Type: text/plain; charset=utf-8"))

    private fun respond(
        raw: BufferedOutputStream,
        status: Int,
        reason: String,
        body: ByteArray,
        headers: List<String>
    ) {
        raw.write(head(status, reason, headers + "Content-Length: ${body.size}"))
        raw.write(body)
        raw.flush()
    }

    private fun head(status: Int, reason: String, headers: List<String>): ByteArray {
        val out = StringBuilder("HTTP/1.1 $status $reason\r\nConnection: close\r\n")
        for (header in headers) out.append(header).append("\r\n")
        return out.append("\r\n").toString().toByteArray(Charsets.ISO_8859_1)
    }

    fun stop() {
        stopped = true
        runCatching { server.close() }
        pool.shutdownNow()
    }

    companion object {
        const val PORT = 47472
        const val MAX_UPLOAD = 8L * 1024 * 1024 * 1024
        const val PAGE = "web/index.html"

        fun start(context: Context): WebServer? = runCatching {
            val server = ServerSocket()
            server.reuseAddress = true
            server.bind(java.net.InetSocketAddress(PORT))
            val token = token()
            val web = WebServer(context.applicationContext, server, token)
            Thread({ web.acceptLoop() }, "fray-web").apply { isDaemon = true }.start()
            web
        }.getOrNull()

        private fun token(): String {
            val bytes = ByteArray(10)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        }
    }
}
