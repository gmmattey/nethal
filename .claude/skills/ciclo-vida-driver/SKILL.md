---
name: ciclo-vida-driver
description: Estágios de um driver (DRAFT a STABLE), critérios objetivos de promoção e critérios para um driver entrar no SignallQ. Consultar antes de propor promoção de estágio ou declarar um driver pronto para uso além de laboratório.
---

Consulte os critérios de estágio relevantes para a tarefa abaixo:

$ARGUMENTS

Fonte completa: `docs/product/specification.md` §9, §16, `CONTRIBUTING.md`.

---

## Estágios

```text
DRAFT
DISCOVERY_ONLY
READ_ONLY_ALPHA
READ_ONLY_BETA
WRITE_BETA
STABLE
DEPRECATED
BLOCKED
```

Progressão é sempre sequencial. Nenhum driver pula de `DISCOVERY_ONLY` direto para `WRITE_BETA`.

## Critério para `STABLE`

- Pelo menos 20 testes bem-sucedidos.
- Cobertura de pelo menos 3 firmwares diferentes, ou firmware único com justificativa documentada.
- Taxa de falha crítica abaixo de 2%.
- Nenhuma ação registrada que derrube conectividade sem aviso.
- Documentação de capabilities completa.
- Fallback seguro implementado.
- Logs suficientes para diagnóstico.

## Transições entre estágios (guia de maturidade, não gate bloqueante)

- `DRAFT → DISCOVERY_ONLY`: driver identificado, sem ainda ler dado real do equipamento.
- `DISCOVERY_ONLY → READ_ONLY_ALPHA`: pelo menos um teste real (modelo + firmware) documentado por Caio.
- `READ_ONLY_ALPHA → READ_ONLY_BETA`: capabilities de leitura declaradas; Marisa confere telemetria no review normal.
- `READ_ONLY_BETA → WRITE_BETA`: toda capability de escrita tem confirmação explícita do usuário e passou pela revisão de QA da Marisa (parte do review, não sign-off bloqueante) — ver `/seguranca-nethal`.
- `WRITE_BETA → STABLE`: critérios objetivos acima cumpridos + decisão de produto do Rafael.
- Qualquer estágio `→ BLOCKED`: qualquer um do squad sinaliza risco de segurança a qualquer momento; o Rafael decide bloquear/reverter.

## Critérios para um driver entrar no SignallQ

Um driver NetHAL só é proposto para o SignallQ quando:

- Está marcado `STABLE`.
- Tem documentação de limitações.
- Não depende de fluxo frágil demais.
- Foi testado em massa real (não só em laboratório interno).
- Não exige permissões abusivas.
- Tem fallback quando falha.
- Não prejudica a experiência principal do SignallQ.
- Não promete controle universal.

A decisão final de propor um driver para o SignallQ é do Rafael, com a revisão de QA da Marisa como insumo.

## Limites

- Esta skill descreve a maturidade de driver — quem evidencia teste é Caio, quem revisa QA/segurança é Marisa, quem decide promoção é Rafael.
- Estágio declarado sem evidência documentada (teste real, revisão) não vale — sempre exigir rastro.
