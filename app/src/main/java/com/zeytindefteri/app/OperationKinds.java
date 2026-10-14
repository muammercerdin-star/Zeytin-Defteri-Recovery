package com.zeytindefteri.app;

import java.util.Locale;

/**
 * İşlem türlerinin saf (Android'siz) tanımı: kayıtta kullanılan ad, çizgi ikon anahtarı ve renk tonu.
 * Ad listesi NewOperationActivity ile aynıdır; veritabanına yazılan değer değişmez.
 * İkon anahtarı LineIcons ile drawable'a çevrilir (ic_op_&lt;anahtar&gt;).
 */
public final class OperationKinds {
    private OperationKinds() {}

    /** Kayıt formunda gösterilen türler; sıra formdaki seçim kutularının sırasıdır. */
    public static final String[] TYPES = {"Sulama", "Gübre", "İlaçlama", "Budama", "Sinek tuzağı",
            "Toprak İşleme", "İşçilik", "Mazot", "Nakliye", "Diğer"};

    public enum Tone { WATER, GREEN, AMBER, SOIL, NEUTRAL }

    private static final Locale TR = new Locale("tr", "TR");

    /** Türün çizgi ikon anahtarı. Eski/elle girilmiş adlar (Gübreleme, Hasat, Satış) da tanınır. */
    public static String iconKey(String type) {
        String t = normalize(type);
        if (t.isEmpty()) return "note";
        if (t.startsWith("sulama")) return "water";
        if (t.startsWith("gübre")) return "leaf";
        if (t.startsWith("ilaç")) return "flask";
        if (t.startsWith("budama")) return "scissors";
        if (t.startsWith("sinek")) return "trap";
        if (t.startsWith("toprak")) return "soil";
        if (t.startsWith("işçi")) return "worker";
        if (t.startsWith("mazot") || t.startsWith("yakıt")) return "fuel";
        if (t.startsWith("nakliye")) return "truck";
        if (t.startsWith("hasat")) return "basket";
        if (t.startsWith("elek")) return "elek";
        if (t.startsWith("satış")) return "tag";
        if (t.startsWith("diğer")) return "more";
        return "note";
    }

    public static Tone tone(String type) {
        switch (iconKey(type)) {
            case "water": return Tone.WATER;
            case "leaf": case "basket": case "elek": case "tag": return Tone.GREEN;
            case "flask": case "trap": return Tone.AMBER;
            case "soil": return Tone.SOIL;
            default: return Tone.NEUTRAL;
        }
    }

    /** Listede gösterilecek ad; boşsa "İşlem". */
    public static String label(String type) {
        return type == null || type.trim().isEmpty() ? "İşlem" : type.trim();
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(TR);
    }
}
