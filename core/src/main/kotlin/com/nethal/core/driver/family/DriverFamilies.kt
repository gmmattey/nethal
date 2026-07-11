package com.nethal.core.driver.family

import com.nethal.core.catalog.DriverFamilyRegistry
import com.nethal.core.driver.family.nokia.gpon.NokiaGponDriverFamilyFactory
import com.nethal.core.driver.family.tplink.gdprcgi.TpLinkGdprCgiDriverFamilyFactory
import com.nethal.core.driver.family.tplink.legacycgi.TpLinkLegacyCgiDriverFamilyFactory
import com.nethal.core.driver.family.tplink.stokluci.TpLinkStokLuciDriverFamilyFactory
import com.nethal.core.driver.family.tplink.xdrds.TpLinkXdrDsDriverFamilyFactory

/**
 * Ponto central de composição do [DriverFamilyRegistry] real do `core` — monta o mapa fixo
 * `driverFamilyId -> DriverFamilyFactory` uma única vez, nunca via reflection ou scan dinâmico
 * (`docs/architecture/hal-layering-model.md` §8/§10 passo 6).
 *
 * Vive em `core`, não em `app`, por decisão explícita deste passo (4 do plano de refatoração):
 * nasceu quando o único consumidor real de Driver Family era `ManualCheckRunnerC20`
 * (`core/driver/tplink/`, roda como task Gradle `:core:tplinkC20ManualCheck`). Desde a issue #16
 * o `app` também consome este composition root (`NetHalApplication`/`ViewModelFactory` montam o
 * `CapabilityEngine` real a partir daqui, usado por `CapabilitiesViewModel`) — este arquivo
 * continua sendo a única fonte de verdade de quais Driver Families existem, consumida por ambos.
 *
 * Cada Driver Family nova registrada aqui deve ser somada a esta lista — nunca descoberta
 * automaticamente por classpath scanning.
 */
fun defaultDriverFamilyRegistry(): DriverFamilyRegistry = DriverFamilyRegistry(
    listOf(
        TpLinkLegacyCgiDriverFamilyFactory(),
        TpLinkStokLuciDriverFamilyFactory(),
        TpLinkGdprCgiDriverFamilyFactory(),
        TpLinkXdrDsDriverFamilyFactory(),
        NokiaGponDriverFamilyFactory(),
    ),
)
