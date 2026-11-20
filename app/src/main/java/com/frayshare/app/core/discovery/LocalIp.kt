package com.frayshare.app.core.discovery

import java.net.Inet4Address
import java.net.NetworkInterface

object LocalIp {

    fun first(): String? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
        for (network in interfaces.asSequence()) {
            val up = runCatching { network.isUp && !network.isLoopback }.getOrDefault(false)
            if (!up) continue
            for (address in network.inetAddresses.asSequence()) {
                if (address is Inet4Address && address.isSiteLocalAddress) {
                    return address.hostAddress
                }
            }
        }
        return null
    }
}
