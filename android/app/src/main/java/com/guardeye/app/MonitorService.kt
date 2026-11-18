package com.guardeye.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Foreground service: user-consented MediaProjection capture -> duplicate skip -> on-device OCR -> rules -> warning + history. */
class MonitorService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var prev: IntArray? = null; private var busy = false; private var lastKey = ""; private var lastKeyT = 0L
    private var intervalMs = 5000L

    private val tick = object : Runnable { override fun run() { scan(); handler.postDelayed(this, intervalMs) } }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mon", "Protection running", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("warn", "Warnings", NotificationManager.IMPORTANCE_HIGH))
        val stop = PendingIntent.getService(this, 0, Intent(this, MonitorService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "mon").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("GuardEye is capturing your screen")
            .setContentText("Tap Stop at any time").setOngoing(true).addAction(0, "Stop", stop).build()
        if (i?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val code = i?.getIntExtra("code", 0) ?: 0; val data = i?.getParcelableExtra<Intent>("data") ?: run { stopSelf(); return START_NOT_STICKY }
        intervalMs = (i.getIntExtra("sec", 5) * 1000L)
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data).also { p ->
            p.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, handler) // required on Android 14+
            val m = resources.displayMetrics; val w = m.widthPixels / 2; val h = m.heightPixels / 2
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            display = p.createVirtualDisplay("guardeye", w, h, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, null)
        }
        getSharedPreferences("guardeye", MODE_PRIVATE).edit().putBoolean("on", true).apply()
        handler.postDelayed(tick, 1500); return START_NOT_STICKY
    }

    private fun scan() {
        if (busy) return
        val img = reader?.acquireLatestImage() ?: return
        val bmp: Bitmap
        try {
            val pl = img.planes[0]; val rowPad = pl.rowStride - pl.pixelStride * img.width
            val full = Bitmap.createBitmap(img.width + rowPad / pl.pixelStride, img.height, Bitmap.Config.ARGB_8888)
            full.copyPixelsFromBuffer(pl.buffer); bmp = Bitmap.createBitmap(full, 0, 0, img.width, img.height); full.recycle()
        } finally { img.close() }
        val small = Bitmap.createScaledBitmap(bmp, 32, 32, true); val px = IntArray(1024); small.getPixels(px, 0, 32, 0, 0, 32, 32); small.recycle()
        val p = prev; prev = px
        if (p != null && px.indices.sumOf { Math.abs((px[it] and 0xFF) - (p[it] and 0xFF)) } / 1024.0 < 1.0) { bmp.recycle(); return } // unchanged screen
        busy = true
        recognizer.process(InputImage.fromBitmap(bmp, 0)).addOnSuccessListener { t ->
            val r = Analyzer.analyze(t.text)  // text is used in memory only, never stored
            if (r.score >= 25) {
                val key = r.reasons.joinToString("|")
                if (key != lastKey || System.currentTimeMillis() - lastKeyT > 60000) {
                    lastKey = key; lastKeyT = System.currentTimeMillis()
                    HistoryStore.add(this, r, "Screen capture + on-device OCR (ML Kit) + rule-based wording check")
                    warn(r)
                }
            }
        }.addOnCompleteListener { bmp.recycle(); busy = false }
    }

    private fun warn(r: Result) {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, "warn").setSmallIcon(android.R.drawable.ic_dialog_alert).setContentIntent(open).setAutoCancel(true)
            .setContentTitle("CAUTION: " + r.category).setStyle(NotificationCompat.BigTextStyle().bigText("Why: " + r.reasons.joinToString("; ") + "\nVerify the source before sending money or sharing. This is an estimate, not proof."))
            .setContentText(r.reasons.firstOrNull() ?: "").build()
        getSystemService(NotificationManager::class.java).notify(System.currentTimeMillis().toInt(), n)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick); display?.release(); reader?.close(); projection?.stop(); recognizer.close()
        getSharedPreferences("guardeye", MODE_PRIVATE).edit().putBoolean("on", false).apply(); super.onDestroy()
    }
}
