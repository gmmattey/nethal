# Correção — tokens de cor divergentes em `:core:designsystem`

Data: 2026-07-11
Autora: Vera (UX & Design)

## Contexto

Durante a extração mecânica de `Theme.kt`/`Color.kt` para `:core:designsystem` (PR #115, ADR
0002), o Caio identificou que o app implementado usa uma paleta teal/cyan
(`NetHalTeal #2B6E7A` / `NetHalCyan #35B8C0`, bg `#111417`) que diverge do design system oficial
documentado em `docs/design/design-system.dc.html` (accent Electric Blue `#006FFF`, bg `#0B0F19`).

## Investigação

- `docs/design/_archive/2026-07-11-design-v1/` guarda uma exploração de marca anterior (fonte
  Space Grotesk, diretriz "evitar cyberpunk") — não menciona teal/cyan como paleta final, e foi
  explicitamente substituída pelo design system atual em PR #65 ("docs(design): substitui
  docs/design pelo design system atual do NetHAL Lab").
- `git log` de `core/designsystem/.../theme/{Color,Theme}.kt` mostra um único commit
  (`7480305`, extração mecânica do PR #115) — sem histórico anterior de decisão deliberada de
  usar teal/cyan.
- Nenhum ADR, spec ou nota de produto em `docs/` registra teal/cyan como escolha de marca.
- `design-system.dc.html` (fonte da verdade, linha ~26) declara explicitamente: "cor é reservada
  a função (accent = interativo/dado primário, neon = estado)" — accent é `#006FFF` em todo o
  documento, sem menção a teal/cyan.

**Conclusão: divergência por desatualização, não decisão deliberada.** O app foi implementado
com uma paleta de trabalho anterior à consolidação do design system atual (PR #65) e nunca foi
sincronizado. Não há indício de mudança de marca intencional — portanto não é uma decisão que
precise subir para o Luiz.

## Correção aplicada

`core/designsystem/src/main/kotlin/com/nethal/core/designsystem/theme/Color.kt` e `Theme.kt`
atualizados para os tokens dark de `docs/design/design-system.dc.html`:

| Token | Antes | Depois |
|---|---|---|
| Accent/primary | `NetHalCyan #35B8C0` | `NetHalAccent #006FFF` |
| secondary | `NetHalTeal #2B6E7A` | `NetHalAccent #006FFF` (design system não define hue secundário distinto) |
| BG principal | `#111417` | `#0B0F19` |
| Surface/Card | `#1A1E22` | `#161B26` |
| Surface-2 | `#23282D` | `#1D2433` |
| Texto primário | `#E7EBEE` | `#E8ECF5` |
| Texto secundário | `#A9B2BA` | `#8891A8` |
| Erro | `#CF6679` | `#EF4444` |

Light theme não foi adicionado nesta correção — o app continua dark-only (`NetHalLabTheme` não
tem toggle), consistente com o design brief.

## Próximo passo

Caio: revisar visualmente as telas do Lab após o merge (screenshot/emulador) — a troca de accent
de cyan pra azul elétrico muda contraste em botões/ícones que dependiam da cor antiga. Se algum
componente ficar com contraste insuficiente contra `#0B0F19`, sinalizar pra mim antes de ajustar
localmente.
