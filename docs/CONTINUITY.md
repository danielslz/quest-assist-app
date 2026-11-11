# Continuidade do desenvolvimento (handoff)

Guia para retomar o projeto em **outra máquina** ou no **Kiro IDE**. Reúne o que
NÃO está no código: setup de ambiente, protótipo de nuvem, teste no device e o
ponto exato onde o desenvolvimento parou.

> Visão geral, arquitetura e roadmap: `docs/TECHNICAL.md`.
> O que construir e por quê: `.kiro/specs/visual-assist/{requirements,design,tasks}.md`.
> Como buildar/testar: `.kiro/steering/build.md`.

## 1. Repositório
- GitHub: `github.com/danielslz/quest-assist-app` (branch `main`).
- Git local usa o e-mail `danielslz@gmail.com` (não o global). Ao clonar em
  outra máquina, configure no repo:
  ```bash
  git config user.email "danielslz@gmail.com"
  ```
- Pacote do app: `com.pesquisa.visualassist` · Activity `.VisualAssistActivity`.

## 2. Ambiente de build (Aurora Linux imutável + distroboxes)
Host é Aurora Linux (uBlue, imutável): toolchain NÃO é instalada no host, e sim
em distroboxes. Em uma máquina nova, **recriar com os scripts de bootstrap**
(reproduzíveis, idempotentes — ver `scripts/README.md`):
```bash
bash scripts/bootstrap-quest-dev.sh    # build Android
bash scripts/bootstrap-quest-ml.sh     # ML/slides (opcional)
```
Passos manuais que o bootstrap não cobre (download/licença fechados): o Meta
Spatial Editor CLI e os modelos não versionados (ver `scripts/README.md` e o
README principal).

Resumo do que cada box contém:
- **`quest-dev`** — build Android. Ambiente em `~/.config/quest-dev/env.sh`
  (versionado em `scripts/quest-dev.env.sh`): JDK 17, Kotlin, Gradle (o build usa
  o WRAPPER 9.4.1 + Kotlin 2.1.0 do projeto), Android SDK em `~/Android/Sdk`,
  NDK `27.0.12077973`, `META_SPATIAL_EDITOR_CLI_PATH`.
- **`quest-ml`** — Python/venv em `~/Projetos/quest-ml/.venv` (deps em
  `scripts/quest-ml-requirements.txt`). Para modelos e geração de slides, NÃO
  para buildar o app.

Comandos canônicos em `.kiro/steering/build.md`. Regra: `adb` roda no HOST;
Gradle só dentro de `quest-dev`.

## 3. Device de teste (Quest 3)
- Quest 3, Horizon OS v207 (precisa v74+ para a Passthrough Camera API).
- Modo desenvolvedor + depuração USB autorizada. Conferir: `adb devices` (host).
- Abrir o app pela biblioteca do headset (abrir via `adb am start` pode disparar
  diálogos do OS que atrapalham o launch imersivo — ver `design.md`,
  "Descobertas de runtime").
- Logs úteis:
  ```bash
  adb logcat --pid=$(adb shell pidof com.pesquisa.visualassist)
  # tags: VisualAssist, VisualAssistTTS, VisualAssistCloud, VisualAssistConfig
  ```

## 4. Protótipo de nuvem (descrição de cena — Tarefa 6)
A descrição de cena usa um endpoint **OpenAI-compatible**. Protótipo local:
- **Ramalama** (via brew no host) servindo um **VLM com mmproj** (projetor
  multimodal). Modelo validado: **SmolVLM-500M** (inclui o mmproj; o `moondream`
  do Ollama NÃO inclui e rejeita imagens).
  ```bash
  ramalama serve -d --host 0.0.0.0 -p 8080 --backend cuda -n visualassist-llm smolvlm
  ramalama ps                              # conferir
  curl -s http://localhost:8080/v1/models  # endpoint OpenAI-compat
  ramalama stop visualassist-llm           # liberar a GPU ao terminar
  ```
