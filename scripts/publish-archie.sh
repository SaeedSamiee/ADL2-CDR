#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/archie"
./gradlew publishToMavenLocal -x test
echo "Archie published to ~/.m2/repository/com/nedap/healthcare/archie/"
