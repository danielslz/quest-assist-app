# AGENTS.md — Orientação para agentes de IA

App de **assistência visual para Meta Quest 3** (Meta Spatial SDK, Kotlin).
Auxilia pessoas com deficiência visual: câmera de passthrough → visão computacional
(objetos, OCR, presença de pessoas) → feedback por áudio (TTS + espacial).

## Antes de começar, leia
- `docs/TECHNICAL.md` — arquitetura, funcionalidades, tecnologias e roadmap.
- `docs/PRIVACY.md` — dados que saem do dispositivo e pendências de privacidade.
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

## Estado atual (2026-10-21)
Núcleo validado no Quest 3 (Horizon OS v207): passthrough + câmera + detecção de
objetos (voz pt-BR) + OCR sob demanda + descrição de cena via LLM + painel de
debug + controle acessível por botões.

Concluído:
- 1–4: base, Activity/permissões, câmera (Passthrough Camera API), áudio + TTS
  offline (sherpa-onnx pt-BR).
- 5.1–5.2: pipeline de visão, detecção de objetos (MediaPipe EfficientDet-Lite0).
- 5.3: OCR sob demanda (ML Kit).
- 5.4: presença de pessoas (`PersonPresenceTracker`, COCO person; sem rosto).
- 6: descrição de cena via LLM multimodal (OpenAI-compat; protótipo SmolVLM via
  Ramalama; tradução EN→PT offline; fallback on-device).
- 7: configurações (verbosidade, limiar, on/off detecção, cloud opt-in).
- 8.3: documento de privacidade (`docs/PRIVACY.md`); pendências em 8.3b.
- 9 (parcial): controle acessível por BOTÕES do controle + tutorial interativo
  falado + HUD. Validado no device.

Próximas:
- 9.2/9.6: migrar o acionamento para MICROGESTURES (toque/deslize do polegar via
  `MicrogesturesSystem`/`MicrogestureBits`), reusando `input/AppAction` e o
  `runAction` já prontos. Botões permanecem como alternativa.
- 5.5: distinguir o próprio braço/mão de pessoas reais (decisão pendente; ver tasks.md).
- 4.4: reduzir a latência da fala inicial (medições em tasks.md).
- 4.5: velocidade da fala ajustável pelo usuário, inclusive sem enxergar o painel.
- 4.2: áudio espacial (precisa de .wav). 8: empacotamento/publicação. 8.3b: pendências
  de privacidade (tradutor sob demanda, HTTPS).

Ver `docs/CONTINUITY.md` para setup de ambiente, Ramalama e teste no device.
