package com.frayshare.app.core.transfer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.frayshare.app.core.util.Mime
import com.frayshare.app.core.util.PathSafe
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest

class Staging(private val context: Context) {

    /**
     * Сколько байт уже лежит на диске для этого имени. Докачка доверяет
     * недописанному .fraypart только при совпадении токена отправителя.
     */
    fun stored(name: String, token: String): Long {
        if (token.isBlank()) return 0L
        val saved = readToken(name)
        if (saved != null && saved != token) return 0L
        if (saved == null && partExists(name)) return 0L
        writeToken(name, token)
        return rawStored(name)
    }

    private fun rawStored(name: String): Long {
        val path = relative(name)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            querySize(path, partOf(name)) ?: querySize(path, base(name)) ?: 0L
        } else {
            finalFile(name).takeIf { it.exists() }?.length() ?: partFile(name).length()
        }
    }

    fun partExists(name: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            partUri(relative(name), partOf(name)) != null
        } else {
            partFile(name).exists()
        }

    fun sink(name: String, truncate: Boolean): OutputStream =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val path = relative(name)
            val uri = partUri(path, partOf(name)) ?: insertPart(path, partOf(name))
            context.contentResolver.openOutputStream(uri, if (truncate) "w" else "wa")
                ?: error("cannot open $name")
        } else {
            val target = partFile(name)
            target.parentFile?.mkdirs()
            FileOutputStream(target, !truncate)
        }

    /** Создать пустой файл (элементы нулевой длины в протоколе не шлются). */
    fun createEmpty(name: String) {
        runCatching { sink(name, true).use { } }
            .onFailure { Log.w(TAG, "empty file failed: ${it.message}") }
    }

    fun deletePart(name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = partUri(relative(name), partOf(name)) ?: return
            runCatching { context.contentResolver.delete(uri, null, null) }
        } else {
            partFile(name).delete()
        }
    }

    fun finalize(name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val path = relative(name)
            val uri = partUri(path, partOf(name)) ?: return
            val target = uniqueName(base(name), path)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, target)
                put(MediaStore.MediaColumns.MIME_TYPE, Mime.of(target))
            }
            runCatching { context.contentResolver.update(uri, values, null, null) }
                .onFailure { Log.w(TAG, "finalize failed for $name: ${it.message}") }
        } else {
            val source = partFile(name)
            if (!source.exists()) return
            val path = relative(name)
            source.renameTo(File(directory(), subdirs(name) + uniqueName(base(name), path)))
        }
    }

    private fun relative(name: String): String {
        val safe = PathSafe.clean(name)
        val cut = safe.lastIndexOf('/')
        val subdirs = if (cut >= 0) safe.substring(0, cut + 1) else ""
        return DIRECTORY + subdirs
    }

    // Подкаталоги внутри Download/Fray без префикса — для legacy-пути (Android 8-9),
    // где directory() уже содержит Download/Fray.
    private fun subdirs(name: String): String {
        val safe = PathSafe.clean(name)
        val cut = safe.lastIndexOf('/')
        return if (cut >= 0) safe.substring(0, cut + 1) else ""
    }

    private fun base(name: String): String {
        val safe = PathSafe.clean(name)
        val cut = safe.lastIndexOf('/')
        return if (cut >= 0) safe.substring(cut + 1) else safe
    }

    private fun partOf(name: String) = base(name) + PART_SUFFIX

    private fun tokenFile(name: String) = File(
        File(context.filesDir, TOKEN_DIR),
        digestKey(name) + ".tok"
    )

    private fun digestKey(name: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest((relative(name) + partOf(name)).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun readToken(name: String): String? =
        runCatching { tokenFile(name).takeIf { it.exists() }?.readText()?.trim() }.getOrNull()

    private fun writeToken(name: String, token: String) {
        runCatching {
            val file = tokenFile(name)
            file.parentFile?.mkdirs()
            file.writeText(token)
        }
    }

    private fun partUri(path: String, displayName: String): Uri? {
        val id = queryId(path, displayName) ?: return null
        return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
    }

    private fun insertPart(path: String, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("cannot create $displayName")
    }

    private fun queryId(path: String, displayName: String): Long? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(path, displayName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    private fun querySize(path: String, displayName: String): Long? {
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(path, displayName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    private fun uniqueName(name: String, path: String): String {
        var candidate = name
        var number = 2
        while (taken(path, candidate)) {
            val dot = name.lastIndexOf('.')
            candidate = if (dot > 0) {
                "${name.substring(0, dot)} ($number)${name.substring(dot)}"
            } else {
                "$name ($number)"
            }
            number += 1
        }
        return candidate
    }

    private fun taken(path: String, candidate: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            querySize(path, candidate) != null
        } else {
            File(directory(), path.removePrefix(DIRECTORY) + candidate).exists()
        }

    private fun directory() = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Fray"
    )

    private fun partFile(name: String) = File(directory(), subdirs(name) + partOf(name))

    private fun finalFile(name: String) = File(directory(), subdirs(name) + base(name))

    companion object {
        private const val PART_SUFFIX = ".fraypart"
        private const val DIRECTORY = "Download/Fray/"
        private const val TOKEN_DIR = "fray_meta"
        private const val TAG = "Fray"
    }
}
