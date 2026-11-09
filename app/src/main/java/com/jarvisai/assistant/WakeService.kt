package com.jarvisai.assistant

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.speech.*
import android.view.*
import android.widget.*
import java.util.Locale

class WakeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var busy = false
    private var failures = 0
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
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        return START_NOT_STICKY
    }

    private fun listen() {
        if (!running || busy || !SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { failures = 0 }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                busy = false
                failures = (failures + 1).coerceAtMost(8)
                schedule(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 2500 else 700L * failures)
            }
            override fun onResults(results: Bundle?) {
                busy = false
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (words.any { it.contains("جارویس", ignoreCase = true) || it.contains("jarvis", ignoreCase = true) }) {
                    showWake()
                    schedule(4500)
                } else schedule(550)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            busy = true
            recognizer?.startListening(request)
        } catch (e: Exception) {
            busy = false
            schedule(3000)
        }
    }

    private fun schedule(delay: Long) {
        handler.removeCallbacks(retry)
        if (running) handler.postDelayed(retry, delay)
    }

    private fun showWake() {
        if (!Settings.canDrawOverlays(this)) {
            val launch = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            try { startActivity(launch) } catch (_: Exception) {}
            return
        }
        if (overlay != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 36, 40, 36)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xF0081935.toInt())
                cornerRadius = 48f
                setStroke(2, 0xFF438EEB.toInt())
            }
        }
        val title = TextView(this).apply {
            text = "JARVIS  ●"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "بیدارم. برای گفتگو لمس کن"
            textSize = 15f
            setTextColor(0xFFB8D7FF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 0)
        }
        panel.addView(title)
        panel.addView(subtitle)
        panel.setOnClickListener {
            dismiss()
            val launch = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            try { startActivity(launch) } catch (_: Exception) {}
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = 100
        }
        try {
            wm.addView(panel, params)
            overlay = panel
            handler.postDelayed({ dismiss() }, 6000)
        } catch (_: Exception) {}
    }

    private fun dismiss() {
        overlay?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        overlay = null
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        dismiss()
        super.onDestroy()
    }
}
