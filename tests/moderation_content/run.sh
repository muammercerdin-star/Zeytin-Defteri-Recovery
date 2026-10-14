#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
P=app/src/main/java/com/zeytindefteri/app
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
javac -nowarn -encoding UTF-8 -d "$D" $P/ModerationPage.java tests/moderation_content/ModerationContentPageTest.java
java -cp "$D" com.zeytindefteri.app.ModerationContentPageTest
python3 tests/moderation_content/check_wiring.py
(cd functions && node --test test/moderation.content.test.js >/dev/null) && echo "moderation.content.test.js: geçti"
