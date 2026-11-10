package com.jarvisai.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.sin

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    companion object { const val EXTRA_WAKE_SESSION = "jarvis_wake_session" }
    private var conversationActive = false
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var status: TextView
    private lateinit var art: AuraView
    private var listening = false
    private val permissionCode = 102
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var foreground = false
    private var speaking = false
    private var failures = 0
    private var wakeToggle: TextView? = null
    private fun wakeEnabled(): Boolean = getSharedPreferences("jarvis", MODE_PRIVATE).getBoolean("wake", false)
    private fun setWake(enabled: Boolean) {
        getSharedPreferences("jarvis", MODE_PRIVATE).edit().putBoolean("wake", enabled).apply()
        if (enabled) {
            conversationActive = false
            recognizer?.cancel()
            listening = false
            handler.removeCallbacks(retry)
            val intent = Intent(this, WakeService::class.java)
            try {
                startForegroundService(intent)
                status.text = "در انتظار «جارویس»... برای صحبت می‌توانی روی صفحه لمس کنی"
            } catch (e: Exception) {
                getSharedPreferences("jarvis", MODE_PRIVATE).edit().putBoolean("wake", false).apply()
                status.text = "فعال‌سازی سرویس ممکن نشد"
            }
        } else {
            stopService(Intent(this, WakeService::class.java))
            scheduleListen(400)
        }
        wakeToggle?.text = if (wakeEnabled()) "● بیدارباش فعال — لمس برای توقف" else "○ فعال‌کردن بیدارباش جارویس"
    }
    private fun requestWake() {
        if (wakeEnabled()) { setWake(false); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionCode)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")), 103)
            return
        }
        setWake(true)
    }
    private fun showMenu() {
        val options = arrayOf("تنظیمات", "درباره جارویس")
        android.app.AlertDialog.Builder(this)
            .setTitle("JARVIS")
            .setItems(options) { _, which ->
                if (which == 0) showSettings() else android.app.AlertDialog.Builder(this)
                    .setMessage("Jarvis AI — دستیار صوتی اندروید").setPositiveButton("بستن", null).show()
            }.show()
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(16), dp(22), dp(12))
        }
        val description = TextView(this).apply {
            text = "بیدارباش آفلاین sherpa-onnx\\nبا گفتن «جارویس» پنجره شناور باز می‌شود. بدون کلید API و اینترنت. میکروفون در حالت آماده‌باش فعال می‌ماند و نشانگر حریم خصوصی اندروید طبیعی است. مدل فعلی انگلیسی و آزمایشی است."
            textSize = 15f
            setTextColor(0xFF243650.toInt())
            setPadding(0, 0, 0, dp(18))
        }
        panel.addView(description)
        val toggle = android.widget.Switch(this).apply {
            text = "فعال‌سازی بیدارباش آفلاین"
            textSize = 16f
            isChecked = wakeEnabled()
        }
        panel.addView(toggle)
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("تنظیمات جارویس")
            .setView(panel)
            .setPositiveButton("بستن", null)
            .create()
        toggle.setOnCheckedChangeListener { _, enabled ->
            if (enabled != wakeEnabled()) requestWake()
            toggle.isChecked = wakeEnabled()
        }
        dialog.show()
    }

    private val retry = Runnable { if (foreground && !speaking) startListen() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        tts = TextToSpeech(this, this)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val ambient = View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.BLACK, 0xFF020713.toInt(), 0xFF071B35.toInt())
            )
        }
        root.addView(ambient, FrameLayout.LayoutParams(-1, -1))
        art = AuraView(this)
        art.contentDescription = "برای صحبت با جارویس لمس کن"
        art.setOnClickListener {
            if (wakeEnabled()) {
                conversationActive = true
                startService(Intent(this, WakeService::class.java).setAction("PAUSE"))
                scheduleListen(450)
            } else requestListen()
        }
        root.addView(art, FrameLayout.LayoutParams(-1, -1))
        val greeting = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val hello = TextView(this).apply {
            text = "Hello"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF94A6C5.toInt())
        }
        val title = TextView(this).apply {
            text = "How can I help\nyou today?"
            textSize = 32f
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        }
        greeting.addView(hello)
        greeting.addView(title)
        root.addView(greeting, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
            topMargin = dp(210)
        })
        status = TextView(this).apply {
            text = ""
            textSize = 13f
            gravity = Gravity.CENTER
            maxLines = 2
            setTextColor(0xFFBDD8F9.toInt())
        }
        root.addView(status, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM).apply {
            leftMargin = dp(25)
            rightMargin = dp(25)
            bottomMargin = dp(45)
        })
        val menuButton = TextView(this).apply {
            text = "☰"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "باز کردن منو"
            setOnClickListener { showMenu() }
        }
        root.addView(menuButton, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(12)
            topMargin = dp(12)
        })
        setContentView(root)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionCode)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts?.language = Locale.forLanguageTag("fa-IR")
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { speaking = true }
                override fun onDone(utteranceId: String?) { runOnUiThread { speaking = false; if (wakeEnabled()) endSession() else scheduleListen(450) } }
                override fun onError(utteranceId: String?) { runOnUiThread { speaking = false; if (wakeEnabled()) endSession() else scheduleListen(450) } }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        val woken = intent?.getBooleanExtra(EXTRA_WAKE_SESSION, false) == true
        intent?.removeExtra(EXTRA_WAKE_SESSION)
        if (wakeEnabled()) {
            try { startForegroundService(Intent(this, WakeService::class.java)) } catch (_: Exception) {}
            if (woken) {
                conversationActive = true
                startService(Intent(this, WakeService::class.java).setAction("PAUSE"))
                scheduleListen(700)
            } else {
                status.text = "در انتظار «جارویس»... برای صحبت صفحه را لمس کن"
            }
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            scheduleListen(1000)
        }
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        if (newIntent.getBooleanExtra(EXTRA_WAKE_SESSION, false) && wakeEnabled()) {
            newIntent.removeExtra(EXTRA_WAKE_SESSION)
            conversationActive = true
            startService(Intent(this, WakeService::class.java).setAction("PAUSE"))
            scheduleListen(600)
        }
    }

    override fun onPause() {
        foreground = false
        conversationActive = false
        handler.removeCallbacks(retry)
        recognizer?.cancel()
        listening = false
        art.active = false
        if (wakeEnabled()) {
            try { startService(Intent(this, WakeService::class.java).setAction("RESUME")) } catch (_: Exception) {}
        }
        super.onPause()
    }

    private fun endSession() {
        conversationActive = false
        handler.removeCallbacks(retry)
        recognizer?.cancel()
        listening = false
        art.active = false
        status.text = "در انتظار «جارویس»..."
        if (wakeEnabled() && foreground) {
            try { startService(Intent(this, WakeService::class.java).setAction("RESUME")) } catch (_: Exception) {}
        }
    }

    private fun scheduleListen(delay: Long) {
        handler.removeCallbacks(retry)
        if (foreground && !speaking && (!wakeEnabled() || conversationActive)) handler.postDelayed(retry, delay)
    }

    private fun requestListen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionCode)
        } else startListen()
    }

    private fun startListen() {
        if (listening || !foreground || speaking || (wakeEnabled() && !conversationActive)) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "سرویس تشخیص گفتار روی گوشی در دسترس نیست"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    failures = 0
                    art.active = true
                    status.text = "در حال گوش دادن..."
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { art.level = rmsdB.coerceIn(0f, 12f) / 12f }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "در حال پردازش..." }
                override fun onError(error: Int) {
                    listening = false
                    art.active = false
                    status.text = "در انتظار فرمان صوتی..."
                    failures = (failures + 1).coerceAtMost(6)
                    if (wakeEnabled() && failures >= 2) endSession()
                    else scheduleListen(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2500 else 650L * failures)
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    art.active = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (spoken.isBlank()) {
                        if (wakeEnabled()) endSession() else scheduleListen(500)
                    } else respond(spoken)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) status.text = partial
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            try {
                startListening(intent)
                listening = true
            } catch (e: Exception) {
                listening = false
                status.text = "امکان شروع میکروفون وجود ندارد"
                scheduleListen(3000)
            }
        }
    }

    private fun respond(spoken: String) {
        val answer = when {
            spoken.isBlank() -> "صدایت را نشنیدم."
            spoken.contains("سلام") -> "سلام! من جارویس هستم."
            spoken.contains("تنظیمات") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                "تنظیمات گوشی باز شد."
            }
            spoken.contains("ساعت") -> {
                val time = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
                "ساعت الان $time است."
            }
            else -> "شنیدم: $spoken. قابلیت هوش مصنوعی آنلاین هنوز فعال نشده است."
        }
        status.text = answer
        if (foreground && tts != null) {
            speaking = true
            val result = tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response")
            if (result == TextToSpeech.ERROR) { speaking = false; if (wakeEnabled()) endSession() else scheduleListen(600) }
        } else if (wakeEnabled()) endSession() else scheduleListen(600)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) scheduleListen(500)
            else status.text = "برای صحبت، مجوز میکروفون لازم است."
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 103 && Settings.canDrawOverlays(this)) setWake(true)
    }

    override fun onDestroy() {
        handler.removeCallbacks(retry)
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    private class AuraView(context: android.content.Context) : View(context) {
        var active = false
            set(value) { field = value }
        var level = 0f
            set(value) { field = value }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private var phase = 0f
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val d = resources.displayMetrics.density
            val cx = w * 0.5f
            val cy = h * 0.32f
            val radius = minOf(w * 0.25f, 112f * d)
            val pulse = 1f + 0.035f * sin(phase * 0.7f).toFloat()
            p.style = Paint.Style.FILL
            p.shader = android.graphics.RadialGradient(cx, cy, radius * 1.7f,
                intArrayOf(0x88255FC4.toInt(), 0x44236BCE.toInt(), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius * 1.7f, p)
            p.shader = null
            for (i in 0..6) {
                val path = Path()
                val shift = phase * (if (i % 2 == 0) 0.9f else -0.75f)
                for (step in 0..220) {
                    val t = step / 220.0
                    val theta = t * Math.PI * 2.0
                    val twist = sin(theta * 2.0 + shift + i * 0.5) * 0.24
                    val rr = radius * pulse * (0.72 + 0.16 * sin(theta * 3 + shift + i))
                    val x = cx + (rr * kotlin.math.cos(theta + twist)).toFloat()
                    val y = cy + (rr * 0.94 * kotlin.math.sin(theta + twist)).toFloat()
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                p.style = Paint.Style.STROKE
                p.strokeWidth = (if (i == 2) 2.6f else 1.4f) * d
                p.strokeCap = Paint.Cap.ROUND
                p.color = intArrayOf(0xFF2461F7.toInt(), 0xFF4B9FFF.toInt(),
                    0xFFB0E9FF.toInt(), 0xFF2C59C9.toInt(),
                    0xFF69CFFF.toInt(), 0xFF3059B9.toInt(), 0xFF88CFFF.toInt())[i]
                p.alpha = if (active) 255 else 185
                p.setShadowLayer(7f * d, 0f, 0f, p.color)
                canvas.drawPath(path, p)
                p.clearShadowLayer()
            }
            phase += if (active) 0.055f + level * 0.055f else 0.018f
            postInvalidateDelayed(33)
        }
    }
}
