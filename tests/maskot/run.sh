#!/bin/bash
# Tanecik: sözler (saf Java) ve çizimler (VectorDrawable kuralları).
set -e
here="$(cd "$(dirname "$0")" && pwd)"; root="$(cd "$here/../.." && pwd)"
out="$(mktemp -d)"; trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -d "$out" "$root/app/src/main/java/com/zeytindefteri/app/TanecikLines.java" "$here/TanecikLinesTest.java"
java -Dstdout.encoding=UTF-8 -cp "$out" com.zeytindefteri.app.TanecikLinesTest
