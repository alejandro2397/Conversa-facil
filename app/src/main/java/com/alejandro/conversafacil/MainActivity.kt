package com.alejandro.conversafacil

import android.Manifest
import android.content.Intent
import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import android.os.Bundle
import kotlinx.coroutines.delay
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

data class AppLanguage(val name: String, val flag: String, val code: String, val speechLocale: String)
data class TranslationEntry(val source: String, val translated: String, val sourceCode: String, val targetCode: String)

private val languages = listOf(
    AppLanguage("Español", "🇪🇸", "es", "es-ES"),
    AppLanguage("Inglés", "🇺🇸", "en", "en-US"),
    AppLanguage("Chino", "🇨🇳", "zh", "zh-CN"),
    AppLanguage("Hindi", "🇮🇳", "hi", "hi-IN"),
    AppLanguage("Portugués", "🇧🇷", "pt", "pt-BR"),
    AppLanguage("Francés", "🇫🇷", "fr", "fr-FR"),
    AppLanguage("Árabe", "🇸🇦", "ar", "ar-SA"),
    AppLanguage("Ruso", "🇷🇺", "ru", "ru-RU"),
    AppLanguage("Japonés", "🇯🇵", "ja", "ja-JP"),
    AppLanguage("Alemán", "🇩🇪", "de", "de-DE"),
    AppLanguage("Coreano", "🇰🇷", "ko", "ko-KR"),
    AppLanguage("Italiano", "🇮🇹", "it", "it-IT"),
    AppLanguage("Turco", "🇹🇷", "tr", "tr-TR"),
    AppLanguage("Vietnamita", "🇻🇳", "vi", "vi-VN"),
    AppLanguage("Indonesio", "🇮🇩", "id", "id-ID")
)

class MainActivity : ComponentActivity() {
    private val speechResult = mutableStateOf("")
    private val translationResult = mutableStateOf("")
    private val translationLoading = mutableStateOf(false)
    private val translationError = mutableStateOf("")
    private var tts: TextToSpeech? = null
    private var lastSource = "es"
    private var lastTarget = "zh"
    private val translatorCache = LinkedHashMap<String, com.google.mlkit.nl.translate.Translator>(4, 0.75f, true)
    private val preparingPairs = mutableSetOf<String>()
    private val preparingCallbacks = mutableMapOf<String, MutableList<() -> Unit>>()
    private var cachedTranslator: com.google.mlkit.nl.translate.Translator? = null
    private var cachedPair: String? = null
    private var cachedReady = false
    private var translationRequestId = 0L
    private val history = mutableStateListOf<TranslationEntry>()
    private var autoSpeakAfterTranslation = false
    private var ttsReady = false
    private var pendingSpeech: Pair<String, AppLanguage>? = null
    private var conversationMode = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var conversationSource: AppLanguage? = null
    private var conversationTarget: AppLanguage? = null

