package android.content;

/** Masaüstü testi için en küçük taklit. */
public interface SharedPreferences {
    String getString(String key, String defValue);

    Editor edit();

    interface Editor {
        Editor putString(String key, String value);

        Editor remove(String key);

        void apply();
    }
}
