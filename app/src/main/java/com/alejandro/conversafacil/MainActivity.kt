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
    private var tts: TextToSpeech? = null
    private var lastSource = "es"
    private var lastTarget = "zh"

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text ->
            speechResult.value = text
            translate(text, lastSource, lastTarget)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, null)
        setContent {
            ConversaFacilApp(
                spokenText = speechResult.value,
                translatedText = translationResult.value,
                startListening = ::startListening,
                speak = ::speak,
                translateText = ::translate
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

    private fun translate(text: String, source: String, target: String) {
        if (text.isBlank() || source == target) {
            translationResult.value = if (source == target) text else ""
            return
        }
        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        )
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { result ->
                        translationResult.value = result
                        translator.close()
                    }
                    .addOnFailureListener {
                        translationResult.value = "No se pudo traducir. Inténtalo de nuevo."
                        translator.close()
                    }
            }
            .addOnFailureListener {
                translationResult.value = "No se pudo descargar el idioma. Revisa tu conexión."
                translator.close()
            }
    }

    private fun speak(text: String, language: AppLanguage) {
        if (text.isBlank()) return
        val locale = Locale.forLanguageTag(language.speechLocale)
        val status = tts?.setLanguage(locale)
        if (status == TextToSpeech.LANG_MISSING_DATA || status == TextToSpeech.LANG_NOT_SUPPORTED) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "conversa_voz")
    }

    override fun onDestroy() {
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
    startListening: (AppLanguage, AppLanguage) -> Unit,
    speak: (String, AppLanguage) -> Unit,
    translateText: (String, String, String) -> Unit
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
                    ).padding(20.dp)
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
                        Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.16f)) {
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
                                    focusedBorderColor = Color(0xFF315BEA),
                                    unfocusedBorderColor = Color(0xFFDCE3EF)
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
                                Surface(Modifier.size(52.dp).scale(micScale), CircleShape, color = Color(0xFFE8F0FF)) {
                                    IconButton(onClick = { isListening = true; startListening(source, target) }) {
                                        Icon(Icons.Default.Mic, "Hablar", tint = Color(0xFF315BEA), modifier = Modifier.size(27.dp))
                                    }
                                }
                            }

                            AnimatedVisibility(visible = isListening) {
                                Text("🎙️ Escuchando…", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFF315BEA))
                            }

                            Text("Frases rápidas", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFF70809A))
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Hola", "¿Cuánto cuesta?", "Gracias", "¿Dónde está?").forEach { phrase ->
                                    AssistChip(
                                        onClick = { sourceText = phrase; translateText(phrase, source.code, target.code) },
                                        label = { Text(phrase) },
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAFBF5)),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Traducción", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color(0xFF087F61))
                                    Text(target.flag + " " + target.name, style = MaterialTheme.typography.labelLarge, color = Color(0xFF4D7D70))
                                }
                                Text("✨", style = MaterialTheme.typography.headlineMedium)
                            }
                            Text(
                                if (targetText.isBlank()) "Tu traducción aparecerá aquí" else targetText,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = if (targetText.isBlank()) FontWeight.Normal else FontWeight.Bold),
                                color = if (targetText.isBlank()) Color(0xFF78968E) else Color(0xFF204E45)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { speak(targetText, target) },
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    enabled = targetText.isNotBlank(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A42E8))
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
                        color = Color(0xFF718198)
                    )
                }
            }
        }
    }
