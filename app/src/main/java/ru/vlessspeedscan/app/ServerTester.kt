package ru.vlessspeedscan.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Measures direct TCP connection establishment time to a node endpoint.
 *
 * This is a reachability diagnostic, NOT ICMP ping and NOT latency through
 * a proxy tunnel. It does not enable Android VPNService.
 */
data class TcpProbeResult(
    val latencyMs: Long?,
    val error: String? = null
)

object ServerTester {
    suspend fun testTcp(host: String, port: Int, timeoutMs: Int = 5000): TcpProbeResult =
        withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                val started = System.nanoTime()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val elapsed = (System.nanoTime() - started) / 1_000_000
                TcpProbeResult(latencyMs = elapsed)
            } catch (e: Exception) {
                TcpProbeResult(
                    latencyMs = null,
                    error = e.message ?: e.javaClass.simpleName
                )
            } finally {
                try { socket.close() } catch (_: Exception) { }
            }
        }
}
