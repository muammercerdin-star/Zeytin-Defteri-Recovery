#!/bin/bash
# Başlangıç sözlüğünden gömülü bilgi paketini üretir (İP8). Önce kontrol bankasını tazeler; söz geçersizse ya da
# paket cümle bankasını bozuyorsa dosya yazılmaz. Kullanım: bash tools/make_pack.sh [--check]
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
if [ "$1" != "--check" ]; then python3 "$ROOT/tools/make_control_bank.py"; fi
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" "$A/ContributionCleaner.java" "$A/CommunityQueue.java" "$A/KnowledgePack.java" "$A/PackedStore.java" \
  "$A/PackGate.java" "$ROOT/tools/pack/PackTool.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.PackTool "$ROOT" "$@"
