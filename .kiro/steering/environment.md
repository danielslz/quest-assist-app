---
inclusion: always
---
# Convenções e versões do projeto

App Meta Quest 3 (Spatial SDK, Kotlin) de assistência visual. Specs completas em
`.kiro/specs/visual-assist/` (requirements, design, tasks).

## Versões fixadas (NÃO rebaixar)
- AGP 8.11.1 · Gradle 9.4.1 (wrapper) · Kotlin 2.1.0 · JDK 17 · NDK 27.0.12077973
- Spatial SDK 0.14.0 · compileSdk/targetSdk/minSdk = 34 · horizonos minSdkVersion 69
- Passthrough Camera API exige runtime Horizon OS **v74+** no headset

## Convenções de código
- Kotlin idiomático; 2 espaços de indentação (segue os samples da Meta).
- Lógica pura (testável) separada de código Android:
  ex. `ResolutionPolicy`, `AnnouncementQueue` não dependem de classes Android.
- Detectores implementam `VisionDetector`; inferência roda fora do thread de render.
- Toda saída ao usuário é por áudio (`AudioFeedbackManager`), nunca só visual.

## Privacidade (obrigatório — Requisito 7 e LGPD)
- NÃO persistir frames da câmera em disco.
- Detecção de pessoas é apenas de PRESENÇA; NUNCA identificar indivíduos.
- Modo cloud é opt-in explícito; app funciona 100% offline por padrão.

## Testes
- Adicione teste unitário (JUnit) para toda lógica pura nova.
- Rode `:app:testDebugUnitTest` na `quest-dev` antes de concluir uma task (ver build.md).
- Ao concluir uma subtarefa, marque `[x]` em `.kiro/specs/visual-assist/tasks.md`.
