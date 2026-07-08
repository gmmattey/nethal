# Compatibility Catalog — formato do manifesto

Este documento descreve o schema real do catálogo de compatibilidade offline, versionado em
`core/src/main/resources/catalog/catalog-<YYYY.MM.DD>.json`, embarcado no NetHAL Core como
fallback local para o Driver Registry (§8.5 da spec de produto). O Driver Registry (implementação
do Bruno, Feat 3 / SIG-309) carrega este arquivo como base offline e sincroniza contra uma versão
remota mais nova quando houver conexão, sem nunca bloquear uso offline — mesma regra do §8.5.

## Por que Nokia G-1425G-B e TP-Link Archer C6 são os dois primeiros profiles

Critério único e explícito: são os dois únicos equipamentos com **acesso físico real para teste**,
citados no project charter. Toda promoção de estágio de driver exige teste real documentado
(modelo + firmware, ver `/ciclo-vida-driver`) — não faz sentido descrever profiles em detalhe para
equipamentos que o squad não consegue testar fisicamente ainda. Outros alvos da matriz de
priorização (`docs/architecture/driver-adoption-strategy.md`) continuam válidos como roadmap, mas
entram no catálogo quando houver acesso físico ou parceria de teste equivalente.

> **Correção de modelo (2026-07-07):** o manifesto `catalog-2026.07.06.json` pesquisou o Nokia
> como **G-1425G-A** por engano. O Luiz confirmou que a unidade física de teste do NetHAL é o
> **G-1425G-B** — mesmo modelo do driver Nokia já em produção no SignallQ (produto irmão). O
> profile foi recriado (`nokia_g1425ga_v1` → `nokia_g1425gb_v1`) no manifesto
> `catalog-2026.07.07.json`, com evidência de fingerprint agora vinda de código de produção real
> (ver seção "Fontes consultadas" abaixo), não mais de pesquisa documental de terceiros sobre o
> modelo errado.

## Localização e nomenclatura

```text
core/src/main/resources/catalog/catalog-YYYY.MM.DD.json
```

- Um arquivo por versão de manifesto — nunca sobrescrever a versão anterior, permitindo diff
  incremental e rollback (conforme "Catálogo offline e sincronização" em
  `driver-adoption-strategy.md`).
- `manifestVersion` dentro do JSON deve ser idêntico à data no nome do arquivo.
- `previousManifest` referencia o nome do arquivo anterior (ou `null` no primeiro manifesto),
  permitindo ao Driver Registry calcular o diff incremental sem precisar reprocessar tudo.

## Estrutura do manifesto

```jsonc
{
  "$schema": "https://nethal.dev/schema/compatibility-catalog-v1.json",
  "manifestVersion": "2026.07.06",
  "generatedAt": "2026-07-06T00:00:00Z",
  "generatedBy": "diego-drivers-protocolos",
  "previousManifest": null,          // nome do manifesto anterior, para diff incremental
  "profiles": [ /* ver abaixo */ ]
}
```

## Estrutura de cada `profile`

Um `profile` é a unidade de compatibilidade — mapeia 1:1 para um `vendor` + `model` (não
firmware individual; firmwares testados ficam em `firmwareKnown[]` dentro do profile). Campos:

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `profileId` | string | sim | Identificador estável, `snake_case`, usado pelo Driver Registry para resolver o driver correspondente em `drivers/<vendor_family>/`. |
| `vendor` | string | sim | Nome do fabricante, forma canônica (ex.: `"Nokia"`, `"TP-Link"`). |
| `model` | string | sim | Modelo exato testado/pesquisado. Nunca genérico ("Archer" sozinho não vale). |
| `deviceType` | enum | sim | Mesmo vocabulário de `DeviceInfo.DeviceType` em `core` (`ROUTER`, `ONT`, `ONU`, `MESH`, `AP`, `REPEATER`, `UNKNOWN`). |
| `productLine` | string | sim | Descrição textual da família/linha comercial do produto, para contexto humano (ex.: `"Archer"`). Renomeado de `family` (ver changelog `2026-07-07`) para não colidir com `driverFamilyId`, que é outro conceito. |
| `platformId` | string | sim | Metadado de catálogo identificando a plataforma tecnológica compartilhada (protocolo + autenticação), ex.: `"tplink-encrypted-web"`, `"tplink-legacy-cgi"`, `"nokia-gpon-rsa-aes"`. Ver `docs/architecture/hal-layering-model.md` §5.2/§11.1 — é deliberadamente `String` simples, sem tipo Kotlin próprio nesta rodada. |
| `driverFamilyId` | string | sim | Chave de resolução no `DriverFamilyRegistry` (`docs/architecture/hal-layering-model.md` §5.5/§10 — implementado desde o passo 4/6). Mapa fixo `driverFamilyId -> DriverFamilyFactory`, montado uma única vez na inicialização do `core` (`com.nethal.core.driver.family.defaultDriverFamilyRegistry()`), nunca via reflection. O valor é a chave literal usada para registrar a factory correspondente (ex.: `"tplink-legacy-cgi-driver"`, registrado por `TpLinkLegacyCgiDriverFamilyFactory`). |
| `driverConfig` | `JsonElement` | não (default `null`) | Payload opaco de configuração específica do driver (endpoints, seções, mapeamento de campos) que cada Driver Family interpreta do seu próprio jeito. Deliberadamente sem schema comum entre plataformas diferentes (TP-Link vs Nokia) — ver changelog `2026-07-07` (passo 4) para o primeiro schema concreto, o do profile `tplink_archer_c20_v1`/`tplink-legacy-cgi-driver`. Continua `null` para profiles cuja Driver Family ainda não foi criada. |
| `firmwareKnown` | string[] | sim (pode ser vazio) | Firmwares confirmados por teste real. Vazio até o primeiro teste documentado. |
| `stage` | enum | sim | Estágio do profile — mesmo vocabulário de `/ciclo-vida-driver`: `DRAFT`, `DISCOVERY_ONLY`, `READ_ONLY_ALPHA`, `READ_ONLY_BETA`, `WRITE_BETA`, `STABLE`, `DEPRECATED`, `BLOCKED`. |
| `stageReason` | string | sim | Por que o profile está neste estágio agora — obrigatório, nunca deixar implícito. |
| `physicalTestAccess` | boolean | sim | Se o squad tem a unidade física disponível para teste real. |
| `physicalTestAccessNote` | string | não | Contexto sobre o acesso físico (quando obtido, o que falta testar). |
| `managementDefaults` | object | sim | IPs candidatos, porta e nível de confiança de cada um (ver abaixo). |
| `credentialConvention` | object | sim | Convenção de credencial *documental* — nunca usada para tentativa automática de login (ver regra de segurança abaixo). |
| `fingerprintEvidence` | object[] | sim | Lista de evidências de fingerprint, cada uma com `type`, `value`, `confidence`, `confidenceLevel`, `source`, `note`. |
| `expectedProtocols` | object[] | sim | Protocolos esperados/detectados, com `protocol`, `detectionState` (vocabulário de `/protocolos-locais`: `SUPPORTED`/`DETECTED_BUT_UNSUPPORTED`/`REQUIRES_AUTH`/`BLOCKED`/`UNKNOWN`), `note`. |
| `capabilities` | object[] | sim | Capabilities candidatas (vocabulário `CapabilityId`), cada uma com `id`, `state` (vocabulário `CapabilityState`), `reason` obrigatório quando `state != AVAILABLE`. |
| `knownFirmwareBugs` | object[] | não | Bugs de firmware conhecidos, com `description`, `confidence`, `source`. |
| `operatorProvisioningRisk` | object | sim para CPE-ISP | `risk` (`LOW`/`MEDIUM`/`HIGH`) e `note` — sinaliza se o equipamento é gerenciado por ACS de operadora e pode ter configuração revertida. |
| `confidenceScoreOverall` | number (0–1) | sim | Score agregado do profile, calculado pela heurística de `/protocolos-locais` (ver seção abaixo). |
| `confidenceScoreOverallNote` | string | sim | Como o score foi calculado, item a item — auditável por Marisa/Rafael. |

### `managementDefaults`

```jsonc
{
  "candidateIps": ["192.168.0.1", "192.168.1.1", "tplinkwifi.net"],
  "ipConfidence": 0.85,
  "ipConfidenceNote": "por que esse número, com fonte",
  "managementPort": 80,
  "managementPortNote": "por que essa porta, com fonte"
}
```

### `credentialConvention`

```jsonc
{
  "defaultUser": "AdminGPON",
  "defaultPasswordPattern": "ALC#FGU (ou variante por operador)",
  "confidence": 0.4,
  "confidenceNote": "fonte e ressalvas",
  "policyNote": "NUNCA usar como tentativa automática de login — só fingerprint passivo ou sugestão manual ao usuário"
}
```

Este campo é **documental**, nunca operacional. Nenhum código do NetHAL deve ler
`credentialConvention` para preencher um formulário de login automaticamente — isso violaria a
regra de "nada de bypass de auth, brute-force ou uso automático de senha padrão" do `CLAUDE.md` e
do `SECURITY.md`. O único uso legítimo é: (a) fingerprint passivo (ex.: a string aparece em uma
página pública), ou (b) UI sugerindo ao usuário "experimente a senha da etiqueta do equipamento".

### `fingerprintEvidence[]`

