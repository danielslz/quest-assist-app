# Documentação Técnica — Assistente Visual para Meta Quest 3

> App de realidade mista para auxílio a pessoas com deficiência visual.
> Captura as câmeras frontais do Meta Quest 3, executa visão computacional
> on-device e comunica o ambiente por voz (TTS offline) em português.
>
> Projeto de pesquisa — Ciência da Computação. Autor: Daniel Lima.

Última atualização: 2026-11-11 (5.4 presença de pessoas; uso em movimento; pendências 4.4/5.5)

---

## 0. Licenciamento

O projeto é distribuído sob a licença **MIT** (`LICENSE`) — permissiva, adequada
a pesquisa acadêmica e código aberto, incentivando reuso em acessibilidade.

Licenças das dependências (todas compatíveis com MIT):
- Meta Spatial SDK / samples: MIT
- MediaPipe Tasks Vision: Apache 2.0
- sherpa-onnx: Apache 2.0
- Modelo de voz `vits-piper-pt_BR-*`: ver licença do projeto Piper/sherpa-onnx
- EfficientDet-Lite0 (MediaPipe models): Apache 2.0

Os arquivos de modelo (voz TTS, detecção) NÃO são versionados no repositório;
são baixados conforme o `README.md`.

---

## 1. Visão geral

O Assistente Visual transforma o Meta Quest 3 em um "óculos inteligente"
assistivo. Ele usa a **Passthrough Camera API** para acessar as câmeras RGB
frontais do headset, roda modelos de **visão computacional on-device** e
descreve o ambiente ao usuário por **áudio** (síntese de voz + pistas
direcionais). Todo o processamento essencial é **offline** e **privado**.

### Objetivos de design
- **Acessibilidade primeiro**: toda saída é por áudio; funciona com hand tracking
  (sem exigir controllers).
- **Offline e privado**: nenhum frame é persistido; o núcleo não depende de rede.
- **On-device**: baixa latência e independência de conectividade.
- **Testável**: lógica de decisão (fila de áudio, direção, resolução) é pura e
  coberta por testes unitários.

---

## 2. Arquitetura

```
┌─────────────────── Meta Quest 3 · Horizon OS v74+ ──────────────────┐
│                                                                     │
│  Passthrough Camera (Camera2)                                       │
│         │  frames YUV_420_888 (1280x960)                            │
│         ▼                                                           │
│  CameraController ─────────────▶ lastFrame (p/ OCR sob demanda)     │
│         │                                                           │
│         ▼                                                           │
│  VisionPipeline  (coroutine · throttle ~1.2s · drop de frames)      │
│         │                                                           │
│         ▼                                                           │
│  ObjectDetector (MediaPipe EfficientDet-Lite0)                      │
│         │                     TextReader (ML Kit OCR, sob demanda)  │
│         │                            │  (botão "Ler texto")         │
│         ▼                            ▼                              │
│  AudioFeedbackManager  (fila: prioridade · debounce · TTL)          │
│         │                                                           │
│         ▼                                                           │
│  SherpaTtsEngine (VITS/Piper pt-BR) ─▶ AudioTrack ─▶ voz            │
│                                                                     │
│  ObjectDetector ─▶ DebugFrameState ─▶ DebugPanel (Compose)          │
│                    painel Spatial SDK: câmera + bounding boxes      │
└─────────────────────────────────────────────────────────────────────┘
```

### Fluxo de execução
1. `VisualAssistActivity` (estende `AppSystemActivity`) registra `VRFeature` +
   `ComposeFeature`.
2. Em `onSceneReady`: habilita passthrough, monta cena mínima, registra o painel
   de debug e solicita permissão de câmera.
3. `CameraController` abre a câmera de passthrough e entrega frames.
4. `VisionPipeline` (coroutine, fora do render) roda os detectores com throttle.
5. Detecções viram anúncios no `AudioFeedbackManager`, falados pelo TTS offline.
6. O `DebugPanel` exibe a câmera com as bounding boxes (ferramenta de dev).

---

## 3. Funcionalidades implementadas

### 3.1 Acesso à câmera de passthrough  ✅
- `camera/CameraController.kt` — wrapper da Passthrough Camera API (Camera2).
  Descobre a câmera via vendor tags Meta (`com.meta.extra_metadata.position`),
  abre sessão, entrega frames YUV_420_888.
- `camera/ResolutionPolicy.kt` — escolhe resolução testada (1280x960); não assume
  a maior disponível (robustez a novas resoluções do OS). **Testado.**
