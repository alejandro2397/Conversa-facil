package com.alejandro.conversafacil

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
    private val translators = ConcurrentHashMap<String, com.google.mlkit.nl.translate.Translator>()

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.let { text ->
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
                startListening = ::startListening,
                speak = ::speak,
                copyText = ::copyText
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

    private fun getTranslator(source: String, target: String): com.google.mlkit.nl.translate.Translator {
        val key = "$source-$target"
        return translators.getOrPut(key) {
            Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        }
    }

    private fun prepareTranslator(source: String, target: String) {
        if (source == target) return
        getTranslator(source, target).downloadModelIfNeeded(DownloadConditions.Builder().build())
    }

    private fun translate(text: String, source: String, target: String) {
        if (text.isBlank() || source == target) {
            translationResult.value = if (source == target) text else ""
            return
        }
        val translator = getTranslator(source, target)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { translator.translate(text)
                .addOnSuccessListener { translated -> translationResult.value = translated }
                .addOnFailureListener { translationResult.value = "No se pudo traducir. Inténtalo de nuevo." }
            }
            .addOnFailureListener { translationResult.value = "Preparando el idioma… inténtalo de nuevo en un momento." }
    }

    private fun speak(text: String, language: AppLanguage) {
        if (text.isBlank()) return
        val locale = Locale.forLanguageTag(language.speechLocale)
        val status = tts?.setLanguage(locale)
        if (status == TextToSpeech.LANG_MISSING_DATA || status == TextToSpeech.LANG_NOT_SUPPORTED) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "conversa_voz")
    }

    private fun copyText(text: String) {
        if (text.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Conversa Fácil", text))
    }

    override fun onDestroy() {
        translators.values.forEach { it.close() }
        translators.clear()
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSelector(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selected.flag, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    selected.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF203A5F)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 430.dp)
        ) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${language.flag}  ${language.name}",
                            fontWeight = if (language.code == selected.code) FontWeight.Bold else FontWeight.Normal
                        )
                    },
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
    copyText: (String) -> Unit
) {
    var source by remember { mutableStateOf(languages[0]) }
    var target by remember { mutableStateOf(languages[2]) }
    var sourceText by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val micScale by animateFloatAsState(
        targetValue = if (isListening) 1.06f else 1f,
        animationSpec = tween(180),
        label = "micScale"
    )

    LaunchedEffect(spokenText) {
        if (spokenText.isNotBlank()) {
            isListening = false
            sourceText = spokenText
        }
    }

    LaunchedEffect(translatedText) {
        if (translatedText.isNotBlank()) targetText = translatedText
    }

    fun clearConversation() {
        sourceText = ""
        targetText = ""
    }

    fun swapLanguages() {
        val oldSource = source
        source = target
        target = oldSource

        val oldText = sourceText
        sourceText = targetText
        targetText = oldText
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF315BEA),
            secondary = Color(0xFF00A98F),
            background = Color(0xFFF4F7FC),
            surface = Color.White
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF4F7FC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp)
            ) {
                Header()

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "YO",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF607796)
                            )
                            LanguageSelector(source) {
                                if (it.code != target.code) {
                                    source = it
                                    clearConversation()
                                }
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        FilledIconButton(
                            onClick = ::swapLanguages,
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF315BEA)
                            )
                        ) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = "Cambiar idiomas",
                                tint = Color.White
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "OTRA PERSONA",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF607796)
                            )
                            LanguageSelector(target) {
                                if (it.code != source.code) {
                                    target = it
                                    clearConversation()
                                }
                            }
                        }
                    }

                    ConversationCard(
                        language = source,
                        text = sourceText,
                        isSource = true,
                        onSpeak = { speak(sourceText, source) },
                        onCopy = { copyText(sourceText) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFD7E0EE)
                        )

                        Spacer(Modifier.width(12.dp))

                        Surface(
                            modifier = Modifier
                                .size(62.dp)
                                .scale(micScale),
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 5.dp
                        ) {
                            IconButton(
                                onClick = {
                                    isListening = true
                                    startListening(source, target)
                                }
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Hablar",
                                    tint = Color(0xFF315BEA),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFD7E0EE)
                        )
                    }

                    AnimatedVisibility(visible = isListening) {
                        Text(
                            "Escuchando…",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF315BEA)
                        )
                    }

                    ConversationCard(
                        language = target,
                        text = targetText,
                        isSource = false,
                        onSpeak = { speak(targetText, target) },
                        onCopy = { copyText(targetText) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(
                            onClick = ::clearConversation,
                            enabled = sourceText.isNotBlank() || targetText.isNotBlank(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Limpiar conversación")
                        }
                    }

                    Text(
                        "Habla en tu idioma y deja que Conversa Fácil haga el resto.",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF71839B)
                    )

                    AdMobBanner()
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF315BEA), Color(0xFF6843D9))
                )
            )
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "CF",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    "Conversa Fácil",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    "Habla. Traduce. Entiende.",
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ConversationCard(
    language: AppLanguage,
    text: String,
    isSource: Boolean,
    onSpeak: () -> Unit,
    onCopy: () -> Unit
) {
    val container = if (isSource) Color.White else Color(0xFFEAFBF6)
    val labelColor = if (isSource) Color(0xFF315BEA) else Color(0xFF008B72)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(language.flag, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(7.dp))
                Text(
                    language.name,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = labelColor
                )
                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = onCopy,
                    enabled = text.isNotBlank()
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copiar",
                        tint = labelColor
                    )
                }

                IconButton(
                    onClick = onSpeak,
                    enabled = text.isNotBlank()
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Escuchar",
                        tint = labelColor
                    )
                }
            }

            Text(
                text = text.ifBlank {
                    if (isSource) "Toca el micrófono para hablar…" else "La traducción aparecerá aquí…"
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (text.isBlank()) Color(0xFF8A9AAF) else Color(0xFF233A58),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


@Composable
private fun AdMobBanner() {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-5236094250280846/9454238594"
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
