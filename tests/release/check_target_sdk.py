"""Play Store hedef API (Android 16 / API 36) hazırlığı: statik kontrol.
Gerçek derleme ve cihaz testi değildir; derleme Android Studio / Gradle ile yapılmalıdır."""
from pathlib import Path
import re, sys

ROOT = Path(__file__).resolve().parents[2]
app = (ROOT / 'app/build.gradle').read_text()
top = (ROOT / 'build.gradle').read_text()
wrapper = (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text()
man = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf8')
J = ROOT / 'app/src/main/java'
A = J / 'com/zeytindefteri/app'
appj = (J / 'ZeytinApplication.java').read_text(encoding='utf8')
insets = (A / 'SystemBarInsets.java').read_text(encoding='utf8')
feed = (A / 'ProducerFeedActivity.java').read_text(encoding='utf8')
picker = (A / 'LocationPickerActivity.java').read_text(encoding='utf8')
all_java = '\n'.join(p.read_text(encoding='utf8') for p in J.rglob('*.java'))
themes = '\n'.join(p.read_text(encoding='utf8') for p in (ROOT / 'app/src/main/res').rglob('*.xml'))

checks = {
    'targetSdk 36 (Play: yeni uygulamalar 31.08.2026\'dan beri)': re.search(r'targetSdk\s+36\b', app) is not None,
    'compileSdk 36': re.search(r'compileSdk\s+36\b', app) is not None,
    'AGP 8.10.0 (en fazla API 36 destekler)': "'com.android.application' version '8.10.0'" in top,
    'Gradle 8.11.1 (AGP 8.10 en az) + resmi SHA-256': 'gradle-8.11.1-bin.zip' in wrapper
        and 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6' in wrapper,
    'kenardan kenara: her ekrana çubuk/klavye boşluğu': 'onActivityPostCreated' in appj and 'SystemBarInsets.install(a)' in appj
        and 'WindowInsets.Type.ime()' in insets and 'SDK_INT < 35' in insets and 'WindowInsets.CONSUMED' in insets,
    'kenardan kenara kapatma ayarı kullanılmıyor (Android 16 yok sayar)': 'windowOptOutEdgeToEdgeEnforcement' not in themes,
    'yeni geri tuşu API\'si açık': 'android:enableOnBackInvokedCallback="true"' in man,
    'Sezon Raporum geri tuşu Android 13+ için kayıtlı': 'BackCompat.register(this, () -> showSection("REPORTS"))' in feed
        and 'BackCompat.unregister(this, seasonReportBack)' in feed,
    'Konum seçici geri tuşu Android 13+ için kayıtlı': 'BackCompat.register(this, this::handleBack)' in picker,
    'KEYCODE_BACK yakalanmıyor (Android 16 göndermez)': 'KEYCODE_BACK' not in all_java,
    'onBackPressed yalnızca bilinen iki ekranda': len(re.findall(r'public void onBackPressed\(', all_java)) == 2,
}
for k, ok in checks.items():
    print(('PASS ' if ok else 'FAIL ') + k)
sys.exit(0 if all(checks.values()) else 1)
