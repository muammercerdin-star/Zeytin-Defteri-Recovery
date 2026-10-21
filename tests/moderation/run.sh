#!/bin/sh
# Moderasyon: küfür filtresi (kandırma yolları + yanlış alarm), moderasyon görünürlük kuralları, Firestore kural metni.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d)
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" app/src/main/java/com/zeytindefteri/app/ProfanityFilter.java tests/moderation/ProfanityFilterTest.java
java -Dfile.encoding=UTF-8 -cp "$D" com.zeytindefteri.app.ProfanityFilterTest
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" app/src/main/java/com/zeytindefteri/app/MeydanModeration.java tests/moderation/MeydanModerationTest.java
java -Dfile.encoding=UTF-8 -cp "$D" com.zeytindefteri.app.MeydanModerationTest
