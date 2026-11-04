package com.alejandro.conversafacil

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class LiveTranslationActivity : ComponentActivity() {
    private var selectedLanguage by mutableStateOf("zh")
    private var speakEnabled by mutableStateOf(true)
    private var statusText by mutableStateOf("Selecciona el idioma de la transmisión.")
    private var showCaptions = true
    private var pendingStart = false

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) checkOverlayPermissionAndStart()
        else statusText = "Necesitamos permiso de micrófono para escuchar el LIVE."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showCaptions = intent.getBooleanExtra(LiveTranslationService.EXTRA_CAPTIONS, true)
        speakEnabled = intent.getBooleanExtra(LiveTranslationService.EXTRA_SPEAK, true)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Conversa Fácil LIVE", style = MaterialTheme.typography.headlineMedium)
                        Text("Escucha el sonido que sale por el altavoz usando el micrófono del teléfono.")
                        Text("Idioma de la transmisión", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selectedLanguage == "zh", { selectedLanguage = "zh" })
                            Text("Chino mandarín")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selectedLanguage == "en", { selectedLanguage = "en" })
                            Text("Inglés")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(speakEnabled, { speakEnabled = true })
                            Text("Subtítulos + voz en español")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(!speakEnabled, { speakEnabled = false })
                            Text("Solo subtítulos")
                        }
                        Text(statusText, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { requestStart() }, modifier = Modifier.fillMaxWidth()) {
                            Text("INICIAR TRADUCCIÓN")
                        }
                        Button(onClick = { stopLiveService(); statusText = "Traducción detenida." }, modifier = Modifier.fillMaxWidth()) {
                            Text("DETENER")
                        }
                        Text(
                            "Sube el volumen del LIVE y deja que el sonido salga por el altavoz. Esta versión usa el micrófono; la captura directa del audio interno de TikTok se añadirá después.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    private fun requestStart() {
        pendingStart = true
        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun checkOverlayPermissionAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            statusText = "Autoriza «Mostrar sobre otras aplicaciones» y vuelve aquí."
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            @Suppress("DEPRECATION")
            startActivityForResult(intent, 2403)
            return
        }
        startLiveService()
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2403 && Settings.canDrawOverlays(this) && pendingStart) startLiveService()
    }

    private fun startLiveService() {
        val intent = Intent(this, LiveTranslationService::class.java).apply {
            putExtra(LiveTranslationService.EXTRA_SOURCE, selectedLanguage)
            putExtra(LiveTranslationService.EXTRA_SPEAK, speakEnabled)
            putExtra(LiveTranslationService.EXTRA_CAPTIONS, showCaptions)
        }
        ContextCompat.startForegroundService(this, intent)
        pendingStart = false
        statusText = "LIVE activo. Ahora abre TikTok y reproduce la transmisión en voz alta."
    }

    private fun stopLiveService() {
        stopService(Intent(this, LiveTranslationService::class.java))
    }
}
