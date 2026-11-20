package com.frayshare.app.core.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.frayshare.app.core.model.DataKind
import com.frayshare.app.core.model.TransferItem
import java.io.File

object ShareIn {

    data class SharedFile(
        val uri: String?,
        val filePath: String?,
        val name: String,
        val size: Long,
        val mime: String
    )

    fun kindOf(mime: String): DataKind = when {
        mime.startsWith("image/") -> DataKind.PHOTO
        mime.startsWith("video/") -> DataKind.VIDEO
        else -> DataKind.FILE
    }

    fun textFileName(text: String): String {
        val trimmed = text.trim()
        val link = trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("www.")
        return if (link) "Ссылка.txt" else "Текст.txt"
    }

    fun buildItems(
        texts: List<String>,
        files: List<SharedFile>,
        makeTextFile: (name: String, content: String) -> TransferItem
    ): List<TransferItem> {
        val out = mutableListOf<TransferItem>()
        val used = mutableMapOf<String, Int>()
        texts.filter { it.isNotBlank() }.forEach { text ->
            val base = textFileName(text)
            val seen = used[base] ?: 0
            used[base] = seen + 1
            val name = if (seen == 0) base else "$base (${seen + 1})"
            out.add(makeTextFile(name, text.trim()))
        }
        files.forEach { file ->
            out.add(
                TransferItem(
                    id = file.uri ?: file.filePath ?: "share-${file.name}",
                    name = file.name.ifBlank { "Файл" },
                    kind = kindOf(file.mime),
                    sizeBytes = file.size,
                    uri = file.uri,
                    filePath = file.filePath
                )
            )
        }
        return out
    }

    fun makeTextItem(context: Context, text: String): TransferItem? =
        buildItems(listOf(text), emptyList()) { name, content ->
            writeText(context, name, content)
        }.firstOrNull()

    fun collect(context: Context, intent: Intent?): List<TransferItem> {
        val action = intent?.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return emptyList()
        val texts = mutableListOf<String>()
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { if (it.isNotBlank()) texts.add(it) }
        val streams = mutableListOf<Uri>()
        if (action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { streams.add(it) }
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach { streams.add(it) }
        }
        val declared = intent.type ?: ""
        val files = streams.mapNotNull { uri -> describe(context, uri, declared) }
        return buildItems(texts, files) { name, content -> writeText(context, name, content) }
    }

    private fun describe(context: Context, uri: Uri, declared: String): SharedFile? {
        val resolver = context.contentResolver
        var name = ""
        var size = -1L
        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: ""
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        }
        var mime = declared
        if (mime.isBlank()) mime = runCatching { resolver.getType(uri) }.getOrDefault("") ?: ""
        if (size < 0) {
            // available() возвращает размер буфера, а не файла — размер берём у файлового дескриптора.
            size = runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            }.getOrDefault(0L)
            if (size < 0) size = 0
        }
        return SharedFile(uri.toString(), null, name, size, mime)
    }

    private fun writeText(context: Context, name: String, content: String): TransferItem {
        val file = File(context.cacheDir, "share_text_${System.nanoTime()}.txt")
        file.writeText(content)
        return TransferItem(
            id = "share-text-${file.name}",
            name = name,
            kind = DataKind.FILE,
            sizeBytes = file.length(),
            filePath = file.absolutePath
        )
    }
}
