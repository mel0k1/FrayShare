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
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
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
import com.frayshare.app.core.direct.DirectState
import com.frayshare.app.core.direct.WifiDirect
import com.frayshare.app.core.discovery.DiscoveredPeer
import com.frayshare.app.core.discovery.Discovery
import com.frayshare.app.core.model.DataKind
import com.frayshare.app.core.model.JobState
import com.frayshare.app.core.model.Role
import com.frayshare.app.core.model.TransferProgress
import com.frayshare.app.core.qr.Pairing
import com.frayshare.app.core.source.Catalog
import com.frayshare.app.core.source.Sources
import com.frayshare.app.core.transfer.Protocol
import com.frayshare.app.core.transfer.TransferController
import com.frayshare.app.core.util.Format
import com.frayshare.app.ui.QrImage
import com.frayshare.app.ui.QrScanner
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
    val invite by TransferController.invite.collectAsState()
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Переезд на новый телефон", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Соберёт контакты, фото, видео, приложения и файлы из загрузок. Оба устройства соедините через одну сеть Wi-Fi, точку доступа одного из них или Wi-Fi Direct. Настройки, переписки и внутренние данные приложений не переносятся — для них делайте резервную копию Google.",
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
                    invite = if (progress.state == JobState.AWAITING_PEER) invite else null,
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
                    var scanning by remember { mutableStateOf(false) }
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
                    val direct by WifiDirect.state.collectAsState()
                    LaunchedEffect(direct) {
                        val ready = direct as? DirectState.Ready ?: return@LaunchedEffect
                        WifiDirect.stopScanning()
                        TransferController.startSending(
                            context,
                            DiscoveredPeer(name = ready.name, address = ready.address, port = Protocol.PORT),
                            items
                        )
                    }
                    PeerPanel(
                        peer = peer,
                        enabled = items.isNotEmpty(),
                        searchLabel = if (fullMode) "Перенести всё" else "Искать получателя",
                        onSearch = { Discovery.startAnnouncing(Build.MODEL) },
                        onScan = { scanning = true },
                        onConnect = { found -> TransferController.startSending(context, found, items) }
                    )
                    if (direct is DirectState.Peers && (direct as DirectState.Peers).devices.size > 1) {
                        val devices = (direct as DirectState.Peers).devices
                        AlertDialog(
                            onDismissRequest = { WifiDirect.shutdown() },
                            title = { Text("Устройства Wi-Fi Direct") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    devices.forEach { device ->
                                        TextButton(onClick = { WifiDirect.connect(device) }) {
                                            Text(device.name)
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(onClick = { WifiDirect.shutdown() }) { Text("Отмена") }
                            }
                        )
                    }
                    when (val snapshot = direct) {
                        is DirectState.Scanning -> DirectStatus("Ищу устройства Wi-Fi Direct...")
                        is DirectState.Connecting -> DirectStatus(
                            "Подключаюсь к ${snapshot.name}. Подтвердите запрос Android на другом телефоне"
                        )
                        is DirectState.Failed -> DirectStatus(snapshot.message, error = true)
                        else -> {}
                    }
                    if (scanning) {
                        QrScanner(
                            onPayload = { text ->
                                scanning = false
                                val found = Pairing.parse(text)
                                if (found != null) {
                                    TransferController.startSending(
                                        context,
                                        DiscoveredPeer(
                                            name = "",
                                            address = found.address,
                                            port = found.port,
                                            key = found.key
                                        ),
                                        items
                                    )
                                }
                            },
                            onDismiss = { scanning = false }
                        )
                    }
                    TextButton(onClick = { Discovery.stop(); role = Role.NONE }) {
                        Text("Назад")
                    }
                }
            }
            else -> {
                Text("Приём передачи", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "На этом телефоне появится QR. На старом нажмите «Сканировать QR» и наведите камеру. Работает и поиск по сети — там выберите «${if (fullMode) "Перенести всё" else "Искать получателя"}». Для Wi-Fi Direct роутер не нужен: нажмите здесь «Ожидать подключение», на старом телефоне — «Wi-Fi Direct» и подтвердите запрос Android",
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
private fun DirectStatus(message: String, error: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!error) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Round
            )
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun RoleCards(onSend: () -> Unit, onReceive: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
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
        Card(
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
    Card(
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
    onScan: () -> Unit,
    onConnect: (DiscoveredPeer) -> Unit
) {
    val context = LocalContext.current
    val directPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }
    val directLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { given -> if (given) WifiDirect.start(context) }
    if (peer == null) {
        Button(
            onClick = onSearch,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Text(searchLabel)
        }
        OutlinedButton(
            onClick = onScan,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Filled.QrCode2, contentDescription = null)
            Text("Сканировать QR")
        }
        OutlinedButton(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, directPermission) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    WifiDirect.start(context)
                } else {
                    directLauncher.launch(directPermission)
                }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(Icons.Filled.Wifi, contentDescription = null)
            Text("Wi-Fi Direct")
        }
        Text(
            "QR и поиск работают в одной сети Wi-Fi. Wi-Fi Direct соединяет телефоны напрямую, без общего роутера",
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
    invite: String?,
    onDone: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(statusText(progress), style = MaterialTheme.typography.headlineSmall)
            if (progress.state == JobState.AWAITING_PEER && invite != null) {
                QrImage(
                    invite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .padding(horizontal = 24.dp)
                )
                Text(
                    "На старом телефоне: «Сканировать QR». Приём пойдёт по шифрованному каналу.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (progress.currentName.isNotBlank()) {
                Text(
                    progress.currentName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearWavyProgressIndicator(
                progress = { Format.percent(progress.bytesDone, progress.bytesTotal) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${Format.bytes(progress.bytesDone)} из ${Format.bytes(progress.bytesTotal)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (progress.speedBps > 0) {
                Text(
                    "${Format.bytes(progress.speedBps)}/с",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
