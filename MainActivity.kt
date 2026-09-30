package com.mirrordrive

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*
import java.util.concurrent.Executors

class MainActivity: Activity() {
    private lateinit var status: TextView
    private lateinit var host: EditText
    private val CAPTURE=7001
    override fun onCreate(b: Bundle?) { super.onCreate(b); setContentView(R.layout.activity_main)
        status=findViewById(R.id.status); host=findViewById(R.id.host)
        findViewById<Button>(R.id.discover).setOnClickListener {
            status.text="Procurando Receiver..."
            Executors.newSingleThreadExecutor().execute {
                val ip=ReceiverDiscovery.find()
                runOnUiThread { if(ip!=null){host.setText(ip);status.text="Receiver encontrado: $ip"}else status.text="Receiver não encontrado; informe o IP." }
            }
        }
        findViewById<Button>(R.id.send).setOnClickListener {
            val pm=getSystemService(MediaProjectionManager::class.java)
            startActivityForResult(pm.createScreenCaptureIntent(), CAPTURE)
        }
        findViewById<Button>(R.id.receive).setOnClickListener { startActivity(Intent(this, ReceiverActivity::class.java)) }
    }
    override fun onActivityResult(r:Int,c:Int,d:Intent?) { super.onActivityResult(r,c,d); if(r==CAPTURE && c==RESULT_OK && d!=null) {
        val target=host.text.toString().trim()
        if(target.isEmpty()){status.text="Informe o IP do Receiver";return}
        status.text="Transmitindo para $target"
        startForegroundService(Intent(this, MirrorProjectionService::class.java).apply { putExtra("resultCode",c); putExtra("data",d); putExtra("host",target) })
    }}
}