    private val conversationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val source = conversationSource
            val target = conversationTarget
            if (source != null && target != null) {
                conversationMode = true
                prepareTranslator(source.code, target.code) {
                    if (conversationMode) listenConversationTurn()
                }
            }
        }
    }

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text ->
            speechResult.value = text
            autoSpeakAfterTranslation = true
            translate(text, lastSource, lastTarget)
        }
    }

    private fun ensureSpeechRecognizer() {
        if (speechRecognizer != null || !SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) {
                    if (conversationMode) window.decorView.postDelayed({ listenConversationTurn() }, 700)
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isBlank() || !conversationMode) {
                        if (conversationMode) listenConversationTurn()
                        return
                    }
                    val source = conversationSource ?: return
                    val target = conversationTarget ?: return
                    speechResult.value = text
                    autoSpeakAfterTranslation = true
                    translate(text, source.code, target.code)
                }
            })
        }
    }

    private fun listenConversationTurn() {
        if (!conversationMode) return
        val source = conversationSource ?: return
        ensureSpeechRecognizer()
        val recognizer = speechRecognizer ?: return
        recognizer.cancel()
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, source.speechLocale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    private fun startConversationMode(source: AppLanguage, target: AppLanguage, onState: (Boolean) -> Unit) {
        conversationSource = source
        conversationTarget = target
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onState(false)
            conversationPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        conversationMode = true
        onState(true)
        prepareTranslator(source.code, target.code) {
            if (conversationMode) listenConversationTurn()
        }
    }

    private fun stopConversationMode(onState: (Boolean) -> Unit) {
        conversationMode = false
        speechRecognizer?.cancel()
        tts?.stop()
        onState(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                pendingSpeech?.let { (text, language) ->
                    pendingSpeech = null
                    speak(text, language)
                }
            }
        }
        Thread { MobileAds.initialize(this) {} }.start()
        // Los modelos se descargan únicamente cuando el usuario selecciona
        // un idioma que todavía no está disponible en el dispositivo.
        // Así el primer arranque sigue siendo ligero y la espera ocurre
        // exactamente dentro del panel de traducción.
        setContent {
            ConversaFacilApp(
                spokenText = speechResult.value,
                translatedText = translationResult.value,
                isTranslating = translationLoading.value,
                translationError = translationError.value,
                startListening = ::startListening,
                speak = ::speak,
                translateText = ::translate,
                prepareLanguages = ::prepareTranslator,
                history = history,
                clearHistory = { history.clear() },
                startConversationMode = ::startConversationMode,
                stopConversationMode = ::stopConversationMode
            )
        }
    }

    private fun startListening(language: AppLanguage, target: AppLanguage) {
        lastSource = language.code
        lastTarget = target.code
        speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechLocale)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla en ${language.name}")
        })
    }

    private fun prepareTranslator(source: String, target: String, onReady: (() -> Unit)? = null) {
        if (source == target) {
            cachedTranslator = null
            cachedPair = source + "-" + target
            cachedReady = true
            onReady?.invoke()
            return
        }
        val pair = source + "-" + target
        translatorCache[pair]?.let { translator ->
            cachedTranslator = translator
            cachedPair = pair
            cachedReady = true
            onReady?.invoke()
            return
        }
        if (preparingPairs.contains(pair)) {
            onReady?.let { preparingCallbacks.getOrPut(pair) { mutableListOf() }.add(it) }
            return
        }
        preparingPairs.add(pair)
        preparingCallbacks[pair] = mutableListOf<() -> Unit>().apply { onReady?.let { add(it) } }
        cachedTranslator = null
        cachedPair = pair
        cachedReady = false
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        )
        translatorCache[pair] = translator
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                preparingPairs.remove(pair)
                if (translatorCache[pair] === translator) {
                    cachedTranslator = translator
                    cachedPair = pair
                    cachedReady = true
                    while (translatorCache.size > 4) {
                        val eldest = translatorCache.entries.iterator().next()
                        if (eldest.key != pair) {
                            eldest.value.close()
                            translatorCache.remove(eldest.key)
                        } else break
                    }
                    preparingCallbacks.remove(pair)?.forEach { it.invoke() }
                }
            }
            .addOnFailureListener {
                preparingPairs.remove(pair)
                if (translatorCache[pair] === translator) {
                    translatorCache.remove(pair)
                    translator.close()
                    cachedTranslator = null
                    cachedReady = false
                    translationError.value = "No se pudo preparar este idioma. Conéctate a Internet e inténtalo de nuevo."
                    preparingCallbacks.remove(pair)
                    translationLoading.value = false
                }
            }
    }
    private fun translate(text: String, source: String, target: String) {
        translationError.value = ""
        val requestId = ++translationRequestId
        if (text.isBlank()) {
            translationResult.value = ""
            translationLoading.value = false
            return
        }
        if (source == target) {
            translationResult.value = text
            translationLoading.value = false
            return
        }
        translationLoading.value = true
        val pair = source + "-" + target
        fun runTranslation() {
            if (requestId != translationRequestId) return
            val translator = translatorCache[pair]
            if (translator == null || cachedPair != pair || !cachedReady) {
                prepareTranslator(source, target) { runTranslation() }
                return
            }
            translatorCache[pair]
            translator.translate(text)
                .addOnSuccessListener { result ->
                    if (requestId != translationRequestId) return@addOnSuccessListener
                    translationResult.value = result
                    history.add(TranslationEntry(text, result, source, target))
                    if (history.size > 20) history.removeAt(0)
                    if (autoSpeakAfterTranslation) {
                        autoSpeakAfterTranslation = false
                        languages.firstOrNull { it.code == target }?.let { speak(result, it) }
                    }
                    translationLoading.value = false
                }
                .addOnFailureListener {
                    if (requestId != translationRequestId) return@addOnFailureListener
                    translationResult.value = ""
                    translationError.value = "No se pudo traducir. Inténtalo de nuevo."
                    translationLoading.value = false
                }
        }
        runTranslation()
    }
    private fun speak(text: String, language: AppLanguage) {
        if (text.isBlank()) return
        if (!ttsReady) {
            pendingSpeech = text to language
            return
        }
        val locale = Locale.forLanguageTag(language.speechLocale)
        val status = tts?.setLanguage(locale)
        if (status == TextToSpeech.LANG_MISSING_DATA || status == TextToSpeech.LANG_NOT_SUPPORTED) {
            translationError.value = "La voz de " + language.name + " no está disponible en este dispositivo."
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "conversa_voz")
        if (conversationMode) {
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { window.decorView.postDelayed({ listenConversationTurn() }, 250) }
                override fun onError(utteranceId: String?) { window.decorView.postDelayed({ listenConversationTurn() }, 250) }
            })
        }
    }

    override fun onDestroy() {
        translatorCache.values.toSet().forEach { it.close() }
        translatorCache.clear()
        cachedTranslator = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector(
    title: String,
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(title + ": " + selected.flag + " " + selected.name + "  ▼")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 430.dp)
        ) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.flag + "  " + language.name) },
                    onClick = {
                        onSelect(language)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ConversaFacilApp(
    spokenText: String,
    translatedText: String,
    isTranslating: Boolean,
    translationError: String,
    startListening: (AppLanguage, AppLanguage) -> Unit,
    speak: (String, AppLanguage) -> Unit,
    translateText: (String, String, String) -> Unit,
    prepareLanguages: (String, String, (() -> Unit)?) -> Unit,
    history: List<TranslationEntry>,
    clearHistory: () -> Unit,
    startConversationMode: (AppLanguage, AppLanguage, (Boolean) -> Unit) -> Unit,
    stopConversationMode: ((Boolean) -> Unit) -> Unit
) {
    var source by remember { mutableStateOf(languages[0]) }
    var target by remember { mutableStateOf(languages[2]) }
    var sourceText by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isPreparingLanguage by remember { mutableStateOf(false) }
    var conversationMode by remember { mutableStateOf(false) }
    var loadingMessageIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val micScale by animateFloatAsState(if (isListening) 1.08f else 1f, tween(220), label = "micScale")


    LaunchedEffect(spokenText) {
        if (spokenText.isNotBlank()) isListening = false
        if (spokenText.isNotBlank()) sourceText = spokenText
    }
    LaunchedEffect(translatedText) {
        if (translatedText.isNotBlank()) targetText = translatedText
    }
    LaunchedEffect(source.code, target.code) {
        if (source.code == target.code) {
            isPreparingLanguage = false
            return@LaunchedEffect
        }
        isPreparingLanguage = true
        loadingMessageIndex = 0
        prepareLanguages(source.code, target.code) {
            isPreparingLanguage = false
        }
    }

    LaunchedEffect(isPreparingLanguage) {
        if (!isPreparingLanguage) return@LaunchedEffect
        while (isPreparingLanguage) {
            delay(900)
            loadingMessageIndex = (loadingMessageIndex + 1) % 4
        }
    }

    // Traducción automática con una espera mínima para no bloquear mientras se escribe.
    // El modelo ya se prepara al seleccionar los idiomas, así la traducción arranca casi inmediatamente.
    LaunchedEffect(sourceText, source.code, target.code) {
        if (sourceText.isBlank()) {
            targetText = ""
            return@LaunchedEffect
        }
        delay(120)
        translateText(sourceText, source.code, target.code)
    }

    fun swapLanguages() {
        val oldSource = source
        source = target
        target = oldSource
        val oldText = sourceText
        sourceText = targetText
        targetText = oldText
    }

    fun shareApp() {
        try {
            // FileProvider cannot expose the installed APK directly from /data/app.
            // Copy it to our app cache, which is explicitly allowed by file_paths.xml.
            val sourceApk = File(context.applicationInfo.sourceDir)
            val sharedApk = File(context.cacheDir, "Conversa-Facil.apk")
            sourceApk.inputStream().use { input ->
                sharedApk.outputStream().use { output -> input.copyTo(output) }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                sharedApk
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_TEXT, "Te comparto Conversa Fácil. Puedes instalarla y traducir entre varios idiomas.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Compartir Conversa Fácil"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                "No se pudo preparar la app para compartir.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F9FC)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.fillMaxWidth().background(
                        Brush.linearGradient(listOf(Color(0xFF315BEA), Color(0xFF7A42E8)))
                    ).padding(start = 20.dp, end = 20.dp, top = 34.dp, bottom = 20.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Conversa Fácil", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
                            Text("Habla. Traduce. Conecta.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.88f))
                        }
                        Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.26f)) {
                            Text("● EN LÍNEA", modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        }
                    }
                }

                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("IDIOMAS", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFF70809A))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LanguageSelector("Tú", source) {
                                    if (it.code != target.code) { source = it; sourceText = ""; targetText = "" }
                                }
                                Surface(Modifier.size(44.dp), CircleShape, color = Color(0xFF315BEA), shadowElevation = 5.dp) {
                                    IconButton(onClick = ::swapLanguages) {
                                        Icon(Icons.Default.SwapHoriz, "Cambiar idiomas", tint = Color.White)
                                    }
                                }
                                LanguageSelector("Traducción", target) {
                                    if (it.code != source.code) { target = it; targetText = "" }
                                }
                            }
                        }
                    }

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = if (conversationMode) Color(0xFFEDE8FF) else Color.White),
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Surface(Modifier.size(48.dp), CircleShape, color = if (conversationMode) Color(0xFF7A42E8) else Color(0xFFF0E8FF)) {
                                Text("🎧", modifier = Modifier.wrapContentSize(Alignment.Center), style = MaterialTheme.typography.titleLarge)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Modo conversación", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFF263B73))
                                Text(if (conversationMode) "Escuchando y respondiendo en " + target.name + "…" else "Habla con otra persona sin tocar la pantalla", style = MaterialTheme.typography.bodySmall, color = Color(0xFF667085))
                            }
                            Switch(
                                checked = conversationMode,
                                onCheckedChange = { enabled ->
                                    if (enabled) startConversationMode(source, target) { conversationMode = it }
                                    else stopConversationMode { conversationMode = it }
                                }
                            )
                        }
                    }

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(5.dp)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Tu mensaje", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFF173B70))
                                Text(source.flag, style = MaterialTheme.typography.headlineSmall)
                            }

                            OutlinedTextField(
                                value = sourceText,
                                onValueChange = { sourceText = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 5,
                                placeholder = { Text("Escribe aquí o usa el micrófono…") },
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF7A42E8),
                                    unfocusedBorderColor = Color(0xFFE1E3E8)
                                )
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { if (sourceText.isNotBlank()) translateText(sourceText, source.code, target.code) },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(17.dp)
                                ) {
                                    Text("Traducir", fontWeight = FontWeight.Bold)
                                }
                                Surface(Modifier.size(52.dp).scale(micScale), CircleShape, color = Color(0xFFF0E8FF)) {
                                    IconButton(onClick = { isListening = true; startListening(source, target) }) {
                                        Icon(Icons.Default.Mic, "Hablar", tint = Color(0xFF7A42E8), modifier = Modifier.size(27.dp))
                                    }
                                }
                            }

                            AnimatedVisibility(visible = isListening) {
                                Text("🎙️ Escuchando…", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFF315BEA))
                            }

                            Text("Frases rápidas", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFF70809A))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Hola", "¿Cuánto cuesta?", "Gracias", "¿Dónde está?").forEach { phrase ->
                                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF0F2F5)) {
                                        AssistChip(
                                            onClick = { sourceText = phrase; translateText(phrase, source.code, target.code) },
                                            label = { Text(phrase, color = Color(0xFF1F2937)) },
                                            shape = RoundedCornerShape(14.dp),
                                            border = null,
                                            colors = AssistChipDefaults.assistChipColors(containerColor = Color.Transparent, labelColor = Color(0xFF1F2937))
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (history.isNotEmpty()) {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(3.dp)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("Conversación", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFF173B70))
                                        Text(history.size.toString() + if (history.size == 1) " mensaje" else " mensajes", style = MaterialTheme.typography.labelMedium, color = Color(0xFF667085))
                                    }
                                    TextButton(onClick = clearHistory) { Text("Borrar") }
                                }
                                history.asReversed().take(8).forEach { item ->
                                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color(0xFFF3F5F8)) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(item.source, fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2937))
                                            Text(item.translated, fontWeight = FontWeight.Bold, color = Color(0xFF204E45))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Traducción", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFF00A86B))
                                    LanguageSelector("Idioma", target) {
                                        if (it.code != source.code) {
                                            target = it
                                            targetText = ""
                                            isPreparingLanguage = true
                                            loadingMessageIndex = 0
                                            prepareLanguages(source.code, it.code) {
                                                isPreparingLanguage = false
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text("✨", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF00A86B))
                            }
                            if (isPreparingLanguage) {
                                val downloadMessages = listOf(
                                    "📥 Descargando el archivo del idioma…",
                                    "🧩 Preparando el idioma " + target.name + "…",
                                    "✨ Casi listo… preparando la traducción",
                                    "⚡ Un momento, ya casi puedes traducir"
                                )
                                val message = downloadMessages[loadingMessageIndex]
                                Card(
                                    Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EEFF))
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(9.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            Modifier.size(30.dp),
                                            strokeWidth = 3.dp,
                                            color = Color(0xFF7A42E8)
                                        )
                                        Text(
                                            message,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = Color(0xFF5D35B0)
                                        )
                                        Text(
                                            "Espera un momento mientras descargamos el archivo necesario. Solo tendrás que hacerlo la primera vez.",
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF6F6385)
                                        )
                                    }
                                }
                            } else if (isTranslating) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                                    Text("Traduciendo…", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF087F61))
                                }
                            } else Text(
                                if (targetText.isBlank()) "Tu traducción aparecerá aquí" else targetText,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = if (targetText.isBlank()) FontWeight.Normal else FontWeight.Bold),
                                color = if (targetText.isBlank()) Color(0xFF78968E) else Color(0xFF204E45)
                            )
                            if (translationError.isNotBlank()) Text(translationError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { speak(targetText, target) },
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    enabled = targetText.isNotBlank(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A42E8), disabledContentColor = Color(0xFF777B85))
                                ) {
                                    Icon(Icons.Default.VolumeUp, null)
                                    Spacer(Modifier.width(7.dp))
                                    Text("Escuchar", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { sourceText = ""; targetText = "" },
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text("Limpiar", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }

                    OutlinedButton(onClick = { shareApp() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Share, contentDescription = "Compartir"); Spacer(Modifier.width(8.dp)); Text("Compartir Conversa Fácil", fontWeight = FontWeight.Bold) }

                BannerAd(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp))

                    Text(
                        "🌎  15 idiomas • Traducción en el dispositivo",
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF667085)
                    )
                }
            }
        }
    }
}


@Composable
private fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            adUnitId = "ca-app-pub-5236094250280846/1170308482"
            setAdSize(AdSize.BANNER)
        }
    }
    DisposableEffect(adView) {
        adView.loadAd(AdRequest.Builder().build())
        onDispose { adView.destroy() }
    }
    AndroidView(modifier = modifier.height(50.dp), factory = { adView })
}
