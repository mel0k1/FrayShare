package com.frayshare.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.frayshare.app.core.webshare.WebServer

@Composable
fun WebScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var server by remember { mutableStateOf<WebServer?>(null) }
    var startError by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    DisposableEffect(Unit) {
        onDispose { server?.stop() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val active = server != null
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            "Веб-доступ",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (active) "Сервер работает" else "Доступ выключен",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "Открой ссылку в браузере на компьютере в той же Wi-Fi сети: скачивай фото, видео и загрузки, отправляй файлы на телефон без установки программ.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (active) {
                    val address = server?.url ?: ""
                    SelectionContainer {
                        Text(
                            address,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { clipboard.setText(AnnotatedString(address)) },
                            enabled = address.isNotBlank()
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Text("Копировать ссылку", modifier = Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = {
                            server?.stop()
                            server = null
                        }) {
                            Text("Выключить")
                        }
                    }
                } else {
                    Button(onClick = {
                        server = WebServer.start(context)
                        startError = server == null
                    }) {
                        Text("Включить доступ")
                    }
                    if (startError) {
                        Text(
                            "Порт занят предыдущим сервером — подождите около минуты и попробуйте снова",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Важно", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ключ доступа вшит в ссылку: кто угодно с этой ссылкой увидит файлы телефона. Не пересылай её посторонним, а после работы выключай доступ.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Соединение без шифрования: как и режим без QR, это защита от чужих глаз в сети, но не от перехвата — на людных сетях лучше не включать.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Сервер живёт, пока открыт этот экран. Если телефон уснёт или закроется приложение, доступ выключится.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Адрес зависит от Wi-Fi сети: на новой сети включи доступ заново и скопируй свежую ссылку.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
