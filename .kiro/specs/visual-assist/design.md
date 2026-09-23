# Design — App de Assistência Visual para Meta Quest 3

## Visão geral

App Meta Spatial SDK (Kotlin) que captura frames da Passthrough Camera API,
processa com modelos de ML on-device e comunica resultados por áudio (TTS +
áudio espacial). Um caminho opcional envia frames a um LLM multimodal na cloud
para descrições ricas.

```
┌───────────────────────────────────────────────────────────────┐
│                     Quest 3 (Horizon OS v74+)                    │
│                                                                   │
│  ┌────────────┐   frames    ┌──────────────────┐   detecções    │
│  │ Passthrough │──YUV420────▶│  VisionPipeline  │───────────────┐│
│  │ Camera (PCA)│  1280x960   │ (object/OCR/face)│               ││
│  └────────────┘             └──────────────────┘               ││
│         │                        │        │                     ▼│
│         │                        │        │            ┌──────────────┐
│         │                     on-device   │            │ AudioFeedback │
│         │                   (TFLite/ONNX/  │            │ (TTS+espacial)│
│         │                    MediaPipe/    │            └──────────────┘
│         │                     ML Kit)      │                     ▲│
│         │                                  ▼                     ││
│         │                          ┌───────────────┐            ││
│         └── frame sob demanda ─────▶│ CloudSceneDesc│─(opcional)─┘│
│                                     │ (LLM multimodal)            │
│                                     └───────────────┘            │
└───────────────────────────────────────────────────────────────┘
```

## Componentes

### 1. `VisualAssistActivity` (AppSystemActivity)
Ponto de entrada do Spatial SDK. Registra features, inicializa o pipeline de visão
e o sistema de áudio, gerencia o ciclo de vida e as permissões.

### 2. `CameraController` (Passthrough Camera API / Camera2)
Wrapper sobre a Camera2 API do Horizon OS. Responsabilidades:
- Selecionar a câmera de passthrough via vendor tags
  (`com.meta.extra_metadata.camera_source == 0`, `position` 0/1).
- Escolher resolução testada (1280x960) da lista suportada.
- Entregar frames (YUV420) num callback, com metadados (intrínsecos, pose, timestamp).
- Baseado no wrapper do showcase **Spatial Scanner** da Meta.

### 3. `VisionPipeline`
Orquestra os detectores. Roda em thread/coroutine separada do render.
Interface comum:
```kotlin
interface VisionDetector {
    fun detect(frame: CameraFrame): List<Detection>
}
```
Implementações:
- `ObjectDetector` — MediaPipe Object Detection ou YOLO(ONNX Runtime Mobile).
- `TextRecognizer` — ML Kit Text Recognition (OCR, pt/en).
- `PersonDetector` — MediaPipe Face/Pose Detection (só presença, sem identificação).

`Detection` inclui: rótulo, confiança, bounding box, e posição relativa derivada
(esquerda/centro/direita, perto/longe) usando intrínsecos + pose.

### 4. `AudioFeedbackManager`
- **TTS offline embarcado no APK** (ver "Decisão: TTS" abaixo). O Meta Quest NÃO
  possui motor TTS do sistema (`tts_default_synth = null`), então o
  `android.speech.tts.TextToSpeech` não produz áudio no headset. Usamos
  **sherpa-onnx** (modelo VITS/Piper em português) via ONNX Runtime + `AudioTrack`.
- Fila com prioridade e debounce por rótulo (`AnnouncementQueue`, lógica pura testável).
- Verbosidade configurável.
- Pista direcional via pan estéreo do `AudioTrack` (L/R) e, futuramente, beep
  espacial via Spatial SDK (requer asset .wav mono 48kHz).

> **Decisão: TTS (verificada no device em 2026-09-23)**
> - `adb shell settings get secure tts_default_synth` → `null`; nenhum pacote TTS.
> - Conclusão: TTS do Android não funciona no Quest sem engine instalado.
> - Escolha: **sherpa-onnx TTS offline**, empacotado no app. Coerente com a
>   arquitetura on-device (já usamos ONNX Runtime). Funciona 100% offline.
> - Modelo em `app/src/main/assets/tts/` (ex.: `vits-piper-pt_BR-*`).
> - Alternativas descartadas: side-load de engine TTS (frágil no Horizon OS);
>   cloud TTS (viola o requisito offline).

