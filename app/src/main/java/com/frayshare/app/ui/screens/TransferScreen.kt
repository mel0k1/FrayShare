package com.frayshare.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.frayshare.app.core.model.DataKind

@Composable
fun TransferScreen(modifier: Modifier = Modifier) {
    TransferFlow(
        kinds = listOf(DataKind.PHOTO, DataKind.VIDEO, DataKind.APP, DataKind.FILE),
        fullMode = false,
        modifier = modifier
    )
}
