# Zeytin Defteri - Gradle dönüşümü

Bu kopya CodeAssist `module.toml` yapısından standart Android Gradle yapısına dönüştürülmüştür.

- Uygulama modülü: `app/`
- Kaynak kod: `app/src/main/`
- Firebase Android config: `app/google-services.json`
- Firestore deployment dosyaları: proje kökünde `firebase.json` ve `firestore.rules`
- Eski `module.toml` yalnızca referans/geri dönüş amacıyla kökte bırakılmıştır; Gradle tarafından kullanılmaz.
- Gradle: 8.7
- Android Gradle Plugin: 8.5.2
- compileSdk/targetSdk: 34
- minSdk: 24
- Java: 8

AndroidIDE ilk build sırasında Gradle/AGP/Firebase bağımlılıklarını internetten indirebilir. İlk build sonraki buildlerden uzun sürebilir.