```jsonc
{
  "type": "html_title" | "http_headers" | "management_protocol" | "webui_menu_structure" |
          "auth_mechanism" | "session_behavior" | "vendor_app_reference" | "product_documentation",
  "value": <string|string[]|null>,
  "confidence": 0.0,
  "confidenceLevel": "NONE_VERIFIED" | "LOW" | "MEDIUM" | "MEDIUM_HIGH" | "HIGH",
  "source": "URL ou documento exato consultado",
  "note": "ressalva, limitação, ou por que o valor é null"
}
```

Regra inegociável: se a evidência não foi capturada diretamente (probe real, HTML bruto, header
real), `value` fica `null` e `confidence` fica `0.0`. Nunca preencher com valor plausível "para não
deixar em branco". O campo `source` é sempre obrigatório mesmo quando `value` é `null` — documenta
o que foi tentado e por que falhou (ex.: HTTP 403 no fetch).

### Scoring de confiança (`confidenceScoreOverall`)

Segue a heurística de `/protocolos-locais` / `driver-adoption-strategy.md`, seção "Scoring de
confiança":

- 0,25 — match de headers/banners reais (Server, WWW-Authenticate, título HTML, marca em XML)
- 0,20 — match de descritor/endpoint canônico real
- 0,20 — autenticação bem-sucedida testada no modo esperado
- 0,15 — capability sanity check coerente (leitura real validada)
- 0,10 — firmware/modelo presente no catálogo offline (documentação oficial confirmada)
- 0,10 — evidência comunitária/histórico local bem-sucedido

Regras de decisão por faixa de score (aplicam-se quando o profile avançar de `DRAFT` e um probe
real começar a produzir score dinâmico, não ao score estático documental deste manifesto):

- `< 0,50` → só leitura passiva, sem autenticação adicional
- `0,50–0,75` → leitura autenticada permitida
- `0,75–0,90` → escrita não destrutiva permitida
- `> 0,90` → reboot e mudanças sensíveis só com consentimento explícito

Os dois profiles deste manifesto (`0.25` e `0.35`) refletem pesquisa documental, não probe real —
ambos abaixo de `0.50` porque nenhum header/título/autenticação foi capturado diretamente ainda.

## Ciclo de vida de um profile novo no catálogo

1. **Entrada em `DRAFT`**: profile criado com o máximo de evidência documental pública verificável
   (fabricante, comunidade, bibliotecas de reuso), mas sem nenhum probe real. É o estado deste
   manifesto para Nokia G-1425G-B e TP-Link Archer C6 — mesmo o Nokia tendo, nesta rodada, uma
   implementação de driver real em `core/driver/nokia` (ver nota abaixo), o `stage` permanece
   `DRAFT` porque nenhum probe/login foi executado pelo NetHAL contra a unidade física.
2. **`DRAFT → DISCOVERY_ONLY`**: primeiro probe passivo real contra a unidade física (GET simples
   na tela de login, captura de headers, sem autenticação) documentado com timestamp e output real.
   Atualiza `fingerprintEvidence[]` substituindo `value: null` por valores capturados e recalcula
   `confidenceScoreOverall`.
3. **`DISCOVERY_ONLY → READ_ONLY_ALPHA`**: pelo menos um teste real de leitura autenticada (modelo
   + firmware) documentado, conforme `/ciclo-vida-driver`.
4. Estágios seguintes seguem exatamente `/ciclo-vida-driver` — este documento não duplica aquela
   skill, apenas aponta onde o estágio do profile fica registrado (campo `stage` do manifesto).

## Fonte de evidência "driver de produção de produto irmão"

O profile `nokia_g1425gb_v1` (manifesto `2026.07.07`) introduz uma categoria de evidência mais
forte que "documentação pública de terceiros": código-fonte real de um driver de produção,
rodando em campo, do produto irmão SignallQ (`C:\Projetos\SignallQ\android\feature\fibra`), contra
o mesmo modelo exato de hardware. Isso não é probe do NetHAL — o `stage` do profile continua
`DRAFT` e a heurística de score trata essa fonte como "evidência comunitária/histórico local"
(0,10 do teto da categoria), não como "autenticação testada" ou "capability sanity check" (que só
pontuam quando o próprio NetHAL executa o teste). Ver `fingerprintEvidence[].source` do profile
para a citação exata de cada arquivo do SignallQ usado como referência.

O driver correspondente no NetHAL (`core/src/main/kotlin/com/nethal/core/driver/nokia/`) foi
implementado do zero para o vocabulário do NetHAL, usando o código do SignallQ como referência de
protocolo (handshake RSA+AES, endpoints, conversões de unidade, aliases de campo incluindo o typo
conhecido de firmware `SupplyVottage`) — não é uma cópia literal de arquivo.

## Limitação conhecida — TOFU no handshake do driver Nokia

O login do `NokiaOntDriver` busca a chave pública RSA na própria página de login do equipamento
(`GET /`), sem certificado nem pinagem — é *trust on first use* (TOFU), inerente ao protocolo do
firmware Nokia, não uma escolha do NetHAL nem algo evitável sem quebrar compatibilidade com o
equipamento real. Isso foi levantado por Marisa na revisão de segurança do PR #6 (driver Nokia).

Mitigação já implementada: `NokiaOntDriver` recusa construir contra qualquer host que não seja IP
privado (RFC 1918) — ver `PrivateIpRanges` em `core/src/main/kotlin/com/nethal/core/discovery/`.
Isso reduz o risco a "host malicioso dentro da própria LAN do usuário", não elimina o TOFU em si.

**Pendência antes de `READ_ONLY_BETA`**: quando a Tela 5 (Autenticação, spec §11) for implementada,
ela precisa avisar explicitamente o usuário sobre essa limitação antes do primeiro login — algo
como "este equipamento não permite verificar a autenticidade do host antes de enviar sua senha;
use apenas na sua própria rede confiável". Não é bloqueante para o driver continuar em `DRAFT`.

## Limitação conhecida — TOFU no handshake stok/luci do TP-Link Archer C6

O login do `TpLinkStokLuciDriverFamily` (profile `tplink_archer_c6_stok_v1`) busca duas chaves
públicas RSA distintas do próprio host, sem certificado nem pinagem — `POST
/cgi-bin/luci/;stok=/login?form=keys` (chave de cifra de senha, 1024-bit) e `POST
/cgi-bin/luci/;stok=/login?form=auth` (chave de assinatura do envelope `sign`, 512-bit, + `seq`) —
mesma classe de risco *trust on first use* (TOFU) já documentada acima para o Nokia, inerente ao
protocolo desse firmware TP-Link, não uma escolha do NetHAL.

Mitigação já implementada: `TpLinkStokLuciDriverFamily` recusa construir contra qualquer host que
não seja IP privado (RFC 1918) — mesma guarda `PrivateIpRanges` usada por todos os drivers. Isso
reduz o risco a "host malicioso dentro da própria LAN do usuário", não elimina o TOFU em si.

**Pendência antes de `READ_ONLY_BETA`**: mesma pendência do Nokia — a Tela 5 (Autenticação) precisa
avisar explicitamente o usuário sobre essa limitação antes do primeiro login neste profile também.
Não é bloqueante para o driver continuar em `DISCOVERY_ONLY`.

Revisão de segurança: Marisa, 2026-07-07 (implementação do `TpLinkStokLuciDriverFamily`), aprovado
com esta ressalva documentada.

## Nota de mapeamento — `manufacturer` real (`ALCL`) vs. nome comercial (`Nokia`)

A execução real de `nokiaManualCheck` (SIG-333) contra a unidade física confirmou que
`/device_status.cgi` reporta `manufacturer=ALCL` — herança do fabricante original Alcatel-Lucent,
adquirido pela Nokia em 2016, cuja base de firmware desta família de ONT GPON não foi renomeada
internamente. Isso **não é um bug nem uma inconsistência a corrigir**: é só uma diferença entre o
identificador interno de firmware e o nome comercial atual (`Nokia`) usado no catálogo e na UI.
Qualquer exibição ao usuário deve continuar usando `vendor: "Nokia"` (nome comercial, já correto no
profile); `manufacturer=ALCL` só é relevante como evidência de fingerprint interna ou nota de
debugging, nunca como valor a expor na Tela de identificação do equipamento.

## Fontes consultadas — manifesto `2026.07.09` (2026-07-06, SIG-333, segunda execução)

- **Nokia G-1425G-B**: segunda execução real de `nokiaManualCheck` contra a mesma unidade física
  do Luiz, agora com a instrumentação de captura de fingerprint da tela de login (introduzida no
  manifesto `2026.07.08`). Trouxe o dado que faltava: `html_title` = "GPON Home Gateway", capturado
  por probe passivo real, sem autenticação, na raiz do equipamento. O header `Server` foi verificado
  e confirmado como genuinamente ausente na resposta — não uma lacuna de captura. Uptimes de GPON,
  WAN e DeviceInfo incrementaram corretamente (~16 min) entre as duas execuções, confirmando leitura
  real (não cache/fixture).
- **TP-Link Archer C6**: inalterado nesta rodada.

## Fontes consultadas — manifesto `2026.07.08` (2026-07-06, SIG-333)

