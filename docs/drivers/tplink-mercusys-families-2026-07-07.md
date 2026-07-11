# TP-Link/Mercusys por família de protocolo

## Famílias alteradas ou criadas

- `TpLinkStokLuciDriverFamily`
  - Mantida como a família do cluster `tplink-stok-luci` padrão (`/cgi-bin/luci`, `stok/sysauth`, `form=keys|auth|login`, `admin/*`).
  - A expansão nesta rodada ficou na camada de suporte/identificação: a nova matriz tipada vincula **70** modelos/revisões reais a esta família com `confidenceLevel` explícito, sem promovê-los automaticamente a profiles validados em runtime.
- `TpLinkGdprCgiDriverFamily`
  - Nova família para o ramo `/cgi_gdpr`, com handshake RSA + AES em variantes **CBC** e **GCM**.
  - Suporta os estilos de login observados no relatório:
    - `MR_QUERY_LOGIN`
    - `C50_GDPR_BODY_LOGIN`
    - `EX_JSON_GDPR_BODY_LOGIN`
  - A implementação atual cobre login + leitura autenticada bruta; `readCapability()` continua indisponível por falta de parser/capability validation em hardware real.
- `TpLinkXdrDsDriverFamily`
  - Nova família para o ramo XDR/R baseado em `/ds`.
  - Cobre os dois fluxos de autenticação observados no relatório:
    - login legado com password obfuscado
    - login com `get_encrypt_info` + `nonce` + `md5(password:nonce)`
  - A implementação atual cobre login + leitura JSON bruta de `/stok={stok}/ds`; `readCapability()` continua indisponível.

## Cobertura por família

- `tplink-stok-luci-driver`
  - **70** entradas na matriz.
  - Distribuição: `LAB_VALIDATED=4`, `CODE_REFERENCED=1`, `FAMILY_INFERRED=65`.
  - Exemplos:
    - `LAB_VALIDATED`: Archer C6 `v2.0`, `v3.0`, `v3.20`, `4.0`
    - `CODE_REFERENCED`: Archer C6U `v1.0`
    - `FAMILY_INFERRED`: cluster Archer/Mercusys listado no relatório sem amarração estática fora do README
- `tplink-gdpr-cgi-driver`
  - **22** entradas na matriz.
  - Distribuição: `CODE_REFERENCED=7`, `FAMILY_INFERRED=15`.
  - Modelos explicitamente referenciados em código/teste: Archer C50 `v4`, EX511 `v2.0`, M8550 `v1`, TL-WR841N `v14`, entre outros.
- `tplink-xdr-ds-driver`
  - **7** entradas na matriz.
  - Distribuição: `CODE_REFERENCED=3`, `FAMILY_INFERRED=4`.
  - Modelos explicitamente referenciados em código/teste: TL-7DR7270, TL-R470GP-AC `4.0`, TL-XDR3010 `V2`.

## Modelos excluídos ou pendentes

- Fora do core residencial atual, marcados `UNSUPPORTED`:
  - `EAP JSON endpoints`
  - `CPE data/*.json`
  - `Easy Smart switch CGI`
  - 5 linhas espúrias de README convertidas para `unsupported-readme-artifact`
- Protocolos distintos com evidência objetiva, mas ainda sem família no core:
  - `TP-Link SG/CE_RED stok-luci`
  - `TP-Link stok-luci senha específica`
  - `Plain CGI RSA login`
  - `VR generic getGDPRParm`
  - `Encrypted id-block text protocol`
  - `Classic WDR HTML+Basic`
  - `Mesh Deco/Halo`
- Esses ramos foram mantidos como `EXPERIMENTAL` na matriz, nunca como `CODE_REFERENCED` da LAB, porque a LAB ainda não tem driver family/core capability model correspondente para eles.

## Riscos conhecidos

- O catálogo principal de compatibilidade continua deliberadamente pequeno e físico-orientado. Nesta rodada ele **não** foi inflado com 163 profiles; a expansão entrou em uma matriz separada.
- `TpLinkGdprCgiDriverFamily` e `TpLinkXdrDsDriverFamily` estão honestamente limitadas a login + leitura bruta autenticada. Falta validação física antes de qualquer promoção de capability para `AVAILABLE`.
- O cluster `tplink-stok-luci` continua separado das variantes `SG/CE_RED` e `senha específica`, porque o relatório mostrou diferenças objetivas de autenticação e envelope.
- Não houve implementação ad hoc de capabilities fora do vocabulário atual da LAB (`VPN`, `SMS/USSD`, `LED`, `LTE metrics`, `switch stats`).

## Próximos passos de validação física

