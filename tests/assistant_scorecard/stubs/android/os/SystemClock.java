package android.os;

/** Masaüstü testi için taklit saat: test ilerletir. */
public final class SystemClock {
    public static long now = 1_000_000L;

    private SystemClock() {
    }

    public static long elapsedRealtime() {
        return now;
    }
}
