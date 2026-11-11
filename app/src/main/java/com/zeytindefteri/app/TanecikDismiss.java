package com.zeytindefteri.app;

/**
 * Tanecik'i sürükleyip "X"e bırakarak kapatma (sohbet balonu gibi): X'in yeri ve "üstünde mi" kararı.
 * Android'e bağlı değil, test edilir.
 */
public final class TanecikDismiss {

    private TanecikDismiss() { }

    /** X dairesinin çapı ve yakalama yarıçapı (dp). */
    public static final int SIZE_DP = 60;
    public static final int CATCH_DP = 64;
    /** X'in merkezi alt menünün bu kadar üstünde (dp). */
    public static final int ABOVE_FLOOR_DP = 72;

    /** X'in merkezi (piksel): yatayda ortada, alt menünün biraz üstünde. */
    public static float centerX(float layerWidth) {
        return layerWidth / 2f;
    }

    public static float centerY(float floorTop, float density) {
        return floorTop - ABOVE_FLOOR_DP * density;
    }

    /** Tanecik'in ortası X'in yakalama alanında mı? */
    public static boolean over(float actorCx, float actorCy, float xCx, float xCy, float density) {
        float r = CATCH_DP * density;
        float dx = actorCx - xCx;
        float dy = actorCy - xCy;
        return dx * dx + dy * dy <= r * r;
    }

    public static final String CLOSED_TOAST = "Tanecik kapandı · Ayarlar → Tanecik'ten geri açabilirsin";
}
