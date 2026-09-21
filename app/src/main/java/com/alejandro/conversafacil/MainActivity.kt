package com.alejandro.conversafacil

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class AppLanguage(val name:String,val flag:String,val code:String,val speechLocale:String)
private val languages=listOf(
 AppLanguage("Español","🇪🇸","es","es-ES"),AppLanguage("Inglés","🇺🇸","en","en-US"),AppLanguage("Chino","🇨🇳","zh","zh-CN"),
 AppLanguage("Hindi","🇮🇳","hi","hi-IN"),AppLanguage("Portugués","🇧🇷","pt","pt-BR"),AppLanguage("Francés","🇫🇷","fr","fr-FR"),
 AppLanguage("Árabe","🇸🇦","ar","ar-SA"),AppLanguage("Ruso","🇷🇺","ru","ru-RU"),AppLanguage("Japonés","🇯🇵","ja","ja-JP"),
 AppLanguage("Alemán","🇩🇪","de","de-DE"),AppLanguage("Coreano","🇰🇷","ko","ko-KR"),AppLanguage("Italiano","🇮🇹","it","it-IT"),
 AppLanguage("Turco","🇹🇷","tr","tr-TR"),AppLanguage("Vietnamita","🇻🇳","vi","vi-VN"),AppLanguage("Indonesio","🇮🇩","id","id-ID")
)

class MainActivity:ComponentActivity(){
 private val speechResult=mutableStateOf(""); private val translationResult=mutableStateOf("")
 private var tts:TextToSpeech?=null; private var lastSource="es"; private var lastTarget="zh"
 private val translators=ConcurrentHashMap<String,com.google.mlkit.nl.translate.Translator>()
 private val launcher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->
  r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let{speechResult.value=it;translate(it,lastSource,lastTarget)}
 }
 override fun onCreate(b:Bundle?){super.onCreate(b);tts=TextToSpeech(this,null);setContent{App(speechResult.value,translationResult.value,::listen,::speak,::copy)}}
 private fun listen(s:AppLanguage,t:AppLanguage){lastSource=s.code;lastTarget=t.code;launcher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,s.speechLocale);putExtra(RecognizerIntent.EXTRA_PROMPT,"Habla en ${s.name}")})}
 private fun tr(s:String,t:String)=translators.getOrPut("$s-$t"){Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(s).setTargetLanguage(t).build())}
 private fun translate(x:String,s:String,t:String){if(x.isBlank()||s==t){translationResult.value=if(s==t)x else "";return};tr(s,t).downloadModelIfNeeded(DownloadConditions.Builder().build()).addOnSuccessListener{tr(s,t).translate(x).addOnSuccessListener{translationResult.value=it}.addOnFailureListener{translationResult.value="No se pudo traducir. Inténtalo de nuevo."}}.addOnFailureListener{translationResult.value="Preparando el idioma… inténtalo de nuevo en un momento."}}
 private fun speak(x:String,l:AppLanguage){if(x.isBlank())return;val status=tts?.setLanguage(Locale.forLanguageTag(l.speechLocale));if(status!=TextToSpeech.LANG_MISSING_DATA&&status!=TextToSpeech.LANG_NOT_SUPPORTED)tts?.speak(x,TextToSpeech.QUEUE_FLUSH,null,"voz")}
 private fun copy(x:String){if(x.isBlank())return;(getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Conversa Fácil",x))}
 override fun onDestroy(){translators.values.forEach{it.close()};tts?.shutdown();super.onDestroy()}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Selector(selected:AppLanguage,onSelect:(AppLanguage)->Unit){
 var open by remember{mutableStateOf(false)}
 Box{
  Surface(onClick={open=true},shape=RoundedCornerShape(20.dp),color=Color.White,shadowElevation=2.dp){
   Row(Modifier.padding(horizontal=14.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){
    Text(selected.flag,style=MaterialTheme.typography.titleLarge);Spacer(Modifier.width(7.dp));Text(selected.name,fontWeight=FontWeight.Bold,color=Color(0xFF173B70))
   }
  }
  DropdownMenu(open,{open=false},modifier=Modifier.heightIn(max=430.dp)){languages.forEach{l->DropdownMenuItem(text={Text("${l.flag}  ${l.name}",fontWeight=if(l.code==selected.code)FontWeight.Bold else FontWeight.Normal)},onClick={onSelect(l);open=false})}}
 }
}

@Composable private fun App(spoken:String,translated:String,listen:(AppLanguage,AppLanguage)->Unit,speak:(String,AppLanguage)->Unit,copy:(String)->Unit){
 var source by remember{mutableStateOf(languages[0])};var target by remember{mutableStateOf(languages[2])};var sourceText by remember{mutableStateOf("")};var targetText by remember{mutableStateOf("")};var listening by remember{mutableStateOf(false)}
 val scale by animateFloatAsState(if(listening)1.08f else 1f,tween(180),label="mic")
 LaunchedEffect(spoken){if(spoken.isNotBlank()){sourceText=spoken;listening=false}}
 LaunchedEffect(translated){if(translated.isNotBlank())targetText=translated}
 fun clear(){sourceText="";targetText=""}
 fun swap(){val a=source;source=target;target=a;val x=sourceText;sourceText=targetText;targetText=x}
 MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF315BEA),secondary=Color(0xFF00A98F),background=Color(0xFFF5F7FB))){
  Surface(Modifier.fillMaxSize(),color=Color(0xFFF5F7FB)){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){
   Header()
   Column(Modifier.padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
     Selector(source){if(it.code!=target.code){source=it;clear()}}
     Spacer(Modifier.width(8.dp));FilledIconButton(onClick=::swap,modifier=Modifier.size(42.dp)){Icon(Icons.Default.SwapHoriz,"Cambiar idiomas")}
     Spacer(Modifier.width(8.dp));Selector(target){if(it.code!=source.code){target=it;clear()}}
    }
    SpeechCard(source,sourceText,listening,scale){listening=true;listen(source,target)}{speak(sourceText,source)}{copy(sourceText)}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){HorizontalDivider(Modifier.weight(1f));Text("  Traducción  ",fontWeight=FontWeight.Bold,color=Color(0xFF70829B));HorizontalDivider(Modifier.weight(1f))}
    ResultCard(target,targetText,speak,copy)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){OutlinedButton(onClick=::clear,enabled=sourceText.isNotBlank()||targetText.isNotBlank(),shape=RoundedCornerShape(16.dp)){Text("Nueva conversación")}}
    Text("15 idiomas • Traducción rápida • Voz",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,color=Color(0xFF8291A5),style=MaterialTheme.typography.bodySmall)
    Ad()
   }
   Spacer(Modifier.height(16.dp))
  }}
 }
}

