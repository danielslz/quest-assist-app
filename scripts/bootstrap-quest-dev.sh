#!/usr/bin/env bash
# Bootstrap da distrobox de build 'quest-dev' (Meta Quest 3 / Spatial SDK).
#
# Recria o ambiente de build Android em uma máquina nova (host Aurora/uBlue
# imutável). Idempotente: pode rodar de novo com segurança.
#
# Uso (no HOST, não dentro de distrobox):
#   bash scripts/bootstrap-quest-dev.sh
#
# Depois de rodar, buildar com:
#   distrobox enter quest-dev -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:assembleDebug'
#
# Pré-requisitos no host: distrobox + podman (já presentes no Aurora/uBlue).
set -euo pipefail

BOX="quest-dev"
IMAGE="docker.io/library/ubuntu:24.04"

# Versões fixadas (ver .kiro/steering/environment.md). O build usa o WRAPPER
# Gradle (9.4.1) e Kotlin 2.1.0 do projeto; o mise abaixo é fallback/ferramentas.
JAVA_VERSION="temurin-17"
NDK_VERSION="27.0.12077973"
BUILD_TOOLS="35.0.0"
PLATFORM="android-34"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

echo "==> 1/5 Criando a distrobox '$BOX' ($IMAGE) se não existir"
if ! distrobox list | grep -q "\b$BOX\b"; then
  distrobox create --name "$BOX" --image "$IMAGE" --yes
fi

echo "==> 2/5 Instalando pacotes base (apt) dentro da box"
distrobox enter "$BOX" -- bash -c '
  set -e
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    build-essential curl git unzip zip ca-certificates \
    libgl1-mesa-dri libglx-mesa0 libegl-mesa0 mesa-vulkan-drivers
'

echo "==> 3/5 Instalando mise + runtimes (Java/Kotlin/Gradle de ferramenta)"
distrobox enter "$BOX" -- bash -c "
  set -e
  if ! command -v mise >/dev/null 2>&1; then
    curl -fsSL https://mise.run | sh
  fi
  export PATH=\"\$HOME/.local/bin:\$PATH\"
  eval \"\$(mise activate bash)\"
  mise use -g java@$JAVA_VERSION
  mise use -g kotlin@latest
  mise use -g gradle@latest
"

echo "==> 4/5 Instalando Android SDK (cmdline-tools, platform, build-tools, NDK)"
distrobox enter "$BOX" -- bash -c "
  set -e
  SDK=\"\$HOME/Android/Sdk\"
  mkdir -p \"\$SDK/cmdline-tools\"
  if [ ! -d \"\$SDK/cmdline-tools/latest\" ]; then
    tmp=\$(mktemp -d)
    curl -fsSL '$CMDLINE_TOOLS_URL' -o \"\$tmp/clt.zip\"
    unzip -q \"\$tmp/clt.zip\" -d \"\$tmp\"
    mv \"\$tmp/cmdline-tools\" \"\$SDK/cmdline-tools/latest\"
    rm -rf \"\$tmp\"
  fi
  export ANDROID_HOME=\"\$SDK\"
  export PATH=\"\$SDK/cmdline-tools/latest/bin:\$PATH\"
  yes | sdkmanager --licenses >/dev/null 2>&1 || true
  sdkmanager --install 'platform-tools' 'platforms;$PLATFORM' \
    'build-tools;$BUILD_TOOLS' 'ndk;$NDK_VERSION' >/dev/null
"

echo "==> 5/5 Instalando o env.sh de ambiente (carregado ao entrar na box)"
mkdir -p "$HOME/.config/$BOX"
install -m 0644 "$(dirname "$0")/quest-dev.env.sh" "$HOME/.config/$BOX/env.sh"
# Garante que o env.sh seja carregado nos shells de login da box.
distrobox enter "$BOX" -- bash -c '
  LINE="[ -f \"$HOME/.config/quest-dev/env.sh\" ] && source \"$HOME/.config/quest-dev/env.sh\""
  grep -qF "$LINE" "$HOME/.bashrc" || echo "$LINE" >> "$HOME/.bashrc"
'

cat <<EOF

==> Pronto. Ambiente '$BOX' recriado.

ATENÇÃO — passos MANUAIS que não dá para automatizar (licença/download fechado):
  - Meta Spatial Editor CLI: baixe o pacote 'meta-spatial-editor-cli-linux-dist'
    do site de desenvolvedores da Meta e extraia em:
      ~/.local/opt/meta-spatial-editor-cli-linux-dist
    (usado pela task :app:export do Gradle; ver quest-dev.env.sh)
  - Modelos não versionados (TTS e detecção): ver README, seção "Modelos".

Teste o build:
  distrobox enter $BOX -- bash -lc 'cd ~/Projetos/quest-assist-app && ./gradlew :app:assembleDebug'
EOF