- **Nokia G-1425G-B**: primeira execução real de leitura autenticada do próprio NetHAL
  (`nokiaManualCheck`, via `NokiaOntDriver.readSnapshot`) contra a unidade física do Luiz. Não é
  mais evidência indireta de driver irmão — é o teste que faltava, exigido por
  `/ciclo-vida-driver` para promover de `DISCOVERY_ONLY` para `READ_ONLY_ALPHA`. Os 4 endpoints
  (GPON, WAN, PPP, DeviceInfo) retornaram dados coerentes entre si (mesmo `serialNumber` em dois
  endpoints, uptimes consistentes, `connectionType` idêntico em WAN e PPP), o que caracteriza
  "capability sanity check coerente" na heurística de score, não só "endpoint respondeu".
- **TP-Link Archer C6**: inalterado nesta rodada.

## Consumo pelo Driver Registry (fora de escopo desta entrega)

O parsing/deserialização deste JSON em Kotlin, a lógica de diff incremental entre manifestos e a
integração com o `DriverRegistry` real são responsabilidade do Bruno na Feat 3 (SIG-309). Este
documento e o `catalog-2026.07.07.json` são o insumo de dados — não incluem implementação de
parser, cliente HTTP de sincronização ou testes de unidade Kotlin (o `DriverRegistry` em si já
existe e está coberto por testes próprios em `core/src/test/kotlin/com/nethal/core/catalog/`,
mas a lógica de diff incremental entre manifestos permanece não implementada).

## Fontes consultadas — manifesto `2026.07.07` (2026-07-07)