@Composable private fun Header(){
 Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF315BEA),Color(0xFF7144E8)))).padding(horizontal=20.dp,vertical=24.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   Surface(Modifier.size(50.dp),shape=RoundedCornerShape(16.dp),color=Color.White.copy(.18f)){Box(contentAlignment=Alignment.Center){Text("CF",color=Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge)}}
   Spacer(Modifier.width(13.dp));Column{Text("Conversa Fácil",color=Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.headlineSmall);Text("Habla • Traduce • Entiende",color=Color.White.copy(.86f))}
  }
 }
}

@Composable private fun SpeechCard(l:AppLanguage,text:String,listening:Boolean,scale:State<Float>,onMic:()->Unit,onSpeak:()->Unit,onCopy:()->Unit){
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(Color.White),elevation=CardDefaults.cardElevation(3.dp)){
  Column(Modifier.padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${l.flag}  ${l.name}",fontWeight=FontWeight.Bold,color=Color(0xFF315BEA));Spacer(Modifier.weight(1f));IconButton(onSpeak,enabled=text.isNotBlank()){Icon(Icons.Default.VolumeUp,"Escuchar")};IconButton(onCopy,enabled=text.isNotBlank()){Icon(Icons.Default.ContentCopy,"Copiar")}}
   Text(text.ifBlank{"Toca el micrófono para hablar"},style=MaterialTheme.typography.titleLarge,textAlign=TextAlign.Center,color=if(text.isBlank())Color(0xFF8998AB) else Color(0xFF203A5F))
   Surface(Modifier.size(104.dp).scale(scale),CircleShape,color=Color(0xFFEAF0FF),shadowElevation=2.dp){IconButton(onMic){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Surface(Modifier.size(72.dp),CircleShape,color=Color(0xFF315BEA),shadowElevation=7.dp){Box(contentAlignment=Alignment.Center){Icon(Icons.Default.Mic,"Hablar",tint=Color.White,modifier=Modifier.size(34.dp))}}}}}
   AnimatedVisibility(listening){Text("Escuchando…",fontWeight=FontWeight.Bold,color=Color(0xFF315BEA))}
  }
 }
}

@Composable private fun ResultCard(l:AppLanguage,text:String,speak:(String,AppLanguage)->Unit,copy:(String)->Unit){
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(Color(0xFFEAFBF6)),elevation=CardDefaults.cardElevation(2.dp)){
  Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Text("${l.flag}  ${l.name}",fontWeight=FontWeight.Bold,color=Color(0xFF008F75));Spacer(Modifier.weight(1f));IconButton({copy(text)},enabled=text.isNotBlank()){Icon(Icons.Default.ContentCopy,"Copiar")};IconButton({speak(text,l)},enabled=text.isNotBlank()){Icon(Icons.Default.VolumeUp,"Escuchar")}}
   AnimatedVisibility(text.isNotBlank()){Text(text,style=MaterialTheme.typography.headlineSmall,color=Color(0xFF21405E))}
   if(text.isBlank())Text("La traducción aparecerá aquí…",style=MaterialTheme.typography.titleLarge,color=Color(0xFF8092A7))
  }
 }
}

@Composable private fun Ad(){AndroidView(Modifier.fillMaxWidth().height(50.dp),factory={c->AdView(c).apply{setAdSize(AdSize.BANNER);adUnitId="ca-app-pub-5236094250280846/9454238594";loadAd(AdRequest.Builder().build())}})}
