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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class WakeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var engine: SherpaWakeEngine? = null
    private var running = false
    private var paused = false
    private var busy = false
    private var overlay: View? = null
    private var windowManager: WindowManager? = null
    private var recognizer: SpeechRecognizer? = null
    private var voice: TextToSpeech? = null
    private var subtitle: TextView? = null
    private var overlayOrb: WakeOrb? = null
    private var sessionId = 0
    private var sessionActive = false
    private var speaking = false
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
        voice = TextToSpeech(this) { code ->
            if (code == TextToSpeech.SUCCESS) voice?.language = Locale.forLanguageTag("fa-IR")
        }
        handler.postDelayed(retry, 500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                getSharedPreferences("jarvis", MODE_PRIVATE).edit().putBoolean("wake", false).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            "PAUSE" -> {
                paused = true
                handler.removeCallbacks(retry)
                stopEngine()
                finishSession()
                busy = false
            }
            "RESUME" -> {
                paused = false
                busy = false
                if (!sessionActive) schedule(800)
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
                        sessionActive = true
                        showWake()
                        beginConversation()
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
        if (running && !paused && !sessionActive) handler.postDelayed(retry, delay)
    }

    private fun showWake() {
        if (!Settings.canDrawOverlays(this) || overlay != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        val size = dp(278)
        val panel = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xF50B2449.toInt(), 0xFB020815.toInt())
            ).apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
            }
            elevation = dp(18).toFloat()
            clipToOutline = true
            contentDescription = "جارویس، در حال گوش دادن"
        }
        val orb = WakeOrb(this)
        overlayOrb = orb
        panel.addView(orb, FrameLayout.LayoutParams(dp(176), dp(176), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(12)
        })
        val title = TextView(this).apply {
            text = "JARVIS"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            letterSpacing = 0.20f
        }
        panel.addView(title, FrameLayout.LayoutParams(dp(230), dp(36), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(179)
        })
        val line = TextView(this).apply {
            text = "در حال گوش دادن..."
            textSize = 12f
            setTextColor(0xFFB9DEFF.toInt())
            gravity = Gravity.CENTER
            maxLines = 2
        }
        subtitle = line
        panel.addView(line, FrameLayout.LayoutParams(dp(216), dp(45), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(215)
        })
        panel.setOnClickListener {
            if (sessionActive && !speaking) {
                subtitle?.text = "در حال گوش دادن..."
                startCommandRecognition()
            }
        }
        val params = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER; x = 0; y = 0 }
        try {
            wm.addView(panel, params)
            overlay = panel
        } catch (error: Exception) {
            android.util.Log.e("JarvisWake", "Unable to show overlay", error)
        }
    }

    private fun beginConversation() {
        if (!running || !sessionActive) return
        if (overlay == null) {
            // Without overlay permission, use the existing full-screen voice UI.
            val launch = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_WAKE_SESSION, true)
            try { startActivity(launch) } catch (_: Exception) {}
            finishSession()
            return
        }
        handler.postDelayed({ if (sessionActive) startCommandRecognition() }, 350)
    }

    private fun startCommandRecognition() {
        if (!sessionActive || !running || speaking) return
        recognizer?.destroy()
        recognizer = null
        overlayOrb?.level = 0f
        val id = ++sessionId
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            subtitle?.text = "تشخیص گفتار در دسترس نیست"
            handler.postDelayed({ if (id == sessionId) finishSession() }, 2000)
            return
        }
        try {
            val sr = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { subtitle?.text = "گوش می‌دم..." }
                override fun onBeginningOfSpeech() { subtitle?.text = "دارم می‌شنوم..." }
                override fun onRmsChanged(rmsdB: Float) {
                    overlayOrb?.level = (rmsdB / 12f).coerceIn(0f, 1f)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { subtitle?.text = "در حال پردازش..." }
                override fun onError(error: Int) {
                    if (id != sessionId) return
                    recognizer?.destroy()
                    recognizer = null
                    subtitle?.text = "صدایی دریافت نشد"
                    handler.postDelayed({ if (id == sessionId) finishSession() }, 1300)
                }
                override fun onResults(results: Bundle?) {
                    if (id != sessionId) return
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    if (heard.isBlank()) finishSession() else answer(heard)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (id != sessionId) return
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!partial.isNullOrBlank()) subtitle?.text = partial
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            })
            handler.postDelayed({ if (id == sessionId && sessionActive && !speaking) finishSession() }, 12000)
        } catch (error: Exception) {
            android.util.Log.e("JarvisWake", "Command recognition failed", error)
            finishSession()
        }
    }

    private fun answer(heard: String) {
        recognizer?.destroy()
        recognizer = null
        subtitle?.text = heard
        val response = when {
            heard.contains("سلام") -> "سلام! من جارویس هستم."
            heard.contains("ساعت") -> "ساعت الان " +
                java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date()) + " است."
            heard.contains("تنظیمات") -> "برای تنظیمات، برنامه جارویس را باز کن."
            else -> "شنیدم: $heard"
        }
        subtitle?.text = response
        val id = ++sessionId
        val tts = voice
        if (tts == null) {
            handler.postDelayed({ if (id == sessionId) finishSession() }, 1800)
            return
        }
        speaking = true
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                handler.post { if (id == sessionId) finishSession() }
            }
            override fun onError(utteranceId: String?) {
                handler.post { if (id == sessionId) finishSession() }
            }
        })
        val result = tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, "jarvis-overlay-$id")
        if (result == TextToSpeech.ERROR) {
            speaking = false
            handler.postDelayed({ if (id == sessionId) finishSession() }, 1800)
        } else handler.postDelayed({ if (id == sessionId) finishSession() }, 11000)
    }

    private fun finishSession() {
        if (!sessionActive && overlay == null) return
        sessionActive = false
        speaking = false
        ++sessionId
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        voice?.stop()
        dismiss()
        if (running && !paused) schedule(1100)
    }

    private class WakeOrb(context: Context) : View(context) {
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var phase = 0.0
        var level = 0f
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) * (0.26f + level * 0.055f)
            p.style = android.graphics.Paint.Style.FILL
            p.shader = android.graphics.RadialGradient(cx, cy, r * 1.65f,
                intArrayOf(0xAA246EEB.toInt(), 0x553678D9, Color.TRANSPARENT),
                null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r * 1.65f, p)
            p.shader = null
            for (i in 0..8) {
                val path = android.graphics.Path()
                for (step in 0..140) {
                    val t = step * Math.PI * 2.0 / 140.0
                    val rr = r * (0.82 + 0.20 * kotlin.math.sin(t * 3 + phase * 1.4 + i * 0.8) + 0.055 * kotlin.math.sin(t * 7 - phase * 1.9))
                    val x = cx + (rr * kotlin.math.cos(t + phase * 0.15 + i * 0.25)).toFloat()
                    val y = cy + (rr * kotlin.math.sin(t + phase * 0.15 + i * 0.25)).toFloat()
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                p.style = android.graphics.Paint.Style.STROKE
                p.strokeWidth = resources.displayMetrics.density * (if (i == 2) 2.3f else 1.2f)
                p.color = intArrayOf(0xFF5EA9FF.toInt(), 0xFF2C72F4.toInt(), 0xFFA4E9FF.toInt(), 0xFF467EEA.toInt(), 0xFF7ECFFF.toInt(), 0xFF4B99F2.toInt(), 0xFFB5EFFF.toInt(), 0xFF396DDC.toInt(), 0xFF80B9FF.toInt())[i]
                p.setShadowLayer(7f, 0f, 0f, p.color)
                canvas.drawPath(path, p)
                p.clearShadowLayer()
            }
            phase += 0.095 + level * 0.11
            postInvalidateDelayed(33)
        }
    }

    private fun dismiss() {
        overlay?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        overlay = null
        subtitle = null
        overlayOrb = null
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        stopEngine()
        finishSession()
        voice?.shutdown()
        voice = null
        dismiss()
        super.onDestroy()
    }
}