- `camera/YuvUtils.kt` — conversão YUV→NV21 e NV21→Bitmap (para o MediaPipe).
- `camera/CameraEye.kt` — enum left/right/unknown.

### 3.2 Detecção de objetos  ✅
- `vision/ObjectDetector.kt` — MediaPipe Tasks Vision (EfficientDet-Lite0,
  `assets/models/efficientdet_lite0.tflite`). Filtra por confiança, calcula
  posição relativa e traduz rótulos COCO EN→PT.
- `vision/DirectionMapper.kt` — posição relativa (esquerda/centro/direita) a
  partir da bounding box. **Testado.**
- `vision/VisionPipeline.kt` — orquestra detectores em coroutine, com throttle
  de inferência (~1.2s) e drop de frames antigos (sem busy-loop).
- `vision/Models.kt` — `CameraFrame`, `Detection`, `Direction`.

#### Presença de pessoas (5.4)  ✅
- A classe COCO `person` do `ObjectDetector` vira `Detection.Kind.PERSON`. Não há
  segundo modelo nem processamento de rosto: só presença (Req. 4.2/4.3).
- `vision/PersonPresence.kt` — `PersonPresenceTracker` (**puro, 16 testes**):
  resume contagem por lado + proximidade e decide quando falar. Frases como
  "Pessoa à sua frente", "Pessoa próxima à sua direita", "Duas pessoas: uma à
  esquerda e uma à direita".
- Regras: 1ª aparição e pessoa que fica **próxima** → imediato; demais mudanças
  → após 2 ciclos iguais (ignora o piscar da detecção); sem mudança → silêncio;
  4 s sem ver ninguém → esquece e reanuncia. Fala também em verbosidade MINIMAL.
- `AudioFeedbackManager.report` usa o tracker para pessoas; sem `dedupeKey`, pois
  o debounce da fila descartaria uma mudança real (ex.: pessoa se aproximou).
- **Limitação:** "próxima" = altura da caixa ≥ 60% do frame (`NEAR_HEIGHT`),
  heurística **não calibrada** com distâncias reais.

### 3.3 Feedback por áudio  ✅
- `audio/SherpaTtsEngine.kt` — TTS **offline** com sherpa-onnx (modelo VITS/Piper
  pt-BR em `assets/tts/`), reproduzido via `AudioTrack`. O Quest não tem TTS de
  sistema, por isso o motor é embarcado.
- `audio/AudioFeedbackManager.kt` — orquestra a fala: seleção por **relevância**
  (confiança + proximidade + centralidade), no máx. 1 objeto/ciclo + pessoas
  sempre; consumo serial (uma fala por vez).
- `audio/AnnouncementQueue.kt` — fila pura **testável**: prioridade, **debounce**
  por rótulo (5s), **TTL** (descarta detecções obsoletas ~1.8s), fila curta com
  substituição por prioridade. **Testada.**
- `audio/SpeechEngine.kt` — interface que desacopla o motor de TTS (permite mock
  em testes e troca de engine).

### 3.4 Leitura de texto / OCR  ✅
- `vision/TextReader.kt` — OCR **sob demanda** com ML Kit Text Recognition (script
  latino, pt/en). Acionado pelo botão "Ler texto" no painel: captura o último
  frame, reconhece o texto e o lê por voz (prioridade alta). Anuncia "nenhum texto
  detectado" quando vazio. Não roda no pipeline contínuo (é pontual).

### 3.5 Painel de debug com bounding boxes  ✅
- `ui/DebugPanel.kt` — painel Compose (Spatial SDK). Design limpo: por padrão só
  o **preview grande** + barra enxuta (Ler texto · Ajustes · Fechar). O botão
  **"Ajustes"** expande/recolhe os controles (verbosidade, detecção, confiança,
  mover ←→↑↓, perto/longe, menor/maior).
- `vision/OverlayRenderer.kt` — desenha boxes + rótulos (confiança %) no bitmap.
- `vision/DebugFrameState.kt` — estado observável (Compose) que separa o **frame
  da câmera** (fluido, ~15 fps, publicado pela Activity) das **detecções**
  (ritmo da inferência, ~0.8 fps, publicadas pelo ObjectDetector). O painel
  compõe os dois → **preview fluido** com caixas que "seguem".
- **Posição/tamanho por botões** (determinístico): a Activity ajusta `Transform`
  (X/Y/Z) e `Scale` da entidade do painel. O painel é criado com
  `Entity.createPanelEntity(...)`; o painel usa resolução lógica definida
  (`DpDisplayOptions`) para o Compose medir o layout corretamente.
