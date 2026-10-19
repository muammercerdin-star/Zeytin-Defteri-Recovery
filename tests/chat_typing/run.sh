#!/bin/sh
# "… yazıyor" göstergesi: etiket ve süre/engel süzgeci (Firebase saplamalarıyla, Android'siz).
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); T=tests/chat_typing
export JAVA_TOOL_OPTIONS=
STUB=$(mktemp -d); mkdir -p $STUB/android/content $STUB/android/os
cat > $STUB/android/content/SharedPreferences.java <<'J'
package android.content; public interface SharedPreferences { boolean getBoolean(String k, boolean d); Editor edit(); interface Editor { Editor putBoolean(String k, boolean v); void apply(); } }
J
cat > $STUB/android/content/Context.java <<'J'
package android.content; public abstract class Context { public static final int MODE_PRIVATE=0; public abstract Context getApplicationContext(); public abstract SharedPreferences getSharedPreferences(String n,int m); }
J
cat > $STUB/android/os/Looper.java <<'J'
package android.os; public class Looper { public static Looper getMainLooper(){ return null; } }
J
cat > $STUB/android/os/Handler.java <<'J'
package android.os; public class Handler { public Handler(Looper l){} public boolean postDelayed(Runnable r,long d){return true;} public void removeCallbacks(Runnable r){} }
J
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" $(find $T/stubs $STUB -name "*.java") \
  app/src/main/java/com/zeytindefteri/app/ChatTypingRepository.java $T/ChatTypingTest.java
java -cp "$D" com.zeytindefteri.app.ChatTypingTest
