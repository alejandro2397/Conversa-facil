package com.alejandro.conversafacil

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
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
    private var overlay: LinearLayout? = null
    private var overlayBody: TextView? = null
    private var overlayStatus: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayExpanded = true
    private var windowManager: WindowManager? = null
    private var isListening = false
    private var isTranslating = false
    private var lastRecognized = ""
    private var pendingPhrase: String? = null
    private var pendingPhraseSpeak = true
    private var pendingPhraseRestart = true
    private var partialTranslateRunnable: Runnable? = null
    private var translationRequestId = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                val selected = tts?.setLanguage(Locale.forLanguageTag("es-MX"))
                if (selected == TextToSpeech.LANG_MISSING_DATA || selected == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.forLanguageTag("es-ES")
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        mainHandler.post { scheduleRestart() }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { scheduleRestart() }
                    }
                })
            }
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
            ?.addOnSuccessListener {
                updateOverlay("Modelo listo. Escuchando…")
                pendingPhrase?.let { phrase ->
                    val shouldSpeak = pendingPhraseSpeak
                    val shouldRestart = pendingPhraseRestart
                    pendingPhrase = null
                    translatePhrase(phrase, shouldSpeak, shouldRestart)
                }
            }
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
        partialTranslateRunnable?.let { mainHandler.removeCallbacks(it) }
        partialTranslateRunnable = null
        val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
        if (phrase.isNotBlank() && phrase != lastRecognized) {
            lastRecognized = phrase
            translatePhrase(phrase, speakResult = true, restartAfter = true)
        } else scheduleRestart()
    }

    override fun onPartialResults(partialResults: android.os.Bundle?) {
        val phrase = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
        if (phrase.isBlank() || phrase == lastRecognized) return
        updateOverlay("Escuchado: " + phrase + "\n\nPreparando subtítulo en español…")
        partialTranslateRunnable?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable {
            if (isListening && phrase != lastRecognized) {
                translatePhrase(phrase, speakResult = false, restartAfter = false)
            }
        }
        partialTranslateRunnable = task
        mainHandler.postDelayed(task, 850)
    }

    private fun translatePhrase(phrase: String, speakResult: Boolean = true, restartAfter: Boolean = true) {
        val activeTranslator = translator
        if (activeTranslator == null) {
            pendingPhrase = phrase
            pendingPhraseSpeak = speakResult
            pendingPhraseRestart = restartAfter
            updateOverlay("Preparando el traductor…")
            return
        }
        val requestId = ++translationRequestId
        isTranslating = true
        updateOverlay("Original: " + phrase + "\n\nTraduciendo al español…")
        activeTranslator.translate(phrase)
            .addOnSuccessListener { translated ->
                if (requestId != translationRequestId) return@addOnSuccessListener
                updateOverlay(translated)
                isTranslating = false
                if (speakResult && speakEnabled && ttsReady && translated.isNotBlank()) {
                    val utteranceId = "live-" + System.currentTimeMillis()
                    val result = tts?.speak(translated, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                    if (result == TextToSpeech.ERROR && restartAfter) scheduleRestart()
                } else if (restartAfter) {
                    scheduleRestart()
                }
            }
            .addOnFailureListener {
                if (requestId != translationRequestId) return@addOnFailureListener
                updateOverlay("No se pudo traducir esta frase. Comprueba Internet y el idioma.")
                isTranslating = false
                if (restartAfter) scheduleRestart()
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
    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius.toInt()).toFloat()
        }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun showOverlay(message: String) {
        if (!showCaptions || !SettingsCanDrawOverlays()) return
        if (overlay == null) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBackground(Color.rgb(38, 29, 57), 18f)
                elevation = dp(12).toFloat()
                clipToOutline = true
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(8), dp(10))
                background = roundedBackground(Color.rgb(101, 75, 166), 18f)
            }
            val title = makeText("🌎  CONVERSA FÁCIL LIVE", 13f, Color.WHITE, true)
            header.addView(title, LinearLayout.LayoutParams(0, dp(34), 1f))

            val minimize = makeText("—", 22f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setOnClickListener {
                    overlayExpanded = !overlayExpanded
                    overlayBody?.visibility = if (overlayExpanded) View.VISIBLE else View.GONE
                    text = if (overlayExpanded) "—" else "+"
                    overlayParams?.let { currentParams -> this@LiveTranslationService.overlay?.let { view -> windowManager?.updateViewLayout(view, currentParams) } }
                }
            }
            header.addView(minimize, LinearLayout.LayoutParams(dp(38), dp(34)))

            val close = makeText("×", 24f, Color.WHITE, false).apply {
                gravity = Gravity.CENTER
                setOnClickListener { stopSelf() }
            }
            header.addView(close, LinearLayout.LayoutParams(dp(34), dp(34)))

            var startX = 0f
            var startY = 0f
            var initialX = 0
            var initialY = 0
            var moved = false
            header.setOnTouchListener { _, event ->
                val params = overlayParams ?: return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        initialX = params.x
                        initialY = params.y
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - startX).toInt()
                        val dy = (event.rawY - startY).toInt()
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(4)) moved = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try { windowManager?.updateViewLayout(card, params) } catch (_: Exception) {}
                        true
                    }
                    MotionEvent.ACTION_UP -> true
                    else -> false
                }
            }

            val status = makeText("●  ESCUCHANDO", 11f, Color.rgb(190, 170, 255), true).apply {
                setPadding(dp(15), dp(9), dp(15), 0)
            }
            val body = makeText("Esperando la voz…", 15f, Color.WHITE).apply {
                setPadding(dp(15), dp(5), dp(15), dp(14))
                maxLines = 5
                setLineSpacing(dp(2).toFloat(), 1f)
            }
            card.addView(header)
            card.addView(status)
            card.addView(body)
            overlayStatus = status
            overlayBody = body
            overlay = card

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                dp(320),
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(12)
                y = dp(260)
            }
            overlayParams = params
            try { windowManager?.addView(card, params) } catch (_: Exception) {
                overlay = null
                overlayBody = null
                overlayStatus = null
                overlayParams = null
            }
        }
        overlayBody?.text = message
        overlayStatus?.text = if (message.startsWith("Escuchando")) "●  EN VIVO · ESCUCHANDO" else "●  CONVERSA FÁCIL LIVE"
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
        partialTranslateRunnable = null
        translationRequestId++
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        translator?.close()
        translator = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        overlay?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        overlay = null
        overlayBody = null
        overlayStatus = null
        overlayParams = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}