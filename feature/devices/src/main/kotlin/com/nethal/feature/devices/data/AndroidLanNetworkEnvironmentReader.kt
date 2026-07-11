package com.nethal.feature.devices.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import com.nethal.core.discovery.NetworkEnvironment
import com.nethal.core.discovery.NetworkEnvironmentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementação Android de `NetworkEnvironmentReader` (interface pure-JVM de `:core:discovery`)
 * própria de `:feature:devices` — reaproveita o mesmo binding de
 * `ConnectivityManager`/`LinkProperties` já usado por
 * `com.nethal.lab.data.discovery.AndroidNetworkEnvironmentReader` (`:app`), mas NÃO importa essa
 * classe diretamente: um módulo `:feature:*` não pode depender de `:app` (inverteria a regra de
 * dependência única da ADR 0002, e criaria ciclo — `:app` é quem vai depender de
 * `:feature:devices` na fiação final do host de bottom nav). A duplicação real aqui é pequena e
 * deliberada — só o binding de plataforma (~35 linhas), não a lógica de scan (ARP, OUI,
 * classificação), que existe uma única vez neste módulo. Se um terceiro consumidor precisar do
 * mesmo binding, aí sim vale extrair para um módulo `:core` Android compartilhado — não faz
 * sentido criar essa camada nova para dois consumidores só (ver decisão de arquitetura no PR da
 * issue #105).
 */
class AndroidLanNetworkEnvironmentReader(
    private val context: Context,
) : NetworkEnvironmentReader {

    override suspend fun read(): NetworkEnvironment? = withContext(Dispatchers.IO) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return@withContext null

        val activeNetwork = connectivityManager.activeNetwork ?: return@withContext null
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork)

        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (!isWifi || !hasInternet || linkProperties == null) {
            return@withContext NetworkEnvironment(
                localIp = null,
                gatewayIp = null,
                subnetPrefixLength = null,
                dnsServers = emptyList(),
                isWifi = isWifi,
            )
        }

        NetworkEnvironment(
            localIp = localIpFrom(linkProperties),
            gatewayIp = gatewayIpFrom(linkProperties),
            subnetPrefixLength = subnetPrefixLengthFrom(linkProperties),
            dnsServers = linkProperties.dnsServers.mapNotNull { it.hostAddress },
            isWifi = true,
        )
    }

    private fun localIpFrom(linkProperties: LinkProperties): String? {
        return linkProperties.linkAddresses
            .firstOrNull { it.address.hostAddress?.contains(':') == false }
            ?.address
            ?.hostAddress
    }

    private fun subnetPrefixLengthFrom(linkProperties: LinkProperties): Int? {
        return linkProperties.linkAddresses
            .firstOrNull { it.address.hostAddress?.contains(':') == false }
            ?.prefixLength
    }

    /**
     * `LinkProperties.routes` não expõe "gateway" diretamente; o gateway é o `gateway` da rota
     * padrão (destino `0.0.0.0/0`) sem interface de destino específica.
     */
    private fun gatewayIpFrom(linkProperties: LinkProperties): String? {
        return linkProperties.routes
            .firstOrNull { route -> route.isDefaultRoute && route.gateway != null }
            ?.gateway
            ?.hostAddress
    }
}
