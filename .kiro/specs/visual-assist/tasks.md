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
  - [ ] 5.1 Definir `VisionDetector`, `CameraFrame`, `Detection` e `VisionPipeline`
    - Inferência em coroutine separada do render
    - _Requisitos: 2.2_
  - [ ] 5.2 `ObjectDetector` com MediaPipe/ONNX Runtime + modelo em assets
    - Cálculo de posição relativa (esquerda/centro/direita, perto/longe)
    - Anúncio por áudio com debounce
    - _Requisitos: 2.1, 2.3, 2.4_
  - [ ] 5.3 `TextRecognizer` (OCR) com ML Kit, acionado sob demanda
    - Leitura via TTS; mensagem quando não há texto; pt/en
    - _Requisitos: 3.1, 3.2, 3.3, 3.4_
  - [ ] 5.4 `PersonDetector` (presença via MediaPipe, sem identificação)
    - Anúncio de presença/posição; sem persistência de rostos
    - _Requisitos: 4.1, 4.2, 4.3_

- [ ] 6. Descrição de cena via cloud (opcional)
  - [ ] 6.1 `CloudSceneDescriber` com consentimento e detecção de conectividade
    - Envio de JPEG a endpoint configurável; fallback on-device offline
    - _Requisitos: 6.1, 6.2, 6.3, 6.4_
  - [ ] 6.2 Endpoint de prototipagem via Ramalama (config de dev)
    - _Requisitos: 6.1_

- [ ] 7. Configurações e privacidade
  - [ ] 7.1 `SettingsStore` (verbosidade, limiar, idioma, cloud on/off, endpoint)
    - _Requisitos: 5.3, 6.3_
  - [ ] 7.2 Garantir não-persistência de frames e modo 100% offline padrão
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
