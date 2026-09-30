package com.mirrordrive

import java.net.*

class H264RtpSender(private val host: String, private val port: Int = Discovery.VIDEO) {
    private val socket = DatagramSocket().apply { trafficClass = 0x10 }
    private var seq = 0
    private var timestamp = 0L
    private val ssrc = 0x4D444956
    fun close() = socket.close()
    fun sendAccessUnit(data: ByteArray, ptsUs: Long = 0) {
        val nals = splitAnnexB(data)
        timestamp = if (ptsUs > 0) ((ptsUs * 90) and 0xffffffffL) else ((timestamp + 3000) and 0xffffffffL)
        nals.forEach { if (it.isNotEmpty()) sendNal(it) }
    }
    private fun sendNal(nal: ByteArray) {
        val mtu = 1180
        if (nal.size <= mtu) { send(nal, true); return }
        val type = nal[0].toInt() and 31; val nri = nal[0].toInt() and 0x60
        var off = 1
        while (off < nal.size) {
            val len = minOf(mtu - 2, nal.size - off)
            val p = ByteArray(2 + len); p[0] = (nri or 28).toByte()
            var fu = type or if (off == 1) 0x80 else 0
            if (off + len == nal.size) fu = fu or 0x40
            p[1] = fu.toByte(); System.arraycopy(nal, off, p, 2, len)
            send(p, off + len == nal.size); off += len
        }
    }
    private fun send(payload: ByteArray, marker: Boolean) {
        val s = seq and 0xffff; seq = (seq + 1) and 0xffff
        val h = byteArrayOf(0x80.toByte(), (96 or if (marker) 0x80 else 0).toByte(), (s ushr 8).toByte(), s.toByte(),
            (timestamp ushr 24).toByte(), (timestamp ushr 16).toByte(), (timestamp ushr 8).toByte(), timestamp.toByte(),
            (ssrc ushr 24).toByte(), (ssrc ushr 16).toByte(), (ssrc ushr 8).toByte(), ssrc.toByte())
        val out = ByteArray(h.size + payload.size); System.arraycopy(h,0,out,0,h.size); System.arraycopy(payload,0,out,h.size,payload.size)
        socket.send(DatagramPacket(out, out.size, InetAddress.getByName(host), port))
    }
    private fun splitAnnexB(a: ByteArray): List<ByteArray> {
        val out = mutableListOf<ByteArray>(); var i = 0; var start = -1
        fun sc3(p:Int)=p+3<=a.size && a[p]==0.toByte()&&a[p+1]==0.toByte()&&a[p+2]==1.toByte()
        fun sc4(p:Int)=p+4<=a.size && a[p]==0.toByte()&&a[p+1]==0.toByte()&&a[p+2]==0.toByte()&&a[p+3]==1.toByte()
        while(i<a.size){val n=when{sc4(i)->4;sc3(i)->3;else->0};if(n>0){if(start>=0)out.add(a.copyOfRange(start,i));start=i+n;i+=n}else i++}
        if(start>=0&&start<a.size)out.add(a.copyOfRange(start,a.size)); return out
    }
}
