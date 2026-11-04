package com.alejandro.conversafacil

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

/**
 * MVP de Conversa Fácil LIVE: escucha mediante el micrófono del teléfono,
 * traduce frases reconocidas con ML Kit y muestra un subtítulo flotante.
 * La captura directa del audio interno de TikTok no está incluida todavía.
 */
class LiveTranslationService : Service(), RecognitionListener {
    companion object {
        const val ACTION_STOP = "com.alejandro.conversafacil.live.STOP"
        const val EXTRA_SOURCE = "source_language"
        const val EXTRA_SPEAK = "speak_translation"
        const val EXTRA_CAPTIONS = "show_captions"
        private const val CHANNEL_ID = "conversa_live_channel"
        private const val NOTIFICATION_ID = 2401
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var translator: Translator? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speakEnabled = true
    private var showCaptions = true
    private var sourceLanguage = "zh"
    private var sourceLocale = "zh-CN"
    private var overlay: TextView? = null
    private var windowManager: WindowManager? = null
    private var isListening = false
    private var isTranslating = false
    private var lastRecognized = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.forLanguageTag("es-MX")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        sourceLanguage = intent?.getStringExtra(EXTRA_SOURCE) ?: "zh"
        sourceLocale = if (sourceLanguage == "en") "en-US" else "zh-CN"
        speakEnabled = intent?.getBooleanExtra(EXTRA_SPEAK, true) ?: true
        showCaptions = intent?.getBooleanExtra(EXTRA_CAPTIONS, true) ?: true
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay("Preparando reconocimiento de voz…")
        prepareTranslator()
        startRecognition()
        return START_NOT_STICKY
    }

    private fun prepareTranslator() {
        translator?.close()
        translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(sourceLanguage).setTargetLanguage("es").build()
        )
        translator?.downloadModelIfNeeded(DownloadConditions.Builder().build())
            ?.addOnSuccessListener { updateOverlay("Modelo listo. Escuchando…") }
            ?.addOnFailureListener { updateOverlay("No se pudo descargar el idioma. Revisa Internet.") }
    }

    private fun startRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateOverlay("No hay servicio de reconocimiento de voz disponible.")
            stopSelf()
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
                it.setRecognitionListener(this)
            }
        }
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLocale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try {
            isListening = true
            recognizer?.startListening(request)
        } catch (_: Exception) {
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!isListening) return
        mainHandler.postDelayed({
            if (isListening && !isTranslating) startRecognition()
        }, 500)
    }

    override fun onResults(results: android.os.Bundle?) {
        val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
        if (phrase.isNotBlank() && phrase != lastRecognized) {
            lastRecognized = phrase
            translatePhrase(phrase)
        } else scheduleRestart()
    }

    private fun translatePhrase(phrase: String) {
        val activeTranslator = translator
        if (activeTranslator == null) {
            updateOverlay("Preparando el traductor…")
            scheduleRestart()
            return
        }
        isTranslating = true
        updateOverlay("Original: " + phrase + "\n\nTraduciendo…")
        activeTranslator.translate(phrase)
            .addOnSuccessListener { translated ->
                updateOverlay(translated)
                if (speakEnabled && ttsReady && translated.isNotBlank()) {
                    tts?.speak(translated, TextToSpeech.QUEUE_ADD, null, "live-" + System.currentTimeMillis())
                }
                isTranslating = false
                scheduleRestart()
            }
            .addOnFailureListener {
                updateOverlay("No se pudo traducir esta frase. Continúo escuchando…")
                isTranslating = false
                scheduleRestart()
            }
    }

    override fun onError(error: Int) {
        if (!isListening) return
        val message = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Problema de conexión del reconocimiento."
            SpeechRecognizer.ERROR_AUDIO -> "No se pudo acceder al micrófono."
            else -> "Reconectando al reconocimiento de voz…"
        }
        updateOverlay(message)
        scheduleRestart()
    }

    override fun onReadyForSpeech(params: android.os.Bundle?) {
        updateOverlay("Escuchando " + if (sourceLanguage == "zh") "mandarín…" else "inglés…")
    }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: android.os.Bundle?) {}
    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

    private fun showOverlay(message: String) {
        if (!showCaptions || !SettingsCanDrawOverlays()) return
        if (overlay == null) {
            val view = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding(28, 22, 28, 22)
                setBackgroundColor(Color.argb(235, 43, 34, 62))
                gravity = Gravity.CENTER_VERTICAL
                elevation = 12f
            }
            overlay = view
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM; y = 100 }
            try { windowManager?.addView(view, params) } catch (_: Exception) { overlay = null }
        }
        overlay?.text = "🌎 Conversa Fácil LIVE\n" + message
    }

    private fun SettingsCanDrawOverlays(): Boolean =
        android.provider.Settings.canDrawOverlays(this)

    private fun updateOverlay(message: String) {
        mainHandler.post { showOverlay(message) }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, LiveTranslationService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(
            this, 2402, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Conversa Fácil LIVE está activo")
            .setContentText("Escuchando y traduciendo al español")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Detener", pending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Conversa Fácil LIVE", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isListening = false
        mainHandler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        translator?.close()
        translator = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        overlay?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        overlay = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
