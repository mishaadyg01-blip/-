package ru.vlessspeedscan.app

import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject

data class ProxyProbeResult(
    val latencyMs: Long? = null,
    val error: String? = null
)

object LibXrayBridge {
    private const val API_VERSION = 3
    private const val TEST_URL = "https://cp.cloudflare.com/"
    private const val TIMEOUT_SECONDS = 8

    fun pingServers(servers: List<ServerProfile>): Map<String, ProxyProbeResult> {
        val converted = servers.map { server ->
            server.id to convertLink(server.rawLink)
        }
        val results = linkedMapOf<String, ProxyProbeResult>()

        converted.chunked(5).forEach { batch ->
            val configs = JSONArray()
            batch.forEach { (_, config) ->
                val item = JSONObject()
                if (config != null) item.put("xrayJson", config)
                configs.put(item)
            }

            val request = JSONObject()
                .put("apiVersion", API_VERSION)
                .put("method", "pingBatch")
                .put("payload", JSONObject()
                    .put("configs", configs)
                    .put("timeout", TIMEOUT_SECONDS)
                    .put("url", TEST_URL))

            val response = invoke(request)
            if (!response.optBoolean("success")) {
                val message = response.optString("error", "Ошибка проверки Xray")
                batch.forEach { (id, _) -> results[id] = ProxyProbeResult(error = message) }
                return@forEach
            }

            val rows = response.optJSONObject("data")?.optJSONArray("results")
            batch.forEachIndexed { index, pair ->
                val (id, config) = pair
                if (config == null) {
                    results[id] = ProxyProbeResult(error = "Не удалось преобразовать ссылку")
                } else {
                    val row = rows?.optJSONObject(index)
                    if (row != null && row.optBoolean("success")) {
                        results[id] = ProxyProbeResult(latencyMs = row.optLong("delay"))
                    } else {
                        results[id] = ProxyProbeResult(
                            error = row?.optString("error")?.takeIf { it.isNotBlank() }
                                ?: "Нет ответа через прокси"
                        )
                    }
                }
            }
        }
        return results
    }

    private fun convertLink(link: String): String? {
        val request = JSONObject()
            .put("apiVersion", API_VERSION)
            .put("method", "convertShareLinksToXrayJson")
            .put("payload", JSONObject().put("text", link))
        val response = invoke(request)
        if (!response.optBoolean("success")) return null
        val data = response.optJSONObject("data") ?: return null
        val outbounds = data.optJSONArray("outbounds") ?: return null
        return JSONObject().put("outbounds", outbounds).toString()
    }

    private fun invoke(request: JSONObject): JSONObject {
        return try {
            JSONObject(LibXray.invoke(request.toString()))
        } catch (e: Exception) {
            JSONObject()
                .put("success", false)
                .put("error", e.localizedMessage ?: "Ошибка вызова Xray")
        }
    }
}