- Nota: o grab por gesto do SDK (`Grabbable`/ISDK) mostrou-se inconsistente
  neste fluxo (`createPanelEntity`) — por isso o reposicionamento é por botões.

### 3.6 Configurações  ✅
- `settings/Settings.kt` — modelo puro/imutável (verbosidade, limiar de confiança,
  on/off da detecção, cloud on/off) com validação de faixa. **Testado.**
- `settings/SettingsStore.kt` — persistência (SharedPreferences) + estado
  observável (Compose); notifica observadores ao mudar. Aplicado em runtime a:
  `AudioFeedbackManager` (verbosidade), `ObjectDetector` (limiar),
  `VisionPipeline` (liga/desliga inferência).
- Controles no `DebugPanel`. Cloud **OFF por padrão** (Requisito 7.4).

### 3.7 Descrição de cena via LLM multimodal  ✅
- `cloud/SceneDescriptionProtocol.kt` — monta/parseia requisições
  **OpenAI-compatible** (`/v1/chat/completions` com imagem base64). **Testado.**
- `cloud/CloudSceneDescriber.kt` — envia o frame (JPEG) via OkHttp ao endpoint;
  verifica conectividade; envio **opt-in** (cloud OFF por padrão).
- `cloud/OnDeviceSceneDescriber.kt` — fallback offline: descreve pelos objetos
  detectados quando sem rede/cloud. **Testado.**
- `cloud/SceneTranslator.kt` — traduz a resposta EN→PT **offline** (ML Kit); o
  VLM leve responde em inglês, traduzimos antes de falar.
- `settings/AppConfig.kt` — lê `config.json` do armazenamento do app (editável
  via `adb`, sem recompilar) para o endpoint/modelo do LLM — equivalente a `.env`.
- Protótipo validado com **Ramalama** servindo **SmolVLM** (VLM com `mmproj`).
  Lição: o modelo precisa incluir o `mmproj` (projetor multimodal), senão o
  servidor rejeita imagens ("image input is not supported").

### 3.9 Controle acessível por botões/gestos  🟡
Problema: os botões do painel exigem visão para mirar — inúteis para o
usuário-alvo. Solução: acionamento por **controle físico** (feito) e, no
futuro, **microgestures** (pendente), sempre com confirmação por voz.

- `input/AppAction.kt` — enum puro das ações.
- `input/ButtonActionMapper.kt` — **puro, testado**: bitmask de botões → ação.
- `input/ButtonInputSystem.kt` — `SystemBase` que lê o `Controller` por frame
  (edge detection via `buttonState`) e dispara a ação.
- Mapeamento: **A** descrever · **X** ler texto · **B** liga/desliga detecção ·
  **Y** repetir · **clique analógico esq.** tutorial · **clique analógico dir.**
  fechar (confirmação dupla). Gatilhos evitados (conflitam com seleção de UI).
- **Tutorial interativo** (onboarding): cada botão só anuncia sua função até o
  usuário praticar todos; roda só na 1ª abertura (`onboardingDone`); rechamável
  pelo analógico esq. e por botão em Ajustes. Detecções são silenciadas durante
  o tutorial. Fala como locução única pausada (a fila é curta, maxQueue=3).
- **HUD** no topo do painel mostra o último acionamento (feedback visual).
- Pendente: migrar o mesmo fluxo para microgestures (`MicrogesturesSystem`).

### 3.10 Uso em movimento (boundaryless)  ✅
- O app é usado caminhando, então o boundary/Guardian do Quest não deve interrompê-lo.
  O manifest declara `com.oculus.feature.BOUNDARYLESS_APP` (mecanismo oficial da Meta
  para apps 6DOF de mixed reality, sem cena imersiva). Confirmado no APK (`aapt`) e
  no Quest 3: o aviso de saída da área deixou de aparecer.
- Evitado o truque `adb setprop debug.oculus.guardian_pause` (não suportado; há
  relato de que também desliga o passthrough).
- **Segurança (leia):** o modo boundaryless da Meta supõe que o usuário enxerga o
  ambiente pelo passthrough. O usuário-alvo deste app não enxerga, e o grid do
  Guardian também é visual, então ele não protegia essa pessoa de qualquer forma.
  O app é **auxiliar e NÃO substitui** bengala, cão-guia ou acompanhante: a
  detecção roda a ~1,2 s por ciclo e a fala acrescenta atraso, portanto não é um
  sistema de segurança em tempo real. Testes em movimento: local conhecido e com
  acompanhante. Fora do escopo verificado: caminhadas longas e ambientes variados.
