# ADR 0001 — Fronteira de sanitização: modelo do driver carrega dado bruto, sanitização é do Telemetry Collector

Status: Aceita (2026-07-07, Rafael).

## Contexto

`TpLinkStokLuciDriverFamily.readSnapshot()` (`core/src/main/kotlin/com/nethal/core/driver/family/tplink/stokluci/`)
introduziu 4 capabilities novas (`READ_WIFI_STATUS`, `READ_LAN_STATUS`, `READ_WAN_STATUS`,
`READ_CONNECTED_CLIENTS`) via `TpLinkStokLuciStatusParser`/`TpLinkStokLuciModels.kt`. O parser
aplicou sanitização de forma inconsistente, já dentro do modelo do driver:

- SSID vira hash SHA-256 irreversível (`ssidHash`).
- MAC fica mascarado, só OUI (`macAddressMasked`).
- IP de WAN/LAN e `hostname` dos clientes conectados (`access_devices_wired[]`) ficam em texto
  puro, no mesmo modelo.

Marisa aprovou sem bloqueio de segurança, mas levantou a inconsistência: por que só SSID/MAC são
tratados e IP/hostname não, se a justificativa é "sanitizar na origem"? A revisão apontou duas
leituras mutuamente exclusivas, sem decisão de Product Owner registrada até este ADR.

Hoje não existe Telemetry Collector implementado — `readSnapshot()` alimenta só
`ManualCheckRunner` (ferramenta de dev) e, no futuro, a tela do NetHAL Lab, para uso local do
próprio dono do equipamento vendo a própria rede. Não há upload para nuvem em nenhum ponto do
código atual.

## Decisão

**O modelo do driver carrega dado bruto. Sanitização é responsabilidade exclusiva do Telemetry
Collector (`docs/architecture/overview.md`), aplicada só no momento de exportação — se e quando
essa camada existir.**

Motivo:

- `docs/product/specification.md` §8.9 define a lista "não coletar"/"sempre mascarar" no contexto
  explícito do **Telemetry Collector** ("coleta dados para evolução de compatibilidade"), não do
  modelo de dados interno do driver. É regra de fronteira de exportação, não de representação
  local.
- NetHAL Lab é ferramenta local-first para o usuário diagnosticar a própria rede. Um SSID
  transformado em hash SHA-256 irreversível já no parser é dado inútil para esse propósito — o
  app nunca conseguiria mostrar ao usuário o nome real da própria rede Wi-Fi, quebrando o valor
  central da tela de diagnóstico. Isso não é característica de segurança, é perda de produto sem
  ganho correspondente (ninguém é protegido de si mesmo vendo o próprio SSID na própria tela).
  Hash irreversível faz sentido só se o destino for telemetria anônima agregada — não é o caso
  aqui.
- É consistente com a separação de componentes já desenhada em
  `docs/architecture/overview.md` (`Telemetry Collector` é uma caixa própria da pilha do SDK,
  separada de `Driver Registry`/`Capability Engine`) e com o princípio de camadas de
  `docs/architecture/hal-layering-model.md`: Driver Family não deveria carregar responsabilidade
  de uma camada que ainda nem existe.
- Mantém a regra dura de "nunca ler senha" (Wi-Fi PSK) intacta — essa é regra de coleta em si
  (nunca ler o dado, ponto), diferente de sanitização de dado que precisa ser lido para o
  diagnóstico funcionar (SSID, MAC, IP, hostname).

## Consequências

- Quando o Telemetry Collector for implementado, ele é o único ponto autorizado a aplicar hash de
  SSID, mascaramento de MAC/IP público e qualquer outra regra da spec §8.9, na fronteira de
  exportação — nunca antes disso. Enquanto essa camada não existir, nenhum dado de
  `TpLinkStokLuciSnapshot` pode ser enviado para fora do dispositivo por nenhum caminho.
- `TpLinkStokLuciWifiRadio.ssidHash` deve virar `ssid: String?` (dado real, sem hash).
- `TpLinkStokLuciLanStatus.macAddressMasked` e `TpLinkStokLuciConnectedClient.macAddressMasked`
  devem virar `macAddress: String?` (MAC completo, sem mascaramento).
- IP (`TpLinkStokLuciLanStatus.ipv4Address`, `TpLinkStokLuciWanStatus.ipv4Address`,
  `TpLinkStokLuciConnectedClient.ipAddress`) e `hostname` já estavam corretos (brutos) — mantidos
  como estão, sem mudança.
- Senha de Wi-Fi (`*_psk_key`) continua nunca lida pelo parser — isso não muda.
- Este padrão vale para toda Driver Family futura, não só `tplink-stok-luci`: nenhum parser de
  driver deve fazer hash/mascaramento próprio. Se algum campo genuinamente não pode ser lido em
  claro (ex.: exigência legal específica de algum mercado), isso é discussão de escopo de
  capability, não de sanitização ad hoc no parser — trazer para Rafael/Marisa antes de implementar.
- Não altera estágio do driver (`READ_ONLY_ALPHA` permanece). Promoção de estágio é decisão
  separada, fora do escopo deste ADR.

## Próximo passo

Diego ajusta `TpLinkStokLuciModels.kt` e `TpLinkStokLuciStatusParser.kt` conforme a lista de
consequências acima, atualiza os testes que hoje esperam `ssidHash`/`macAddressMasked`
(`core/src/test/kotlin/com/nethal/core/driver/family/tplink/stokluci/`) e atualiza o KDoc de
`TpLinkStokLuciModels.kt` que hoje descreve a sanitização como regra da spec §8.9 aplicada "já na
origem" — essa frase deve ser removida/corrigida para refletir esta decisão.

## Extensão 2026-07-08 (Caio, issue #16 — Capability Engine com sessão real)

Esta decisão se aplicava só ao modelo interno de `TpLinkStokLuciDriverFamily`
(`TpLinkStokLuciWifiRadio`/`TpLinkStokLuciLanStatus`/`TpLinkStokLuciConnectedClient`, todos
`internal`). O modelo **público** do SDK (`core/model/WifiStatus.kt`) ainda carregava o campo
`ssidHash: String?` — nunca usado por nenhum código até `TpLinkStokLuciDriverFamily.readCapability`
se tornar o primeiro leitor real a precisar devolver `WifiStatus` de verdade (ver
`core/model/CapabilityPayload.kt`). Mesmo raciocínio desta ADR, generalizado por ela mesma ("este
padrão vale para toda Driver Family futura, não só `tplink-stok-luci`") ao primeiro ponto de contato
real: `WifiRadio.ssidHash` renomeado para `ssid` (dado bruto), `docs/product/specification.md` §13
atualizada em conjunto. Não é uma decisão nova, é a aplicação da decisão já registrada aqui ao único
lugar que ainda não tinha sido ajustado porque nada o consumia ainda.
