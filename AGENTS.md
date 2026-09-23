# AGENTS.md — Orientação para agentes de IA

App de **assistência visual para Meta Quest 3** (Meta Spatial SDK, Kotlin).
Auxilia pessoas com deficiência visual: câmera de passthrough → visão computacional
(objetos, OCR, presença de pessoas) → feedback por áudio (TTS + espacial).

## Antes de começar, leia
- `docs/TECHNICAL.md` — arquitetura, funcionalidades, tecnologias e roadmap.
- `.kiro/steering/build.md` — como buildar/testar/deployar e em quais containers.
- `.kiro/steering/environment.md` — versões fixadas e convenções.
- `.kiro/steering/maintenance.md` — OBRIGATÓRIO: atualizar doc/README a cada mudança.
- `.kiro/specs/visual-assist/{requirements,design,tasks}.md` — o que construir e por quê.

## Fluxo de trabalho
1. Escolha a próxima subtarefa não marcada em `.kiro/specs/visual-assist/tasks.md`.
2. Implemente seguindo o `design.md`; cada task referencia os requisitos que atende.
3. Separe lógica pura (testável) de código Android; adicione testes JUnit.
4. Build + testes na distrobox `quest-dev` (ver `build.md`). NUNCA rode Gradle no host.
5. Atualize `docs/TECHNICAL.md` e `README.md` (ver `maintenance.md`).
6. Marque a subtarefa como `[x]` em `tasks.md`. Commit + push (avisando o usuário).

## Restrições
- Host é imutável (Aurora/uBlue): não instale toolchain no host; use as distroboxes.
- Privacidade: sem persistir frames; detecção de pessoas só de presença (LGPD).
- Não rebaixe as versões fixadas do toolchain/SDK.

## Estado atual
- Núcleo validado no Quest 3: passthrough + câmera + detecção de objetos + voz pt-BR + painel de debug.
- Tarefas concluídas: 1–4 (base, Activity, câmera, áudio/TTS), 5.1–5.2 (pipeline, objetos).
- Próximas: 5.3 OCR, 5.4 pessoas, 4.2 áudio espacial, 6 cloud, 7 configurações.