- O painel de debug fica ancorado no mundo (`LOCAL_FLOOR`): ao andar, ele fica para
  trás. Só afeta quem depura visualmente.

### 3.8 Ciclo de vida / plataforma  ✅
- `VisualAssistActivity.kt` — entrada; permissões com feedback por áudio;
  passthrough; painel; saída limpa (`finishApp`).
- Manifest: hand tracking (sem controllers), passthrough, `passthrough-contextual`
  (fundo do mundo real), ícone do app.

---

## 4. Tecnologias utilizadas

| Camada | Tecnologia | Versão | Papel |
|--------|-----------|--------|-------|
| Plataforma XR | Meta Spatial SDK | 0.14.0 | app MR nativo, passthrough, painéis |
| Runtime | Meta Horizon OS | v74+ (testado v207) | SO do Quest 3 |
| Linguagem | Kotlin | 2.1.0 | app |
| Build | Gradle 9.4.1 / AGP 8.11.1 | — | compilação |
| Toolchain | JDK 17, NDK 27.0.12077973 | — | Android + nativo |
| Câmera | Passthrough Camera API (Camera2) | — | frames RGB frontais |
| Detecção objetos | MediaPipe Tasks Vision | 1.0.0 | EfficientDet-Lite0 |
| OCR | ML Kit Text Recognition | 16.0.1 | leitura de texto (sob demanda) |
| Tradução | ML Kit Translate | 17.0.3 | descrição EN->PT offline |
| Descrição de cena | VLM via endpoint OpenAI-compat | — | SmolVLM (Ramalama) p/ protótipo |
| TTS | sherpa-onnx (VITS/Piper pt-BR) | 1.13.8 | voz offline |
| UI dos painéis | Jetpack Compose | BOM 2024.09 | painel de debug |
| SDK Android | compile/target/min SDK | 34 / 34 / 34 | — |

### Ambiente de desenvolvimento (host imutável Aurora/uBlue)
- **distrobox `quest-dev`** (Ubuntu 24.04): build do app (mise + JDK17 + Android SDK/NDK + Spatial Editor CLI).
- **distrobox `quest-ml`** (Ubuntu 24.04, GPU NVIDIA): conversão/teste de modelos (ONNX, ultralytics, OpenCV).
- **Android Studio** via JetBrains Toolbox no host; `adb` no host.
- Ver `.kiro/steering/build.md` para os comandos canônicos.

---

## 5. Privacidade e conformidade (LGPD / política Meta)
- Imagens da câmera são tratadas como Device User Data (política Meta).
- **Nenhum frame é persistido** em disco.
- Detecção de pessoas é apenas de **presença** — sem identificação de indivíduos
  e sem processar rostos (usa a classe "person" do detector de objetos).
- O app funciona **100% offline** no modo padrão; o modo cloud (roadmap) será
  opt-in explícito.

---

## 6. Roadmap

| # | Item | Status | Descrição |
|---|------|--------|-----------|
| 1 | Estrutura base | ✅ | projeto Spatial SDK, build no Aurora |
| 2 | Activity + permissões + passthrough | ✅ | ciclo de vida, cena, câmera |
| 3 | CameraController (Passthrough Camera API) | ✅ | 1280x960 + intrínsecos |
| 4 | Áudio (fila) + TTS offline | ✅ | sherpa-onnx pt-BR, fila testável |
| 5.1 | VisionPipeline | ✅ | inferência fora do render, throttle |
| 5.2 | ObjectDetector (MediaPipe) | ✅ | objetos + direção + pt |
| — | Painel de debug (boxes) | ✅ | validação visual |
| 5.3 | OCR (ML Kit) | ✅ | ler placas/letreiros sob demanda (botão) |
| 5.4 | Presença de pessoas | ✅ | classe COCO person + tracker (contagem/lado/proximidade); sem identificação |
| 4.2 | **Áudio espacial (beep direcional)** | ⏳ | requer asset .wav mono 48kHz |
| 6 | Descrição de cena via LLM | ✅ | OpenAI-compat; fallback offline; tradução pt; SmolVLM/Ramalama |
| 7 | Configurações | ✅ | verbosidade, limiar, on/off (painel); cloud OFF default |
| — | Preview fluido no painel | ✅ | preview ~15fps desacoplado da inferência |
| 9 | Controle acessível (botões) | 🟡 parcial | botões A/X/B/Y + analógicos; tutorial interativo; HUD. Microgestures pendentes |
| 8 | Empacotamento / publicação | ⏳ | split ABI, release, loja Meta |

