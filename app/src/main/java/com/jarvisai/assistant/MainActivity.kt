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
        window.statusBarColor = Color.rgb(3, 6, 19)
        window.navigationBarColor = Color.rgb(3, 6, 19)
        tts = TextToSpeech(this, this)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(3, 6, 19)) }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(25), 0, dp(25), 0)
        }
        val voice = TextView(this).apply {
            text = "VOICE  /  AI"
            textSize = 11f
            letterSpacing = 0.16f
            setTextColor(Color.rgb(151, 172, 213))
        }
        val version = TextView(this).apply {
            text = "V 0.3"
            textSize = 11f
            gravity = Gravity.END
            setTextColor(Color.rgb(151, 172, 213))
        }
        header.addView(voice, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(version)
        root.addView(header, FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP))

        val card = FrameLayout(this)
        val cardParams = FrameLayout.LayoutParams(-1, dp(540), Gravity.CENTER).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
        }
        card.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xAA203F78.toInt(), 0xEE08142E.toInt(), 0xFF0B1735.toInt())
        ).apply {
            cornerRadius = dp(35).toFloat()
            setStroke(dp(1), 0xFF5275B6.toInt())
        }
        root.addView(card, cardParams)
        art = AuraView(this)
        card.addView(art, FrameLayout.LayoutParams(-1, -1))

        val titleGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "JARVIS"
            textSize = 43f
            letterSpacing = 0.17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        val subtitle = TextView(this).apply {
            text = "Speak. Listen. Understand."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFB4C9EC.toInt())
        }
        titleGroup.addView(title)
        titleGroup.addView(subtitle)
        card.addView(titleGroup, FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(75)
        })

        val mic = object : View(this) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val density = resources.displayMetrics.density
                p.style = Paint.Style.FILL
                p.color = 0x2237BDFF
                canvas.drawCircle(cx, cy, 45f * density, p)
                p.color = 0xFF50C8FF.toInt()
                canvas.drawCircle(cx, cy, 37f * density, p)
                p.color = Color.WHITE
                canvas.drawCircle(cx, cy, 32f * density, p)
                p.color = 0xFF07152C.toInt()
                canvas.drawRoundRect(cx - 6f*density, cy - 17f*density, cx + 6f*density, cy + 8f*density, 6f*density, 6f*density, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2.8f * density
                p.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(cx - 12f*density, cy - 10f*density, cx + 12f*density, cy + 16f*density, 0f, 180f, false, p)
                canvas.drawLine(cx, cy + 16f*density, cx, cy + 23f*density, p)
                canvas.drawLine(cx - 7f*density, cy + 23f*density, cx + 7f*density, cy + 23f*density, p)
            }
        }.apply {
            contentDescription = "شروع شنیدن فرمان صوتی"
            setOnClickListener { requestListen() }
        }
        card.addView(mic, FrameLayout.LayoutParams(dp(104), dp(104), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
            bottomMargin = dp(100)
        })

        val hint = TextView(this).apply {
            text = "TAP TO SPEAK"
            textSize = 11f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(0xFFA8BFDF.toInt())
        }
        card.addView(hint, FrameLayout.LayoutParams(-1, dp(25), Gravity.BOTTOM).apply {
            bottomMargin = dp(78)
        })
        status = TextView(this).apply {
            text = "برای صحبت با جارویس لمس کن"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            maxLines = 3
            setPadding(dp(20), 0, dp(20), 0)
        }
        card.addView(status, FrameLayout.LayoutParams(-1, dp(66), Gravity.BOTTOM).apply {
            bottomMargin = dp(12)
        })

        val footer = TextView(this).apply {
            text = "AI ASSISTANT                                 VOICE READY"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(0xFF829AC0.toInt())
        }
        root.addView(footer, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM))
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

    private class AuraView(context: android.content.Context) : View(context) {
        var active = false
            set(value) { field = value; invalidate() }
        var level = 0f
            set(value) { field = value; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var phase = 0f
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val d = resources.displayMetrics.density
            val cy = h * 0.49f
            val left = w * 0.08f
            val span = w * 0.84f
            for (i in 0..3) {
                val path = Path()
                for (step in 0..160) {
                    val t = step / 160f
                    val x = left + span * t
                    val envelope = sin(Math.PI * t).toFloat()
                    val wave = sin(t * Math.PI * (4.0 + i * 0.6) + i * 0.8 + phase).toFloat()
                    val amplitude = (25f + i * 5f + if (active) level * 32f else 0f) * d
                    val y = cy + wave * envelope * amplitude
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (if (i == 1) 1.8f else 1.2f) * d
                paint.color = intArrayOf(0xFF4C8BEB.toInt(), 0xFFC2D9FF.toInt(), 0xFF70D9FF.toInt(), 0xFF879DDA.toInt())[i]
                paint.alpha = if (active) 245 else 170
                canvas.drawPath(path, paint)
            }
            if (active) {
                phase += 0.12f
                postInvalidateDelayed(33)
            }
        }
    }
}
