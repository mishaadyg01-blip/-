package ru.vlessspeedscan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF0B0F14)
private val Panel = Color(0xFF151C24)
private val Accent = Color(0xFF58D6A3)
private data class Node(val name: String, val protocol: String, val status: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Accent)) { HomeScreen() } }
    }
}

@Composable
private fun HomeScreen() {
    var tab by remember { mutableStateOf(0) }
    var subscription by remember { mutableStateOf("") }
    val nodes = remember { listOf(Node("Пока нет серверов", "Добавьте ссылку на подписку", "Ожидание")) }
    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        Text("VLESS SPEED SCAN", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("ПРОВЕРКА И УПРАВЛЕНИЕ СЕРВЕРАМИ", color = Color.Gray, fontSize = 10.sp)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Серверы", "Подписки").forEachIndexed { i, label -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) }) }
        }
        Spacer(Modifier.height(14.dp))
        if (tab == 0) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Проверка серверов", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Без включения системного VPN", color = Accent, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { }, modifier = Modifier.fillMaxWidth(), enabled = false) { Text("Проверка будет добавлена") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("СПИСОК СЕРВЕРОВ", color = Color.Gray, fontSize = 11.sp)
            LazyColumn { items(nodes) { node -> Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(node.name, color = Color.White); Text(node.protocol, color = Color.Gray, fontSize = 12.sp) }
                    Text(node.status, color = Color.Gray, fontSize = 12.sp)
                }
            } } }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Мои подписки", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text("Добавление нескольких подписок и их обновление будет подключено следующим этапом.", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = subscription, onValueChange = { subscription = it }, modifier = Modifier.fillMaxWidth(), label = { Text("URL подписки") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { }, enabled = subscription.startsWith("http")) { Text("Добавить подписку") }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Версия 0.1.0 • Каркас интерфейса", color = Color.Gray, fontSize = 11.sp)
    }
}
