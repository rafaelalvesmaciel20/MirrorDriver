package com.mirrordrive

import android.app.Activity
import android.media.*
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

/** Receiver with RTP/H264 FU-A reassembly and a small sequence-based jitter buffer. */
class ReceiverActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var surface: SurfaceView
    private var decoder: MediaCodec? = null
    private var socket: DatagramSocket? = null
    @Volatile private var running = true
    private val nalUnits = ConcurrentHashMap<Int, ByteArray>()
    private val packetQueue = ConcurrentSkipListMap<Int, Pair<Boolean, ByteArray>>()
    private var current = java.io.ByteArrayOutputStream()
    private var expectedSeq = -1

    override fun onCreate(b: Bundle?) { super.onCreate(b); surface = SurfaceView(this); setContentView(surface); surface.holder.addCallback(this); Thread { advertiseLoop() }.start() }
    private fun advertiseLoop() {
        val ip = try { java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }.firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }?.hostAddress ?: "0.0.0.0" } catch (_: Exception) { "0.0.0.0" }
        while (running) { try { Discovery.advertiseOnce("MirrorDrive Receiver", ip); Thread.sleep(1200) } catch (_: Exception) {} }
    }

    override fun surfaceCreated(h: SurfaceHolder) { startDecoder(h.surface) }
    private fun startDecoder(s: android.view.Surface) {
        decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080)
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2_000_000)
            configure(fmt, s, null, 0); start()
        }
        Thread { receiveLoop() }.start()
        Thread { jitterLoop() }.start()
    }
    private fun receiveLoop() {
        socket = DatagramSocket(Discovery.VIDEO)
        val buf = ByteArray(65535)
        while (running) {
            val p = DatagramPacket(buf, buf.size)
            try { socket!!.receive(p) } catch (_: Exception) { break }
            if (p.length < 12) continue
            val seq = ((buf[2].toInt() and 255) shl 8) or (buf[3].toInt() and 255)
            val marker = (buf[1].toInt() and 0x80) != 0
            val payload = buf.copyOfRange(12, p.length)
            packetQueue[seq] = marker to payload
        }
    }

    private fun jitterLoop() {
        while (running) {
            try { Thread.sleep(20) } catch (_: InterruptedException) {}
            val snapshot = packetQueue.entries.toList().sortedBy { it.key }
            if (snapshot.size < 2) continue
            val take = snapshot.dropLast(1)
            for (e in take) { packetQueue.remove(e.key); handleRtp(e.key, e.value.first, e.value.second) }
        }
    }
    private fun handleRtp(seq: Int, marker: Boolean, p: ByteArray) {
        if (p.isEmpty()) return
        val type = p[0].toInt() and 31
        if (type == 28 && p.size >= 2) {
            val fu = p[1].toInt() and 0xff
            val start = (fu and 0x80) != 0
            val end = (fu and 0x40) != 0
            val nalType = fu and 31
            if (start) { current.reset(); current.write((p[0].toInt() and 0x60) or nalType); current.write(p, 2, p.size - 2) }
            else { current.write(p, 2, p.size - 2) }
            if (end) { queueNal(current.toByteArray()); current.reset() }
        } else queueNal(p)
    }
    private fun queueNal(nal: ByteArray) {
        val b = decoder ?: return
        val idx = b.dequeueInputBuffer(10_000)
        if (idx < 0) return
        val inb = b.getInputBuffer(idx) ?: return
        inb.clear(); inb.put(byteArrayOf(0,0,0,1)); inb.put(nal)
        b.queueInputBuffer(idx, 0, nal.size, System.nanoTime()/1000, 0)
        val info = MediaCodec.BufferInfo()
        var out = b.dequeueOutputBuffer(info, 0)
        while (out >= 0) { b.releaseOutputBuffer(out, true); out = b.dequeueOutputBuffer(info, 0) }
    }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, he: Int) {}
    override fun surfaceDestroyed(h: SurfaceHolder) { running = false; socket?.close(); try { decoder?.stop() } catch (_: Exception) {}; decoder?.release(); decoder = null }
}
