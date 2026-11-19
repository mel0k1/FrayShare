package com.frayshare.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.frayshare.app.core.discovery.DiscoveredPeer
import com.frayshare.app.core.discovery.Discovery
import com.frayshare.app.core.model.DataKind
import com.frayshare.app.core.model.JobState
import com.frayshare.app.core.model.Role
import com.frayshare.app.core.model.TransferProgress
import com.frayshare.app.core.source.Catalog
import com.frayshare.app.core.source.Sources
import com.frayshare.app.core.transfer.TransferController
import com.frayshare.app.core.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TransferFlow(
    kinds: List<DataKind>,
    fullMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progress by TransferController.progress.collectAsState()
    val peer by Discovery.peer.collectAsState()
    var role by rememberSaveable { mutableStateOf(Role.NONE) }
    var granted by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf<Catalog?>(null) }
    var selected by rememberSaveable {
        mutableStateOf(if (fullMode) kinds.toSet() else setOf(DataKind.PHOTO, DataKind.VIDEO))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it }
    }

    LaunchedEffect(Unit) {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) granted = true else permissionLauncher.launch(missing.toTypedArray())
    }

    LaunchedEffect(granted) {
        if (granted && catalog == null) {
            catalog = withContext(Dispatchers.IO) { Sources.load(context) }
        }
    }

    DisposableEffect(Unit) {
        onDispose { Discovery.stop() }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        if (fullMode && progress.state == JobState.IDLE && role == Role.NONE) {
            FilledTonalCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Переезд на новый телефон", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Соберёт контакты, фото, видео, приложения и файлы из загрузок. Оба устройства подключите к одной сети Wi-Fi или к точке доступа одного из них. Настройки, переписки и внутренние данные приложений не переносятся — для них делайте резервную копию Google.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        when {
            progress.state != JobState.IDLE -> {
                ProgressCard(
                    progress = progress,
                    isReceiver = role == Role.RECEIVE,
                    onDone = { TransferController.cancel() }
                )
            }
            role == Role.NONE -> {
                RoleCards(
                    onSend = { role = Role.SEND },
                    onReceive = {
                        role = Role.RECEIVE
                        Discovery.startListening(Build.MODEL)
                        TransferController.startReceiving(context)
                    }
                )
            }
            role == Role.SEND -> {
                val cat = catalog
                if (cat == null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    Text("Выберите, что отправить", style = MaterialTheme.typography.headlineSmall)
                    kinds.forEach { kind ->
                        KindRow(
                            kind = kind,
                            catalog = cat,
                            checked = selected.contains(kind),
                            onChange = { checked ->
                                selected = if (checked) selected + kind else selected - kind
                            }
                        )
                    }
                    val items = cat.selected(selected)
                    Text(
                        if (items.isEmpty()) "Ничего не выбрано" else
                            "${items.size} ${Format.plural(items.size, "файл", "файла", "файлов")} · ${Format.bytes(items.sumOf { it.sizeBytes })}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PeerPanel(
                        peer = peer,
                        enabled = items.isNotEmpty(),
                        searchLabel = if (fullMode) "Перенести всё" else "Искать получателя",
                        onSearch = { Discovery.startAnnouncing(Build.MODEL) },
                        onConnect = { found -> TransferController.startSending(context, found, items) }
                    )
                    TextButton(onClick = { Discovery.stop(); role = Role.NONE }) {
                        Text("Назад")
                    }
                }
            }
            else -> {
                Text("Приём передачи", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Телефон виден другим устройствам Fray в этой сети. На старом телефоне выберите «${if (fullMode) "Перенести всё" else "Искать получателя"}» и подключитесь.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        Discovery.startListening(Build.MODEL)
                        TransferController.startReceiving(context)
                    },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Ожидать подключение")
                }
                TextButton(onClick = { role = Role.NONE; TransferController.cancel() }) {
                    Text("Назад")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun requiredPermissions(): List<String> {
    val base = mutableListOf(Manifest.permission.READ_CONTACTS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        base += listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        base += Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        base += Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
    return base
}

private fun kindInfo(kind: DataKind): Pair<ImageVector, String> = when (kind) {
    DataKind.PHOTO -> Icons.Filled.PhotoLibrary to "Фото"
    DataKind.VIDEO -> Icons.Filled.VideoLibrary to "Видео"
    DataKind.APP -> Icons.Filled.Apps to "Приложения"
    DataKind.FILE -> Icons.Filled.Folder to "Файлы"
    DataKind.CONTACT -> Icons.Filled.ContactPhone to "Контакты"
}

private fun statusText(progress: TransferProgress): String = when (progress.state) {
    JobState.AWAITING_PEER -> "Ожидание подключения"
    JobState.CONNECTED -> "Соединено: ${progress.peerName}"
    JobState.RUNNING -> "Передача"
    JobState.COMPLETED -> "Готово"
    JobState.FAILED -> "Ошибка"
    JobState.IDLE -> ""
}

@Composable
private fun RoleCards(onSend: () -> Unit, onReceive: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilledTonalCard(
            onClick = onSend,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Отправить", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Выбрать файлы и найти получателя",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FilledTonalCard(
            onClick = onReceive,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Получить", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ждать подключение старого телефона",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KindRow(
    kind: DataKind,
    catalog: Catalog,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val (icon, title) = kindInfo(kind)
    val items = catalog.of(kind)
    FilledTonalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (items.isEmpty()) "Пусто"
                    else "${items.size} · ${Format.bytes(items.sumOf { it.sizeBytes })}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Checkbox(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun PeerPanel(
    peer: DiscoveredPeer?,
    enabled: Boolean,
    searchLabel: String,
    onSearch: () -> Unit,
    onConnect: (DiscoveredPeer) -> Unit
) {
    if (peer == null) {
        Button(
            onClick = onSearch,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Text(searchLabel)
        }
        Text(
            "Оба устройства должны быть в одной сети Wi-Fi или в точке доступа одного из них",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        FilledTonalButton(
            onClick = { onConnect(peer) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Filled.Wifi, contentDescription = null)
            Text("Передать на ${peer.name}")
        }
        Text(
            "Найдено устройство: ${peer.address}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProgressCard(
    progress: TransferProgress,
    isReceiver: Boolean,
    onDone: () -> Unit
) {
    FilledTonalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(statusText(progress), style = MaterialTheme.typography.headlineSmall)
            if (progress.currentName.isNotBlank()) {
                Text(
                    progress.currentName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { Format.percent(progress.bytesDone, progress.bytesTotal) },
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Round
            )
            Text(
                "${Format.bytes(progress.bytesDone)} из ${Format.bytes(progress.bytesTotal)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (progress.totalItems > 0) {
                Text(
                    "${progress.currentItem} из ${progress.totalItems}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (progress.state == JobState.COMPLETED && isReceiver) {
                Text(
                    "Файлы сохранены в папку Download/Fray",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (progress.state == JobState.FAILED) {
                Text(
                    "Передача прервана. Проверьте сеть и попробуйте снова.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when (progress.state) {
                JobState.COMPLETED, JobState.FAILED -> {
                    FilledTonalButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Новая передача")
                    }
                }
                else -> {
                    OutlinedButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Отменить")
                    }
                }
            }
        }
    }
}
