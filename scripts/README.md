# scripts/ — Bootstrap do ambiente de desenvolvimento

Recriam as distroboxes de build em uma máquina nova (host Aurora/uBlue imutável),
tornando o setup reproduzível. Os scripts rodam **no host** (precisam de
`distrobox` + `podman`, já presentes no Aurora) e são idempotentes.

## Arquivos
- `bootstrap-quest-dev.sh` — distrobox **`quest-dev`** (build Android):
  Ubuntu 24.04 + apt base + mise (Java 17/Kotlin/Gradle) + Android SDK
  (cmdline-tools, `platforms;android-34`, `build-tools;35.0.0`, `ndk;27.0.12077973`)
  + instala o `env.sh`.
- `quest-dev.env.sh` — ambiente carregado ao entrar na `quest-dev` (ANDROID_HOME,
  NDK, mise, Spatial Editor CLI). Copiado para `~/.config/quest-dev/env.sh`.
- `bootstrap-quest-ml.sh` — distrobox **`quest-ml`** (modelos/utilitários):
  Python + venv a partir de `quest-ml-requirements.txt`.
- `quest-ml-requirements.txt` — dependências Python do `quest-ml` (pip freeze).

## Uso
```bash
bash scripts/bootstrap-quest-dev.sh    # ambiente de build
bash scripts/bootstrap-quest-ml.sh     # ambiente de ML/slides (opcional)
```

## Passos manuais (não automatizáveis — download/licença fechados)
1. **Meta Spatial Editor CLI**: baixar do site de desenvolvedores da Meta e
   extrair em `~/.local/opt/meta-spatial-editor-cli-linux-dist`
   (usado pela task `:app:export` do Gradle).
2. **Modelos não versionados** (TTS e detecção): ver `README.md`, seção "Modelos".

Depois do bootstrap, validar o build:
```bash
distrobox enter quest-dev -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:assembleDebug'
```

> Versões fixadas e convenções: `.kiro/steering/environment.md` e `build.md`.
> Retomada geral do projeto: `docs/CONTINUITY.md`.
