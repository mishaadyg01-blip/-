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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Bg = Color(0xFF0B0F14)
private val Panel = Color(0xFF151C24)
private val Accent = Color(0xFF58D6A3)

private data class SubscriptionEntry(
    val url: String,
    val title: String,
    val serverCount: Int,
    val message: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Accent)) {
                HomeScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    var tab by remember { mutableIntStateOf(0) }
    var subscriptionUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("Добавьте URL подписки, чтобы загрузить серверы.") }
    val subscriptions = remember { mutableStateListOf<SubscriptionEntry>() }
    val servers = remember { mutableStateListOf<ServerProfile>() }
    val proxyResults = remember { mutableStateMapOf<String, ProxyProbeResult>() }
    val scope = rememberCoroutineScope()

    suspend fun loadSubscription(url: String, isNew: Boolean) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            notice = "Укажите полный адрес, начинающийся с http:// или https://"
            return
        }
        busy = true
        notice = "Загружаю подписку…"
        try {
            val body = withContext(Dispatchers.IO) {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty("User-Agent", "VegaSpan/0.1")
                try {
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            }
            val result = SubscriptionParser.parse(body)
            if (result.profiles.isEmpty()) {
                notice = "Поддерживаемых серверов не найдено. Проверьте формат подписки."
            } else {
                if (isNew) subscriptions.removeAll { it.url == url }
                val entry = SubscriptionEntry(
                    url = url,
                    title = "Подписка ${subscriptions.size + if (isNew) 1 else 0}",
                    serverCount = result.profiles.size,
                    message = "Серверов: ${result.profiles.size}; пропущено строк: ${result.rejectedLines}"
                )
                if (isNew) subscriptions.add(entry)
                else {
                    val index = subscriptions.indexOfFirst { it.url == url }
                    if (index >= 0) subscriptions[index] = entry
                }
                servers.removeAll { existing -> result.profiles.any { it.id == existing.id } }
                servers.addAll(result.profiles)
                notice = "Загружено серверов: ${result.profiles.size}."
                subscriptionUrl = ""
            }
        } catch (e: Exception) {
            notice = "Не удалось загрузить подписку: ${e.localizedMessage ?: "ошибка сети"}"
        } finally {
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).padding(18.dp)) {
        Text("VEGASPAN", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("ПРОВЕРКА И УПРАВЛЕНИЕ СЕРВЕРАМИ", color = Color.Gray, fontSize = 10.sp)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Серверы", "Подписки").forEachIndexed { i, label ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(14.dp))
        if (tab == 0) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Проверка серверов", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Импортировано: ${servers.size}", color = Accent, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                scanning = true
                                proxyResults.clear()
                                val results = withContext(Dispatchers.IO) { LibXrayBridge.pingServers(servers.toList()) }
                                results.forEach { (id, result) -> proxyResults[id] = result }
                                scanning = false
                            }
                        },
                        enabled = servers.isNotEmpty() && !scanning,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (scanning) "Проверяю через прокси…" else "Проверить ping через прокси") }
                    Text(
                        "Проверяется HTTP-задержка через Xray-прокси. Включать VPN для проверки не нужно.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(servers.sortedWith(compareBy { proxyResults[it.id]?.latencyMs ?: Long.MAX_VALUE }), key = { it.id }) { server ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(server.name, color = Color.White, fontWeight = FontWeight.Medium)
                                Text("${server.protocol} • ${server.host}:${server.port}", color = Color.Gray, fontSize = 12.sp)
                                val result = proxyResults[server.id]
                                Text(
                                    when {
                                        result == null -> "Прокси ping: ещё не проверен"
                                        result.latencyMs != null -> "Через прокси: ${result.latencyMs} мс"
                                        else -> "Ошибка прокси: ${result.error ?: "сервер не отвечает"}"
                                    },
                                    color = if (result?.latencyMs != null) Accent else Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Добавить подписку по ссылке", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = subscriptionUrl,
                        onValueChange = { subscriptionUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL подписки") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { scope.launch { loadSubscription(subscriptionUrl.trim(), true) } },
                        enabled = !busy && subscriptionUrl.startsWith("http"),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "Загрузка…" else "Загрузить серверы") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(notice, color = Color.LightGray, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(subscriptions, key = { it.url }) { item ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(item.message, color = Color.Gray, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { scope.launch { loadSubscription(item.url, false) } },
                                    enabled = !busy
                                ) { Text("Обновить", color = Accent) }
                                TextButton(onClick = {
                                    subscriptions.remove(item)
                                    if (subscriptions.isEmpty()) servers.clear()
                                }) { Text("Удалить", color = Color(0xFFFF8A80)) }
                            }
                        }
                    }
                }
            }
        }
        Text("Статус: ${if (busy || scanning) "выполняется операция" else "готово"} • v0.3.0", color = Color.Gray, fontSize = 10.sp)
    }
}