- **Nokia G-1425G-B**: código-fonte real do driver Nokia de produção do SignallQ
  (`C:\Projetos\SignallQ\android\feature\fibra\...\NokiaModemClient.kt`,
  `NokiaModemCrypto.kt`, `NokiaModemParser.kt`, `ExecutorFibra.kt`) — driver autenticado rodando em
  campo contra este exato modelo de hardware. Fonte de confiança muito mais alta que pesquisa
  documental de terceiros, mas ainda não é um teste do próprio NetHAL (ver seção "Fonte de
  evidência 'driver de produção de produto irmão'" acima).
- **TP-Link Archer C6**: inalterado nesta rodada — mesmas fontes do manifesto anterior (emulador
  oficial TP-Link, SetupRouter.com, GitHub `AlexandrErohin/TP-Link-Archer-C6U`).

## Fontes consultadas — manifesto `2026.07.06` (histórico, modelo Nokia incorreto)

Ver `fingerprintEvidence[].source` de cada profile no próprio JSON (`catalog-2026.07.06.json`)
para a citação exata por evidência. Resumo das fontes de maior peso:

- **Nokia G-1425G-A (modelo incorreto — corrigido para G-1425G-B no manifesto `2026.07.07`)**:
  manuals.plus (identificação do documento oficial "Nokia ONT G-1425G-A Product Guide",
  3FE-77771-AAAA-TCZZA Issue 5, Nov/2021 — conteúdo bloqueado por HTTP 403 no fetch direto);
  made4it.com.br (TR-069/DHCP Option 43 para G-1425-GA); ManualsLib (G-1425G-E, variante irmã,
  estrutura de menu e TR-369); router-network.com e knowledgebase.bison.co.in (convenção
  AdminGPON/ALC#FGU e IP 192.168.1.254 para a família Nokia GPON, não confirmado especificamente
  para o G-1425G-A); hack-gpon.org (G-010G-Q, explicitamente tratado como *não* extrapolável ao
  G-1425G-A por ser hardware/chipset diferente).
- **TP-Link Archer C6**: emulador oficial do fabricante
  (https://emulator.tp-link.com/c6-us-v2/index.html — fonte primária para estrutura de menu e
  confirmação de app companion "TP-Link Tether"); documentação oficial TP-Link (IP padrão, guia de
  habilitação de HTTPS local); SetupRouter.com (IP/porta/comportamento de primeiro login por
  hardware v2/v4); GitHub `AlexandrErohin/TP-Link-Archer-C6U` (mecanismo de auth AES-CBC/AES-GCM,
  já registrado como reuso conhecido em `driver-adoption-strategy.md`).

Nenhum probe HTTP direto (GET real contra `Server`/`title`) foi executado nesta rodada — as
ferramentas de pesquisa disponíveis (WebSearch/WebFetch) não substituem um probe de rede real
contra a unidade física. Isso é declarado explicitamente no manifesto (`value: null`,
`confidenceLevel: NONE_VERIFIED`) para as duas evidências mais importantes de fingerprint HTTP
(título HTML e headers) em ambos os profiles.

## Riscos — `driverConfig` como superfície futura de dado não confiável

`driverConfig` (introduzido no passo 5 do plano de refatoração HAL, preenchido pela primeira vez
para o TP-Link Archer C20 no passo 4) é um `JsonElement` opaco que cada Driver Family interpreta do
seu próprio jeito — hoje, seções/campos que viram literalmente o corpo de requisições autenticadas
enviadas ao equipamento (ver `TpLinkLegacyCgiResponseParser.buildRequestBody` e
`TpLinkLegacyCgiDriverConfig`).

Isso é seguro **só porque o catálogo hoje é 100% embarcado/local** (`RemoteCatalogSource` em
`core/catalog/DriverRegistry.kt` é `NoOpRemoteCatalogSource`, nunca busca nada de rede). O sync
remoto de catálogo é item real do roadmap do produto (spec §8.5), não hipotético — no dia em que
`RemoteCatalogSource` ganhar implementação real, um manifesto malicioso ou corrompido poderia, sem
o gate certo, injetar seção/campo arbitrário em uma requisição autenticada contra o roteador do
usuário. Não é bypass de autenticação nem exfiltração direta de credencial, mas é uma superfície de
"comando cego vindo de dado remoto não confiável".

**Gate obrigatório antes de qualquer `RemoteCatalogSource` real** (ver também `SECURITY.md`,
seção "Catalog integrity"):

1. Manifesto remoto deve ser assinado/verificado antes de ser aceito.
2. Nenhuma Driver Family pode enviar uma seção/campo vindo de `driverConfig` sem checar contra uma
   allowlist de seções/campos que ela já conhece para o próprio protocolo — presença no JSON nunca
   deve, por si só, autorizar o envio.

Revisão de segurança: Marisa, 2026-07-07 (passo 4 do plano de refatoração HAL — reorganização do
C20 como `TpLinkLegacyCgiDriverFamily`), aprovado com esta ressalva documentada.

## Changelog

- **2026-07-07 (mapeamento das capabilities restantes do `tplink-stok-luci`, manifesto
  `catalog-2026.07.21.json`)** — Revisão dos três pontos em aberto deixados pela rodada anterior
  (parser estruturado + ADR 0001), sem coleta de evidência ao vivo nova. Nenhuma capability nova
  entrou em `SUPPORTED_CAPABILITIES`/`TpLinkStokLuciStatusParser` nesta rodada — é uma rodada de
  documentação/decisão, não de implementação.

  1. **`READ_DEVICE_INFO`/`READ_FIRMWARE` continuam `UNKNOWN`.** Checado o corpo de resposta de
     todas as chamadas já capturadas ao vivo do fluxo `tplink-stok-luci` (`form=keys`, `form=auth`,
     `form=login`, `admin/status?form=all`, ver `docs/drivers/live-evidence/tplink-archer-c6-stok-v1.json`)
     — nenhuma delas carrega campo de vendor/modelo/versão de firmware. O modelo/firmware
     conhecidos desta unidade (`Archer C6 v2.0`, `1.1.10 Build 20230830 rel.69433(5553)`) são
     metadado de identificação manual da unidade física de teste, não um campo de API parseado —
     não há como preencher `READ_DEVICE_INFO`/`READ_FIRMWARE` sem inventar heurística. Nenhum
     endpoint novo foi chamado nesta rodada por falta de evidência ao vivo (regra explícita da
     tarefa: sem evidência, documenta `UNKNOWN`, não inventa).

  2. **Guest network confirmada sem capability própria — decisão mantida.** Revisão do vocabulário
     oficial (`docs/drivers/driver-model.md`, `core/src/main/kotlin/com/nethal/core/model/Capability.kt`
     — `CapabilityId` completo) confirma que não existe `READ_GUEST_NETWORK_STATUS` nem
     equivalente. A modelagem já em produção desde a rodada anterior (`guest_2g_ssid`/
     `guest_5g_ssid` como entradas de `TpLinkStokLuciWifiRadio` com `guestNetwork=true`, dentro de
     `READ_WIFI_STATUS`) permanece — não é proposta capability nova unilateralmente (decisão de
     vocabulário é do Rafael, ver `/modelo-capacidades`). Fica resolvido o "gap a discutir com
     Rafael" sinalizado na entrada de changelog anterior: a conclusão é manter como está, não que
     falte decisão.

  3. **Campos observados sem capability correspondente no vocabulário atual — nenhum parser
     implementado, listados só para referência futura:**
     - `wireless_2g_wps_state` (estado de WPS) — não existe capability dedicada de WPS no
       vocabulário.
     - `storage_*`, `usb_available`, `printer_*` (compartilhamento USB/armazenamento/impressora) —
       não existe capability de armazenamento ou impressora no vocabulário.
     - `modem_*` — prefixo observado sem nome/conteúdo de campo exato confirmado; insuficiente para
       mapear com segurança para `READ_WAN_STATUS` ou qualquer outra capability sem forçar
       mapeamento sem evidência.

     Nenhum desses campos ganhou implementação de parser nesta rodada — regra explícita da tarefa
     de não forçar mapeamento onde não há capability correspondente clara.

  4. **Correção de consistência incidental:** o texto de `reason` de `READ_WIFI_STATUS`/
     `READ_LAN_STATUS`/`READ_CONNECTED_CLIENTS` em `catalog-2026.07.20.json` ainda descrevia SSID
     como hash SHA-256 e MAC sempre mascarado — texto nunca atualizado quando a ADR 0001 corrigiu o
     modelo do driver para dado bruto. Corrigido em `catalog-2026.07.21.json` para refletir o
     comportamento real do código (`TpLinkStokLuciStatusParser`/`TpLinkStokLuciModels.kt`) desde a
     ADR.

  **Estágio do profile:** permanece `READ_ONLY_ALPHA`, sem promoção (fora de escopo desta rodada).
  `confidenceScoreOverall` permanece `0.75` — rodada de revisão/documentação, sem evidência ao vivo
  nova.

  **Testes:** nenhum teste novo — `SUPPORTED_CAPABILITIES` de `TpLinkStokLuciDriverFamily` não
  mudou (`TpLinkStokLuciDriverFamilyTest` já cobre isso). Ajustadas as asserções de
  `manifestVersion` (`"2026.07.20"` → `"2026.07.21"`) em `DriverRegistryTest` e
  `FingerprintEngineTest` para o novo manifesto embarcado default; `loadEmbeddedCatalogResource()`
  atualizada para `catalog/catalog-2026.07.21.json`.

- **2026-07-07 (ADR 0001 — modelo do `tplink-stok-luci` passa a carregar SSID/MAC brutos)** —
  `docs/architecture/adr/0001-fronteira-sanitizacao-telemetria.md` (Rafael) decidiu que
  sanitização de dado sensível (hash de SSID, mascaramento de MAC) não é responsabilidade do
  parser/modelo do driver — é responsabilidade exclusiva de um futuro Telemetry Collector, aplicada
  só na fronteira de exportação. `TpLinkStokLuciWifiRadio.ssidHash` virou `ssid: String?` (SSID
  real, sem hash); `TpLinkStokLuciLanStatus.macAddressMasked` e
  `TpLinkStokLuciConnectedClient.macAddressMasked` viraram `macAddress: String?` (MAC completo, sem
  mascaramento). A entrada de changelog anterior (abaixo) que descrevia essa sanitização como regra
  da spec §8.9 aplicada "já na origem" reflete o entendimento anterior, corrigido por este ADR — a
  proibição de coleta de senha do Wi-Fi (`*_psk_key`) não muda, continua nunca lida. Não altera
  `stage` (`READ_ONLY_ALPHA` permanece).

- **2026-07-07 (parser estruturado de capabilities do `tplink-stok-luci`, manifesto
  `catalog-2026.07.20.json`)** — `TpLinkStokLuciDriverFamily` (profile `tplink_archer_c6_stok_v1`)
  ganhou `TpLinkStokLuciStatusParser`, mapeando o corpo já decifrado de `admin/status?form=all`
  (validado ao vivo em rodada anterior, ver evidência acima) para o vocabulário de capabilities do
  NetHAL:
  - `READ_WIFI_STATUS` ← `wireless_2g_ssid`/`wireless_5g_ssid`/`wireless_2g_channel` (rádios
    principais) e `guest_2g_ssid`/`guest_5g_ssid` (redes de convidados, modeladas como rádios
    adicionais com `guestNetwork=true` — não existe capability própria para rede de convidados no
    vocabulário atual, sinalizado como possível gap a discutir com Rafael).
  - `READ_LAN_STATUS` ← `lan_macaddr` (mascarado) + `lan_ipv4_ipaddr`.
  - `READ_WAN_STATUS` ← `wan_ipv4_ipaddr`.
  - `READ_CONNECTED_CLIENTS` ← `access_devices_wired[]` (`macaddr` mascarado, `ipaddr`, `hostname`).

  **Sanitização (spec §8.9), aplicada já na origem do parsing:** SSID nunca fica em texto puro —
  vira hash SHA-256 (`ssidHash`); MAC sempre mascarado (3 últimos octetos); `wireless_*_psk_key`/
  `guest_*_psk_key` (a senha do Wi-Fi) **nunca são lidos para nenhum campo** do modelo resultante —
  não existe campo para isso em `TpLinkStokLuciWifiRadio` de propósito. WAN/LAN IP permanecem em
  texto puro (não estão na lista de campos proibidos/mascarados da spec §8.9 — só "IP público
  completo" da telemetria está listado, e o valor de diagnóstico de ver o próprio IP é central para
  o NetHAL Lab).

  **Campos do payload sem capability correspondente no vocabulário atual:** nenhum — todos os
  campos citados na tarefa (`wireless_2g_ssid`, `wireless_5g_ssid`, `wireless_2g_channel`,
  `wireless_2g_psk_key`, `lan_macaddr`, `lan_ipv4_ipaddr`, `wan_ipv4_ipaddr`,
  `access_devices_wired`, `guest_2g_ssid`, `guest_5g_ssid`) foram mapeados ou deliberadamente
  excluídos (`psk_key`, nunca — é segredo, não identificador). `READ_DEVICE_INFO`/`READ_FIRMWARE`
  continuam `UNKNOWN`: nenhum campo de modelo/firmware apareceu no payload observado até aqui.

  **Limite arquitetural que permanece, idêntico ao de `tplink-legacy-cgi-driver`/
  `tplink-gdpr-cgi-driver`/`tplink-xdr-ds-driver`:** `DriverFamily.readCapability(id)` (a
  implementação da interface pública, sem parâmetro de credencial) continua retornando
  `CapabilityReadResult.Unavailable` para todo `CapabilityId` — não existe Capability Engine
  gerenciando sessão ainda (`docs/architecture/hal-layering-model.md` §8 passo 5), então o dado
  estruturado real só é alcançável hoje via o novo método `readSnapshot(username, password)`,
  mesmo padrão de `login()`/`readStatusRaw()` já existentes. `readCapability(id)` ganhou
  `SUPPORTED_CAPABILITIES` (mesmo padrão introduzido antes por `TpLinkLegacyCgiDriverFamily`) para
  distinguir "esta Driver Family nunca vai cobrir `$id`" de "cobre, mas exige sessão que esta
  assinatura não recebe".

  **Estágio do profile:** permanece `READ_ONLY_ALPHA` — não sobe para `READ_ONLY_BETA` nesta
  rodada porque essa transição exige sign-off explícito de telemetria da Marisa
  (`/ciclo-vida-driver`), ainda não feito para as capabilities novas. `capabilities[]` do profile
  passou de `UNKNOWN` para `EXPERIMENTAL` nas quatro capabilities cobertas pelo parser
  (`READ_WIFI_STATUS`, `READ_LAN_STATUS` — nova, antes ausente do array —, `READ_WAN_STATUS`,
  `READ_CONNECTED_CLIENTS`), nunca `AVAILABLE`: o mapeamento de nome de campo usado no parser não
  foi recapturado/reconfirmado byte a byte nesta rodada, só herdado de nomes de campo relatados de
  uma sessão de teste manual anterior (console do `gradlew :core:tplinkC6StokManualCheck`, não
  persistido em arquivo). `confidenceScoreOverall` subiu de 0.70 para 0.75 (componente "capability
  sanity check" de 0.10 para 0.15) pelo mesmo motivo.

  **Testes novos:** `TpLinkStokLuciStatusParserTest` (puro, sem rede — mapeamento de campos,
  sanitização de SSID/MAC, ausência total de `psk_key` no modelo resultante, JSON malformado/campos
  ausentes nunca lançam) e extensões em `TpLinkStokLuciDriverFamilyTest`
  (`SUPPORTED_CAPABILITIES`, `readSnapshot` ponta a ponta contra o fake de transporte,
  distinção de motivo em `readCapability`). `ManualCheckRunner.runTplinkC6Stok` passou a chamar
  `readSnapshot` também, imprimindo o resultado estruturado e sanitizado ao lado do corpo bruto já
  impresso antes.

- **2026-07-07 (correção de consistência do Archer C6 físico no runtime atual)** — O texto
  histórico abaixo preserva corretamente cada hipótese/refutação por rodada, mas ficou um descompasso
  entre changelog, catálogo embarcado e evidência viva do profile `tplink_archer_c6_stok_v1`. A
  evidência consolidada em `docs/drivers/live-evidence/tplink-archer-c6-stok-v1.json` já registra
  que a implementação atual fez **login real bem-sucedido** e **leitura autenticada real de
  `admin/status?form=all`** contra o hardware do Luiz em 2026-07-07. Pelo critério deste próprio
  documento (`DISCOVERY_ONLY -> READ_ONLY_ALPHA` exige ao menos uma leitura autenticada real),
  o profile não podia continuar descrito como `DISCOVERY_ONLY` por falta de teste. O ajuste desta
  rodada é de consistência, não de descoberta nova: o catálogo atual passa a refletir o estado real
  já comprovado da Driver Family `tplink-stok-luci-driver`:
  `READ_ONLY_ALPHA` para login + `readStatusRaw`, ainda **sem** mapeamento estruturado de
  capabilities nem cobertura completa de navegação/coleta.

- **2026-07-07 (quarta rodada, chave/IV AES corrigida para string decimal de 16 dígitos —
  evidência via captura byte a byte externa, manifesto `catalog-2026.07.19.json`)** — A terceira
  rodada (manifesto `catalog-2026.07.18.json`) corrigiu as duas chaves RSA distintas
  (`form=keys`/`form=auth`), mas o teste real seguinte (`gradlew :core:tplinkC6StokManualCheck`)
  **ainda falhou** com `INVALID_CREDENTIALS`/HTTP 403. Nesta rodada, uma ferramenta externa (Codex,
  outro agente de IA, **não Claude Code**) capturou o texto puro exato do campo `sign` antes de
  cifrar, durante um login real bem-sucedido pelo navegador contra a mesma unidade física/firmware
  (Archer C6 v2.0, `1.1.10 Build 20230830 rel.69433(5553)`):

  ```
  k=5945270769887026&i=3257785177414969&h=f6fdffe48c908deb0f4c3bd36c032e72&s=855135262
  ```

  (84 caracteres — a senha real usada no login nunca aparece em claro nesta captura, só via hash
  MD5; não foi compartilhada com este agente e não deveria ser.)

  Essa captura refuta a suposição presente desde a primeira rodada de que a chave/IV AES eram bytes
  binários aleatórios reais (`SecureRandom.nextBytes`) hex-encodados para virar `k=`/`i=`. O valor
  real: `k=5945270769887026` e `i=3257785177414969` são strings de **exatamente 16 caracteres, só
  dígitos decimais `0-9`** (nunca hex, que teria `a-f` misturado). Isso confirma que este firmware
  usa a variante **`EncryptionWrapperMR`** da lib de referência `tplinkrouterc6u` — distinta da
  `EncryptionWrapper` genérica que orientou as três rodadas anteriores: a chave/IV AES-128 são
  strings decimais de 16 caracteres usadas **diretamente como os 16 bytes ASCII/UTF-8** da chave e
  do IV, nunca decodificadas de hex, nunca bytes binários aleatórios convertidos para hex depois.

  `s=855135262` do exemplo capturado confirma matematicamente `seq (855134878) + tamanho do
  ciphertext AES em bytes (384) = 855135262` — a fórmula já implementada em
  `TpLinkStokLuciCrypto.buildSignPlaintext` estava correta e não precisou mudar. String de 84
  caracteres cifrada em pedaços de 53 bytes (RSA 512-bit, já implementado certo) → 2 pedaços
  (53+31) → 2 blocos de 64 bytes = 256 caracteres hex no `sign` final, batendo com o tamanho real já
  observado em capturas anteriores. `h=f6fdffe48c908deb0f4c3bd36c032e72` (MD5, 32 caracteres hex)
  não teve seu conteúdo exato confirmado nesta rodada — mantida a hipótese `md5(password)`; é a
  próxima (e provavelmente última) suspeita se esta correção sozinha não bastar.

  **Código alterado:** `TpLinkStokLuciCrypto` ganhou `AES_KEY_OR_IV_DIGIT_COUNT` (16) e
  `generateAesKeyOrIvDigits()` (gera uma string de 16 dígitos decimais via `SecureRandom`) —
  substitui o uso de `generateRandomBytes` + `bytesToHex` para a chave/IV desta plataforma
  (`generateRandomBytes`/`bytesToHex` continuam existindo como utilitários genéricos, usados agora
  só pelo fake de teste). `TpLinkStokLuciAuthenticationClient.login` gera duas strings decimais
  (chave e IV), converte cada uma para bytes via `Charsets.US_ASCII` para a `SecretKeySpec`/
  `IvParameterSpec` que cifra de fato o campo `data`, e passa as mesmas strings decimais (não hex)
  para `buildSignPlaintext` compor `k=`/`i=` do `sign` — garantindo que é literalmente a mesma
  chave/IV nos dois lugares. O hash `h=` continua `md5(password)`, sem alteração, documentado como
  próxima suspeita.

  Testes atualizados: `TpLinkStokLuciCryptoTest` ganhou `generateAesKeyOrIvDigits produces exactly
  16 decimal ASCII digits...`, `...round-trips through AES-CBC` (confirma que a string decimal usada
  como bytes ASCII cifra/decifra corretamente) e `buildSignPlaintext matches the shape of the real
  sign plaintext captured byte by byte externally` (reproduz a forma exata do `sign` capturado, sem
  usar a senha real). `TpLinkStokLuciAuthenticationClientTest` ganhou um teste que roda o login
  completo contra o fake e confirma que a chave/IV extraída do `sign` decifrado é uma string de 16
  dígitos decimais. `FakeTpLinkStokLuciHttpTransport` foi ajustado para extrair `k=`/`i=` como
  dígitos decimais (regex `\d+` em vez de `[0-9a-f]+`) e expor `lastCapturedAesKeyDigits`/
  `lastCapturedAesIvDigits` para asserção nos testes.

  Novo manifesto `catalog-2026.07.19.json` (`previousManifest: catalog-2026.07.18.json`): só o
  profile `tplink_archer_c6_stok_v1` foi alterado — nova entrada `fingerprintEvidence[]` tipo
  `auth_mechanism` (HIGH, 0.75) documenta a captura byte a byte externa; novo item em
  `knownFirmwareBugs[]` documenta a lição (segundo erro independente pode produzir o mesmo sintoma
  de falha que o primeiro); `stageReason`/`physicalTestAccessNote` atualizados;
  `confidenceScoreOverall` sobe de `0.5` para `0.55`. `stage` permanece `DISCOVERY_ONLY` até o
  próximo teste real (`gradlew :core:tplinkC6StokManualCheck`) confirmar login bem-sucedido com a
  chave/IV decimal corrigida — não promover sem esse teste (`/ciclo-vida-driver`).
  `loadEmbeddedCatalogResource()` (default de `DriverRegistry.kt`) atualizado para apontar para o
  novo manifesto.

- **2026-07-07 (terceira rodada, evidência DEFINITIVA via Playwright — reposição de `form=keys`,
  manifesto `catalog-2026.07.18.json`)** — A segunda rodada de correção (manifesto
  `catalog-2026.07.17.json`) tinha concluído, por engano, que o handshake do
  `TpLinkStokLuciAuthenticationClient` usa uma única chamada de preparação (`form=auth`) com uma
  única chave RSA reaproveitada para cifrar a senha e assinar o envelope `sign`. Essa conclusão foi
  baseada em captura **incompleta** feita com a extensão Chrome, que pulou a chamada `form=keys` por
  algum motivo de cache/estado do navegador naquela tentativa específica — não porque o protocolo
  real só tem uma chamada. Nesta rodada usamos **Playwright** (não mais a extensão Chrome) para
  abrir um navegador real, interceptar `page.on('response')` e capturar o corpo **completo** de
  request E response de cada chamada `cgi-bin/luci` durante um login real que teve sucesso total —
  inclusive chamadas autenticadas pós-login, com `stok` real funcionando. Essa captura completa
  **confirma que existem sim duas chamadas de preparação com duas chaves RSA distintas**, exatamente
  como a lib de referência `tplinkrouterc6u` sempre documentou: `form=keys` (`operation=read`)
  devolve `{"success":true,"data":{"password":[<256 hex>,"010001"],"mode":"router","username":""}}`
  — chave RSA 1024-bit usada só para cifrar a senha; `form=auth` (`operation=read`) devolve
  `{"success":true,"data":{"key":[<128 hex>,"010001"],"seq":<número>}}` — chave RSA 512-bit
  **diferente** da anterior, usada só para assinar o envelope `sign`. O tamanho de bloco do RSA em
  pedaços do `sign` (53 bytes) foi confirmado como corretamente derivado do tamanho real da chave de
  assinatura (512-bit = 64 bytes, menos 11 bytes de overhead PKCS1v1.5) — não é mais um valor
  arbitrário. A remoção do `&confirm=true` do corpo de login (correção da rodada anterior) permanece
  confirmada correta pela captura completa desta rodada. `TpLinkStokLuciAuthenticationClient`,
  `TpLinkStokLuciResponseParser` e `TpLinkStokLuciModels` foram corrigidos para repor a chamada a
  `form=keys` e usar as duas chaves distintas corretamente. `stage` permanece `DISCOVERY_ONLY` até o
  próximo teste real (`gradlew :core:tplinkC6StokManualCheck`) confirmar login bem-sucedido com esta
  implementação corrigida.

- **2026-07-07 (correção do corpo de login `tplink-stok-luci` — `&confirm=true` removido, senha
  cifrada em RSA confirmada byte a byte, manifesto `catalog-2026.07.17.json`)** — Segunda rodada de
  correção do `TpLinkStokLuciDriverFamily`. A correção anterior (envelope `sign`/`data`, uma única
  chamada a `form=auth`) ainda falhava com `INVALID_CREDENTIALS`/HTTP 403 contra o hardware físico
  do Luiz. Causa raiz encontrada por evidência ao vivo mais precisa: hook real instalado em
  `CryptoJS.AES.encrypt` na própria página do equipamento (não mais interceptação de
  `XMLHttpRequest`), capturando o texto plano exato que entra no AES durante um login real
  bem-sucedido pelo navegador. O texto plano é exatamente `operation=login&password=<256 caracteres
  hex>` — **sem** `&confirm=true` (tamanho total capturado, 281 caracteres, bate exatamente com
  `"operation=login&password="` de 25 caracteres + 256 caracteres hex). Os 256 caracteres hex do
  campo `password` são a senha **já cifrada em RSA** (saída de RSA de 1024 bits com a mesma chave de
  `form=auth`), não a senha em texto puro como a implementação anterior assumia por analogia com a
  lib de referência `tplinkrouterc6u`.

  `TpLinkStokLuciCrypto.buildLoginPlaintext` mudou de assinatura: recebe agora
  `rsaEncryptedPasswordHex` (a senha já cifrada em RSA, em hex), não mais a senha em texto puro, e
  monta só `operation=login&password=<rsaEncryptedPasswordHex>`, sem `confirm=true`.
  `TpLinkStokLuciAuthenticationClient.login` cifra a senha em RSA (mesma chave devolvida por
  `form=auth`, PKCS1v1.5) antes de montar o texto plano do login. A mesma captura ao vivo confirmou
  `keyWords: 4` no hook (CryptoJS usa palavras de 32 bits; 4 palavras = 16 bytes = 128 bits) →
  **AES-128**, nunca AES-256 — a implementação já usava `AES_KEY_SIZE_BYTES = 16`, então nenhuma
  mudança de código foi necessária aí, só a confirmação em KDoc.

  Testes atualizados: `TpLinkStokLuciCryptoTest` (`buildLoginPlaintext` agora testa o corpo com a
  senha já em RSA-hex, sem `confirm=true`). `TpLinkStokLuciAuthenticationClientTest` e
  `FakeTpLinkStokLuciHttpTransport` não precisaram de mudança — o fake decifra o envelope `sign`
  para extrair a chave/IV AES e nunca inspecionava o texto plano de `data` diretamente.

  Novo manifesto `catalog-2026.07.17.json` (`previousManifest: catalog-2026.07.16.json`): só o
  profile `tplink_archer_c6_stok_v1` foi alterado — a entrada de evidência anterior sobre a
  estrutura do texto plano de `data` (baseada só na lib `tplinkrouterc6u`, MEDIUM) foi rebaixada para
  LOW e marcada como parcialmente refutada; nova entrada `auth_mechanism` (HIGH) documenta o achado
  confirmado byte a byte; `stageReason`/`physicalTestAccessNote`/`knownFirmwareBugs[]` atualizados.
  `confidenceScoreOverall` permanece `0.4` — ainda não há execução real de login bem-sucedida com a
  implementação corrigida, só a correção da causa raiz do 403 anterior. `stage` permanece
  `DISCOVERY_ONLY` até o próximo teste real (`gradlew :core:tplinkC6StokManualCheck`) confirmar
  sucesso — não promover sem esse teste (`/ciclo-vida-driver`). `loadEmbeddedCatalogResource()`
  (default de `DriverRegistry.kt`) atualizado para apontar para o novo manifesto.

- **2026-07-07 (implementação de `TpLinkStokLuciDriverFamily` — protocolo entendido por pesquisa
  de terceiros, NUNCA testado contra hardware real)** — Implementa o login (passos 1-5 do
  handshake, ver abaixo) e uma leitura autenticada simples da plataforma `tplink-stok-luci`, pacote
  `core/driver/family/tplink/stokluci/` (`TpLinkStokLuciDriverFamily`,
  `TpLinkStokLuciAuthenticationClient`, `TpLinkStokLuciCrypto`, `TpLinkStokLuciResponseParser`,
  `TpLinkStokLuciDriverConfig`), seguindo exatamente o mesmo padrão arquitetural do
  `TpLinkLegacyCgiDriverFamily` (`DriverFamily`/`DriverFamilyFactory`, `HttpTransport` compartilhado,
  `DriverRetryPolicy`, `AuthenticationStrategy`). Registrado em `DriverFamilyRegistry`
  (`core/driver/family/DriverFamilies.kt`) sob a chave `"tplink-stok-luci-driver"`.

  Entendimento do protocolo vem da leitura direta do código-fonte real do pacote Python
  `tplinkrouterc6u` (PyPI, GPL-3.0) — classe `TplinkEncryption` (`tplinkrouterc6u/client/c6u.py`) e
  `EncryptionWrapper` (`tplinkrouterc6u/common/encryption.py`) — usado só como referência da
  existência/forma do protocolo, nunca copiado literalmente; a implementação Kotlin é original,
  usando `javax.crypto`/`java.security` do JDK como qualquer outra Authentication Strategy do
  projeto. Handshake de login implementado: (1) `POST /cgi-bin/luci/;stok=/login?form=keys` →
  chave RSA (módulo/expoente hex) para cifrar a senha; (2) `POST /cgi-bin/luci/;stok=/login?form=auth`
  → sequência + chave RSA de assinatura (diferente da do passo 1, guardada para uso futuro em
  chamadas autenticadas, não usada no login em si); (3) senha cifrada com RSA **PKCS#1 v1.5**
  (diferente do mecanismo antigo `tplink-encrypted-web`, que usa RSA sem padding); (4)
  `POST /cgi-bin/luci/;stok=/login?form=login`, corpo `operation=login&password=<hex>&confirm=true`
  — sem campo de usuário, batendo com a evidência real já capturada; (5) sucesso extrai `stok` do
  corpo JSON e `sysauth` do header `Set-Cookie` (via regex, não parser de cookie genérico).

  **Escopo desta entrega e o que ficou de fora:** só os passos 1-5 (login) mais uma leitura
  autenticada simples (`readStatusRaw`, endpoint `admin/status?form=all&operation=read`, sem
  envelope AES/assinatura) foram implementados. A etapa 6 completa (chamadas autenticadas com
  envelope AES-CBC de chave/IV por sessão + campo `sign` assinado com a chave RSA do passo 2, em
  pedaços de 53 bytes) fica documentada em KDoc mas não implementada — próximo passo. A terceira
  geração de firmware que a mesma pesquisa de terceiros documenta (`TplinkRouterV1_11`,
  autenticação só-RSA sem AES, distinguível pelo tamanho da chave RSA da senha: >=512 chars hex =
  2048-bit vs. <512 = 1024-bit do mecanismo aqui implementado) não foi implementada — só registrada
  como nota de risco no catálogo, caso o teste real revele que é essa a variante correta.

  **Nunca testado contra hardware real.** `profile.tplink_archer_c6_stok_v1.stage` permanece
  `DISCOVERY_ONLY` — a implementação existe e está coberta por testes com fake de transporte
  (`TpLinkStokLuciAuthenticationClientTest`, `TpLinkStokLuciDriverFamilyTest`,
  `TpLinkStokLuciCryptoTest`, 23 testes novos), mas nenhuma execução real de login aconteceu ainda.
  `driverConfig` do profile ganhou o primeiro schema desta plataforma (`statusReadPath`,
  `statusReadQuery`). `ManualCheckRunner` (`core/tooling/ManualCheckRunner.kt`) ganhou um branch
  novo (`runTplinkC6Stok`) e a task Gradle `tplinkC6StokManualCheck`
  (`gradlew :core:tplinkC6StokManualCheck --args="<ip> <usuario>"`) — comando a rodar quando o Luiz
  quiser validar o login contra a unidade física real; o resultado (sucesso ou falha) deve ser
  reportado para atualizar `stage`/`fingerprintEvidence[]`/`confidenceScoreOverall` do profile, não
  promovido sem esse teste (`/ciclo-vida-driver`).

  Novo manifesto `catalog-2026.07.15.json` (`previousManifest: catalog-2026.07.14.json`): só o
  profile `tplink_archer_c6_stok_v1` foi alterado — ganhou `driverConfig`, duas novas entradas de
  `fingerprintEvidence[]` do tipo `auth_mechanism` (o handshake detalhado entendido via leitura de
  código de terceiros, e a nota sobre a terceira geração de firmware não implementada), e
  `stageReason`/`confidenceScoreOverallNote` atualizados para refletir que a implementação existe
  mas segue sem validação real. `confidenceScoreOverall` permanece `0.35` — a heurística de score
  não tem categoria para "existe implementação", só para evidência real contra o equipamento, então
  nenhuma categoria muda até o teste real acontecer. `loadEmbeddedCatalogResource()` (default de
  `DriverRegistry.kt`) atualizado para apontar para o novo manifesto, seguindo a rede de segurança
  já existente (`DriverRegistryTest`, "default embedded manifest is the newest catalog file in
  resources") que detecta esse tipo de drift automaticamente.

- **2026-07-07 (TP-Link Archer C6 tem duas plataformas por firmware — refutação real + profile
  novo `tplink_archer_c6_stok_v1`)** — Teste real contra a unidade física de teste do Luiz (Archer
  C6, recém resetada de fábrica, IP `192.168.0.1`) refutou o mecanismo que o profile
  `tplink_archer_c6_v1` (driver atual `TplinkOntDriver`/`TplinkAuthenticationClient`, "web
  encrypted password": RSA sem padding + AES via `POST /cgi/getParm` + `POST /cgi_gdpr`) descreve:
  `POST /cgi/getParm` devolveu HTTP 404 — o endpoint não existe neste firmware. Investigação
  subsequente (probes passivos reais, sem credencial, mais pesquisa comunitária) revelou que esta
  unidade roda um mecanismo de login completamente diferente, do tipo `stok`/luci. Novo manifesto
  `catalog-2026.07.14.json` (`previousManifest: catalog-2026.07.13.json`):
  - **`tplink_archer_c6_v1` (inalterado em `stage`, continua `DRAFT`)**: ganhou uma entrada nova em
    `fingerprintEvidence[]` do tipo `auth_mechanism` com `confidenceLevel: REFUTED`, documentando o
    HTTP 404 real contra a unidade do Luiz; `knownFirmwareBugs[]` ganhou uma entrada confirmada
    documentando que pelo menos uma geração de firmware da linha Archer C6 abandona completamente
    o mecanismo "Web Encrypted Password" em favor do mecanismo `stok`/luci; `physicalTestAccess`
    volta para `true` (o Luiz tem uma unidade Archer C6 física real, só que ela não roda este
    mecanismo específico); `confidenceScoreOverall` recalculado de `0.4` para `0.35` (a categoria
    "autenticação testada" não pode mais contribuir em cenário otimista, já que a única execução
    real terminou em refutação, não em ausência de teste). Nenhuma capability nem `stage` mudou
    além do necessário para registrar esta evidência negativa — o profile segue `DRAFT`.
  - **`tplink_archer_c6_stok_v1` (novo, `DISCOVERY_ONLY`)**: mesma família comercial (`vendor:
    "TP-Link"`, `model: "Archer C6"`), plataforma tecnológica diferente —
    `platformId: "tplink-stok-luci"`, `driverFamilyId: "tplink-stok-luci-driver"` (driver ainda não
    implementado, é só o identificador previsto). `stage: "DISCOVERY_ONLY"` pelo mesmo critério já
    aplicado ao driver Nokia: houve contato de rede real e documentado (probes sem credencial)
    antes de qualquer tentativa de autenticação. Evidência real capturada em 2026-07-07, toda sem
    credencial: `POST /cgi/getParm` → HTTP 404 (controle negativo, motivou a investigação);
    `GET /` → HTTP 200, sem header `Server`, redireciona via meta-refresh para
    `/webpages/login.html`; `GET /webpages/login.html` → HTTP 200, título genérico `Opening...`,
    scripts `tpEncrypt.js`/`cryptoJS.min.js` (cifra client-side própria, diferente de
    `TplinkAuthCrypto`), quatro formulários (`form-first-login`, `form-login`, `form-login-bind`,
    `form-forget-password`) todos com `action="/cgi-bin/luci"` e **nenhum com campo de usuário** —
    autenticação só por senha. Evidência complementar de pesquisa comunitária (não teste real):
    pacote `tplinkrouterc6u`/`home-assistant-tplink-router` (sucessor de
    `AlexandrErohin/TP-Link-Archer-C6U`, já citado em `driver-adoption-strategy.md`) documenta duas
    gerações de login para o mesmo hardware — a antiga ("Web Encrypted Password") e uma nova via
    `POST /cgi-bin/luci/;stok=/login?form=login` com corpo JSON `sign`/`data`, chaves buscadas em
    `GET/POST /cgi-bin/luci/;stok=/login?form=keys`, sessão via token `stok` + cookie `sysauth`; uma
    issue aberta no repositório (`home-assistant-tplink-router#31`) afirma que firmwares mais novos
    não suportam mais Web Encrypted Password. Todas as capabilities ficam `UNKNOWN` (nenhuma
    leitura autenticada ainda). `physicalTestAccess: true` (mesma unidade física do Luiz).
    `confidenceScoreOverall: 0.35` (evidência de endpoint/estrutura real + evidência comunitária
    forte, mas zero autenticação testada, por design de `DISCOVERY_ONLY`).

  Este é o primeiro caso real (não hipotético) em que o mesmo vendor+modelo comercial exige dois
  profiles distintos por divergência genuina de plataforma entre gerações de firmware — documentado
  em detalhe em `docs/architecture/hal-layering-model.md`, nova seção "Caso real — TP-Link Archer C6
  com duas plataformas por firmware". Gap de `DriverRegistry.findProfile(vendor, model)` (assumia um
  único profile por vendor+modelo, ficando ambíguo com os dois profiles TP-Link/Archer C6) já
  **corrigido** — ver "Gap corrigido" no mesmo documento: `DriverRegistry` ganhou
  `findProfiles(vendor, model)`, que devolve todos os matches. Nenhum código Kotlin de driver foi
  implementado ou alterado nesta rodada (`core/driver/`, `core/auth/` intocados) — isso é trabalho de
  catálogo/pesquisa, esperando por teste real de login bem-sucedido antes de
  qualquer implementação de Driver Family nova.

- **2026-07-07 (nota de risco de `driverConfig`, revisão de segurança da Marisa)** — Adiciona a
  seção "Riscos — `driverConfig` como superfície futura de dado não confiável" acima, ressalva
  obrigatória da revisão de segurança do passo 4 do plano de refatoração HAL (reorganização do C20
  como `TpLinkLegacyCgiDriverFamily`). Sem mudança de stage, capability ou comportamento de driver
  — só documentação do gate exigido antes de qualquer `RemoteCatalogSource` real. Espelhado em
  `SECURITY.md`, seção "Catalog integrity".
- **2026-07-07 (driverConfig do TP-Link C20 — passo 4 do plano de refatoração HAL, caso de
  validação da arquitetura)** — Implementa o passo 4 de `docs/architecture/hal-layering-model.md`
  §10/§11.3: reorganiza `TplinkC20OntDriver`/`TplinkC20AuthenticationClient`/
  `TplinkC20ResponseParser`/`TplinkC20Models` (pacote `driver/tplink/`) como a primeira Driver
  Family real, `TpLinkLegacyCgiDriverFamily` (pacote `driver/family/tplink/legacycgi/`), sem
  mudança de protocolo/autenticação/retry/capabilities — mesmo comportamento observável de antes,
  só reorganização estrutural. Ponto central desta entrega: os literais de seção/campo antes
  hardcoded em `TplinkC20OntDriver.readSnapshot()` (ex.: `listOf("LAN_WLAN" to listOf("name",
  "SSID"))`) e a constante `TplinkC20AuthenticationClient.LOGIN_VALIDATION_SECTIONS` saem do código
  e passam a vir de `profile.driverConfig`, seguindo este schema concreto (opaco para o resto do
  catálogo — só `TpLinkLegacyCgiDriverFamilyFactory` interpreta):

  ```jsonc
  "driverConfig": {
    // Bundle único usado tanto para validar a credencial (não há endpoint de login dedicado
    // neste protocolo) quanto para a leitura de device info — nunca deve divergir do único
    // bundle com prova real de sucesso.
    "loginValidationBundle": {
      "sections": [
        {"section": "IGD_DEV_INFO", "fields": ["modelName", "description", "X_TP_isFD"]},
        {"section": "ETH_SWITCH", "fields": ["numberOfVirtualPorts"]},
        {"section": "SYS_MODE", "fields": ["mode"]},
        {"section": "/cgi/info", "fields": []}
      ]
    },
    // Índice posicional de cada seção dentro de loginValidationBundle.sections, usado pelo
    // parser para reencontrar o bloco certo na resposta (protocolo indexa por posição, não por
    // nome de seção).
    "deviceInfoIndex": 0,
    "ethSwitchIndex": 1,
    "sysModeIndex": 2,
    "wifiStatusBundle": {
      "sections": [{"section": "LAN_WLAN", "fields": ["name", "SSID"]}]
    },
    "wifiStatusIndex": 0,
    "connectedClientsBundle": {
      "sections": [
        {"section": "LAN_HOST_ENTRY", "fields": ["leaseTimeRemaining", "MACAddress", "hostName", "IPAddress"]}
      ]
    },
    "connectedClientsIndex": 0
  }
  ```

  Um segundo profile no mesmo protocolo (ex.: Archer C50 V2, citado como exemplo em
  `hal-layering-model.md` §9) só precisaria de um `driverConfig` próprio com os nomes de
  seção/campo daquele modelo — zero Kotlin novo. Novo manifesto `catalog-2026.07.13.json`
  (`previousManifest: catalog-2026.07.12.json`): só o profile `tplink_archer_c20_v1` ganhou
  `driverConfig` preenchido (replicando literalmente os valores antes hardcoded no driver); nenhum
  outro campo, evidência, capability ou `stage` foi alterado — esta reorganização não é promoção de
  estágio. `DriverFamilyRegistry` (`core/catalog/DriverFamilyRegistry.kt`, infraestrutura do passo
  6) ganhou sua primeira composição real: `com.nethal.core.driver.family.defaultDriverFamilyRegistry()`
  registra `TpLinkLegacyCgiDriverFamilyFactory` sob a chave `"tplink-legacy-cgi-driver"` — o fluxo
  completo (`hal-layering-model.md` §8: Profile → `DriverFamilyRegistry.resolve` → instância →
  leitura) foi verificado ponta a ponta por um teste de integração novo
  (`DriverFamilyRegistryIntegrationTest`) que carrega o profile real do catálogo embarcado, resolve
  a Driver Family via registry e lê um snapshot completo com transporte fake.
  `ManualCheckRunnerC20.kt` também foi atualizado para resolver o profile via `DriverRegistry` e
  instanciar a Driver Family via `DriverFamilyRegistry`, em vez de construir o driver antigo
  diretamente. Achado incidental durante esta entrega (não é mudança de comportamento de
  driver/protocolo, é gap de modelo de dados): os manifestos `catalog-2026.07.11` a
  `catalog-2026.07.13` já usavam os valores `"REFUTED"` (`FingerprintConfidenceLevel`) e
  `"vendor_class_reference"` (`FingerprintEvidenceType`) em `fingerprintEvidence[]`, mas nenhum dos
  dois existia no enum Kotlin correspondente — nenhum teste carregava essas versões via
  `DefaultDriverRegistry` até o teste de integração novo desta entrega, então o gap nunca havia
  quebrado nada em CI. Ambos os valores foram adicionados aos enums (`CompatibilityCatalog.kt`)
  para o catálogo real carregar sem erro — dado já existente nos manifestos publicados, não uma
  capability ou comportamento novo.
- **2026-07-07 (extensão de schema — passo 5 do plano de refatoração HAL)** — Implementa o passo 5 de
  `docs/architecture/hal-layering-model.md` §10/§11.3: estende `CompatibilityProfile` com três campos
  novos, preparando o catálogo para a camada de Driver Family que será introduzida nos passos 4 e 6
  (ainda não executados). Mudanças de schema: (1) campo `family` renomeado para `productLine` — mesma
  semântica de sempre (linha de produto comercial), só renomeado para não colidir com o novo
  `driverFamilyId` (colisão de nome apontada em `hal-layering-model.md` §3 item 7); (2) novo campo
  obrigatório `platformId` (string simples, sem tipo Kotlin — decisão explícita do Luiz de não criar
  abstração de `Platform` nesta rodada, §11.1); (3) novo campo obrigatório `driverFamilyId` (string
  simples, ainda **sem nenhuma resolução de código** — é só o nome que a Driver Family correspondente
  terá quando for criada no passo 4/§10); (4) novo campo opcional `driverConfig` (`JsonElement`,
  default `null`) — payload opaco de configuração de driver, deliberadamente sem schema comum entre
  plataformas. Novo manifesto `catalog-2026.07.12.json` (`previousManifest: catalog-2026.07.11.json`):
  os três profiles existentes (`nokia_g1425gb_v1`, `tplink_archer_c6_v1`, `tplink_archer_c20_v1`)
  ganharam `platformId`/`driverFamilyId` (`driverConfig` deixado no default `null`, pois nenhuma
  Driver Family existe ainda para consumi-lo) e tiveram `family` renomeado para `productLine` — nenhum
  outro campo, evidência, capability ou `stage` foi alterado nesta rodada. Valores escolhidos:
  Nokia G-1425G-B → `platformId: "nokia-gpon-rsa-aes"` / `driverFamilyId: "nokia-ont-gpon-driver"`;
  TP-Link Archer C6 → `platformId: "tplink-encrypted-web"` / `driverFamilyId:
  "tplink-encrypted-web-driver"`; TP-Link Archer C20 → `platformId: "tplink-legacy-cgi"` /
  `driverFamilyId: "tplink-legacy-cgi-driver"` — todos os seis valores replicam literalmente os
  identificadores de exemplo já usados em `hal-layering-model.md` §5.2/§5.5/§7, para manter
  catálogo e arquitetura no mesmo vocabulário desde o primeiro dia. Nenhuma classe `DriverFamily`,
  `DriverFamilyFactory` ou `DriverFamilyRegistry` foi criada nesta entrega — isso é escopo dos passos
  4 e 6 do plano de refatoração, ainda não executados; `driverFamilyId` por enquanto é só dado.
- **2026-07-09 (promoção para READ_ONLY_ALPHA, decisão do Rafael)** — Segunda execução real de
  `nokiaManualCheck` (mesma unidade física do Luiz, SIG-333) trouxe o probe passivo real que faltava
  desde a correção de sequência do dia anterior: título HTML da tela de login capturado —
  `html_title` = "GPON Home Gateway". O header `Server` foi verificado e confirmado como genuinamente
  ausente na resposta deste firmware (não é lacuna de captura, é fato do servidor HTTP). Novo
  manifesto `catalog-2026.07.09.json` (`previousManifest: catalog-2026.07.08.json`): `stage` do
  profile `nokia_g1425gb_v1` avança de `DISCOVERY_ONLY` para `READ_ONLY_ALPHA`, cumprindo a
  aprovação condicionada do Rafael ("assim que o dado real da página de login chegar"). Evidência
  completa: (1) probe passivo real (título capturado sem autenticação), (2) leitura autenticada real
  dos 4 endpoints já validada na execução anterior e reconfirmada nesta segunda chamada com uptimes
  coerentemente incrementados, (3) duas execuções reais consistentes contra a mesma unidade física.
  `confidenceScoreOverall` recalculado de `0.85` para `0.9` — a categoria "match de headers/banners
  reais" (0,25) passa a contribuir porque `FingerprintEngine.matchesHeaderOrBanner` aceita título OU
  header como critério de match, e o título bateu. Cálculo somaria 1.00 se todas as seis categorias
  fossem levadas ao teto simultaneamente pela primeira vez; arredondado para `0.9` por prudência
  editorial (nenhuma das duas execuções cobriu cenário de erro/timeout real, e a faixa `>0.90` da
  heurística de score é reservada para decisões de risco mais alto, não aplicável a um profile ainda
  só-leitura). Nenhuma capability de escrita foi implementada ou proposta.
- **2026-07-08 (correção de sequência de estágio, decisão do Rafael)** — O `stage` do profile
  `nokia_g1425gb_v1` avançou incorretamente na entrega anterior deste mesmo dia: o critério
  documentado de `DRAFT → DISCOVERY_ONLY` (primeiro probe passivo real, título HTML e headers
  capturados) nunca havia sido cumprido — os campos `html_title`/`http_headers` de
  `fingerprintEvidence[]` continuavam `value: null` mesmo após o login autenticado real de
  `nokiaManualCheck`. Rafael determinou que o ciclo é sequencial e não pode pular etapas: "
  `DISCOVERY_ONLY` não é sobre completude da evidência, é sobre ter havido contato de rede real e
  documentado antes de autenticar". Correção aplicada: `NokiaAuthenticationClient.login()` já fazia
  um GET real na raiz do equipamento (para extrair `pubkey`/`nonce`/`csrf_token`) — este GET agora
  também expõe título HTML e header `Server` como `NokiaDriverSnapshot.loginPageEvidence`
  (`NokiaModels.kt`), impresso pelo `ManualCheckRunner` como "Evidência de fingerprint (Tela de
  login)". Não é uma chamada de rede nova, só exposição de dado já obtido em memória. Com isso, o
  `stage` do profile passa a `DISCOVERY_ONLY` (não `READ_ONLY_ALPHA`) nesta correção — os campos
  `html_title`/`http_headers` continuam `value: null` no manifesto até o Luiz rodar
  `nokiaManualCheck` novamente (mesmo comando de sempre) e reportar o título/header reais
  capturados pela nova instrumentação. Assim que isso acontecer, e dado que a leitura autenticada
  dos 4 endpoints já está validada por execução anterior, a promoção para `READ_ONLY_ALPHA` pode
  ocorrer no mesmo ciclo, por decisão do Rafael.
- **2026-07-08** — Primeira leitura autenticada real do próprio NetHAL contra a unidade física
  Nokia G-1425G-B, via `nokiaManualCheck` (SIG-333), fechando parcialmente o critério documentado
  para `DISCOVERY_ONLY → READ_ONLY_ALPHA` (ver correção de sequência acima — o probe passivo de
  `DISCOVERY_ONLY` ainda faltava). Novo manifesto `catalog-2026.07.08.json`
  (`previousManifest: catalog-2026.07.07.json`): `firmwareKnown` do profile `nokia_g1425gb_v1`
  passa a incluir `softwareVersion=3FE49568IJJJ09` / `hardwareVersion=3FE49937ADAA`; as 5
  capabilities de leitura sobem de `EXPERIMENTAL` para `AVAILABLE`; `confidenceScoreOverall` sobe
  de `0.55` para `0.85` (recálculo item a item na nota do próprio manifesto). Documentada
  nota de mapeamento `manufacturer=ALCL` (herança Alcatel-Lucent) vs. nome comercial `Nokia`.
- **2026-07-07** — Corrigido modelo Nokia de `G-1425G-A` para `G-1425G-B` (a unidade física de
  teste do NetHAL, confirmada pelo Luiz, é o G-1425G-B; o manifesto anterior pesquisou o modelo
  errado). Novo manifesto `catalog-2026.07.07.json` (`previousManifest: catalog-2026.07.06.json`)
  com `profileId` novo (`nokia_g1425ga_v1` → `nokia_g1425gb_v1`), evidência de fingerprint agora
  citando o driver de produção do SignallQ como fonte, e `confidenceScoreOverall` recalculado de
  `0.25` para `0.55`. Implementado driver Nokia real (leitura, 4 endpoints) em
  `core/src/main/kotlin/com/nethal/core/driver/nokia/`. Profile TP-Link mantido inalterado.
- **2026-07-06** — Manifesto inicial (`catalog-2026.07.06.json`), dois profiles em `DRAFT`
  (Nokia G-1425G-A — modelo incorreto — e TP-Link Archer C6), evidência 100% documental.
