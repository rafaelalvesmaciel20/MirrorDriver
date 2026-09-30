package com.mirrordrive

import java.net.*

object Discovery {
    const val GROUP = "239.255.77.77"
    const val PORT = 47777
    const val CONTROL = 47778
    const val VIDEO = 5000

    fun advertiseOnce(deviceName: String, ip: String) {
        val msg = "MIRRORDRIVE|1|$deviceName|$ip|$CONTROL|$VIDEO".toByteArray()
        DatagramSocket().use { s ->
            s.timeToLive = 1
            s.send(DatagramPacket(msg, msg.size, InetAddress.getByName(GROUP), PORT))
        }
    }
}
