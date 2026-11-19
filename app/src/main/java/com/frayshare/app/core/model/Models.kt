package com.frayshare.app.core.model

enum class DataKind {
    PHOTO,
    VIDEO,
    APP,
    FILE,
    CONTACT
}

enum class Role {
    NONE,
    SEND,
    RECEIVE
}

enum class JobState {
    IDLE,
    AWAITING_PEER,
    CONNECTED,
    RUNNING,
    COMPLETED,
    FAILED
}

enum class JobDirection {
    SEND,
    RECEIVE
}

data class TransferItem(
    val id: String,
    val name: String,
    val kind: DataKind,
    val sizeBytes: Long,
    val uri: String? = null,
    val filePath: String? = null
)

data class TransferProgress(
    val state: JobState = JobState.IDLE,
    val currentItem: Int = 0,
    val totalItems: Int = 0,
    val currentName: String = "",
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val peerName: String = "",
    val speedBps: Long = 0
)

data class HistoryEntry(
    val id: Long,
    val direction: JobDirection,
    val title: String,
    val itemCount: Int,
    val bytes: Long,
    val finishedAt: Long,
    val success: Boolean
)
