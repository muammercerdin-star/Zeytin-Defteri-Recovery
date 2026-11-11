package com.zeytindefteri.app;

public class OperationDeleteTextTest {
    static int n, fail;
    static void ok(boolean c, String what) { n++; if (!c) { fail++; System.out.println("FAIL " + what); } }
    public static void main(String[] a) {
        String m = OperationDeleteText.message("İlaçlama", "Pınarlı", "2026-11-05", 5050, false);
        ok(m.startsWith("İlaçlama · Pınarlı · 2026-11-05"), "başlık satırı: " + m);
        ok(m.contains("masraf toplamdan düşülür"), "masraf notu");
        ok(m.contains("Geri alınamaz"), "geri alınamaz uyarısı");
        ok(!m.contains("hasat kaydı"), "bağsız işlemde hasat notu yok");
        String z = OperationDeleteText.message("Budama", "", "", 0, false);
        ok(z.startsWith("Budama\n\n") && !z.contains("masraf"), "masrafsız işlem: " + z);
        String h = OperationDeleteText.message("Hasat", "Pınarlı", "2026-11-07", 1200, true);
        ok(h.contains("hasat kaydı (kilolarıyla) birlikte silinir") && h.contains("Elek kayıtlarına dokunulmaz"), "hasat bağı");
        ok(OperationDeleteText.title(true).equals("Hasat kaydı silinsin mi?"), "hasat başlığı");
        ok(OperationDeleteText.title(false).equals("İşlem silinsin mi?"), "işlem başlığı");
        ok(OperationDeleteText.done(false).equals("İşlem silindi"), "bitti");
        ok(OperationDeleteText.message(null, null, null, 0, false).contains("Geri alınamaz"), "boş değerler");
        System.out.println(n - fail + "/" + n + " geçti");
        if (fail > 0) System.exit(1);
    }
}
