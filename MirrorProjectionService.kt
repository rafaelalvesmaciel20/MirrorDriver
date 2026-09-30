package com.mirrordrive

import android.app.*
import android.content.Intent
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import java.nio.ByteBuffer

class MirrorProjectionService:Service(){
    private var projection:MediaProjection?=null; private var codec:MediaCodec?=null; private var sender:H264RtpSender?=null; private var running=true
    private var currentHost=""
    override fun onCreate(){super.onCreate();getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("mirror","MirrorDrive",NotificationManager.IMPORTANCE_LOW))}
    override fun onStartCommand(i:Intent?,f:Int,s:Int):Int{
        startForeground(10,Notification.Builder(this,"mirror").setContentTitle("MirrorDrive").setContentText("Transmissão ativa").setSmallIcon(android.R.drawable.ic_menu_view).build())
        val data=i?.getParcelableExtra<Intent>("data")?:return START_NOT_STICKY; val rc=i.getIntExtra("resultCode",Activity.RESULT_CANCELED); currentHost=i.getStringExtra("host") ?: return START_NOT_STICKY
        projection=getSystemService(MediaProjectionManager::class.java).getMediaProjection(rc,data)
        startEncoding(); return START_STICKY
    }
    private fun startEncoding(){
        val wm=getSystemService(WINDOW_SERVICE) as WindowManager; val m=DisplayMetrics(); @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        val w=(m.widthPixels/2)*2; val h=(m.heightPixels/2)*2; val bitrate=5_000_000; val fps=30
        codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC); val fmt=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,w,h).apply{setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);setInteger(MediaFormat.KEY_BIT_RATE,bitrate);setInteger(MediaFormat.KEY_FRAME_RATE,fps);setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)}
        codec!!.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE); val input=codec!!.createInputSurface(); codec!!.start()
        projection!!.createVirtualDisplay("MirrorDrive",w,h,m.densityDpi,0,input,null,null); sender=H264RtpSender(currentHost, Discovery.VIDEO)
        Thread{drain()}.start()
    }
    private fun drain(){val info=MediaCodec.BufferInfo(); while(running){val c=codec?:break; val idx=c.dequeueOutputBuffer(info,10000); if(idx>=0){val b=c.getOutputBuffer(idx);if(b!=null&&info.size>0){val arr=ByteArray(info.size);b.position(info.offset);b.limit(info.offset+info.size);b.get(arr);sender?.sendAccessUnit(arr)};c.releaseOutputBuffer(idx,false)}}}
    override fun onDestroy(){running=false;try{codec?.stop()}catch(_:Exception){};codec?.release();projection?.stop();sender?.close();super.onDestroy()}
    override fun onBind(i:Intent?):IBinder?=null
}
