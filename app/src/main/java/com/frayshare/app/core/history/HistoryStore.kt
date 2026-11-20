package com.frayshare.app.core.history

import android.content.Context
import com.frayshare.app.core.model.HistoryEntry
import com.frayshare.app.core.model.JobDirection
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object HistoryStore {

    private const val MAX_ENTRIES = 50

    private fun file(context: Context) = File(context.filesDir, "history.json")

    fun load(context: Context): List<HistoryEntry> {
        val target = file(context)
        if (!target.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(target.readText())
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                HistoryEntry(
                    id = obj.getLong("id"),
                    direction = JobDirection.valueOf(obj.getString("direction")),
                    title = obj.optString("title"),
                    itemCount = obj.getInt("items"),
                    bytes = obj.getLong("bytes"),
                    finishedAt = obj.getLong("finishedAt"),
                    success = obj.getBoolean("success")
                )
            }
        }.getOrElse {
            // Битый файл нельзя чинить вечно: сбросить, иначе история перестанет сохраняться.
            target.delete()
            emptyList()
        }
    }

    fun append(context: Context, entry: HistoryEntry) {
        runCatching {
            val target = file(context)
            val array = if (target.exists()) JSONArray(target.readText()) else JSONArray()
            val obj = JSONObject()
                .put("id", entry.id)
                .put("direction", entry.direction.name)
                .put("title", entry.title)
                .put("items", entry.itemCount)
                .put("bytes", entry.bytes)
                .put("finishedAt", entry.finishedAt)
                .put("success", entry.success)
            array.put(obj)
            while (array.length() > MAX_ENTRIES) {
                array.remove(0)
            }
            val tmp = File(context.filesDir, "history.json.tmp")
            tmp.writeText(array.toString())
            if (!tmp.renameTo(target)) {
                tmp.delete()
            }
        }
    }
}
