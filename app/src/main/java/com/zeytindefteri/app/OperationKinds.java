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

    /** Türe göre form: miktar etiketi, örnek, birimler (ilki varsayılan) ve ipucu. */
    public static final class Form {
        public final String quantityLabel, quantityHint, tip;
        public final String[] units;
        Form(String quantityLabel, String quantityHint, String[] units, String tip) {
            this.quantityLabel = quantityLabel; this.quantityHint = quantityHint; this.units = units; this.tip = tip;
        }
    }

    public static Form form(String type) {
        switch (type == null ? "" : type) {
            case "Sulama": return new Form("Su / süre", "Örn. 4", new String[]{"saat", "m³", "L"},
                    "Sulamada çalışma süresini veya verilen su miktarını kaydedebilirsin.");
            case "Gübre": return new Form("Gübre miktarı", "Örn. 18", new String[]{"kg", "g", "L", "ml"},
                    "Gübre adını nota yazabilirsin. Örn. MKP 0-52-34, 18 kg.");
            case "İlaçlama": return new Form("Ürün miktarı", "Örn. 500", new String[]{"ml", "L", "g", "kg"},
                    "Kullanılan ilacın veya yaprak ürününün adını nota kaydet.");
            case "Budama": return new Form("Budanan miktar", "Örn. 120", new String[]{"ağaç", "saat", "gün"},
                    "Budanan ağaç sayısını veya harcanan çalışma süresini kaydedebilirsin.");
            case "Sinek tuzağı": return new Form("Tuzak sayısı", "Örn. 20", new String[]{"adet"},
                    "Asılan veya yenilenen zeytin sineği tuzağı sayısını kaydet.");
            case "Toprak İşleme": return new Form("İşlenen alan / süre", "Örn. 8", new String[]{"dekar", "saat", "gün"},
                    "Sürüm, çapalama veya diğer toprak işlemlerini kaydedebilirsin.");
            case "İşçilik": return new Form("İşçilik", "Örn. 3", new String[]{"gün", "kişi", "saat"},
                    "İşçilik süresini veya çalışan kişi sayısını kaydet.");
            case "Mazot": return new Form("Mazot miktarı", "Örn. 25", new String[]{"L"},
                    "Bahçede kullanılan mazot miktarını ve masrafını kaydet.");
            case "Nakliye": return new Form("Nakliye", "Örn. 1", new String[]{"sefer", "km", "ton"},
                    "Nakliye seferini, mesafesini veya taşınan miktarı kaydedebilirsin.");
            default: return new Form("Miktar", "Örn. 1", new String[]{"adet", "kg", "L", "saat", "gün"},
                    "Diğer bahçe işlemlerini açıklamasıyla birlikte kaydedebilirsin.");
        }
    }

    /** Gelen tür adını (ör. ana sayfadan) formdaki adla eşleştirir; bulunamazsa ilk tür. */
    public static int indexOf(String type) {
        if (type != null) {
            String t = type.trim();
            if ("Gübreleme".equalsIgnoreCase(t)) t = "Gübre";
            for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equalsIgnoreCase(t)) return i;
        }
        return 0;
    }

    /** Listede gösterilecek ad; boşsa "İşlem". */
    public static String label(String type) {
        return type == null || type.trim().isEmpty() ? "İşlem" : type.trim();
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(TR);
    }
}
