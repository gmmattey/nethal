package com.nethal.feature.devices.data

import com.nethal.core.discovery.NetworkEnvironmentReader
import com.nethal.core.discovery.SsdpDiscoverer
import com.nethal.core.discovery.SsdpResponse
import com.nethal.core.protocol.PrivateIpRanges
import com.nethal.feature.devices.domain.ArpEntry
import com.nethal.feature.devices.domain.ArpTableParser
import com.nethal.feature.devices.domain.LanDevice
import com.nethal.feature.devices.domain.LanDeviceClassifier
import com.nethal.feature.devices.domain.LanDeviceScanner
import com.nethal.feature.devices.domain.LanScanResult
import com.nethal.feature.devices.domain.OuiVendorResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Scan multi-fonte da LAN local (issue #105) — três fontes combinadas, cada uma cobrindo a
 * lacuna da outra:
 *
 * 1. **Varredura ativa por sub-rede**: tenta uma conexão TCP curta (porta 80) em cada host da
 *    sub-rede local. O objetivo não é abrir a porta — é forçar o kernel a resolver o endereço
 *    ARP do host, mesmo que a conexão em si falhe/dê timeout (a resolução ARP acontece na
 *    camada de rede, antes/independente do resultado da camada de transporte).
 * 2. **Tabela ARP do kernel** (`/proc/net/arp`, via [ArpTableReader]): lida depois da varredura
 *    ativa, pega tanto os hosts que acabamos de forçar quanto qualquer outro que já tenha
 *    trocado pacote com o roteador antes (mesmo sem porta aberta nenhuma).
 * 3. **SSDP** (reaproveitando [SsdpDiscoverer] pure-JVM já existente em `:core:discovery`, sem
 *    duplicar a heurística de M-SEARCH): identifica alguns tipos de dispositivo por
 *    header `SERVER`, mesmo sem MAC/hostname.
 *
 * mDNS (jmDNS + `MulticastLock`) foi deliberadamente deixado de fora desta primeira versão — ver
 * decisão de arquitetura no PR da issue #105: ganho de cobertura vs. complexidade/permissão
 * adicional (`CHANGE_WIFI_MULTICAST_STATE`) não comprovado ainda, e as três fontes acima já
 * atendem ao critério mínimo de aceite (IP, MAC quando disponível, fabricante OUI, tipo básico).
 */
class DefaultLanDeviceScanner(
    private val networkEnvironmentReader: NetworkEnvironmentReader,
    private val ssdpDiscoverer: SsdpDiscoverer,
    private val arpTableReader: ArpTableReader = ProcNetArpTableReader(),
    private val probeTimeoutMillis: Int = 250,
    private val probeConcurrency: Int = 32,
    private val maxHostsToScan: Int = 254,
) : LanDeviceScanner {

    override suspend fun scan(): LanScanResult {
        val environment = networkEnvironmentReader.read()
        val localIp = environment?.localIp
        val gatewayIp = environment?.gatewayIp
        val prefixLength = environment?.subnetPrefixLength

        if (environment == null || !environment.isWifi || localIp == null || prefixLength == null) {
            return LanScanResult.NoNetwork
        }
        // Falha segura: só varre sub-rede privada conhecida (RFC 1918) — mesma restrição usada
        // como guarda de SSRF no restante do NetHAL (`PrivateIpRanges`, `HttpTransportIpGuard`).
        if (!PrivateIpRanges.isPrivate(localIp)) {
            return LanScanResult.NoNetwork
        }

        return coroutineScope {
            val ssdpDeferred = async { runCatching { ssdpDiscoverer.discover() }.getOrDefault(emptyList()) }

            activeProbe(subnetHostAddresses(localIp, prefixLength, maxHostsToScan))
            // Pequena folga para o kernel terminar de popular /proc/net/arp após os probes.
            delay(200)

            val arpEntries = withContext(Dispatchers.IO) {
                arpTableReader.readRawTable()?.let(ArpTableParser::parse).orEmpty()
            }
            val ssdpResponses = ssdpDeferred.await()

            LanScanResult.Success(
                mergeSources(
                    localIp = localIp,
                    gatewayIp = gatewayIp,
                    arpEntries = arpEntries,
                    ssdpResponses = ssdpResponses,
                ),
            )
        }
    }

    private suspend fun activeProbe(hostAddresses: List<String>) {
        val semaphore = Semaphore(probeConcurrency)
        coroutineScope {
            hostAddresses.map { ip ->
                async(Dispatchers.IO) { semaphore.withPermit { attemptConnect(ip) } }
            }.awaitAll()
        }
    }

    /**
     * Só precisa disparar a resolução ARP do kernel — sucesso, recusa (RST) ou timeout da
     * conexão em si são irrelevantes aqui, o objetivo não é abrir porta nenhuma. Porta 80 por
     * ser a mais comumente aberta em equipamento doméstico (aumenta a chance de RST rápido em
     * vez de timeout completo, mas não muda a corretude se não estiver).
     */
    private fun attemptConnect(ip: String) {
        runCatching {
            Socket().use { socket -> socket.connect(InetSocketAddress(ip, 80), probeTimeoutMillis) }
        }
    }

    private fun subnetHostAddresses(localIp: String, prefixLength: Int, maxHosts: Int): List<String> {
        // Nunca varre além de um /24 (254 hosts) — sub-redes maiores têm só o bloco /24 que
        // contém o próprio IP local coberto; limitação conhecida, documentada no PR da #105.
        val effectivePrefix = prefixLength.coerceAtLeast(24)
        val octets = localIp.split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 } || effectivePrefix >= 32) return emptyList()

        val networkInt = (octets[0] shl 24) or (octets[1] shl 16) or (octets[2] shl 8) or octets[3]
        val mask = -1 shl (32 - effectivePrefix)
        val networkBase = networkInt and mask
        val hostCount = (1 shl (32 - effectivePrefix)).coerceAtMost(maxHosts + 2)

        if (hostCount < 3) return emptyList()

        return (1 until hostCount - 1).map { offset -> intToIp(networkBase + offset) }
            .filter { it != localIp }
    }

    private fun intToIp(value: Int): String =
        "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"

    private suspend fun mergeSources(
        localIp: String,
        gatewayIp: String?,
        arpEntries: List<ArpEntry>,
        ssdpResponses: List<SsdpResponse>,
    ): List<LanDevice> = coroutineScope {
        val macByIp = arpEntries.associate { it.ipAddress to it.macAddress }
        val ssdpIps = ssdpResponses.map { it.sourceIp }

        val discoveredIps = (macByIp.keys + ssdpIps + listOfNotNull(gatewayIp))
            .filterTo(LinkedHashSet()) { it != localIp && PrivateIpRanges.isPrivate(it) }

        val hostnameByIp = discoveredIps.associateWith { ip ->
            async(Dispatchers.IO) { resolveHostname(ip) }
        }.mapValues { it.value.await() }

        discoveredIps.map { ip ->
            val mac = macByIp[ip]
            val hostname = hostnameByIp[ip]
            val vendor = mac?.let(OuiVendorResolver::vendorFor)
            val isGateway = ip == gatewayIp

            LanDevice(
                ipAddress = ip,
                macAddress = mac,
                hostname = hostname,
                vendor = vendor,
                deviceType = LanDeviceClassifier.classify(hostname, vendor, isGateway),
            )
        }.sortedWith(
            compareByDescending<LanDevice> { it.ipAddress == gatewayIp }
                .thenBy { ipSortKey(it.ipAddress) },
        )
    }

    /** Chave de ordenação Comparable (zero-padded por octeto) equivalente à ordem numérica do IP. */
    private fun ipSortKey(ip: String): String =
        ip.split(".").joinToString(".") { it.toIntOrNull()?.toString()?.padStart(3, '0') ?: it }

    /**
     * Best-effort: funciona quando o DNS da rede (tipicamente o próprio roteador, via
     * `dnsmasq`/DHCP) expõe hostname por PTR reverso — comum em roteador doméstico, não
     * garantido. `getCanonicalHostName()` devolve o próprio IP como string quando não há PTR;
     * filtrado para não virar um "hostname" falso.
     */
    private suspend fun resolveHostname(ip: String): String? = withTimeoutOrNull(300) {
        runCatching { InetAddress.getByName(ip).canonicalHostName }
            .getOrNull()
            ?.takeIf { it != ip }
    }
}
