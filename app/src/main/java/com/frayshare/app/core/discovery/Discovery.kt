package com.frayshare.app.core.discovery

import com.frayshare.app.core.transfer.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class DiscoveredPeer(
    val name: String,
    val address: String,
    val port: Int,
    val key: ByteArray? = null
)

object Discovery {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _peer = MutableStateFlow<DiscoveredPeer?>(null)
    val peer: StateFlow<DiscoveredPeer?> = _peer.asStateFlow()

    private var announceJob: Job? = null
    private var listenJob: Job? = null
    @Volatile private var announceSocket: DatagramSocket? = null
    @Volatile private var listenSocket: DatagramSocket? = null

    fun startAnnouncing(deviceName: String) {
        stop()
        announceJob = scope.launch {
            val socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 1500
                announceSocket = this
            }
            try {
                val payload = JSONObject()
                    .put("role", "send")
                    .put("name", deviceName)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
                val buffer = ByteArray(4096)
                while (isActive) {
                    try {
                        val target = InetAddress.getByName("255.255.255.255")
                        socket.send(DatagramPacket(payload, payload.size, target, Protocol.DISCOVERY_PORT))
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val message = JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
                        if (message.optString("role") == "receive") {
                            val address = packet.address.hostAddress ?: continue
                            _peer.value = DiscoveredPeer(
                                name = message.optString("name"),
                                address = address,
                                port = message.optInt("port", Protocol.PORT)
                            )
                        }
                    } catch (e: SocketTimeoutException) {
                    } catch (e: Exception) {
                        delay(500)
                    }
                }
            } finally {
                socket.close()
                if (announceSocket === socket) announceSocket = null
            }
        }
    }

    fun startListening(deviceName: String) {
        stop()
        listenJob = scope.launch {
            val socket = try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(Protocol.DISCOVERY_PORT))
                    broadcast = true
                    soTimeout = 2000
                    listenSocket = this
                }
            } catch (e: Exception) {
                android.util.Log.w("Fray", "discovery bind failed: ${e.message}")
                return@launch
            }
            try {
                val buffer = ByteArray(4096)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val message = JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
                        if (message.optString("role") == "send") {
                            val reply = JSONObject()
                                .put("role", "receive")
                                .put("name", deviceName)
                                .put("port", Protocol.PORT)
                                .toString()
                                .toByteArray(Charsets.UTF_8)
                            socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                        }
                    } catch (e: SocketTimeoutException) {
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("Fray", "discovery listen stopped: ${e.message}")
            } finally {
                socket.close()
                if (listenSocket === socket) listenSocket = null
            }
        }
    }

    fun stop() {
        announceJob?.cancel()
        listenJob?.cancel()
        announceJob = null
        listenJob = null
        runCatching { announceSocket?.close() }
        runCatching { listenSocket?.close() }
        announceSocket = null
        listenSocket = null
        _peer.value = null
    }
}
