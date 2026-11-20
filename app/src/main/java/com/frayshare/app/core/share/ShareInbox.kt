package com.frayshare.app.core.share

import com.frayshare.app.core.model.TransferItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ShareInbox {

    private val _shared = MutableStateFlow<List<TransferItem>>(emptyList())
    val shared: StateFlow<List<TransferItem>> = _shared.asStateFlow()

    fun push(items: List<TransferItem>) {
        if (items.isNotEmpty()) _shared.value = items
    }

    fun add(item: TransferItem) {
        _shared.value = _shared.value + item
    }

    fun remove(item: TransferItem) {
        _shared.value = _shared.value - item
    }

    fun clear() {
        _shared.value = emptyList()
    }
}
