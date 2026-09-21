package com.alejandro.conversafacil

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.net.Uri
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
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
    private var voiceRate = 1.0f
    private val prefs by lazy { getSharedPreferences("conversa_facil", Context.MODE_PRIVATE) }
    private val history = mutableStateOf(loadEntries("history"))
    private val favorites = mutableStateOf(loadEntries("favorites"))

    private val photoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = if (lastSource == "zh") TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            else TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image).addOnSuccessListener { result ->
                val text = result.text.trim()
                if (text.isBlank()) translationResult.value = "No encontré texto en la foto."
                else { speechResult.value = text; translate(text, lastSource, lastTarget) }
            }.addOnFailureListener { translationResult.value = "No se pudo leer la foto." }
        } catch (_: Exception) { translationResult.value = "No se pudo abrir la foto." }
    }

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.let { text ->
                    speechResult.value = text
                    translate(text, lastSource, lastTarget)
                }
        }

    private fun openPhoto() { photoLauncher.launch("image/*") }

    private fun loadEntries(key: String): List<String> =
        prefs.getString(key, "")?.split("\n".toRegex())?.filter { it.isNotBlank() } ?: emptyList()

    private fun saveEntries(key: String, values: List<String>) =
        prefs.edit().putString(key, values.joinToString("\n")).apply()

    private fun addFavorite(item: String) {
        favorites.value = (listOf(item) + favorites.value.filterNot { it == item }).take(50)
        saveEntries("favorites", favorites.value)
    }

    private fun removeFavorite(item: String) {
        favorites.value = favorites.value.filterNot { it == item }
        saveEntries("favorites", favorites.value)
    }

    private fun saveHistory(item: String) {
        history.value = (listOf(item) + history.value.filterNot { it == item }).take(50)
        saveEntries("history", history.value)
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
                copyText = ::copyText,
                history = history.value,
                favorites = favorites.value,
                addFavorite = ::addFavorite,
                removeFavorite = ::removeFavorite,
                saveHistory = ::saveHistory,
                openPhoto = ::openPhoto,
                setVoiceRate = { voiceRate = it }
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
        tts?.setSpeechRate(voiceRate)
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
    spokenText: String, translatedText: String,
    startListening: (AppLanguage, AppLanguage) -> Unit,
    speak: (String, AppLanguage) -> Unit,
    copyText: (String) -> Unit,
    history: List<String>, favorites: List<String>,
    addFavorite: (String) -> Unit, removeFavorite: (String) -> Unit,
    saveHistory: (String) -> Unit, openPhoto: () -> Unit, setVoiceRate: (Float) -> Unit
) {
    var source by remember { mutableStateOf(languages[0]) }
    var target by remember { mutableStateOf(languages[2]) }
    var sourceText by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var showTravel by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }

    LaunchedEffect(spokenText) { if (spokenText.isNotBlank()) sourceText = spokenText }
    LaunchedEffect(translatedText) {
        if (translatedText.isNotBlank()) {
            targetText = translatedText
            if (sourceText.isNotBlank()) saveHistory(source.name + "|" + target.name + "|" + sourceText + "|" + translatedText)
        }
    }

    fun clearConversation() { sourceText = ""; targetText = "" }
    fun swapLanguages() {
        val s = source; source = target; target = s
        val t = sourceText; sourceText = targetText; targetText = t
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF315BEA), secondary = Color(0xFF00A98F),
        background = Color(0xFFF4F7FC), surface = Color.White
    )) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F7FC)) {
            Column(Modifier.fillMaxSize()) {
                Header()
                when (tab) {
                    0 -> HomeContent(source, target, sourceText, targetText, startListening, speak, copyText,
                        ::clearConversation, ::swapLanguages, { source = it; clearConversation() },
                        { target = it; clearConversation() }, openPhoto,
                        { if (sourceText.isNotBlank() && targetText.isNotBlank()) addFavorite(source.name + "|" + target.name + "|" + sourceText + "|" + targetText) },
                        { showTravel = true }, { showSpeed = true })
                    1 -> SavedScreen("⭐ Favoritos", favorites, removeFavorite)
                    2 -> SavedScreen("🕘 Historial", history, {})
                    else -> MoreScreen(openPhoto, { showTravel = true }, { showSpeed = true })
                }
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("🏠") }, label = { Text("Inicio") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("⭐") }, label = { Text("Favoritos") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("🕘") }, label = { Text("Historial") })
                    NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Text("☰") }, label = { Text("Más") })
                }
            }
        }
    }
    if (showTravel) TravelDialog(speak) { showTravel = false }
    if (showSpeed) SpeedDialog(setVoiceRate) { showSpeed = false }
}

