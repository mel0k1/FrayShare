package com.frayshare.app.core.webshare

import com.frayshare.app.core.model.TransferItem

object WebListing {

    fun json(device: String, groups: List<Pair<String, List<TransferItem>>>): String {
        val out = StringBuilder()
        out.append("{\"device\":").append(string(device)).append(",\"groups\":[")
        var id = 0
        groups.forEachIndexed { groupIndex, (title, items) ->
            if (groupIndex > 0) out.append(',')
            out.append("{\"title\":").append(string(title)).append(",\"items\":[")
            items.forEachIndexed { itemIndex, item ->
                if (itemIndex > 0) out.append(',')
                out.append("{\"id\":").append(id)
                    .append(",\"name\":").append(string(item.name))
                    .append(",\"size\":").append(item.sizeBytes)
                    .append(",\"kind\":").append(string(item.kind.name))
                    .append('}')
                id += 1
            }
            out.append("]}")
        }
        out.append("]}")
        return out.toString()
    }

    fun string(value: String): String {
        val out = StringBuilder("\"")
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else ->
                    if (ch.code < 0x20) {
                        out.append("\\u")
                        out.append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(ch)
                    }
            }
        }
        return out.append('"').toString()
    }
}
