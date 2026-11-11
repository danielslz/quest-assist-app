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
  - [ ] 4.4 Reduzir a latência da fala inicial (PRÓXIMA SEMANA)
    - Medido no Quest (logs `TTS pronto ... carga=` e `fala: N caracteres, síntese=`):
      carga do modelo de voz 1,9–2,9 s (varia) + 1ª frase de 52 caracteres levou
      1,34 s para sintetizar (~25 ms/caractere; frase de 84 caracteres: 1,7 s).
      Total até o 1º áudio ≈ 3,5–4,5 s após o lançamento.
    - Ideias: frase inicial bem mais curta; sintetizar por frases (tocar a 1ª
      enquanto sintetiza as seguintes); avaliar `numThreads` do modelo.
    - _Requisitos: 5.1_
  - [ ] 4.5 Velocidade da fala ajustável pelo usuário (IDEIA — registrada em 11/11)
    - Hoje é fixa: `speed = 0.82f` em `SherpaTtsEngine.speak` (veio de feedback do
      usuário: 1.0 → 0.9 → 0.82, "um pouquinho mais lenta"). Cada pessoa tem um ritmo
      de escuta e leitores de tela costumam permitir esse ajuste.
    - Esboço: campo `speechSpeed` em `Settings` (persistido; limites por função pura,
      como `clampConfidence`; padrão 0.82). `SpeechEngine.speak(text, pan, onDone)` hoje
      não recebe velocidade, então a interface precisa de um parâmetro/propriedade.
      Aplicar em runtime via `settings.observe`, como verbosidade e limiar.
    - Ponto principal (acessibilidade): precisa dar para ajustar SEM enxergar o painel,
      por exemplo com uma entrada do controle (direções do analógico, ainda livres;
      a definir) e confirmação falada na nova velocidade, além do controle em Ajustes
      no painel. Incluir no tutorial e no mapeamento de `AppAction`.
    - A definir: faixa (ex.: 0.6–1.2), passo, qual entrada, e se vale também para a
      dica e o tutorial.
    - Testes: limites/passos (puro) e `Settings` default/validação.
    - _Requisitos: 5.1, 5.3_

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
  - [x] 5.4 Presença de pessoas (sem identificação)
    - Reaproveita a classe COCO "person" do EfficientDet (`Kind.PERSON`), sem
      2º modelo e sem processar rostos; `PersonPresenceTracker` (puro, 16 testes)
      conta pessoas por lado, estima proximidade pela altura da caixa e só fala
      quando algo muda. Substitui o stub `PersonDetector` (removido).
    - Primeira aparição e "pessoa próxima" são imediatas; outras mudanças exigem
      2 ciclos iguais; esquece o estado após 4 s sem ver ninguém. Fala até em
      verbosidade MINIMAL. VALIDADO no device (comportamento); o limiar de
      proximidade `NEAR_HEIGHT=0.6` é heurístico e NÃO foi calibrado com distâncias.
    - _Requisitos: 4.1, 4.2, 4.3_
  - [ ] 5.5 Distinguir o próprio braço/mão de pessoas reais (PRÓXIMA SEMANA)
    - Problema (validado no device): o COCO "person" detecta o braço/mão do usuário
      como "pessoa à esquerda/direita". Sessão de 11/11: 12 detecções, todas durante
      o teste só com os braços; 9 com confiança 0,61–0,81 e caixas altas coladas nas
      bordas. Filtros por confiança e por geometria (topo da caixa, borda inferior)
      NÃO separam — tentativa descartada e removida; fica só o log
      `pessoa conf=... box=[...] altura=...` (ObjectDetector) para diagnóstico.
    - Recomendado (decisão do usuário pendente): para caixas "grandes" (perto),
      exigir um rosto via MediaPipe BlazeFace (~230 KB); pessoas pequenas (longe)
      seguem sem exigir. Limitações: quem está muito perto e de costas não é
      anunciado; o app passa a DETECTAR rostos (só presença, sem identificar nem
      guardar) → atualizar README/PRIVACY; exige baixar o modelo.
    - Alternativas: pose landmarks (~5 MB, mais pesado); máscara pelas mãos via hand
      tracking (sem API de juntas clara no SDK e o app não guarda a pose da câmera);
      aceitar a limitação (mata o aviso de "pessoa próxima").
    - Antes de implementar: captura ROTULADA (bloco só com braços; bloco só com outra
      pessoa a ~1 m, 2 m e 3 m) para medir quanto a regra realmente separa.
    - _Requisitos: 4.1, 4.2_

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
  - [x] 8.3 Documentar dados que saem do dispositivo (README de privacidade)
    - `docs/PRIVACY.md`: o que é capturado/onde fica, o que é enviado no modo
      nuvem, tráfego fora da nuvem (download do modelo de tradução), telemetria
      do ML Kit e o que NÃO foi verificado (tráfego real não capturado).
    - _Requisitos: 7.3_
  - [ ] 8.3b Pendências de privacidade levantadas pela 8.3
    - Criar o `SceneTranslator` só quando a nuvem estiver ativa (hoje é criado
      em toda abertura e baixa o modelo na 1ª execução, mesmo offline — Req. 7.4).
    - HTTPS obrigatório fora de LAN (remover `usesCleartextTraffic` na publicação).
    - Capturar o tráfego do app no Quest para validar o `PRIVACY.md`.
    - _Requisitos: 7.2, 7.3, 7.4_

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
