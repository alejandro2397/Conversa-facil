package com.alejandro.conversafacil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ConversaFacilApp() }
    }
}

@Composable
private fun ConversaFacilApp() {
    var spanishText by remember { mutableStateOf("") }
    var chineseText by remember { mutableStateOf("") }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Conversa Fácil", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Español 🇸🇻  ↔  中文 🇨🇳",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                LanguageCard("🇸🇻 Yo hablo español", spanishText.ifEmpty { "Pulsa una frase para empezar" })
                LanguageCard("🇨🇳 La otra persona habla chino", chineseText.ifEmpty { "这里会 aparecerá la traducción" })
                Text("Frases rápidas", style = MaterialTheme.typography.titleLarge)
                QuickRow("Hola", "你好", { spanishText = "Hola"; chineseText = "你好" }, { spanishText = "你好"; chineseText = "Hola" })
                QuickRow("¿Cuánto cuesta?", "多少钱？", { spanishText = "¿Cuánto cuesta?"; chineseText = "多少钱？" }, { spanishText = "多少钱？"; chineseText = "¿Cuánto cuesta?" })
                QuickRow("Quiero comprar", "我想买", { spanishText = "Quiero comprar"; chineseText = "我想买" }, { spanishText = "我想买"; chineseText = "Quiero comprar" })
                QuickRow("Gracias", "谢谢", { spanishText = "Gracias"; chineseText = "谢谢" }, { spanishText = "谢谢"; chineseText = "Gracias" })
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { spanishText = ""; chineseText = "" },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Limpiar") }
            }
        }
    }
}

@Composable
private fun LanguageCard(title: String, text: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun QuickRow(
    spanish: String,
    chinese: String,
    onSpanish: () -> Unit,
    onChinese: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSpanish, modifier = Modifier.weight(1f)) { Text(spanish) }
        Button(onClick = onChinese, modifier = Modifier.weight(1f)) { Text(chinese) }
    }
}