- GPU: NVIDIA RTX 2050 (~4 GB). Em outra máquina/GPU, ajustar `--backend`.

### config.json (endpoint sem recompilar)
O app lê `config.json` do armazenamento externo e sobrescreve endpoint/modelo:
`/sdcard/Android/data/com.pesquisa.visualassist/files/config.json`
```json
{ "cloudEnabled": true, "cloudEndpoint": "http://<IP_DA_MAQUINA>:8080", "cloudModel": "smolvlm" }
```
Enviar: `adb push config.json /sdcard/Android/data/com.pesquisa.visualassist/files/config.json`.
- Trocar `<IP_DA_MAQUINA>` pelo IP do host na MESMA rede Wi-Fi do Quest.
- `cloudEnabled` é **opt-in**; sem rede/endpoint, o app usa fallback offline.
- Manifest tem `usesCleartextTraffic="true"` (endpoint HTTP local).

## 5. Ponto de retomada — microgestures (Tarefa 9.2/9.6)
O acionamento acessível já funciona por **botões** (`input/` + `ButtonInputSystem`,
validado). Falta a via por **microgestures**, reusando a MESMA base
(`input/AppAction` + `VisualAssistActivity.runAction`).

APIs do Spatial SDK 0.14.0 já verificadas no classpath:
- `com.meta.spatial.toolkit.MicrogesturesSystem`
  — `addListener { state: Int, isFist: Boolean -> }` retorna
  `MicrogestureListenerHandle`; `removeListener(handle)`.
  Obter via `systemManager.findSystem<MicrogesturesSystem>()`.
- `com.meta.spatial.runtime.MicrogestureBits` — bits dos gestos:
  `Right/LeftMicrogestureTapThumb`, `Right/LeftMicrogestureSwipeLeft/Right/Forward/Back`.
  O `state` é bitmask (`Int`); testar com `and`.

Plano:
1. `input/GestureActionMapper.kt` — puro/testável, bitmask → `AppAction`
   (espelhar `ButtonActionMapper`). Mapeamento sugerido: tap polegar direito →
   DESCRIBE_SCENE; tap esquerdo → READ_TEXT; swipe direita → TOGGLE_DETECTION;
   swipe esquerda → REPEAT_LAST. + testes JUnit.
2. `input/GestureInputController.kt` (ou um System) — registra o listener,
   traduz com o mapper, chama `runAction`. Remover o listener no `onDestroy`.
3. Validar no device; ajustar gestos se houver falso positivo. NÃO usar a pinça
   de palma-para-cima (reservada pelo sistema). Marcar 9.2/9.6 em `tasks.md`.

## 6. Material de reuniões (fora do app)
Apresentações semanais (quintas) em `~/Downloads/Daniel-DD-MM-AAAA.pptx`, geradas
a partir do modelo da semana anterior com `python-pptx` (distrobox `quest-ml`),
preservando tema/fonte. Última: `Daniel-15-10-2026.pptx`.

## 7. Lições/armadilhas já resolvidas (não repetir)
- Fila de áudio tem `maxQueue=3`: falas longas sequenciais são descartadas — use
  `AudioFeedbackManager.announceSequence` (locução única) para textos longos.
- Gatilhos do controle conflitam com a seleção de UI do ISDK — por isso as ações
  usam A/X/B/Y e cliques de analógico, não os gatilhos.
- TTS precisa de `USAGE_MEDIA` + `ALLOW_CAPTURE_BY_ALL` para ser capturado em
  gravações de tela (áudio de acessibilidade é excluído do mix).
- scrcpy NÃO grava o render imersivo do Quest; usar a gravação nativa do headset.
- Boundary/Guardian interrompia o uso em movimento: resolvido com
  `com.oculus.feature.BOUNDARYLESS_APP` no manifest (ver TECHNICAL 3.10, com ressalva
  de segurança para usuários que não enxergam).
- Filtro por confiança/geometria NÃO separa o braço do usuário de uma pessoa real
  (COCO "person"): não repetir; ver tasks 5.5 e o log `pessoa conf=...`.
