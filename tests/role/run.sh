#!/bin/sh
# Kullanım şekli (üretici / tezgâhçı / ikisi de): açılış yönlendirmesi.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d)
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" app/src/main/java/com/zeytindefteri/app/UserRole.java tests/role/UserRoleTest.java
java -Dfile.encoding=UTF-8 -cp "$D" com.zeytindefteri.app.UserRoleTest
