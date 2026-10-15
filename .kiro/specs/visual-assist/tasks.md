# Plano de Implementação — App de Assistência Visual

- [x] 1. Estrutura base do projeto Spatial SDK
  - Configurar `build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml`, wrapper Gradle 9.4.1
  - Criar `AndroidManifest.xml` com permissões de câmera, passthrough, áudio
  - Validar build vazio com `./gradlew :app:assembleDebug` na distrobox quest-dev
  - _Requisitos: 8.1, 8.2_

- [x] 2. Activity e ciclo de vida
  - [x] 2.1 Implementar `VisualAssistActivity` estendendo `AppSystemActivity`
    - VRFeature, cena mínima, enablePassthrough(true), passthrough-contextual no manifest
    - _Requisitos: 1.5_
  - [x] 2.2 Fluxo de permissões de câmera com feedback por áudio
    - Solicitar CAMERA/HEADSET_CAMERA, tratar negação com retry
    - _Requisitos: 1.1, 1.2_

- [x] 3. Acesso à câmera (Passthrough Camera API)
  - [x] 3.1 Implementar `CameraController` sobre Camera2
    - Selecionar câmera de passthrough via vendor tags; escolher 1280x960 da lista
    - _Requisitos: 1.3, 1.4_
  - [x] 3.2 Entrega de frames + metadados (intrínsecos, pose, timestamp) via callback
    - Buffer de 1 frame, formato YUV420 → conversão para RGB/bitmap
    - _Requisitos: 1.3_

- [ ] 4. Sistema de áudio
  - [x] 4.1 Implementar `AudioFeedbackManager` com fila/prioridade/debounce/verbosidade
    - `AnnouncementQueue` (lógica pura testada). Ver descoberta: Quest não tem TTS do sistema.
    - _Requisitos: 5.1, 5.3, 5.4_
  - [x] 4.1b Integrar TTS OFFLINE (sherpa-onnx VITS/Piper pt-BR) + AudioTrack
    - Substitui android.speech.tts (não funciona no Quest). Modelo em assets/tts/.
    - _Requisitos: 5.1_
  - [ ] 4.2 Áudio espacial via Spatial SDK (beep direcional) — requer asset .wav mono 48kHz
    - _Requisitos: 5.2_
  - [x] 4.3 Testes unitários da fila e do debounce
    - _Requisitos: 5.4_

- [ ] 5. Pipeline de visão (on-device)
  - [x] 5.1 Definir `VisionDetector`, `CameraFrame`, `Detection` e `VisionPipeline`
    - Inferência em coroutine separada do render (drop de frames antigos, sem busy-loop)
    - _Requisitos: 2.2_
  - [x] 5.2 `ObjectDetector` com MediaPipe (EfficientDet-Lite0) + modelo em assets
    - Posição relativa (DirectionMapper); tradução COCO EN->PT; debounce por rótulo
    - VALIDADO no Quest: anuncia "cama à sua frente", "pessoa à sua direita", etc.
    - _Requisitos: 2.1, 2.3, 2.4_
  - [x] 5.3 `TextReader` (OCR) com ML Kit, acionado sob demanda (botão do painel)
    - Leitura via TTS (prioridade alta); "nenhum texto detectado" quando vazio; pt/en (latino)
    - _Requisitos: 3.1, 3.2, 3.3, 3.4_
  - [ ] 5.4 `PersonDetector` (presença via MediaPipe, sem identificação)
    - Anúncio de presença/posição; sem persistência de rostos
    - _Requisitos: 4.1, 4.2, 4.3_

- [x] 6. Descrição de cena via cloud (opcional)
  - [x] 6.1 `CloudSceneDescriber` com consentimento e detecção de conectividade
    - OpenAI-compatible (OkHttp); fallback on-device; tradução EN->PT (ML Kit)
    - _Requisitos: 6.1, 6.2, 6.3, 6.4_
  - [x] 6.2 Endpoint de prototipagem via Ramalama (config de dev)
    - config.json (editável via adb); VLM com mmproj (SmolVLM). VALIDADO no device.
    - _Requisitos: 6.1_

