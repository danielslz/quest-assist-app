# Assistente Visual — Meta Quest 3

Aplicativo de realidade mista para **Meta Quest 3/3S** que auxilia pessoas com
deficiência visual. Usa as câmeras frontais do headset (Passthrough Camera API)
para executar visão computacional **on-device** e descrever o ambiente por
**voz em português** (TTS offline).

Projeto de pesquisa (Ciência da Computação) · Autor: Daniel Lima
Repositório: https://github.com/danielslz/quest-assist-app

## Funcionalidades

Implementadas ✅
- **Passthrough + câmera frontal** (Passthrough Camera API, 1280×960, intrínsecos da lente)
- **Detecção de objetos** on-device (MediaPipe EfficientDet-Lite0) com posição
  relativa ("cadeira à sua esquerda") e rótulos em português
- **Leitura de texto (OCR)** sob demanda (ML Kit) — botão "Ler texto" lê placas,
  letreiros e rótulos por voz
- **Voz offline em pt-BR** (sherpa-onnx VITS/Piper) — o Quest não tem TTS de sistema
- **Fila de áudio inteligente**: prioridade, debounce, descarte de anúncios
  obsoletos (TTL) e seleção por relevância (não fala demais)
- **Painel de debug** com a câmera + bounding boxes e botão de fechar
- **Hand tracking** (funciona sem controllers) · **100% offline** · sem persistir frames

Planejadas ⏳
- Detecção de pessoas (presença), áudio espacial, descrição de cena via LLM
  (opt-in), configurações. Ver roadmap em `docs/TECHNICAL.md`.

## Documentação
- **Técnica (arquitetura, tecnologias, roadmap)**: [`docs/TECHNICAL.md`](docs/TECHNICAL.md)
- Specs: [`.kiro/specs/visual-assist/`](.kiro/specs/visual-assist/) (requirements, design, tasks)
- Ambiente/convenções/build: [`.kiro/steering/`](.kiro/steering/)

## Requisitos de runtime
- Meta Quest 3 / 3S com Horizon OS **v74+** (Passthrough Camera API)
- Conta de desenvolvedor Meta + modo desenvolvedor ativo no headset

## Ambiente de desenvolvimento (Aurora/uBlue imutável)
Toolchain vive em distroboxes (nada instalado no host imutável):
- `quest-dev` (Ubuntu 24.04): JDK 17, Android SDK/NDK 27, Gradle, Spatial Editor CLI
- `quest-ml` (Ubuntu 24.04 + GPU): conversão/teste de modelos

## Como buildar e implantar
```bash
# Build debug (APK)
distrobox enter quest-dev -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:assembleDebug'

# Deploy no Quest (modo dev + USB autorizado)
distrobox enter quest-dev -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:installDebug'

# Testes unitários
distrobox enter quest-dev -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:testDebugUnitTest'
```
`adb` roda no host; verifique o headset com `adb devices`.

## Modelos (não versionados — baixar)
- **TTS pt-BR** → `app/src/main/assets/tts/` (model.onnx, tokens.txt, espeak-ng-data/):
  baixe `vits-piper-pt_BR-*` dos releases do sherpa-onnx. Sem ele o app não fala.
- **Detecção de objetos** → `app/src/main/assets/models/efficientdet_lite0.tflite`:
  baixe do MediaPipe (object_detector/efficientdet_lite0).

## Privacidade
Imagens da câmera não são persistidas; app funciona offline por padrão; detecção
de pessoas será apenas de presença (sem identificação) — conforme LGPD e política
de dados da Meta.

## Estado
Núcleo funcional validado no Quest 3: câmera → detecção → voz. Detalhes e
próximos passos em `docs/TECHNICAL.md`.

## Licença
Distribuído sob a licença **MIT** — veja [`LICENSE`](LICENSE). Você pode usar,
modificar e redistribuir livremente, mantendo o aviso de copyright.

Dependências de terceiros mantêm suas próprias licenças (todas permissivas):
Meta Spatial SDK (MIT nos samples), MediaPipe (Apache 2.0), sherpa-onnx (Apache 2.0),
modelo de voz Piper pt-BR e EfficientDet-Lite (ver licenças dos respectivos projetos).
