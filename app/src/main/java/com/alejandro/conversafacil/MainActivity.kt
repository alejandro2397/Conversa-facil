package com.alejandro.conversafacil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                Text("🇸🇻 Español  ↔  中文 🇨🇳", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("Frases rápidas", style = MaterialTheme.typography.titleLarge)
                QuickRow("Hola", "你好") { spanishText = "Hola"; chineseText = "你好" }
                QuickRow("¿Cuánto cuesta?", "多少钱？") { spanishText = "¿Cuánto cuesta?"; chineseText = "多少钱？" }
                QuickRow("Quiero comprar", "我想买") { spanishText = "Quiero comprar"; chineseText = "我想买" }
                QuickRow("Gracias", "谢谢") { spanishText = "Gracias"; chineseText = "谢谢" }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Español", style = MaterialTheme.typography.titleMedium)
                        Text(spanishText.ifEmpty { "Selecciona una frase" }, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(10.dp))
                        Text("中文", style = MaterialTheme.typography.titleMedium)
                        Text(chineseText.ifEmpty { "选择一句话" }, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                OutlinedButton(onClick = { spanishText = ""; chineseText = "" }, modifier = Modifier.fillMaxWidth()) { Text("Limpiar") }
            }
        }
    }
}

@Composable
private fun QuickRow(spanish: String, chinese: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("$spanish    •    $chinese")
    }
}
