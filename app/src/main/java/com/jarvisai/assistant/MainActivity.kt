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
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var status: TextView
    private lateinit var art: AuraView
    private var listening = false
    private val permissionCode = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(3, 6, 18)
        window.navigationBarColor = Color.rgb(3, 6, 18)
        tts = TextToSpeech(this, this)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(3, 6, 18)) }
        art = AuraView()
        root.addView(art, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 36, 32, 36)
        }
        val top = TextView(this).apply {
            text = "VOICE                                        V0.2"
            setTextColor(Color.rgb(185, 201, 230))
            textSize = 12f
        }
        content.addView(top)
        val space = View(this)
        content.addView(space, LinearLayout.LayoutParams(1, 0, 1f))
        val title = TextView(this).apply {
            text = "JARVIS"
            textSize = 44f
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        content.addView(title)
        val subtitle = TextView(this).apply {
            text = "Speak. Listen. Understand."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(170, 195, 228))
        }
        content.addView(subtitle)
        val waveSpace = View(this)
        content.addView(waveSpace, LinearLayout.LayoutParams(1, dp(195)))
        val mic = TextView(this).apply {
            text = "●"
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(3, 13, 32))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(5), Color.rgb(28, 139, 192))
            }
            setOnClickListener { requestListen() }
            contentDescription = "شروع شنیدن فرمان صوتی"
        }
        content.addView(mic, LinearLayout.LayoutParams(dp(90), dp(90)))
        val hint = TextView(this).apply {
            text = "TAP TO SPEAK"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(160, 189, 224))
            setPadding(0, dp(14), 0, 0)
        }
        content.addView(hint)
        status = TextView(this).apply {
            text = "برای صحبت با جارویس روی دکمه بزن"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(26), 0, 0)
        }
        content.addView(status)
        val bottomSpace = View(this)
        content.addView(bottomSpace, LinearLayout.LayoutParams(1, 0, 1f))
        val footer = TextView(this).apply {
            text = "AI ASSISTANT                            VOICE READY"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(147, 179, 218))
        }
        content.addView(footer)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) tts?.language = Locale.forLanguageTag("fa-IR")
    }

    private fun requestListen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionCode)
        } else startListen()
    }

    private fun startListen() {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "سرویس تشخیص گفتار روی گوشی در دسترس نیست"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
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
                    status.text = "تشخیص صدا ناموفق بود (کد $error). دوباره تلاش کن."
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    art.active = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    respond(spoken)
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
        tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionCode) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListen()
            else status.text = "برای صحبت، مجوز میکروفون لازم است."
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    private inner class AuraView : View(this@MainActivity) {
        var active = false
            set(value) { field = value; invalidate() }
        var level = 0f
            set(value) { field = value; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val left = w * 0.10f
            val top = h * 0.19f
            val right = w * 0.90f
            val bottom = h * 0.83f
            val panel = RectF(left, top, right, bottom)
            paint.shader = LinearGradient(left, top, right, bottom,
                intArrayOf(Color.rgb(32, 68, 132), Color.rgb(8, 17, 42), Color.rgb(19, 47, 98)),
                null, Shader.TileMode.CLAMP)
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(panel, dp(40).toFloat(), dp(40).toFloat(), paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1).toFloat()
            paint.color = Color.rgb(88, 137, 215)
            canvas.drawRoundRect(panel, dp(40).toFloat(), dp(40).toFloat(), paint)
            val centerY = h * 0.49f
            for (i in 0..3) {
                val path = Path()
                for (step in 0..160) {
                    val t = step / 160f
                    val x = left + dp(15) + (right - left - dp(30)) * t
                    val envelope = sin(Math.PI * t).toFloat()
                    val frequency = (3 + i) * Math.PI * 2
                    val amplitude = (24f + i * 9f + if (active) level * 40f else 0f) * resources.displayMetrics.density
                    val y = centerY + sin(t * frequency + i * 0.7).toFloat() * envelope * amplitude
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                paint.color = intArrayOf(0xFF6EA7FF, 0xFFB1C8FF, 0xFF73C5E9, 0xFF8E9FDF)[i].toInt()
                paint.strokeWidth = dp(1).toFloat()
                canvas.drawPath(path, paint)
            }
            paint.style = Paint.Style.FILL
        }
    }
}
