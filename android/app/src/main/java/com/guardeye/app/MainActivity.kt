package com.guardeye.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView; private lateinit var btn: Button; private lateinit var log: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null)
            ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java).putExtra("code", r.resultCode).putExtra("data", r.data).putExtra("sec", 5))
        else Toast.makeText(this, "Screen capture was not allowed", Toast.LENGTH_LONG).show()
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val on get() = getSharedPreferences("guardeye", Context.MODE_PRIVATE).getBoolean("on", false)

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        val p = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, p * 2, p, p) }
        status = TextView(this).apply { textSize = 22f; setTextColor(Color.parseColor("#14213D")) }
        btn = Button(this).apply { textSize = 22f; minimumHeight = p * 4; setOnClickListener { toggle() } }
        val clear = Button(this).apply { text = "Clear history"; setOnClickListener { HistoryStore.clear(this@MainActivity); refresh() } }
        log = TextView(this).apply { textSize = 16f }
        root.addView(status); root.addView(btn); root.addView(clear); root.addView(ScrollView(this).apply { addView(log) })
        setContentView(root)
    }
    private fun toggle() {
        if (on) { startService(Intent(this, MonitorService::class.java).setAction("STOP")); return }
        AlertDialog.Builder(this).setTitle("Screen access needed")
            .setMessage("GuardEye will read the text on your screen while protection is on, on this phone only. No screenshots or screen text are saved, only warnings. Android will ask you to confirm, and a notification stays visible while it runs.")
            .setPositiveButton("Continue") { _, _ -> capture.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()) }
            .setNegativeButton("Cancel", null).show()
    }
    private fun refresh() {
        status.text = if (on) "Protection is active (screen capture on)" else "Protection is paused"
        btn.text = if (on) "Stop Protection" else "Start Protection"
        val h = HistoryStore.all(this)
        log.text = if (h.isEmpty()) "No warnings yet." else h.mapIndexed { i, o -> "${i + 1}. ${o.getString("cat")} (${o.getString("risk")} risk, score ${o.getInt("score")}/100 - estimate, not accuracy)\n   ${o.getString("why")}\n   ${DateFormat.getDateTimeInstance().format(Date(o.getLong("ts")))}" }.joinToString("\n\n")
    }
    private val poll = object : Runnable { override fun run() { refresh(); ui.postDelayed(this, 2000) } }
    override fun onResume() { super.onResume(); poll.run() }
    override fun onPause() { super.onPause(); ui.removeCallbacks(poll) }
}
