Acho que essa documentação merece um nível acima de um simples `README`.

Eu criaria um documento chamado:

> **Network Device Connectivity Architecture (NDCA)**

Esse documento vira a "bíblia" de como o NETHAL conversa com equipamentos de rede. Ele orienta o desenvolvimento por anos.

A estrutura que eu faria seria algo assim:

* * *

docs/architecture/network-device-connectivity.md
================================================

1. Objetivo

-----------

Explicar como o NETHAL identifica, autentica e se comunica com equipamentos de rede locais.

* * *

2. Escopo

---------

Equipamentos suportados

* Roteadores

* ONTs

* ONUs

* Modems

* Mesh

* Access Points

* Gateways

* Repetidores

* * *

3. Fluxo de descoberta

----------------------

    Android conectado ao Wi-Fi
                │
                ▼
    Obter Gateway
                │
                ▼
    Descobrir protocolos
                │
                ▼
    Fingerprint
                │
                ▼
    Selecionar Driver
                │
                ▼
    Autenticar
                │
                ▼
    Ler Capabilities
                │
                ▼
    Executar ações

* * *

4. Protocolos suportados
   ========================

HTTP
----

Utilização

* páginas administrativas

* CGI

* REST

* login por formulário

Usado por

* Huawei

* ZTE

* TP-Link

* Intelbras

* ASUS

* Nokia

Capabilities

✓ leitura

✓ escrita

* * *

HTTPS
-----

Mesmo conceito.

Diferenças:

* certificado inválido

* self signed

* cookies

* csrf

* * *

UPnP / SSDP
-----------

Objetivo

Descoberta automática.

Permite

✓ identificar fabricante

✓ identificar modelo

✓ descobrir serviços

Normalmente

✗ não altera configuração

* * *

TR-064
------

SOAP local.

Excelente para

✓ Wi-Fi

✓ WAN

✓ reboot

✓ status

Muito usado por

FRITZ!Box

alguns Huawei

alguns ZTE

* * *

SNMP
----

Principalmente

leitura.

Excelente para

estatísticas

interfaces

CPU

memória

* * *

LuCI RPC
--------

OpenWrt

Muito poderoso.

Permite praticamente tudo.

* * *

SSH
---

Disponível em

OpenWrt

ASUS Merlin

MikroTik

Alguns Nokia

* * *

JSON-RPC
--------

Encontrado em diversos firmwares modernos.

* * *

5. Protocol Priority
   ====================
   
    TR-064
    ↓
    REST
    ↓
    LuCI
    ↓
    HTTP
    ↓
    SNMP
    ↓
    UPnP
    ↓
    SSH

* * *

6. Fabricantes
   ==============

Huawei
------

Modelos prioritários

HG8245H

HG8245Q2

EG8145V5

Protocolos

HTTP

HTTPS

UPnP

TR-064 (alguns)

REST privado

Capabilities esperadas

✓ WAN

✓ Wi-Fi

✓ Firmware

✓ Clientes

⚠ Canal

⚠ Senha

* * *

ZTE
---

Modelos

F660

F670L

H298A

Protocolos

HTTP

HTTPS

UPnP

SOAP

REST

* * *

TP-Link
-------

Modelos

Archer C6

Archer AX23

Archer AX55

Deco

Protocolos

HTTP

HTTPS

REST

UPnP

* * *

Intelbras
---------

Modelos

RX1500

WiFiber

Protocolos

HTTP

REST

UPnP

* * *

Nokia
-----

Modelos

Beacon

G2425

Protocolos

HTTP

HTTPS

SOAP

UPnP

* * *

ASUS
----

RT-AX58U

RT-AX88U

AiMesh

Protocolos

REST

HTTP

SSH

SNMP

* * *

MikroTik
--------

RouterOS API

SSH

HTTP

SNMP

* * *

OpenWrt
-------

LuCI

ubus

RPC

SSH

HTTP

* * *

7. Capabilities
   ===============
   
    READ_DEVICE_INFO
    READ_WAN
    READ_LAN
    READ_WIFI
    READ_WIFI_RADIOS
    READ_CLIENTS
    READ_FIRMWARE
    READ_CPU
    READ_MEMORY
    READ_SIGNAL
    READ_MESH
    READ_UPTIME
    SET_WIFI_CHANNEL
    SET_WIFI_PASSWORD
    SET_WIFI_NAME
    SET_DNS
    REBOOT
    RESTART_WIFI

* * *

8. Driver Selection
   ===================
   
    Gateway
    ↓
    HTTP Probe
    ↓
    Fingerprint
    ↓
    Protocol
    ↓
    Driver
    ↓
    Authentication
    ↓
    Capabilities

* * *

9. Driver Lifecycle
   ===================
   
    Draft
    ↓
    Discovery
    ↓
    Read Only
    ↓
    Read/Write Beta
    ↓
    Stable
    ↓
    Deprecated

* * *

10. Segurança
    =============

Nunca:

* salvar senha

* brute force

* explorar vulnerabilidades

* usar exploits

* acessar equipamentos sem autorização

Sempre:

* confirmação do usuário

* criptografia

* Android Keystore

* telemetria anonimizada

* * *

11. Compatibilidade
    ===================

Tabela enorme.

| Fabricante | Modelo   | HTTP | REST | TR-064 | SNMP | SSH | Driver |
| ---------- | -------- | ---- | ---- | ------ | ---- | --- | ------ |
| Huawei     | HG8245Q2 | ✓    | ⚠    | ⚠      | ✗    | ✗   | Beta   |
| Huawei     | EG8145V5 | ✓    | ⚠    | ⚠      | ✗    | ✗   | Beta   |
| ZTE        | F670L    | ✓    | ⚠    | ✗      | ✗    | ✗   | Alpha  |
| TP-Link    | AX23     | ✓    | ✓    | ✗      | ✗    | ✗   | Stable |
| ASUS       | RT-AX88U | ✓    | ✓    | ✗      | ✓    | ✓   | Stable |
| OpenWrt    | Diversos | ✓    | ✓    | ✗      | ✓    | ✓   | Stable |

* * *

12. Roadmap
    ===========

13. Discovery

14. Fingerprint

15. HTTP

16. UPnP

17. TR-064

18. LuCI

19. SNMP

20. SSH

21. Drivers Huawei

22. Drivers ZTE

23. Drivers TP-Link

24. Drivers Intelbras

25. Drivers Nokia

26. Drivers Mesh

27. Integração SignallQ

* * *

Eu iria além: transformaria esse documento na **especificação oficial da Connectivity Layer** do NETHAL, e o trataria como um documento "vivo". Conforme novos fabricantes, protocolos e drivers fossem homologados, ele seria atualizado e passaria a ser a principal referência técnica do projeto. Assim, qualquer desenvolvedor que entrar no NETHAL no futuro conseguirá entender rapidamente como o sistema descobre dispositivos, escolhe protocolos, autentica usuários e abstrai as diferenças entre centenas de equipamentos de rede.
