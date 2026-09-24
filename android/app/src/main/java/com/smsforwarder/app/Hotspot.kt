package com.smsforwarder.app

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Finds the devices currently connected to this phone's hotspot. */
object Hotspot {

    data class Client(val ip: String, val mac: String?)

    private val AP_NAME = Regex("(swlan|softap|ap)\\d+|wlan[1-9]")

    private fun apInterface(): NetworkInterface? =
        NetworkInterface.getNetworkInterfaces().toList().firstOrNull { nif ->
            nif.isUp && AP_NAME.matches(nif.name) && nif.interfaceAddresses.any { it.address is Inet4Address }
        }

    /** True while this phone's own hotspot is running. */
    fun isOn(): Boolean = try { apInterface() != null } catch (e: Exception) { false }

    /** http addresses (without scheme) of the hotspot interface. */
    fun addresses(): List<String> = try {
        apInterface()?.interfaceAddresses?.map { it.address }?.filterIsInstance<Inet4Address>()?.mapNotNull { it.hostAddress } ?: emptyList()
    } catch (e: Exception) { emptyList() }

    /** True if [addr] belongs to the hotspot's own subnet. */
    fun isFromHotspot(addr: InetAddress): Boolean {
        if (addr !is Inet4Address) return false
        val ia = apInterface()?.interfaceAddresses?.firstOrNull { it.address is Inet4Address } ?: return false
        val prefix = ia.networkPrefixLength.toInt()
        val a = java.nio.ByteBuffer.wrap(addr.address).int
        val b = java.nio.ByteBuffer.wrap(ia.address.address).int
        val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
        return (a and mask) == (b and mask)
    }

    /** Blocking (~3 s): pokes every address on the hotspot subnet, then keeps only hosts that really answer. */
    fun clients(): List<Client> {
        val nif = apInterface() ?: return emptyList()
        val self = nif.interfaceAddresses.first { it.address is Inet4Address }.address.hostAddress ?: return emptyList()
        val base = self.substringBeforeLast('.')
        val pool = Executors.newFixedThreadPool(24)
        try {
            // Sending any packet makes the kernel ARP for the address, which fills the neighbour table.
            val pokes = (1..254).map { i -> "$base.$i" }.filter { it != self }.map { ip ->
                Callable {
                    try {
                        DatagramSocket().use { it.send(DatagramPacket(ByteArray(1), 1, InetAddress.getByName(ip), 9)) }
                    } catch (e: Exception) { /* unreachable, ignore */ }
                }
            }
            pool.invokeAll(pokes, 4, TimeUnit.SECONDS)
            Thread.sleep(1200)

            val macs = neighbours(nif.name)
            val candidates = if (macs.isNotEmpty()) macs.keys.toList() else (1..254).map { "$base.$it" }.filter { it != self }

            // A stale table entry can outlive the device, so confirm each one actually answers.
            val checks = candidates.map { ip ->
                Callable { if (InetAddress.getByName(ip).isReachable(1000)) Client(ip, macs[ip]) else null }
            }
            return pool.invokeAll(checks, 8, TimeUnit.SECONDS)
                .mapNotNull { f -> try { f.get() } catch (e: Exception) { null } }
                .sortedBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
        } finally {
            pool.shutdownNow()
        }
    }

    /** ip -> MAC for entries the kernel currently knows on [iface]. */
    private fun neighbours(iface: String): Map<String, String> {
        val out = HashMap<String, String>()
        try {
            val p = ProcessBuilder("ip", "neigh", "show", "dev", iface).redirectErrorStream(true).start()
            p.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val parts = line.trim().split(Regex("\\s+"))
                    val lladdr = parts.indexOf("lladdr")
                    val ip = parts.firstOrNull() ?: continue
                    if (lladdr < 0 || lladdr + 1 >= parts.size || !ip.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) continue
                    if (parts.last() == "FAILED" || parts.last() == "INCOMPLETE") continue
                    out[ip] = parts[lladdr + 1].lowercase()
                }
            }
            p.waitFor(2, TimeUnit.SECONDS)
        } catch (e: Exception) { /* fall back to a plain sweep */ }
        return out
    }
}
