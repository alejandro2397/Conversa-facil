package com.alejandro.conversafacil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

    val phrases = listOf(
        "Hola" to "你好",
        "¿Cómo está?" to "你好吗？",
        "¿Cuánto cuesta?" to "多少钱？",
        "Quiero comprar" to "我想买",
        "¿Cuántos necesita?" to "您需要多少？",
        "¿Tiene este producto?" to "你有这个产品吗？",
        "Gracias" to "谢谢",
        "Espere un momento" to "请等一下"
    )

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("💬", style = MaterialTheme.typography.displaySmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("Conversa Fácil", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("Habla sin fronteras", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Text("🇸🇻 Español  ↔  🇨🇳 中文", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("ESPAÑOL", style = MaterialTheme.typography.labelLarge)
                        Text(spanishText.ifEmpty { "Elige una frase" }, style = MaterialTheme.typography.headlineSmall)
                        HorizontalDivider()
                        Text("中文", style = MaterialTheme.typography.labelLarge)
                        Text(chineseText.ifEmpty { "选择一句话" }, style = MaterialTheme.typography.headlineSmall)
                    }
                }

                Text("Toca lo que quieres decir", style = MaterialTheme.typography.titleLarge)
                phrases.forEach { (es, zh) ->
                    Button(
                        onClick = { spanishText = es; chineseText = zh },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("$es    •    $zh") }
                }

                OutlinedButton(onClick = { spanishText = ""; chineseText = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Limpiar")
                }
            }
        }
    }
}