- [ ] 7. Configurações e privacidade
  - [x] 7.1 `SettingsStore` (verbosidade, limiar, on/off detecção, cloud on/off)
    - Persistência SharedPreferences; controles no painel; Settings puro testado
    - _Requisitos: 5.3, 6.3_
  - [x] 7.2 Não-persistência de frames e modo offline padrão (cloud OFF por default)
    - _Requisitos: 7.1, 7.2, 7.4_

- [ ] 8. Integração e validação no dispositivo
  - [x] 8.1 Deploy via `./gradlew :app:installDebug` com Quest em modo dev
    - VALIDADO no Quest 3 (Horizon OS v207): app abre, passthrough OK, TTS pt-BR fala.
    - _Requisitos: 8.3_
  - [ ] 8.2 Teste manual no headset: latência, conforto, precisão dos anúncios
    - Ajustar FPS de inferência (meta >= 10) e verbosidade
    - _Requisitos: 2.2_
  - [ ] 8.3 Documentar dados que saem do dispositivo (README de privacidade)
    - _Requisitos: 7.3_

- [~] 9. Controle acessível por gestos/botões
  Motivação: o usuário-alvo (deficiente visual) não consegue usar os botões do
  painel (precisam de visão para mirar). Os botões do painel continuam para
  depuração; a via de uso real é por controle físico (feito) e microgestures
  (pendente). Estratégia: validar primeiro por botões (mais confiável), depois
  migrar o mesmo fluxo para microgestures.
  - [x] 9.1 `AppAction` + `ButtonActionMapper` (lógica pura, testável)
    - Enum `AppAction` (DESCRIBE_SCENE, READ_TEXT, TOGGLE_DETECTION, REPEAT_LAST,
      TUTORIAL, CLOSE_APP). Mapper puro bit→ação, 7 testes JUnit. A mesma base
      serve para microgestures depois.
    - _Requisitos: R9_
  - [ ] 9.2 `GestureInputController` (microgestures) — PENDENTE
    - Registrar listener no `MicrogesturesSystem.addListener { state, isFist -> }`;
      traduzir com um `GestureActionMapper` análogo; remover no `onDestroy`.
    - _Requisitos: R9_
  - [x] 9.3 Integração na Activity + confirmação por voz
    - `ButtonInputSystem` (SystemBase, edge detection via `Controller.buttonState`)
      → `runAction()`. Confirmação falada por ação; REPEAT_LAST guarda a última
      fala (OCR/descrição/toggle). VALIDADO no device.
    - _Requisitos: R9, 5.1_
  - [x] 9.4 Tutorial de voz interativo (onboarding acessível)
    - Modo tutorial: cada botão só ANUNCIA sua função (não executa) até praticar
      todos. Fala como locução única pausada (contorna a fila curta). Durante o
      tutorial, detecções são silenciadas. Roda só na 1ª abertura
      (`onboardingDone`); depois, dica curta. Rechamável pelo clique do analógico
      esquerdo e por botão "Ouvir tutorial" em Ajustes. VALIDADO no device.
    - _Requisitos: R9_
  - [x] 9.5 Botões físicos do controle (via principal atual)
    - A→descrever · X→ler texto · B→liga/desliga detecção · Y→repetir ·
      analógico esq.→tutorial · analógico dir.→fechar (confirmação dupla).
      Gatilhos evitados (conflitam com seleção de UI). VALIDADO no device.
    - _Requisitos: R9_
  - [x] 9.5b HUD de acionamento no painel
    - Topo do painel mostra "Botão X → Ação" a cada acionamento (feedback visual
      imediato, independente do TTS). VALIDADO no device.
  - [ ] 9.6 Migrar o fluxo para microgestures e validar no device — PENDENTE
    - _Requisitos: R9_