1. Validar um representante real do ramo `tplink-gdpr-cgi-driver` em **CBC**.
2. Validar um representante real do ramo `tplink-gdpr-cgi-driver` em **GCM**.
3. Validar um representante real do ramo `tplink-xdr-ds-driver`, cobrindo:
   - fluxo legado
   - fluxo com `get_encrypt_info`
4. Capturar payload bruto de leitura para cada família nova e só então promover parsers/capabilities específicas.
5. Reavaliar, com hardware real, se `SG/CE_RED stok-luci` e `stok-luci senha específica` podem convergir para uma variante interna da família `stok-luci` ou se exigem famílias próprias.

## Atualização 2026-07-10 (issue #20 — Bruno, completar as famílias órfãs, sem hardware físico)

Decisão do Luiz (plano de fundação HAL): as duas famílias permanecem no core (não removidas), e
ganham `authenticate()` real + parser experimental por capability, mas continuam sem promoção de
estágio — nenhum dos itens da lista acima ("Próximos passos de validação física") foi cumprido
nesta rodada, só documentação de protocolo (login) e reaproveitamento de gramática já confirmada em
`tplink-legacy-cgi` (leitura).

- `TpLinkGdprCgiDriverFamily`
  - `authenticate()` real implementado — mesmo molde de `TpLinkStokLuciDriverFamily` (issue #16):
    guarda o `TpLinkGdprCgiAuthenticationClient` autenticado numa instância, reaproveitado por
    `readCapability()` sem novo login a cada leitura.
  - `readCapability()` deixou de ser sempre `Unavailable`: para o estilo de login
    `C50_GDPR_BODY_LOGIN` (único com gramática de leitura inferível), `READ_WIFI_STATUS` e
    `READ_CONNECTED_CLIENTS` agora tentam um parser real
    (`TpLinkGdprCgiResponseParser.parseStackFields`), reaproveitando a mesma gramática de
    dispatcher clássico `[oid#stack]indice,qtd` + `campo=valor` já confirmada ao vivo para
    `tplink-legacy-cgi` — o próprio corpo de login `C50_GDPR_BODY_LOGIN` usa essa gramática. Nomes
    de oid/campo (`LAN_WLAN`→`name`/`SSID`, `LAN_HOST_ENTRY`→`hostName`/`IPAddress`/`MACAddress`)
    são inferência disclosed por analogia com `tplink-legacy-cgi`, **nunca confirmados contra este
    ramo especificamente**. Por isso o resultado nunca sobe além de `CapabilityState.EXPERIMENTAL`,
    mesmo quando o parser "funciona" contra fixture sintética de teste. `MR_QUERY_LOGIN`/
    `EX_JSON_GDPR_BODY_LOGIN` continuam retornando `Unavailable` explicando a falta de gramática de
    leitura documentada para esses estilos — nenhum campo foi inventado para eles.
  - `READ_WAN_STATUS`/`READ_LAN_STATUS`/`READ_DEVICE_INFO`/`READ_FIRMWARE` continuam `Unavailable`
    sem seção configurada: nenhuma base documental (nem em `tplink-legacy-cgi`, que também não
    confirmou seção de WAN) para inferir nome de oid/campo.
- `TpLinkXdrDsDriverFamily`
  - `authenticate()` real implementado (mesmo molde).
  - `readCapability()` passou a executar a leitura autenticada real via sessão cacheada (prova que
    transporte/sessão funcionam ponta a ponta), mas **deliberadamente não mapeia nenhum campo de
    capability** — diferente do `tplink-gdpr-cgi`, a superfície JSON de `/ds` não compartilha
    gramática confirmada com nenhuma outra família do NetHAL, só o campo `error_code` tem uso
    confirmado (no probe `get_encrypt_info` do login). Inventar nome de campo de resposta violaria a
    regra do projeto ("não prometer mais do que a evidência sustenta") — item 4 da lista acima
    continua pendente. Toda capability retorna `Unavailable`, com motivo distinguindo se a leitura
    em si teve sucesso (`error_code=0`) de uma falha de sessão/rede.
- Catálogo: dois profiles novos em `catalog-2026.07.26.json` (`tplink_archer_c50_v4` →
  `tplink-gdpr-cgi-driver`; `tplink_xdr3010_v2` → `tplink-xdr-ds-driver`), ambos `stage: DRAFT`,
  `physicalTestAccess: false`. Capabilities com parser real (`READ_WIFI_STATUS`/
  `READ_CONNECTED_CLIENTS` do C50) declaradas `EXPERIMENTAL`; as demais (incluindo **todas** as do
  XDR3010, por decisão explícita — ver `compatibility-catalog.md`) declaradas `UNKNOWN`. Ver detalhe
  completo e justificativa item a item em `docs/drivers/compatibility-catalog.md`.
- Sem device real, nenhuma promoção de estágio ocorreu nem pode ocorrer — os 5 itens de "Próximos
  passos de validação física" acima continuam abertos.
