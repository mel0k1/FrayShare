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
    val port: Int
)

object Discovery {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _peer = MutableStateFlow<DiscoveredPeer?>(null)
    val peer: StateFlow<DiscoveredPeer?> = _peer.asStateFlow()

    private var announceJob: Job? = null
    private var listenJob: Job? = null

    fun startAnnouncing(deviceName: String) {
        stop()
        announceJob = scope.launch {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 1500
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
            }
        }
    }

    fun startListening(deviceName: String) {
        stop()
        listenJob = scope.launch {
            val socket = DatagramSocket(Protocol.DISCOVERY_PORT)
            try {
                socket.broadcast = true
                socket.soTimeout = 2000
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
            } finally {
                socket.close()
            }
        }
    }

    fun stop() {
        announceJob?.cancel()
        listenJob?.cancel()
        announceJob = null
        listenJob = null
        _peer.value = null
    }
}
