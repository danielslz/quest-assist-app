# Assistente Visual — Meta Quest 3

Aplicativo de realidade mista para **Meta Quest 3/3S** que auxilia pessoas com
deficiência visual, usando as câmeras frontais (Passthrough Camera API) para
executar visão computacional (detecção de objetos, OCR de placas/letreiros,
detecção de presença de pessoas) e comunicar por **áudio** (TTS + áudio espacial).

Projeto de pesquisa (Ciência da Computação). Construído com **Meta Spatial SDK**
(Kotlin), ML **on-device** e caminho **cloud** opcional.

## Documentação (specs)
- Requisitos: `.kiro/specs/visual-assist/requirements.md`
- Design/arquitetura: `.kiro/specs/visual-assist/design.md`
- Tarefas: `.kiro/specs/visual-assist/tasks.md`
- Ambiente de dev: `.kiro/steering/environment.md`

## Estrutura
```
app/src/main/
  AndroidManifest.xml          permissões câmera/passthrough/áudio
  java/com/pesquisa/visualassist/
    VisualAssistActivity.kt    entrada (Spatial SDK), permissões
    camera/CameraController.kt Passthrough Camera API (Camera2)
    vision/                    pipeline + detectores (objeto/OCR/pessoa)
    audio/AudioFeedbackManager.kt  TTS + fila/debounce
  res/values/strings.xml
  assets/models/               modelos ONNX/TFLite (não versionados)
```

## Como buildar (no Aurora, via distrobox quest-dev)
```bash
distrobox enter quest-dev -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:assembleDebug'
```
Deploy no headset (modo dev + USB autorizado):
```bash
distrobox enter quest-dev -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:installDebug'
```

## Preparar modelos (distrobox quest-ml)
```bash
distrobox enter quest-ml -- bash -lc 'source ~/Projetos/quest-ml/.venv/bin/activate && \
  yolo export model=yolo11n.pt format=onnx'   # -> copie o .onnx para app/src/main/assets/models/
```

## TTS offline (voz do app) — OBRIGATÓRIO para o app falar
O Meta Quest NÃO tem motor TTS do sistema. O app usa sherpa-onnx com um modelo
VITS/Piper em português, que deve ser colocado em `app/src/main/assets/tts/`:
1. Baixe um modelo pt_BR dos releases do sherpa-onnx, por exemplo:
   `vits-piper-pt_BR-*.tar.bz2` de https://github.com/k2-fsa/sherpa-onnx/releases
2. Extraia e copie para `app/src/main/assets/tts/`:
   - `model.onnx` (renomeie o `.onnx` do modelo)
   - `tokens.txt`
   - `espeak-ng-data/` (pasta, se o modelo Piper usar)
3. Rebuild + install. Sem esse modelo, o app roda mas não fala (log: "Falha ao inicializar TTS").

> Os arquivos de modelo (.onnx/.tflite) não são versionados (ver .gitignore).

## Privacidade
- Imagens da câmera são tratadas como Device User Data (política Meta).
- Nenhum frame é persistido por padrão; app funciona 100% offline no modo padrão.
- O modo cloud (descrição de cena) é explícito, opcional e desativável.
- Detecção de pessoas é apenas de PRESENÇA — sem identificação (LGPD).

## Estado
Esqueleto com stubs marcados por `TODO(task N)`. Siga `tasks.md` para implementar.
```

## Requisitos de runtime
- Meta Quest 3 / 3S com Horizon OS **v74+** (Passthrough Camera API)
- Conta de desenvolvedor Meta + modo desenvolvedor ativo
