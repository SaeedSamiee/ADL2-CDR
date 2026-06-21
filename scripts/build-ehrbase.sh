#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
"$ROOT/scripts/publish-archie.sh"
cd "$ROOT/ehrbase"
mvn clean install -DskipTests -DskipITs
