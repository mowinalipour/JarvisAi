package com.jarvisai.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private var tts: TextToSpeech? = null
    private val speechRequest = 101
    private val micPermissionRequest = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 36, 36, 36)
            setBackgroundColor(0xFF101827.toInt())
        }
        val title = TextView(this).apply {
            text = "JARVIS AI"
            textSize = 32f
            setTextColor(0xFF69D9FF.toInt())
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "سلام! برای صحبت با جارویس روی میکروفون بزن."
            textSize = 19f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 48)
        }
        val listen = Button(this).apply {
            text = "🎙 صحبت با جارویس"
            setOnClickListener { requestSpeech() }
        }
        root.addView(title)
        root.addView(status)
        root.addView(listen)
        setContentView(root)
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            tts?.language = Locale.forLanguageTag("fa-IR")
        }
    }

    private fun requestSpeech() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionRequest)
        } else {
            startSpeech()
        }
    }

    private fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "دستور خود را بگویید")
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, speechRequest)
        } catch (e: Exception) {
            status.text = "سرویس تشخیص گفتار روی گوشی در دسترس نیست."
        }
    }

    @Deprecated("Uses legacy speech result callback for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == speechRequest && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            val answer = when {
                spoken.contains("سلام") -> "سلام! من جارویس هستم."
                spoken.contains("ساعت") -> "برای دیدن ساعت، نوار بالای گوشی را نگاه کن."
                spoken.contains("تنظیمات") -> {
                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    "تنظیمات گوشی باز شد."
                }
                spoken.isBlank() -> "صدایت را نشنیدم."
                else -> "شنیدم: $spoken. قابلیت هوش مصنوعی آنلاین در نسخه بعد اضافه می‌شود."
            }
            status.text = answer
            tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == micPermissionRequest) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startSpeech()
            else status.text = "برای شنیدن فرمان‌ها باید مجوز میکروفون را فعال کنی."
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
