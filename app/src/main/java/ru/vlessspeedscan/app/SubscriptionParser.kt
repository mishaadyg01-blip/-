package ru.vlessspeedscan.app

import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Parsed connection entry. Raw link is kept locally for a later engine adapter. */
data class ServerProfile(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val rawLink: String
)

data class ParseResult(
    val profiles: List<ServerProfile>,
    val rejectedLines: Int
)

/**
 * Parses common subscription formats without starting a VPN or making network requests.
 * This is deliberately only the import layer; it does not validate whether a server works.
 */
object SubscriptionParser {
    fun parse(text: String): ParseResult {
        val decoded = decodeSubscription(text.trim())
        val accepted = mutableListOf<ServerProfile>()
        var rejected = 0

        decoded.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                try {
                    val profile = when {
                        line.startsWith("vless://", true) -> parseUriProfile(line, "VLESS")
                        line.startsWith("trojan://", true) -> parseUriProfile(line, "Trojan")
                        line.startsWith("vmess://", true) -> parseVmess(line)
                        line.startsWith("ss://", true) -> parseShadowsocks(line)
                        else -> null
                    }
                    if (profile == null) rejected++ else accepted += profile
                } catch (_: Exception) {
                    rejected++
                }
            }

        // Remove duplicate connection links while preserving the first occurrence.
        val unique = accepted.distinctBy { it.rawLink }
        return ParseResult(unique, rejected + (accepted.size - unique.size))
    }

    private fun decodeSubscription(input: String): String {
        if (input.contains("://")) return input
        val compact = input.filterNot { it.isWhitespace() }
        val candidates = listOf(Base64.NO_WRAP, Base64.URL_SAFE or Base64.NO_WRAP)
        for (flags in candidates) {
            try {
                val value = String(Base64.decode(compact, flags), StandardCharsets.UTF_8)
                if (value.contains("://")) return value
            } catch (_: IllegalArgumentException) {
                // Try the next supported Base64 alphabet.
            }
        }
        return input
    }

    private fun parseUriProfile(link: String, protocol: String): ServerProfile? {
        val uri = URI(link)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: return null
        val fragment = uri.rawFragment?.let { decode(it) }
        val user = uri.rawUserInfo.orEmpty()
        val name = fragment?.takeIf { it.isNotBlank() } ?: "$protocol • $host"
        return ServerProfile(
            id = stableId(protocol, user, host, port),
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            rawLink = link
        )
    }

    private fun parseVmess(link: String): ServerProfile? {
        val payload = link.removePrefix("vmess://").substringBefore('#')
        val jsonText = decodeBase64(payload) ?: return null
        val json = JSONObject(jsonText)
        val host = json.optString("add").takeIf { it.isNotBlank() } ?: return null
        val port = json.optString("port").toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val name = json.optString("ps").takeIf { it.isNotBlank() } ?: "VMess • $host"
        val id = json.optString("id")
        return ServerProfile(stableId("VMess", id, host, port), name, "VMess", host, port, link)
    }

    private fun parseShadowsocks(link: String): ServerProfile? {
        val withoutScheme = link.removePrefix("ss://")
        val body = withoutScheme.substringBefore('#').substringBefore('?')
        val decodedWhole = decodeBase64(body)
        val candidate = decodedWhole ?: body
        val authority = candidate.substringAfterLast('@', candidate)
        val hostPort = authority.substringAfterLast('@')
        val match = Regex("^\\[?([^]:]+)]?:(\\d{1,5})$").matchEntire(hostPort) ?: return null
        val host = match.groupValues[1]
        val port = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val label = withoutScheme.substringAfter('#', "").takeIf { it.isNotBlank() }?.let(::decode)
        val name = label ?: "Shadowsocks • $host"
        return ServerProfile(stableId("Shadowsocks", candidate.substringBefore('@'), host, port), name, "Shadowsocks", host, port, link)
    }

    private fun decodeBase64(value: String): String? {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return try {
            String(Base64.decode(padded, Base64.DEFAULT), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")

    private fun stableId(protocol: String, identity: String, host: String, port: Int): String =
        "$protocol|$identity|$host|$port"
}
