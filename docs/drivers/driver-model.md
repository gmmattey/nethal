# Driver Model

## Philosophy

NETHAL is capability-first.

Applications should use capabilities instead of vendor-specific conditionals.

## Capability states

```text
AVAILABLE
UNAVAILABLE
REQUIRES_AUTH
EXPERIMENTAL
UNSAFE
UNKNOWN
```

## Initial capabilities

```text
READ_DEVICE_INFO
READ_WAN_STATUS
READ_LAN_STATUS
READ_WIFI_STATUS
READ_WIFI_RADIOS
READ_CONNECTED_CLIENTS
READ_FIRMWARE
READ_UPTIME
READ_DNS
READ_DHCP
READ_CPU
READ_MEMORY
READ_SIGNAL
READ_MESH_STATUS

SET_WIFI_SSID
SET_WIFI_PASSWORD
SET_WIFI_CHANNEL
SET_WIFI_BANDWIDTH
SET_WIFI_ENABLED
SET_DNS
REBOOT_DEVICE
RESTART_WIFI
```

## Catálogo de compatibilidade (Driver Registry)

O formato real do manifesto offline versionado que alimenta o Driver Registry — por profile
vendor/model, evidências de fingerprint, confidence score e estágio — está documentado em
`docs/drivers/compatibility-catalog.md`. O manifesto mais recente vive em
`core/src/main/resources/catalog/catalog-<YYYY.MM.DD>.json` (ver esse documento para o arquivo
vigente e o histórico de mudanças).

## Driver Family — modelo de camadas (vigente desde 2026-07-06)

A arquitetura definitiva do NetHAL está congelada em
`docs/architecture/hal-layering-model.md`. Nenhum driver deve ser organizado por vendor+modelo a
partir de agora — a cadeia oficial é:

```text
Vendor → Platform → Protocol → Authentication Strategy → Driver Family → Profile → Capability
```

Em código, isso significa:

- **Vendor/Platform/Profile são dado**, só existem como campos do `CompatibilityProfile` do
  catálogo (`vendor`, `platformId`, `profileId`) — nunca criam um tipo Kotlin próprio nem lógica.
- **Authentication Strategy** (`core/auth/AuthenticationStrategy.kt`) e **Driver Family**
  (`core/catalog/DriverFamily.kt`, implementações em `core/driver/family/<vendor>/<família>/`) são
  o único lugar com lógica de comunicação. Uma Driver Family nunca conhece um modelo específico —
  recebe endpoints/seções/campos via `CompatibilityProfile.driverConfig` (payload opaco,
  interpretado só pela própria Driver Family).
- **`profileId` resolve para uma Driver Family via `profile.driverFamilyId`**, usado pelo
  `DriverFamilyRegistry` (`core/catalog/DriverFamilyRegistry.kt`) para encontrar a
  `DriverFamilyFactory` registrada e construir a instância certa — não existe mais construção manual
  de driver por fora do catálogo.

Um modelo novo no mesmo protocolo (ex.: um segundo Archer que usa o mesmo mecanismo de
autenticação de um já suportado) é só um `Profile` novo no catálogo, sem nenhum Kotlin novo. Driver
Family nova só se justifica quando o protocolo/autenticação for genuinamente novo — critério
objetivo na §9 do documento de arquitetura, incluindo a regra "primeiro evidência, depois
abstração" para quando duas Driver Families parecidas podem (ou não) virar uma só.

O TP-Link Archer C20 é o primeiro caso real dessa cadeia (`TpLinkLegacyCgiDriverFamily`, pacote
`core/driver/family/tplink/legacycgi/`) — reorganizado como caso de validação da arquitetura, sem
mudança de estágio, protocolo ou capability. TP-Link Archer C6 e Nokia G-1425G-B ainda não foram
migrados para Driver Family (continuam na forma anterior, `TplinkOntDriver`/`NokiaOntDriver`) —
migração deles é trabalho futuro, não bloqueia uso do driver hoje.
