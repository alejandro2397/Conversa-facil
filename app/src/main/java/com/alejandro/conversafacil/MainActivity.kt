package com.alejandro.conversafacil

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    private lateinit var esToZh: Translator
    private lateinit var zhToEs: Translator
    private var chineseMode = false

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { text ->
            speechResult.value = text
            if (chineseMode) translateChineseToSpanish(text) else translateSpanishToChinese(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        esToZh = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage("es").setTargetLanguage("zh").build())
        zhToEs = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage("zh").setTargetLanguage("es").build())
        val conditions = DownloadConditions.Builder().build()
        esToZh.downloadModelIfNeeded(conditions)
        zhToEs.downloadModelIfNeeded(conditions)
        tts = TextToSpeech(this, null)
        setContent { ConversaFacilApp(speechResult.value, translationResult.value, ::startListening, ::speak) }
    }

    private fun startListening(chinese: Boolean) {
        chineseMode = chinese
        speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (chinese) "zh-CN" else "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (chinese) "说中文" else "Habla en español")
        })
    }

    private fun translateSpanishToChinese(text: String) {
        if (text.isBlank()) return
        esToZh.translate(text).addOnSuccessListener { translationResult.value = it }
            .addOnFailureListener { translationResult.value = "No se pudo traducir. Comprueba tu conexión e inténtalo de nuevo." }
    }

    private fun translateChineseToSpanish(text: String) {
        if (text.isBlank()) return
        zhToEs.translate(text).addOnSuccessListener { translationResult.value = it }
            .addOnFailureListener { translationResult.value = "No se pudo traducir. Comprueba tu conexión e inténtalo de nuevo." }
    }

    private fun speak(text: String, chinese: Boolean) {
        if (text.isBlank()) return
        tts?.language = if (chinese) Locale.SIMPLIFIED_CHINESE else Locale("es", "ES")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "conversa_voz")
    }

    override fun onDestroy() {
        esToZh.close()
        zhToEs.close()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun ConversaFacilApp(
    spokenText: String,
    translatedText: String,
    startListening: (Boolean) -> Unit,
    speak: (String, Boolean) -> Unit
) {
    var spanishText by remember { mutableStateOf("") }
    var chineseText by remember { mutableStateOf("") }
    var spanishToChinese by remember { mutableStateOf(true) }
    var lastSpeaker by remember { mutableStateOf("Tú") }

    LaunchedEffect(spokenText) {
        if (spokenText.isNotBlank()) {
            if (spanishToChinese) spanishText = spokenText else chineseText = spokenText
        }
    }
    LaunchedEffect(translatedText) {
        if (translatedText.isNotBlank()) {
            if (spanishToChinese) chineseText = translatedText else spanishText = translatedText
        }
    }

    val phrases = listOf(
        "Hola" to "你好", "¿Cómo está?" to "你好吗？", "¿Cuánto cuesta?" to "多少钱？",
        "Quiero comprar" to "我想买", "¿Cuántos necesita?" to "您需要多少？",
        "¿Tiene este producto?" to "你有这个产品吗？", "Gracias" to "谢谢", "Espere un momento" to "请等一下"
    )

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("💬", style = MaterialTheme.typography.displaySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("Conversa Fácil", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("Habla • traduce • escucha", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(if (spanishToChinese) "🇸🇻 ESPAÑOL" else "🇨🇳 中文", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        IconButton(onClick = { spanishToChinese = !spanishToChinese }) { Icon(Icons.Default.SwapHoriz, contentDescription = "Cambiar dirección") }
                        Text(if (spanishToChinese) "🇨🇳 中文" else "🇸🇻 ESPAÑOL", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }

                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (spanishToChinese) "🇸🇻 TÚ HABLAS" else "🇨🇳 LA OTRA PERSONA HABLA", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (spanishToChinese) spanishText.ifEmpty { "Pulsa el botón y habla" } else chineseText.ifEmpty { "按按钮说中文" },
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Button(
                            onClick = { lastSpeaker = if (spanishToChinese) "Tú" else "La otra persona"; startListening(!spanishToChinese) },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (spanishToChinese) "🎤 HABLAR EN ESPAÑOL" else "🎤 HABLAR EN CHINO")
                        }
                    }
                }

                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (spanishToChinese) "🇨🇳 TRADUCCIÓN" else "🇸🇻 TRADUCCIÓN", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (spanishToChinese) chineseText.ifEmpty { "Aquí aparecerá el chino" } else spanishText.ifEmpty { "Aquí aparecerá el español" },
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Button(
                            onClick = { if (spanishToChinese) speak(chineseText, true) else speak(spanishText, false) },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("🔊 ESCUCHAR TRADUCCIÓN")
                        }
                    }
                }

                Text("⚡ Frases rápidas", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                phrases.forEach { (es, zh) ->
                    Button(
                        onClick = { spanishText = es; chineseText = zh; lastSpeaker = "Frase rápida" },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("$es  •  $zh") }
                }

                Text("Último hablante: $lastSpeaker", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                OutlinedButton(onClick = { spanishText = ""; chineseText = ""; translationResult.value = "" }, modifier = Modifier.fillMaxWidth()) { Text("🧹 Limpiar conversación") }
            }
        }
    }
}
