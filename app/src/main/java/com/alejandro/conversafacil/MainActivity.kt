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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                speak = ::speak
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
    speak: (String, AppLanguage) -> Unit
) {
    var source by remember { mutableStateOf(languages[0]) }
    var target by remember { mutableStateOf(languages[2]) }
    var sourceText by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }


    LaunchedEffect(spokenText) {
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
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F8FF)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF087CF2), Color(0xFF6537E8), Color(0xFFD52CCB))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("A  文", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
                        Text("Conversa Fácil", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
                        Text("Sin barreras, solo comunicación", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                    }
                }

                Column(
                    Modifier.padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageSelector("Tú hablas", source) { language ->
                            if (language.code != target.code) {
                                source = language
                                sourceText = ""
                                targetText = ""
                            }
                        }

                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = CircleShape,
                            color = Color(0xFF3159E8),
                            shadowElevation = 6.dp
                        ) {
                            IconButton(onClick = ::swapLanguages) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Cambiar idiomas", tint = Color.White)
                            }
                        }

                        LanguageSelector("Traducir a", target) { language ->
                            if (language.code != source.code) {
                                target = language
                                targetText = ""
                            }
                        }
                    }

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
                    ) {
                        Column(
                            Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                "${source.flag}  ${source.name}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF183A70)
                            )
                            Text(
                                sourceText.ifEmpty { "Toca el micrófono y habla" },
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                color = Color(0xFF526A8A)
                            )

                            Surface(
                                modifier = Modifier.size(112.dp),
                                shape = CircleShape,
                                color = Color(0xFFE4F0FF),
                                shadowElevation = 2.dp
                            ) {
                                IconButton(onClick = { startListening(source, target) }) {
                                    Surface(
                                        modifier = Modifier.size(78.dp),
                                        shape = CircleShape,
                                        color = Color(0xFF1685F5),
                                        shadowElevation = 8.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Mic, contentDescription = "Hablar", tint = Color.White, modifier = Modifier.size(38.dp))
                                        }
                                    }
                                }
                            }

                            Text("Toca para hablar", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF173B70))
                        }
                    }

                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFFFFB)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("✨  Traducción", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFF079C73))
                            Text(targetText.ifEmpty { "Aquí aparecerá la traducción..." }, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF45617F))

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { speak(targetText, target) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B35E8))
                                ) {
                                    Icon(Icons.Default.VolumeUp, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Escuchar")
                                }

                                OutlinedButton(
                                    onClick = { sourceText = ""; targetText = "" },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Limpiar")
                                }
                            }
                        }
                    }

                    Text(
                        "15 idiomas disponibles",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF38577F),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