Legenda: ✅ implementado · 🟡 parcial · ⏳ planejado

> **Próxima funcionalidade — controle acessível por gestos.** Os botões do painel
> exigem visão para mirar (servem para depuração). Para o usuário-alvo (deficiente
> visual), o acionamento será por *microgestures* nativos do Spatial SDK (toque e
> deslize do polegar, via `MicrogesturesSystem`/`MicrogestureBits`), com
> confirmação por voz e um tutorial falado na abertura. Design e tarefas em
> `.kiro/specs/visual-assist/` (R9, Tarefa 9).

### Limitações conhecidas
- **Grab por gesto do painel**: mover/redimensionar o painel pelo gesto do
  controle (`Grabbable`/ISDK) não funcionou de forma consistente no fluxo
  `createPanelEntity` (o input é captado mas não arrasta). Resolvido com
  **controles por botões** (mover ←→↑↓, perto/longe, menor/maior) — determinístico.
- **Ícone de app sideloaded** não é exibido pelo launcher Nautilus (Developer
  Mode) — limitação do launcher; resolvido ao publicar pela loja Meta.
- Aviso "nome não disponível" ao **sair pelo botão do controle** do Quest
  (comportamento do Nautilus); sair pelo botão do app não dispara.
- **Primeira execução exige rede** para baixar o modelo de tradução EN→PT (ML Kit),
  mesmo com a nuvem desligada, pois o `SceneTranslator` é criado na abertura do
  app. Exceção ao "100% offline por padrão" (Req. 7.4); correção pendente (tasks
  8.3b). Demais pontos de privacidade e o que não foi verificado: `docs/PRIVACY.md`.
- **Endpoint de nuvem em HTTP** (`usesCleartextTraffic=true`) no protótipo local;
  não usar fora de uma LAN confiável.
- **Braço/mão do usuário detectado como "pessoa"** (falso positivo de presença):
  filtros por confiança/geometria não separam (ver tasks 5.5). Pendente.
- **Latência da fala inicial** ≈ 3,5–4,5 s após o lançamento (carga do modelo de
  voz 1,9–2,9 s + síntese da 1ª frase ~1,3 s). Ver tasks 4.4.

> Nota: o acesso de proprietário do Quest foi resolvido (factory reset +
> reconfiguração com a conta do desenvolvedor). Todas as features estão
> validadas no device (Quest 3, Horizon OS v207).

---

## 7. Como buildar e implantar
Ver `README.md` e `.kiro/steering/build.md`. Resumo:
```bash
# build + deploy no Quest (headset em modo dev, conectado por USB)
distrobox enter quest-dev -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:installDebug'
```

## 8. Testes
Lógica pura coberta por JUnit (rodar na `quest-dev`):
```bash
distrobox enter quest-dev -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:testDebugUnitTest'
```
Cobertura atual (62 testes): `ResolutionPolicy`, `DirectionMapper`,
`AnnouncementQueue` (prioridade, debounce, TTL, fila cheia),
`AudioFeedbackManager` (fluxo serial, presença de pessoas), `Settings` (defaults/validação),
`SceneDescriptionProtocol` (payload/parse), `OnDeviceSceneDescriber` (fallback)
`ButtonActionMapper` (bit→ação, acessibilidade) e `PersonPresenceTracker` (presença).

## 9. Estrutura do código
```
app/src/main/java/com/pesquisa/visualassist/
  VisualAssistActivity.kt      entrada, ciclo de vida, painel, OCR sob demanda
  camera/    CameraController, CameraEye, ResolutionPolicy, YuvUtils
  vision/    VisionPipeline, ObjectDetector, TextReader (OCR), DirectionMapper,
             Models, OverlayRenderer, DebugFrameState,
             PersonPresence (PersonPresenceTracker, puro)
  audio/     AudioFeedbackManager, AnnouncementQueue, SpeechEngine, SherpaTtsEngine
  settings/  Settings (puro), SettingsStore (persistência + observável)
  cloud/     SceneDescriptionProtocol, CloudSceneDescriber, OnDeviceSceneDescriber,
             SceneTranslator; AppConfig (config.json)
  input/     AppAction, ButtonActionMapper (puro), ButtonInputSystem (controle)
  ui/        DebugPanel
app/src/main/assets/  models/ (tflite)  tts/ (voz VITS pt-BR)
.kiro/specs/visual-assist/  requirements.md, design.md, tasks.md
.kiro/steering/  build.md, environment.md, maintenance.md
```
