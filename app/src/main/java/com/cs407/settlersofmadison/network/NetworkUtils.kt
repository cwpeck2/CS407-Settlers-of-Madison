package com.cs407.settlersofmadison.network

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Returns the first non-loopback IPv4 address it finds, or null if none.
     * On an emulator this will typically be something like 10.0.2.15.
     */
    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}