@Composable
private fun HomeContent(
    source: AppLanguage, target: AppLanguage, sourceText: String, targetText: String,
    startListening: (AppLanguage, AppLanguage) -> Unit, speak: (String, AppLanguage) -> Unit,
    copyText: (String) -> Unit, clear: () -> Unit, swap: () -> Unit,
    onSource: (AppLanguage) -> Unit, onTarget: (AppLanguage) -> Unit, openPhoto: () -> Unit,
    favorite: () -> Unit, travel: () -> Unit, speed: () -> Unit
) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("YO", fontWeight = FontWeight.Bold); LanguageSelector(source, onSource) }
            Spacer(Modifier.width(8.dp)); FilledIconButton(onClick = swap) { Icon(Icons.Default.SwapHoriz, "Cambiar idiomas") }; Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("OTRA PERSONA", fontWeight = FontWeight.Bold); LanguageSelector(target, onTarget) }
        }
        ConversationCard(source, sourceText, true, { speak(sourceText, source) }, { copyText(sourceText) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilledIconButton(onClick = { startListening(source, target) }, modifier = Modifier.size(58.dp)) { Icon(Icons.Default.Mic, "Hablar") }
        }
        ConversationCard(target, targetText, false, { speak(targetText, target) }, { copyText(targetText) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = openPhoto, modifier = Modifier.weight(1f)) { Text("📸 Foto") }
            OutlinedButton(onClick = speed, modifier = Modifier.weight(1f)) { Text("🔊 Voz") }
            OutlinedButton(onClick = travel, modifier = Modifier.weight(1f)) { Text("🧳 Viaje") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = favorite, enabled = sourceText.isNotBlank() && targetText.isNotBlank(), modifier = Modifier.weight(1f)) { Text("⭐ Favorito") }
            OutlinedButton(onClick = clear, modifier = Modifier.weight(1f)) { Text("Limpiar") }
        }
        AdMobBanner()
    }
}

@Composable
private fun SavedScreen(title: String, items: List<String>, remove: (String) -> Unit) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        if (items.isEmpty()) Text("Todavía no hay nada aquí.", color = Color(0xFF71839B))
        items.forEach { item ->
            val p = item.split("|")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    if (p.size >= 4) { Text(p[0] + " → " + p[1], fontWeight = FontWeight.Bold); Text(p[2]); Text(p[3], color = Color(0xFF008B72)) }
                    else Text(item)
                    TextButton(onClick = { remove(item) }) { Text("Eliminar") }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(openPhoto: () -> Unit, travel: () -> Unit, speed: () -> Unit) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Más funciones", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        FeatureCard("📸 Traducir desde fotos", "Detecta texto de una imagen y lo traduce.", openPhoto)
        FeatureCard("🧳 Modo viaje", "Frases rápidas para compras, hotel, transporte y emergencias.", travel)
        FeatureCard("🔊 Velocidad de voz", "Lenta, normal o rápida.", speed)
        FeatureCard("📴 Frases sin conexión", "Incluye frases esenciales guardadas dentro de la app.", travel)
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, action: () -> Unit) {
    Card(onClick = action, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Color(0xFF71839B)) }
    }
}

private val travelPhrases = listOf(
    "Hola" to "你好", "¿Cuánto cuesta?" to "多少钱？", "¿Dónde está el baño?" to "洗手间在哪里？",
    "Necesito ayuda" to "我需要帮助", "Gracias" to "谢谢", "Por favor" to "请",
    "¿Dónde está el hotel?" to "酒店在哪里？", "Necesito un médico" to "我需要医生",
    "¿Dónde está el aeropuerto?" to "机场在哪里？"
)

@Composable
private fun TravelDialog(speak: (String, AppLanguage) -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, confirmButton = { TextButton(onClick = close) { Text("Cerrar") } },
        title = { Text("🧳 Modo viaje") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            travelPhrases.forEach { pair -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(pair.first, fontWeight = FontWeight.Bold); Text(pair.second, color = Color(0xFF008B72)) }
                TextButton(onClick = { speak(pair.first, languages[0]) }) { Text("🔊") }
            }}
        }})
}

@Composable
private fun SpeedDialog(setRate: (Float) -> Unit, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, confirmButton = { TextButton(onClick = close) { Text("Cerrar") } },
        title = { Text("🔊 Velocidad de voz") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { setRate(.75f); close() }, Modifier.fillMaxWidth()) { Text("Lenta") }
            Button(onClick = { setRate(1f); close() }, Modifier.fillMaxWidth()) { Text("Normal") }
            Button(onClick = { setRate(1.25f); close() }, Modifier.fillMaxWidth()) { Text("Rápida") }
        }})
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
