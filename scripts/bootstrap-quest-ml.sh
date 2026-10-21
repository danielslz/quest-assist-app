#!/usr/bin/env bash
# Bootstrap da distrobox 'quest-ml' (ferramentas de ML/visão e geração de slides).
# NÃO é usada para buildar o app — só para modelos e utilitários (ex.: python-pptx).
#
# Uso (no HOST):
#   bash scripts/bootstrap-quest-ml.sh
#
# Depois, usar com:
#   distrobox enter quest-ml -- bash -lc 'source ~/Projetos/quest-ml/.venv/bin/activate && python <script>'
set -euo pipefail

BOX="quest-ml"
IMAGE="docker.io/library/ubuntu:24.04"
VENV="$HOME/Projetos/quest-ml/.venv"
REQ="$(cd "$(dirname "$0")" && pwd)/quest-ml-requirements.txt"

echo "==> 1/3 Criando a distrobox '$BOX' se não existir"
if ! distrobox list | grep -q "\b$BOX\b"; then
  distrobox create --name "$BOX" --image "$IMAGE" --yes
fi

echo "==> 2/3 Instalando Python + libs de sistema"
distrobox enter "$BOX" -- bash -c '
  set -e
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    python3 python3-venv python3-pip ffmpeg libgl1 libglib2.0-0
'

echo "==> 3/3 Criando o venv e instalando as dependências Python"
distrobox enter "$BOX" -- bash -c "
  set -e
  mkdir -p \"\$HOME/Projetos/quest-ml\"
  [ -d '$VENV' ] || python3 -m venv '$VENV'
  '$VENV/bin/pip' install --upgrade pip -q
  if [ -f '$REQ' ]; then
    '$VENV/bin/pip' install -q -r '$REQ'
  else
    echo 'AVISO: quest-ml-requirements.txt não encontrado; instalando o mínimo.'
    '$VENV/bin/pip' install -q ultralytics onnx onnxruntime opencv-python python-pptx pillow
  fi
"

cat <<EOF

==> Pronto. Distrobox '$BOX' recriada com venv em $VENV.
Nota: PyTorch/CUDA no requirements assume GPU NVIDIA; ajuste conforme o hardware.
EOF
