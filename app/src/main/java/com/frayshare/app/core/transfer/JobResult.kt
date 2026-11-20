package com.frayshare.app.core.transfer

data class JobResult(
    val success: Boolean,
    val items: Int,
    val bytes: Long,
    val device: String
)
