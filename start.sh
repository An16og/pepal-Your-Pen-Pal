#!/usr/bin/env bash
set -euo pipefail

# Change to script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo
echo "=============================================================="
echo "                   Pepal: Your Pen-pal"
echo "         Offline, Local-First AI Physical Journal"
echo "=============================================================="
echo

# 1. Verify Docker is installed
if ! command -v docker &> /dev/null; then
    echo "[ERROR] Docker is not installed or not in your PATH."
    echo "Please install Docker from https://docs.docker.com/get-docker/"
    exit 1
fi

# 2. Verify Docker daemon is running
if ! docker info &> /dev/null; then
    echo "[ERROR] Docker daemon is not running."
    echo "Please start Docker Desktop or the Docker service and try again."
    exit 1
fi

# 3. Avoid port conflict if host Ollama is already using port 11434
if command -v nc &> /dev/null && nc -z 127.0.0.1 11434 2>/dev/null; then
    echo "[*] Detected host Ollama service on port 11434."
    echo "[*] Mapping container Ollama port to host 11435 to avoid port collision."
    export OLLAMA_HOST_PORT=11435
elif command -v lsof &> /dev/null && lsof -i :11434 &> /dev/null; then
    echo "[*] Detected host Ollama service on port 11434."
    echo "[*] Mapping container Ollama port to host 11435 to avoid port collision."
    export OLLAMA_HOST_PORT=11435
fi

echo "[*] Building and launching containers (app, db, ollama, model-puller)..."
echo "[*] First-time launch will build the image and download models."
echo

docker compose up -d --build

echo
echo "[*] Waiting for Pepal to be ready at http://localhost:8080 ..."
ATTEMPTS=0
until curl -s -f http://localhost:8080/ > /dev/null 2>&1 || [ "$ATTEMPTS" -ge 90 ]; do
    ATTEMPTS=$((ATTEMPTS + 1))
    sleep 2
done

if [ "$ATTEMPTS" -lt 90 ]; then
    echo
    echo "=============================================================="
    echo " [SUCCESS] Pepal (Your Pen-pal) is online at http://localhost:8080"
    echo "=============================================================="
    echo
    echo "[*] Opening in your default browser..."
    if command -v xdg-open &> /dev/null; then
        xdg-open http://localhost:8080 > /dev/null 2>&1 &
    elif command -v open &> /dev/null; then
        open http://localhost:8080
    fi
else
    echo
    echo "[WARNING] Service took longer than expected to report ready."
    echo "It may still be pulling models or initializing the database."
    echo "Check container status: docker compose ps"
    echo "View live logs: docker compose logs -f"
fi

echo
echo "To stop all services at any time, run: ./stop.sh (or docker compose down)"
echo
