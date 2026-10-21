#!/usr/bin/env bash
# Ambiente de desenvolvimento Meta Quest 3 (Spatial SDK).
# Instalado pelo bootstrap em ~/.config/quest-dev/env.sh e carregado
# automaticamente APENAS dentro da distrobox 'quest-dev' (via ~/.bashrc).
# Não afeta o host imutável nem outras distroboxes.

# --- mise (gerenciador de versões: Java, Kotlin, Gradle de ferramenta) ---
# OBS: o build do app usa o WRAPPER Gradle (9.4.1) e Kotlin 2.1.0 do projeto;
# o mise serve de fallback e para rodar ferramentas soltas.
export PATH="$HOME/.local/bin:$PATH"
if command -v mise >/dev/null 2>&1; then
  eval "$(mise activate bash)"
fi

# --- Android SDK / NDK ---
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$PATH"

# --- Meta Spatial Editor CLI (para a task :app:export do Gradle) ---
# Baixar manualmente do site de desenvolvedores da Meta e extrair aqui
# (não é versionável/automatizável — ver scripts/bootstrap-quest-dev.sh).
export META_SPATIAL_EDITOR_CLI_DIR="$HOME/.local/opt/meta-spatial-editor-cli-linux-dist"
export META_SPATIAL_EDITOR_CLI_PATH="$META_SPATIAL_EDITOR_CLI_DIR/MetaSpatialEditorCLI"
export PATH="$META_SPATIAL_EDITOR_CLI_DIR:$PATH"

# --- adb ---
# O adb roda no HOST e gerencia o Quest via USB. Dentro da distrobox, se precisar,
# conecte por TCP: no host 'adb tcpip 5555' (Quest plugado) e então
#   adb connect <IP_DO_QUEST>:5555

# Aviso visual ao entrar
_box="${CONTAINER_ID:-quest-dev}"
echo "[$_box] Ambiente Meta Quest carregado:"
echo "  JAVA_HOME     = $(mise where java 2>/dev/null || echo 'via mise')"
echo "  ANDROID_HOME  = $ANDROID_HOME"
echo "  ANDROID_NDK   = $ANDROID_NDK_HOME"
unset _box
