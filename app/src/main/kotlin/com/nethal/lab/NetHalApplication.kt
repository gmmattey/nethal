package com.nethal.lab

import android.app.Application
import com.nethal.core.consent.ConsentRepository
import com.nethal.core.discovery.DefaultDiscoveryEngine
import com.nethal.core.discovery.DefaultSsdpDiscoverer
import com.nethal.core.discovery.DefaultUpnpIgdProbe
import com.nethal.core.discovery.DiscoveryEngine
import com.nethal.core.discovery.NetworkEnvironmentReader
import com.nethal.lab.data.consent.ConsentDataStoreRepository
import com.nethal.lab.data.consent.consentDataStore
import com.nethal.lab.data.discovery.AndroidNetworkEnvironmentReader

class NetHalApplication : Application() {

    lateinit var consentRepository: ConsentRepository
        private set

    lateinit var networkEnvironmentReader: NetworkEnvironmentReader
        private set

    lateinit var discoveryEngine: DiscoveryEngine
        private set

    override fun onCreate() {
        super.onCreate()
        consentRepository = ConsentDataStoreRepository(consentDataStore)

        networkEnvironmentReader = AndroidNetworkEnvironmentReader(this)
        discoveryEngine = DefaultDiscoveryEngine(
            networkEnvironmentReader = networkEnvironmentReader,
            ssdpDiscoverer = DefaultSsdpDiscoverer(),
            upnpIgdProbe = DefaultUpnpIgdProbe(),
        )
    }
}
