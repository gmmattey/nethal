# SignallQ Nethal

**Network Hardware Abstraction Layer** (motor interno: NetHAL) — renomeado de "Nethal" em 2026-07-23.

Camada experimental para descobrir, identificar, consultar e controlar com segurança equipamentos da rede local, como roteadores, ONTs, ONUs, access points, sistemas mesh e gateways.

## Objetivo

Oferecer uma interface comum sobre diferentes fabricantes, firmwares e protocolos locais. As aplicações devem consultar capacidades detectadas, em vez de depender diretamente do nome de uma marca ou modelo.

## Relação com o SignallQ

Já faz parte da família de marca SignallQ (desde a renomeação de 2026-07-23), mas segue como
laboratório — não é ainda o produto final. Drivers que forem validados, estabilizados e
considerados seguros poderão ser integrados futuramente ao ecossistema SignallQ consumer.

## Escopo inicial

- Aplicativo laboratório Android
- Descoberta de equipamentos na rede local
- Identificação de fabricante, modelo e capacidades
- Drivers inicialmente somente leitura
- Telemetria sanitizada para compatibilidade
- Credenciais mantidas apenas durante a sessão
- Nenhum acesso remoto fora da rede local

## Estrutura

```text
core/   SDK Kotlin/JVM reutilizável, sem dependência de interface Android
app/    Aplicativo Android com Jetpack Compose para laboratório e validação
docs/   Produto, arquitetura, drivers, protocolos e regras de segurança
```

Pacotes atuais:

- Core: `com.nethal.core`
- Aplicativo de laboratório: `com.nethal.lab`

## Documentação

- [Especificação do produto](docs/product/specification.md)
- [Visão de arquitetura](docs/architecture/overview.md)
- [Modelo de drivers](docs/drivers/driver-model.md)
- [Roadmap](ROADMAP.md)
- [Contribuição](CONTRIBUTING.md)

## Princípios de segurança

- Priorizar leitura antes de qualquer ação de escrita.
- Não armazenar senhas de roteadores por padrão.
- Não executar ações destrutivas sem confirmação explícita.
- Não afirmar compatibilidade com um modelo sem evidência reproduzível.
- Sanitizar telemetria antes de qualquer envio externo.
- Manter o funcionamento restrito à rede local, salvo decisão arquitetural futura formalmente aprovada.

## Toolchain atual

- Kotlin 2.0.21
- Android Gradle Plugin 8.7.3
- Jetpack Compose
- `compileSdk` e `targetSdk` 35
- `minSdk` 26

## Status

Fundação inicial em desenvolvimento. O fluxo de onboarding e consentimento foi implementado, mas ainda não existe driver de produção considerado estável.

## Licença

Deve ser definida antes da aceitação de contribuições externas.