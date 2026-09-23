# Documentação Técnica — Assistente Visual para Meta Quest 3

> App de realidade mista para auxílio a pessoas com deficiência visual.
> Captura as câmeras frontais do Meta Quest 3, executa visão computacional
> on-device e comunica o ambiente por voz (TTS offline) em português.
>
> Projeto de pesquisa — Ciência da Computação. Autor: Daniel Lima.

Última atualização: 2026-09-23 (OCR sob demanda)

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
┌──────────────────────── Meta Quest 3 (Horizon OS v74+) ────────────────────────┐
│                                                                                  │
│  Passthrough Camera (Camera2) ──frames YUV_420_888 (1280x960)──▶ CameraController│
│                                                                        │         │
│                                                                        ▼         │
│                                                            VisionPipeline (throttle│
│                                                            ~1.2s, drop de frames) │
│                                                                        │         │
│                                        ┌───────────────────────────────┼───────┐ │
│                                        ▼                               ▼        │ │
│                                 ObjectDetector                 (OCR/Pessoas —   │ │
│                                 (MediaPipe TFLite)              roadmap)         │ │
│                                        │                                        │ │
│                                        ▼                                        │ │
│                         AudioFeedbackManager (fila c/ prioridade,               │ │
│                         debounce, TTL, verbosidade)                             │ │
│                                        │                                        │ │
│                                        ▼                                        │ │
│                         SherpaTtsEngine (VITS/Piper pt-BR, AudioTrack)          │ │
│                                                                                  │
│  DebugFrameState ──▶ DebugPanel (Compose, painel Spatial SDK): câmera + boxes    │
└──────────────────────────────────────────────────────────────────────────────┘
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
- `ui/DebugPanel.kt` — painel Compose (Spatial SDK) que mostra a câmera com as
  caixas desenhadas + botão "Fechar app".
- `vision/OverlayRenderer.kt` — desenha boxes + rótulos (confiança %) no bitmap.
- `vision/DebugFrameState.kt` — estado observável (Compose) do frame anotado.
- Observação: o preview atualiza na taxa da inferência (~0.8 FPS), então é
  "picotado" — é ferramenta de validação, não vídeo fluido.

### 3.6 Ciclo de vida / plataforma  ✅
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
- Detecção de pessoas será apenas de **presença** — sem identificação de indivíduos.
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
| 5.4 | **Detecção de pessoas dedicada** | ⏳ | presença (sem identificação) |
| 4.2 | **Áudio espacial (beep direcional)** | ⏳ | requer asset .wav mono 48kHz |
| 6 | **Descrição de cena via cloud (LLM)** | ⏳ | opt-in; fallback offline; Ramalama p/ protótipo |
| 7 | **Configurações** | ⏳ | verbosidade, limiar, on/off por voz/gesto |
| — | **Preview fluido no painel** | ⏳ | desacoplar preview da inferência |
| 8 | Empacotamento / publicação | ⏳ | split ABI, release, loja Meta |

Legenda: ✅ implementado · ⏳ planejado

### Limitações conhecidas
- **Ícone de app sideloaded** não é exibido pelo launcher Nautilus (Developer
  Mode) — limitação do launcher; resolvido ao publicar pela loja Meta.
- Aviso "nome não disponível" ao **sair pelo botão do controle** do Quest
  (comportamento do Nautilus); sair pelo botão do app não dispara.
- Preview do painel de debug é "picotado" (taxa da inferência).

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
Cobertura atual: `ResolutionPolicy`, `DirectionMapper`, `AnnouncementQueue`
(prioridade, debounce, TTL, fila cheia), `AudioFeedbackManager` (fluxo serial).

## 9. Estrutura do código
```
app/src/main/java/com/pesquisa/visualassist/
  VisualAssistActivity.kt      entrada, ciclo de vida, painel
  camera/  CameraController, CameraEye, ResolutionPolicy, YuvUtils
  vision/  VisionPipeline, ObjectDetector, DirectionMapper, Models,
           OverlayRenderer, DebugFrameState, (TextRecognizer/PersonDetector stubs)
  audio/   AudioFeedbackManager, AnnouncementQueue, SpeechEngine, SherpaTtsEngine
  ui/      DebugPanel
app/src/main/assets/  models/ (tflite)  tts/ (voz VITS pt-BR)
.kiro/specs/visual-assist/  requirements.md, design.md, tasks.md
.kiro/steering/  build.md, environment.md, maintenance.md
```
