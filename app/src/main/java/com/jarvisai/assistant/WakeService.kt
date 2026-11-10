package com.jarvisai.assistant

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*

class WakeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var engine: SherpaWakeEngine? = null
    private var running = false
    private var paused = false
    private var busy = false
    private var overlay: View? = null
    private var windowManager: WindowManager? = null
    private val retry = Runnable { listen() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("jarvis_wake", "Jarvis wake listening", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, WakeService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "jarvis_wake")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis")
            .setContentText("در انتظار شنیدن جارویس")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "توقف شنیدن", stop).build())
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(77, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(77, notification)
        running = true
        handler.postDelayed(retry, 500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> { stopSelf(); return START_NOT_STICKY }
            "PAUSE" -> {
                paused = true
                handler.removeCallbacks(retry)
                stopEngine()
                busy = false
            }
            "RESUME" -> {
                paused = false
                busy = false
                schedule(800)
            }
        }
        return START_NOT_STICKY
    }

    private fun stopEngine() {
        engine?.stop()
        engine = null
        busy = false
    }

    private fun listen() {
        if (!running || paused || busy) return
        busy = true
        val detector = SherpaWakeEngine(
            context = applicationContext,
            onDetected = {
                handler.post {
                    if (running && !paused) {
                        stopEngine() // release AudioRecord before launching command recognizer
                        showWake()
                        schedule(5500)
                    }
                }
            },
            onFailure = { error ->
                android.util.Log.e("JarvisWake", "Offline keyword engine failure", error)
                handler.post {
                    if (running && !paused) {
                        stopEngine()
                        schedule(10000)
                    }
                }
            }
        )
        engine = detector
        detector.start()
    }

    private fun schedule(delay: Long) {
        handler.removeCallbacks(retry)
        if (running && !paused) handler.postDelayed(retry, delay)
    }

    private fun showWake() {
        if (!Settings.canDrawOverlays(this)) {
            val launch = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_WAKE_SESSION, true)
            try { startActivity(launch) } catch (_: Exception) {}
            return
        }
        if (overlay != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        val panel = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xF5091A3D.toInt(), 0xFA030918.toInt())
            ).apply {
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), 0xFF468DEB.toInt())
            }
            elevation = dp(20).toFloat()
        }
        val orb = WakeOrb(this)
        panel.addView(orb, FrameLayout.LayoutParams(dp(100), dp(100), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(12)
        })
        val title = TextView(this).apply {
            text = "J A R V I S"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        panel.addView(title, FrameLayout.LayoutParams(-1, dp(38), Gravity.TOP).apply { topMargin = dp(110) })
        val subtitle = TextView(this).apply {
            text = "بیدارم، برای گفتگو لمس کن"
            textSize = 14f
            setTextColor(0xFFB8D7FF.toInt())
            gravity = Gravity.CENTER
        }
        panel.addView(subtitle, FrameLayout.LayoutParams(-1, dp(42), Gravity.BOTTOM).apply { bottomMargin = dp(10) })
        panel.setOnClickListener {
            dismiss()
            val launch = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            try { startActivity(launch) } catch (_: Exception) {}
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, dp(212),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = dp(42)
        }
        try {
            params.width = resources.displayMetrics.widthPixels - dp(44)
            wm.addView(panel, params)
            overlay = panel
            handler.postDelayed({ dismiss() }, 6000)
        } catch (_: Exception) {}
    }

    private class WakeOrb(context: Context) : View(context) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var phase = 0.0
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) * 0.31f
            p.style = android.graphics.Paint.Style.FILL
            p.shader = android.graphics.RadialGradient(cx, cy, r * 1.65f,
                intArrayOf(0xAA246EEB.toInt(), 0x553678D9, Color.TRANSPARENT),
                null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r * 1.65f, p)
            p.shader = null
            for (i in 0..5) {
                val path = android.graphics.Path()
                for (step in 0..140) {
                    val t = step * Math.PI * 2.0 / 140.0
                    val rr = r * (0.87 + 0.14 * kotlin.math.sin(t * 3 + phase + i * 0.8))
                    val x = cx + (rr * kotlin.math.cos(t + phase * 0.15 + i * 0.25)).toFloat()
                    val y = cy + (rr * kotlin.math.sin(t + phase * 0.15 + i * 0.25)).toFloat()
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                p.style = android.graphics.Paint.Style.STROKE
                p.strokeWidth = resources.displayMetrics.density * (if (i == 2) 2.3f else 1.2f)
                p.color = intArrayOf(0xFF5EA9FF.toInt(), 0xFF2C72F4.toInt(), 0xFFA4E9FF.toInt(), 0xFF467EEA.toInt(), 0xFF7ECFFF.toInt(), 0xFF4B99F2.toInt())[i]
                p.setShadowLayer(7f, 0f, 0f, p.color)
                canvas.drawPath(path, p)
                p.clearShadowLayer()
            }
            phase += 0.045
            postInvalidateDelayed(33)
        }
    }

    private fun dismiss() {
        overlay?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        overlay = null
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        stopEngine()
        dismiss()
        super.onDestroy()
    }
}
