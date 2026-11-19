package com.frayshare.app.core.direct

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DirectPeer(val name: String, val deviceAddress: String)

sealed interface DirectState {
    data object Idle : DirectState
    data object Scanning : DirectState
    data class Peers(val devices: List<DirectPeer>) : DirectState
    data class Connecting(val name: String) : DirectState
    data class Ready(val name: String, val address: String) : DirectState
    data class Failed(val message: String) : DirectState
}

object WifiDirect {

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var context: Context? = null
    private var registered = false
    private var pending: DirectPeer? = null

    private val _state = MutableStateFlow<DirectState>(DirectState.Idle)
    val state: StateFlow<DirectState> = _state.asStateFlow()

    private val handler = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) ==
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    if (!enabled) _state.value = DirectState.Failed("Включите Wi-Fi и повторите")
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    val ch = channel ?: return
                    manager?.requestPeers(ch) { list -> onPeers(list) }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val info = IntentCompat.getParcelableExtra(
                        intent,
                        WifiP2pManager.EXTRA_WIFI_P2P_INFO,
                        WifiP2pInfo::class.java
                    )
                    if (info?.groupFormed == true) {
                        onConnected(info)
                    } else if (_state.value is DirectState.Ready || _state.value is DirectState.Connecting) {
                        _state.value = DirectState.Idle
                    }
                }
            }
        }
    }

    fun start(appContext: Context) {
        if (manager != null) return
        val app = appContext.applicationContext
        val mgr = app.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (mgr == null) {
            _state.value = DirectState.Failed("Устройство не поддерживает Wi-Fi Direct")
            return
        }
        val ch = mgr.initialize(app, app.mainLooper, null)
        manager = mgr
        channel = ch
        context = app
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(app, handler, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
        mgr.discoverPeers(
            ch,
            listener(
                onSuccess = { _state.value = DirectState.Scanning },
                onFailure = { _state.value = DirectState.Failed(failText(it)) }
            )
        )
    }

    fun connect(peer: DirectPeer) {
        val mgr = manager ?: return
        val ch = channel ?: return
        pending = peer
        _state.value = DirectState.Connecting(peer.name)
        val config = WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = 0
        }
        mgr.connect(
            ch,
            config,
            listener(
                onSuccess = {},
                onFailure = { _state.value = DirectState.Failed(failText(it)) }
            )
        )
    }

    fun stopScanning() {
        val mgr = manager
        val ch = channel
        if (mgr != null && ch != null) {
            runCatching { mgr.stopPeerDiscovery(ch, null) }
        }
        pending = null
        _state.value = DirectState.Idle
    }

    fun shutdown() {
        val mgr = manager
        val ch = channel
        if (registered) {
            context?.let { ctx -> runCatching { ctx.unregisterReceiver(handler) } }
        }
        if (mgr != null && ch != null) {
            runCatching { mgr.stopPeerDiscovery(ch, null) }
            runCatching { mgr.removeGroup(ch, null) }
        }
        manager = null
        channel = null
        context = null
        registered = false
        pending = null
        _state.value = DirectState.Idle
    }

    private fun onPeers(list: WifiP2pDeviceList) {
        val current = _state.value
        if (current is DirectState.Connecting || current is DirectState.Ready) return
        val devices = list.deviceList
            .filter { it.status == WifiP2pDevice.AVAILABLE }
            .map {
                DirectPeer(
                    name = it.deviceName.ifBlank { "Устройство ${it.deviceAddress.takeLast(5)}" },
                    deviceAddress = it.deviceAddress
                )
            }
        if (devices.size == 1) {
            connect(devices[0])
        } else if (devices.size > 1) {
            _state.value = DirectState.Peers(devices)
        }
    }

    private fun onConnected(info: WifiP2pInfo) {
        val owner = info.groupOwnerAddress
        val peer = pending
        if (owner == null || peer == null) return
        if (info.isGroupOwner) {
            _state.value = DirectState.Failed("Этот телефон стал владельцем группы; повторите поиск")
            return
        }
        _state.value = DirectState.Ready(peer.name, owner.hostAddress ?: "")
    }

    private fun listener(
        onSuccess: () -> Unit,
        onFailure: (Int) -> Unit
    ): WifiP2pManager.ActionListener = object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            onSuccess()
        }

        override fun onFailure(reason: Int) {
            onFailure(reason)
        }
    }

    private fun failText(reason: Int): String = when (reason) {
        WifiP2pManager.BUSY -> "Служба Wi-Fi занята, попробуйте ещё раз"
        WifiP2pManager.P2P_UNSUPPORTED -> "Устройство не поддерживает Wi-Fi Direct"
        WifiP2pManager.NO_SERVICE_REQUESTS -> "Wi-Fi Direct недоступен на этом устройстве"
        else -> "Ошибка Wi-Fi Direct ($reason)"
    }
}
