---
inclusion: always
---
# Build e execução (Aurora Linux imutável)

O host é **Aurora Linux (uBlue, imutável)**. NÃO instale JDK/SDK/NDK/Gradle no host.
Todo build roda dentro de distroboxes já configuradas. Os comandos abaixo são a
forma canônica de buildar/testar/deployar este projeto.

## Container de build: `quest-dev`
Toolchain (auto-carregado ao entrar): JDK 17, Kotlin, Gradle, Android SDK em
`~/Android/Sdk`, NDK `27.0.12077973`, e `META_SPATIAL_EDITOR_CLI_PATH`.

Executar qualquer comando Gradle SEMPRE assim (a partir da raiz do projeto):

```bash
# Build debug (gera APK)
distrobox enter quest-dev -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:assembleDebug'

# Testes unitários
distrobox enter quest-dev -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:testDebugUnitTest'

# Instalar/deploy no Quest (headset em modo dev, conectado por USB)
distrobox enter quest-dev -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:installDebug'
```

- Use SEMPRE `bash -lc` para carregar o ambiente (mise + Android + Spatial Editor CLI).
- O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.
- `adb` roda no HOST (não no container). Verifique o headset com `adb devices` no host.

## Container de ML: `quest-ml`
Para converter/testar modelos de visão (NÃO para buildar o app).
```bash
distrobox enter quest-ml -- bash -lc 'source ~/Projetos/quest-ml/.venv/bin/activate && python <script>'
```
Tem: ONNX, ONNX Runtime, ultralytics (YOLO), OpenCV, PyTorch CUDA (RTX 2050).
Modelos exportados vão para `app/src/main/assets/models/`.

## Regras
- NUNCA rode `./gradlew` diretamente no host — só dentro de `quest-dev`.
- NUNCA instale pacotes de dev no host imutável; use as distroboxes.
- Após mudar código, rode build + testes na `quest-dev` antes de concluir.
