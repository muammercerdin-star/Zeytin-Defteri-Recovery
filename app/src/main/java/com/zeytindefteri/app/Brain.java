package com.zeytindefteri.app;

/**
 * Değiştirilebilir beyin (İP3): cümleyi eyleme çevirir. Bugün kurallar (RuleBrain); internet yardımcısı ve
 * ileride küçük model aynı arayüze oturur. Çıkış hep aynıdır (Decision); hiç anlamazsa ANLASILMADI.
 * Beyin hiçbir şey yazmaz, ekran açmaz: yalnız karar verir.
 */
interface Brain {

    /** Kural beyninin adı (RuleBrain ve onun elek parçası ElekRules). */
    String RULES = "kural";

    /** Girdi: kullanıcının sözü olduğu gibi ve TurkishText ile sadeleşmiş hâli. */
    final class Input {
        final String raw;
        final String text;

        Input(String raw) {
            this.raw = raw == null ? "" : raw;
            this.text = TurkishText.normalize(this.raw);
        }
    }

    /** Kısa ad ("kural", "internet"); kararın kimden geldiği kayıtta görünür. */
    String name();

    Decision decide(Input input, BrainContext context);
}
