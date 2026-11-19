package com.frayshare.app.core.transfer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.frayshare.app.core.util.Mime
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class Staging(private val context: Context) {

    fun stored(name: String): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        querySize(partName(name)) ?: querySize(name) ?: 0L
    } else {
        finalFile(name).takeIf { it.exists() }?.length() ?: partFile(name).length()
    }

    fun sink(name: String, truncate: Boolean): OutputStream =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = partUri(name) ?: insertPart(name)
            context.contentResolver.openOutputStream(uri, if (truncate) "w" else "wa")
                ?: error("cannot open $name")
        } else {
            FileOutputStream(partFile(name), !truncate)
        }

    fun finalize(name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = partUri(name) ?: return
            val target = uniqueName(name)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, target)
                put(MediaStore.MediaColumns.MIME_TYPE, Mime.of(target))
            }
            runCatching { context.contentResolver.update(uri, values, null, null) }
        } else {
            val source = partFile(name)
            if (!source.exists()) return
            source.renameTo(File(directory(), uniqueName(name)))
        }
    }

    private fun partName(name: String) = "$name$PART_SUFFIX"

    private fun partUri(name: String): Uri? {
        val id = queryId(partName(name)) ?: return null
        return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
    }

    private fun insertPart(name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, partName(name))
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, DIRECTORY)
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("cannot create $name")
    }

    private fun queryId(displayName: String): Long? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(DIRECTORY, displayName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    private fun querySize(displayName: String): Long? {
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(DIRECTORY, displayName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    private fun uniqueName(name: String): String {
        var candidate = name
        var number = 2
        while (taken(candidate)) {
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

    private fun taken(candidate: String): Boolean =
        if (isMedia()) querySize(candidate) != null else finalFile(candidate).exists()

    private fun isMedia() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private fun directory() = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Fray"
    )

    private fun partFile(name: String) = File(directory(), partName(name))

    private fun finalFile(name: String) = File(directory(), name)

    companion object {
        private const val PART_SUFFIX = ".fraypart"
        private const val DIRECTORY = "Download/Fray/"
    }
}
