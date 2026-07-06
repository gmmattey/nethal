package com.nethal.lab.data.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import com.nethal.core.discovery.NetworkEnvironment
import com.nethal.core.discovery.NetworkEnvironmentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Leitura de rede via `ConnectivityManager`/`LinkProperties` (ver /regras-android-nethal —
 * gateway/subnet/DNS nunca são hardcoded, sempre vêm da rede ativa do sistema). Exige
 * `ACCESS_FINE_LOCATION` concedida para que `LinkProperties` retorne dados completos em
 * builds recentes; a checagem de permissão em si é responsabilidade da tela (SIG-318/319
 * dependem da Tela 2 já ter pedido a permissão antes de chamar `discover()`).
 */
class AndroidNetworkEnvironmentReader(
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
     * `LinkProperties.routes` não expõe "gateway" diretamente; o gateway é o `gateway` da
     * rota padrão (destino `0.0.0.0/0`) sem interface de destino específica.
     */
    private fun gatewayIpFrom(linkProperties: LinkProperties): String? {
        return linkProperties.routes
            .firstOrNull { route -> route.isDefaultRoute && route.gateway != null }
            ?.gateway
            ?.hostAddress
    }
}
