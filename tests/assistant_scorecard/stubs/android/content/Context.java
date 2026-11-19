package android.content;

/** Masaüstü testi için en küçük taklit: yalnız SharedPreferences. */
public abstract class Context {
    public static final int MODE_PRIVATE = 0;

    public abstract SharedPreferences getSharedPreferences(String name, int mode);
}