### 5. `CloudSceneDescriber` (opcional)
- Envia frame (JPEG) a um endpoint LLM multimodal (configurável).
- Só ativa com consentimento explícito e conectividade.
- Fallback para descrição on-device.
- Para prototipagem local: aponta para um endpoint servido por **Ramalama** na
  máquina de desenvolvimento (mesma rede).

### 6. `SettingsStore`
Preferências: verbosidade, limiar de confiança, idioma, cloud on/off, endpoint.

## Estratégia de modelos ML

| Tarefa            | Runtime on-device            | Modelo sugerido           |
|-------------------|------------------------------|---------------------------|
| Detecção objetos  | MediaPipe Tasks / ONNX RT    | EfficientDet-Lite / YOLO11n|
| OCR               | ML Kit Text Recognition v2   | (embutido)                |
| Detecção pessoas  | MediaPipe Face/Pose Detector | BlazeFace / MoveNet       |
| Descrição de cena | Cloud (LLM multimodal)       | via API                   |

Conversão/teste de modelos: distrobox **quest-ml** (venv com ONNX/TFLite/ultralytics,
GPU NVIDIA). Modelos vão em `app/src/main/assets/models/`.

## Threading e desempenho
- Render do Spatial SDK no seu loop; inferência em coroutine dedicada (Dispatchers.Default).
- Buffer de 1 frame (dropa frames antigos) para não acumular latência.
- Meta: inferência >= 10 FPS; overhead de câmera ~1-2% GPU, ~45MB memória.

## Permissões (AndroidManifest)
- `android.permission.CAMERA` (ou `horizonos.permission.HEADSET_CAMERA`)
- `com.oculus.feature.PASSTHROUGH`
- `android.permission.INTERNET` (apenas para modo cloud)
- `android.permission.MODIFY_AUDIO_SETTINGS`
- `horizonos:minSdkVersion="69"` (mínimo do SDK); câmera exige runtime OS v74+.

## Estrutura de build (validada)
- AGP **8.11.1**, Gradle **9.4.1** (wrapper), Kotlin **2.1.0**, JDK **17**, NDK **27.0.12077973**
- Plugin `com.meta.spatial.plugin` (Spatial SDK 0.14.0+)
- `compileSdk/targetSdk = 34`, `minSdk = 34`
- Spatial Editor CLI via env var `META_SPATIAL_EDITOR_CLI_PATH` (já configurado)

## Tratamento de erros
- Câmera indisponível → anúncio por áudio + retry.
- Modelo falha ao carregar → desabilita aquele detector, mantém os demais.
- Cloud indisponível → fallback on-device automático.

## Testes
- Unit: lógica de posição relativa, fila de áudio, debounce (JUnit).
- Instrumentado: carregamento de modelos, permissões (androidTest).
- Manual no headset: latência, conforto, precisão dos anúncios.

## Descobertas de runtime no device (Quest 3, Horizon OS v207 — 2026-09-23)
Lições do primeiro deploy real. Requisitos para o app rodar no headset:
1. **`registerFeatures()` deve retornar `VRFeature(this)`** — sem isso o
   AppSystemActivity não inicializa o render.
2. **`onSceneReady()` deve chamar `scene.enablePassthrough(true)`** e configurar
   `setReferenceSpace(LOCAL_FLOOR)` + iluminação, senão a tela fica preta.
3. **Manifest precisa de `meta-data com.oculus.ossplash.background = "passthrough-contextual"`**
   — é o que habilita o fundo passthrough no nível do sistema (`App Enabled for PT: 1`).
4. **Hand tracking declarado** (`oculus.software.handtracking`, `HAND_TRACKING`,
   `handtracking.version V2.0`) para o app NÃO exigir controllers — o Horizon
   bloqueia o launch (`RequiresControllersLaunchInterceptor`) se exigir controllers.
5. **NUNCA chamar `SceneAudioAsset.loadLocalFile` com asset ausente** — lança um
   *Native Assert* que escapa do `runCatching` e quebra o `onSceneReady`.
6. **Headset precisa estar na cabeça e desbloqueado** para o app abrir; com
   lockscreen ativo (`mDreamingLockscreen=true`) o launch é bloqueado e dá timeout.
7. **Abrir o app pela biblioteca do headset**, não via `adb am start` (o adb
   dispara diálogos do OS que bloqueiam o launch imersivo).

Comandos de debug úteis:
- `adb logcat --pid=$(adb shell pidof com.pesquisa.visualassist)`
- Conceder câmera p/ teste: `adb shell pm grant <pkg> android.permission.CAMERA`
- Verificar TTS: `adb shell settings get secure tts_default_synth` (Quest = null)
