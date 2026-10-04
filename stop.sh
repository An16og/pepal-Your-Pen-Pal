#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo
echo "=============================================================="
echo "                  Stopping pepal Journal"
echo "=============================================================="
echo

echo "[*] Gracefully stopping all services (app, db, ollama)..."
docker compose down

echo
echo "=============================================================="
echo " [SUCCESS] All pepal Journal services have been stopped."
echo " Your journal entries and models are safely saved in volumes."
echo "=============================================================="
echo
