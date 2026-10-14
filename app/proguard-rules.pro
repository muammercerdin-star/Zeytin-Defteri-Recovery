# Add project-specific ProGuard/R8 keep rules here.
# These are applied on top of the bundled defaults (proguard-android-optimize.txt) when the
# build type has minifyEnabled = true.
#
# Keep a class that is referenced only by reflection / from XML, e.g.:
# -keep class com.example.SomeClass { *; }
#
# Preserve line numbers for readable crash stack traces, then hide the original file name:
# -keepattributes SourceFile,LineNumberTable
# -renamesourcefileattribute SourceFile

# Supabase istemci ayarları MarketBackendConfig tarafından BuildConfig'ten yansıma (reflection) ile okunur.
-keep class com.zeytindefteri.app.BuildConfig { public static final java.lang.String SUPABASE_URL; public static final java.lang.String SUPABASE_PUBLISHABLE_KEY; }
