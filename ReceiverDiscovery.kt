package com.mirrordrive

import java.net.*

object ReceiverDiscovery {
    fun find(timeoutMs: Int = 1200): String? {
        val group = InetAddress.getByName(Discovery.GROUP)
        val socket = MulticastSocket(Discovery.PORT)
        socket.soTimeout = timeoutMs
        socket.joinGroup(group)
        return try {
            val b = ByteArray(1024)
            val p = DatagramPacket(b, b.size)
            socket.receive(p)
            val s = String(p.data, 0, p.length)
            if (s.startsWith("MIRRORDRIVE|1|")) p.address.hostAddress else null
        } catch (_: Exception) { null }
        finally { try { socket.leaveGroup(group) } catch (_: Exception) {}; socket.close() }
    }
}
