package com.alejandro.conversafacil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Pantalla inicial independiente para Conversa Fácil LIVE.
 * Esta primera integración añade el acceso y las preferencias visuales;
 * la captura de audio y el motor de traducción en streaming se implementarán
 * en una fase posterior, sin modificar el traductor existente.
 */
@Composable
fun LiveFeatureDialog(onDismiss: () -> Unit, onStartLive: () -> Unit) {
    var showCaptions by remember { mutableStateOf(true) }
    var speakTranslation by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFF7F8FC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Conversa Fácil LIVE",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF5D35B0)
                )
                Text(
                    "Entiende transmisiones en otros idiomas sin salir de TikTok.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF526078)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7FA)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("CHINO / INGLÉS  →  ESPAÑOL", fontWeight = FontWeight.Bold, color = Color(0xFF59349D))
                        Text(
                            "Subtítulos en español y lectura en voz alta de la traducción.",
                            color = Color(0xFF514568),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Card(\n                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF)),\n                    shape = RoundedCornerShape(16.dp)\n                ) {\n                    Text(\n                        "Primera versión: utiliza el micrófono del teléfono para escuchar el LIVE por el altavoz. Mantén el volumen audible. La captura directa del audio interno de TikTok será una fase posterior.",\n                        modifier = Modifier.padding(13.dp),\n                        color = Color(0xFF31517A),\n                        style = MaterialTheme.typography.bodySmall\n                    )\n                }\n\n                Text("Qué quieres recibir", fontWeight = FontWeight.Bold, color = Color(0xFF24324A))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showCaptions, onCheckedChange = { showCaptions = it })
                    Text("Subtítulos en español")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = speakTranslation, onCheckedChange = { speakTranslation = it })
                    Text("Escuchar la traducción en español")
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3DB)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "Primera etapa: esta opción ya está añadida como sección independiente. La captura continua, los subtítulos en vivo y la voz sincronizada todavía requieren integración y pruebas con TikTok. Android puede bloquear el audio interno de algunas aplicaciones; por eso también tendremos el modo micrófono.",
                        modifier = Modifier.padding(13.dp),
                        color = Color(0xFF76531C),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    (if (showCaptions) "Subtítulos activados" else "Subtítulos desactivados") + " · " +
                        (if (speakTranslation) "Voz activada" else "Voz desactivada"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF667085),
                    textAlign = TextAlign.Start
                )

                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onStartLive,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Configurar e iniciar LIVE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
