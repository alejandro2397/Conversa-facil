package com.alejandro.conversafacil

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import androidx.compose.ui.unit.dp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

data class AppLanguage(val name: String, val flag: String, val code: String, val speechLocale: String)

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
    private var cachedTranslator: com.google.mlkit.nl.translate.Translator? = null
    private var cachedPair: String? = null
    private var cachedReady = false

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text ->
            speechResult.value = text
            translate(text, lastSource, lastTarget)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, null)
        prepareTranslator("es", "zh")
        setContent {
            ConversaFacilApp(
                spokenText = speechResult.value,
                translatedText = translationResult.value,
                isTranslating = translationLoading.value,
                translationError = translationError.value,
                startListening = ::startListening,
                speak = ::speak,
                translateText = ::translate,
                prepareLanguages = ::prepareTranslator
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
            cachedReady = true
            onReady?.invoke()
            return
        }
        val pair = "$source-$target"
        if (cachedPair == pair && cachedTranslator != null && cachedReady) {
            onReady?.invoke()
            return
        }

        cachedTranslator?.close()
        cachedTranslator = null
        cachedReady = false
        cachedPair = pair

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        )
        cachedTranslator = translator

        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                if (cachedPair == pair) {
                    cachedReady = true
                    onReady?.invoke()
                }
            }
            .addOnFailureListener {
                if (cachedPair == pair) {
                    cachedReady = false
                    translationError.value = "Necesito preparar este idioma. Activa Internet una vez y vuelve a intentar."
                    translationLoading.value = false
                }
            }
    }

    private fun translate(text: String, source: String, target: String) {
        translationError.value = ""
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
        val pair = "$source-$target"

        fun runTranslation() {
            val translator = cachedTranslator
            if (cachedPair != pair || translator == null || !cachedReady) {
                prepareTranslator(source, target) { runTranslation() }
                return
            }

            translator.translate(text)
                .addOnSuccessListener { result ->
                    translationResult.value = result
                    translationLoading.value = false
                }
                .addOnFailureListener {
                    translationResult.value = ""
                    translationError.value = "No se pudo traducir. Inténtalo de nuevo."
                    translationLoading.value = false
                }
        }

        runTranslation()
    }

    private fun speak(text: String, language: AppLanguage) {
        if (text.isBlank()) return
        val locale = Locale.forLanguageTag(language.speechLocale)
        val status = tts?.setLanguage(locale)
        if (status == TextToSpeech.LANG_MISSING_DATA || status == TextToSpeech.LANG_NOT_SUPPORTED) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "conversa_voz")
    }

    override fun onDestroy() {
        cachedTranslator?.close()
        cachedTranslator = null
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
    prepareLanguages: (String, String, (() -> Unit)?) -> Unit
) {
    var source by remember { mutableStateOf(languages[0]) }
    var target by remember { mutableStateOf(languages[2]) }
    var sourceText by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val micScale by animateFloatAsState(if (isListening) 1.08f else 1f, tween(220), label = "micScale")


    LaunchedEffect(spokenText) {
        if (spokenText.isNotBlank()) isListening = false
        if (spokenText.isNotBlank()) sourceText = spokenText
    }
    LaunchedEffect(translatedText) {
        if (translatedText.isNotBlank()) targetText = translatedText
    }

    fun swapLanguages() {
        val oldSource = source
        source = target
        target = oldSource
        val oldText = sourceText
        sourceText = targetText
        targetText = oldText
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
                                            prepareLanguages(source.code, it.code, null)
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text("✨", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF00A86B))
                            }
                            if (isTranslating) {
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
