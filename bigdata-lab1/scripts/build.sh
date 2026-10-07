#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v javac >/dev/null
command -v hadoop >/dev/null
mkdir -p build/classes
javac -encoding UTF-8 -cp "$(hadoop classpath --glob)" -d build/classes src/main/java/lab/Main.java
jar cf build/lab1.jar -C build/classes .
echo 'Built build/lab1.jar'
