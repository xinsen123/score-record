#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODS_DIR="/mnt/d/games/minecraft/.minecraft/mods"
ARCHIVE_BASE_NAME="score-record"
MOD_VERSION="$(grep '^mod_version=' "$ROOT_DIR/gradle.properties" | cut -d= -f2-)"

if [[ ! -d "$MODS_DIR" ]]; then
  printf 'Windows mods directory not found: %s\n' "$MODS_DIR" >&2
  exit 1
fi

"$ROOT_DIR/gradlew" build

JAR_PATH="$ROOT_DIR/build/libs/${ARCHIVE_BASE_NAME}-${MOD_VERSION}.jar"
if [[ ! -f "$JAR_PATH" ]]; then
  printf 'Built mod jar not found: %s\n' "$JAR_PATH" >&2
  exit 1
fi

OLD_JARS=("$MODS_DIR"/${ARCHIVE_BASE_NAME}-*.jar)
if (( ${#OLD_JARS[@]} > 0 )); then
  if ! rm -f -- "${OLD_JARS[@]}"; then
    printf 'Failed to remove old mod jars in %s\n' "$MODS_DIR" >&2
    printf 'If Minecraft or HMCL is running on Windows, close it and try again.\n' >&2
    exit 1
  fi
fi

if ! cp "$JAR_PATH" "$MODS_DIR/"; then
  printf 'Failed to copy %s to %s\n' "$JAR_PATH" "$MODS_DIR" >&2
  printf 'If Minecraft or HMCL is running on Windows, close it and try again.\n' >&2
  exit 1
fi

printf 'Synced %s -> %s\n' "$JAR_PATH" "$MODS_DIR/"
