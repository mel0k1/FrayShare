package com.frayshare.app.core.source

import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import com.frayshare.app.core.model.DataKind
import com.frayshare.app.core.model.TransferItem
import java.io.File

data class Catalog(
    val photos: List<TransferItem> = emptyList(),
    val videos: List<TransferItem> = emptyList(),
    val apps: List<TransferItem> = emptyList(),
    val files: List<TransferItem> = emptyList(),
    val contacts: List<TransferItem> = emptyList()
) {
    fun of(kind: DataKind): List<TransferItem> = when (kind) {
        DataKind.PHOTO -> photos
        DataKind.VIDEO -> videos
        DataKind.APP -> apps
        DataKind.FILE -> files
        DataKind.CONTACT -> contacts
    }

    fun selected(kinds: Set<DataKind>): List<TransferItem> = kinds.flatMap { of(it) }
}

object Sources {

    fun load(context: Context): Catalog = Catalog(
        photos = media(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, DataKind.PHOTO),
        videos = media(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, DataKind.VIDEO),
        apps = apps(context),
        files = downloads(context),
        contacts = contacts(context)
    )

    private fun media(context: Context, collection: Uri, kind: DataKind): List<TransferItem> {
        val out = mutableListOf<TransferItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE
        )
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                val itemUri = ContentUris.withAppendedId(collection, id)
                out.add(
                    TransferItem(
                        id = "$kind-$id",
                        name = name,
                        kind = kind,
                        sizeBytes = size,
                        uri = itemUri.toString()
                    )
                )
            }
        }
        return out
    }

    private fun apps(context: Context): List<TransferItem> {
        val pm = context.packageManager
        val out = mutableListOf<TransferItem>()
        for (info in pm.getInstalledPackages(0)) {
            val appInfo: ApplicationInfo = info.applicationInfo ?: continue
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            val path = appInfo.sourceDir ?: continue
            val file = File(path)
            if (!file.exists()) continue
            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(info.packageName)
            out.add(
                TransferItem(
                    id = "app-${info.packageName}",
                    name = "$label.apk",
                    kind = DataKind.APP,
                    sizeBytes = file.length(),
                    filePath = path
                )
            )
        }
        return out.sortedBy { it.name.lowercase() }
    }

    fun contacts(context: Context): List<TransferItem> {
        val resolver = context.contentResolver
        val builder = StringBuilder()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME
        )
        resolver.query(ContactsContract.Contacts.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val numbers = mutableListOf<String>()
                resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId.toString()),
                    null
                )?.use { phones ->
                    while (phones.moveToNext()) {
                        phones.getString(0)?.let { numbers.add(it) }
                    }
                }
                builder.append("BEGIN:VCARD\r\n")
                builder.append("VERSION:3.0\r\n")
                builder.append("FN:").append(name).append("\r\n")
                builder.append("N:").append(name).append("\r\n")
                for (number in numbers) {
                    builder.append("TEL;TYPE=CELL:").append(number).append("\r\n")
                }
                builder.append("END:VCARD\r\n")
            }
        }
        if (builder.isEmpty()) return emptyList()
        val file = File(context.cacheDir, "fray_contacts.vcf")
        file.writeText(builder.toString())
        return listOf(
            TransferItem(
                id = "contacts",
                name = file.name,
                kind = DataKind.CONTACT,
                sizeBytes = file.length(),
                filePath = file.absolutePath
            )
        )
    }

    private fun downloads(context: Context): List<TransferItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val out = mutableListOf<TransferItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE
        )
        context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                val itemUri = ContentUris.withAppendedId(collection, id)
                out.add(
                    TransferItem(
                        id = "file-$id",
                        name = name,
                        kind = DataKind.FILE,
                        sizeBytes = size,
                        uri = itemUri.toString()
                    )
                )
            }
        }
        return out
    }
}
