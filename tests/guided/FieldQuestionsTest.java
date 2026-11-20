package com.zeytindefteri.app;

import java.util.Arrays;
import java.util.List;

/** Alan soruları katalogda tek yerde; sesli diyalogların soruları birebir aynı kaldı (yönlendiren asistan, C1). */
public class FieldQuestionsTest {
    static int n = 0;
    static int bad = 0;

    static void eq(Object got, Object want, String m) {
        n++;
        if (!want.equals(got)) {
            bad++;
            System.out.println("FAIL " + m + ": «" + got + "» beklenen «" + want + "»");
        }
    }

    public static void main(String[] a) {
        // Eski OperationVoiceDialog.question() metinleri (ce6f56e), tür tür.
        String[][] garden = {{"Sulama", "Hangi bahçeyi suladın?"}, {"Gübre", "Hangi bahçeye gübre attın?"},
                {"İlaçlama", "Hangi bahçeyi ilaçladın?"}, {"Budama", "Hangi bahçeyi budadın?"},
                {"Sinek tuzağı", "Hangi bahçeye tuzak astın?"}, {"Toprak İşleme", "Hangi bahçeyi sürdün?"},
                {"İşçilik", "Hangi bahçede çalıştılar?"}, {"Mazot", "Hangi bahçe için?"}, {"Diğer", "Hangi bahçe için?"}};
        for (String[] g : garden) {
            eq(ActionCatalog.question(ActionCatalog.ISLEM, "bahce", g[0]), g[1], "bahçe sorusu " + g[0]);
        }
        String[][] qty = {{"Sulama", "Kaç saat suladın?"}, {"Gübre", "Ne kadar attın?"}, {"İlaçlama", "Ne kadar kullandın?"},
                {"Budama", "Kaç ağaç budadın?"}, {"Sinek tuzağı", "Kaç tuzak astın?"}, {"Toprak İşleme", "Kaç dekar sürdün?"},
                {"İşçilik", "Kaç işçi çalıştı?"}, {"Mazot", "Kaç litre aldın?"}, {"Nakliye", "Kaç sefer yaptın?"}, {"Diğer", "Ne kadar?"}};
        for (String[] q : qty) {
            eq(ActionCatalog.question(ActionCatalog.ISLEM, "miktar", q[0]), q[1], "miktar sorusu " + q[0]);
        }
        eq(ActionCatalog.question(ActionCatalog.ISLEM, "not", "Gübre"), "Hangi gübreyi attın?", "gübre adı");
        eq(ActionCatalog.question(ActionCatalog.ISLEM, "not", "İlaçlama"), "Ne attın? İlacın adını söyle.", "ilaç adı");
        eq(ActionCatalog.question(ActionCatalog.ISLEM, "tutar", "İşçilik"), "Kaç lira yevmiye verdin?", "yevmiye");
        eq(ActionCatalog.question(ActionCatalog.ISLEM, "tutar", "Sulama"), "Masrafı ne kadar tuttu?", "masraf");
        // Eski VoiceRecordDialog soruları (ce6f56e), sıra sıra.
        List<String> harvest = Arrays.asList("Hangi bahçeden topladın?", "Kaç kilo topladın?",
                "İşçilik masrafı oldu mu? Kaç lira?", "Nakliye tuttu mu? Kaç lira?");
        List<String> brine = Arrays.asList("Kaç kilo zeytin bastın?", "Hangi zeytin? Gemlik mi, Domat mı?", "Kaç kilo tuz attın?",
                "Kaç litre su koydun?");
        List<String> gardenNew = Arrays.asList("Bahçenin adı ne olsun?", "Kaç ağaç var?", "Hangi zeytin? Gemlik, Domat, Ayvalık?",
                "Kaç dönüm?");
        eq(questions(VoiceRecordDialog.Kind.HARVEST, "garden", "kg", "labor", "transport"), harvest, "hasat soruları");
        eq(questions(VoiceRecordDialog.Kind.BRINE, "olive", "variety", "salt", "water"), brine, "salamura soruları");
        eq(questions(VoiceRecordDialog.Kind.GARDEN, "name", "trees", "variety", "area"), gardenNew, "bahçe soruları");
        eq(ActionCatalog.question(ActionCatalog.SATIS, "odeme", ""), "Parasını aldın mı?", "uygulayıcının ödeme sorusu");
        eq(ActionCatalog.question(ActionCatalog.ISLEM, "tur", "masraf"), "Ne için harcadın?", "masraf düğmesi sorusu");
        for (String key : ActionCatalog.questionKeys()) {
            String[] k = key.split("\\|", -1);
            n++;
            if (!ActionCatalog.get(k[0]).fields.contains(k[1])) {
                bad++;
                System.out.println("FAIL katalogda olmayan alan: " + key);
            }
        }
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " FieldQuestions");
        if (bad > 0) {
            System.exit(1);
        }
    }

    static List<String> questions(VoiceRecordDialog.Kind kind, String... keys) {
        VoiceRecordDialog d = new VoiceRecordDialog(kind, Arrays.asList("Girelli", "Kuyu"));
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String k : keys) {
            out.add(d.question(k));
        }
        return out;
    }
}
