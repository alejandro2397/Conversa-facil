package com.alejandro.conversafacil

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val speechResult = mutableStateOf("")
    private val translationResult = mutableStateOf("")
    private var tts: TextToSpeech? = null
    private lateinit var translator: Translator

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text ->
            speechResult.value = text
            translateToChinese(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage("es").setTargetLanguage("zh").build()
        )
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.SIMPLIFIED_CHINESE
        }
        setContent {
            ConversaFacilApp(speechResult.value, translationResult.value, ::startListening, ::speakChinese)
        }
    }

    private fun startListening() {
        speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla en español")
        })
    }

    private fun translateToChinese(text: String) {
        if (text.isBlank()) return
        translator.translate(text)
            .addOnSuccessListener { translationResult.value = it }
            .addOnFailureListener { translationResult.value = "No se pudo traducir. Intenta de nuevo." }
    }

    private fun speakChinese(text: String) {
        if (text.isNotBlank()) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "conversa_chino")
    }

    override fun onDestroy() {
        translator.close()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun ConversaFacilApp(
    spokenText: String,
    chineseText: String,
    startListening: () -> Unit,
    speakChinese: (String) -> Unit
) {
    var spanishText by remember { mutableStateOf("") }
    var chineseTranslation by remember { mutableStateOf("") }

    LaunchedEffect(spokenText) { if (spokenText.isNotBlank()) spanishText = spokenText }
    LaunchedEffect(chineseText) { if (chineseText.isNotBlank()) chineseTranslation = chineseText }

    val phrases = listOf(
        "Hola" to "你好", "¿Cómo está?" to "你好吗？", "¿Cuánto cuesta?" to "多少钱？",
        "Quiero comprar" to "我想买", "¿Cuántos necesita?" to "您需要多少？",
        "¿Tiene este producto?" to "你有这个产品吗？", "Gracias" to "谢谢", "Espere un momento" to "请等一下"
    )

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("💬", style = MaterialTheme.typography.displaySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("Conversa Fácil", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("Habla sin fronteras", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("🇸🇻 Español  ↔  🇨🇳 中文", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("ESPAÑOL", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(spanishText.ifEmpty { "Habla o elige una frase" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = startListening) { Icon(Icons.Default.Mic, contentDescription = "Hablar español") }
                        }
                        HorizontalDivider()
                        Text("中文", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(chineseTranslation.ifEmpty { "La traducción aparecerá aquí" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { speakChinese(chineseTranslation) }) { Icon(Icons.Default.VolumeUp, contentDescription = "Escuchar chino") }
                        }
                    }
                }
                Text("⚡ Frases rápidas", style = MaterialTheme.typography.titleLarge)
                phrases.forEach { (es, zh) ->
                    Button(
                        onClick = { spanishText = es; chineseTranslation = zh },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("$es  •  $zh") }
                }
                OutlinedButton(onClick = { spanishText = ""; chineseTranslation = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Limpiar")
                }
            }
        }
    }
}
