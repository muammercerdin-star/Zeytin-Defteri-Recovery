package com.zeytindefteri.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tanecik sahneleri: cümle → doğru sahne; sorular soru kalır; her sahne denetimden geçer; KÖTÜ SAHNE (Kaydet'e basmaya
 * çalışan) reddedilir; ÖĞRET ve GÖSTER hiçbir şeye dokunmaz; ZARARSIZ İŞ izinsiz oynamaz; her sahne var olan ekran ve
 * öğelere bağlı (Activity dosyası, R.id adı ya da ekranda konan "sahne:" etiketi).
 */
public class ScenesTest {
    static int n = 0;
    static int bad = 0;
    static Path root;
    static String javaSources;
    static String resources;

    static void check(boolean ok, String m) {
        n++;
        if (!ok) {
            bad++;
            System.out.println("FAIL " + m);
        }
    }

    static Decision decide(String raw) {
        return new RuleBrain().decide(new Brain.Input(raw), BrainContext.full(true, AssistantPersona.decode("")::handle));
    }

    public static void main(String[] a) throws IOException {
        root = Paths.get(a[0]);
        javaSources = readAll(root.resolve("app/src/main/java"), ".java");
        resources = readAll(root.resolve("app/src/main/res"), ".xml");
        matching();
        checks();
        badScenes();
        binding();
        System.out.println((bad == 0 ? "PASS " : "FAIL ") + (n - bad) + "/" + n + " Scenes");
        if (bad > 0) {
            System.exit(1);
        }
    }

    static void matching() {
        String[][] scenes = {
                {"Tanecik, alacaklarımı göster", "GOSTER", "alacaklar"},
                {"bu ayki masraflarım", "GOSTER", "masraflar"},
                {"ödeme hatırlatmalarım", "GOSTER", "odeme_hatirlatmalari"},
                {"hasat raporunu aç", "GOSTER", "hasat_raporu"},
                {"piyasa fiyatına bak", "GOSTER", "piyasa"},
                {"Elek nasıl girilir?", "OGRET", "elek_ogret"},
                {"Satış nasıl yapılır?", "OGRET", "satis_ogret"},
        };
        for (String[] s : scenes) {
            Decision d = decide(s[0]);
            check(d.is(s[1]) && s[2].equals(d.fields.get("sahne")), "sahne: " + s[0] + " → " + d);
        }
        String[][] stay = {
                {"Ne kadar alacağım var?", "ALACAK_SORU"}, {"Kim bana borçlu?", "ALACAK_SORU"},
                {"bu ay ne kadar masraf yaptım", "SORU_EXPENSE"}, {"piyasa fiyatı ne", "SORU_MARKET"},
                {"anlamadıklarımı göster", "ANLAMADIKLARI_GOSTER"}, {"karnemi göster", "KARNE_GOSTER"},
                {"Elek aç", "EKRAN_AC"}, {"bugün suladık", "ISLEM"},
        };
        for (String[] s : stay) {
            check(decide(s[0]).is(s[1]), "olduğu gibi kalır: " + s[0] + " → " + decide(s[0]));
        }
        check(TanecikScenes.match("") == null && TanecikScenes.match(null) == null, "boş cümle sahne değil");
    }

    /** Bütün sahneler denetimden geçer; ÖĞRET ve GÖSTER hiçbir şeye dokunmaz (kayıt yazmaz). */
    static void checks() {
        for (TanecikScenes.Scene s : TanecikScenes.all()) {
            check(SceneCheck.errors(s).isEmpty(), "sahne denetimden geçer: " + s.id + " " + SceneCheck.errors(s));
            check(ActionCatalog.get(s.action) != null || s.action.startsWith("SORU_"), "katalogdaki işin yanında: " + s.id);
            boolean touches = false;
            for (TanecikScenes.Step st : s.steps) {
                touches |= st.kind == TanecikScenes.Kind.SELECT || st.kind == TanecikScenes.Kind.TYPE;
            }
            if (s.level == TanecikScenes.Level.OGRET || s.level == TanecikScenes.Level.GOSTER) {
                check(!touches, "ÖĞRET / GÖSTER sahnesi hiçbir alana yazmaz, hiçbir düğmeye dokunmaz: " + s.id);
            }
            if (s.level == TanecikScenes.Level.OGRET) {
                TanecikScenes.Step beforeStop = s.steps.get(s.steps.size() - 2);
                check(beforeStop.text.equals("Şimdi sen dene."), "ÖĞRET 'Şimdi sen dene' deyip biter: " + s.id);
            }
            check(SceneCheck.allowed(s, false), "GÖSTER / ÖĞRET sormadan oynar: " + s.id);
        }
        check(SceneCheck.speaks(10) && !SceneCheck.speaks(23) && !SceneCheck.speaks(2), "gece Tanecik konuşmaz");
    }

    /** Kötü sahneler reddedilir: Kaydet'e / Sil'e basmaya, ödemeye dokunmaya, GÖSTER'de yazmaya çalışan. */
    static void badScenes() {
        TanecikScenes.Scene pressSave = new TanecikScenes.Scene("kotu_kaydet", ActionCatalog.ELEK, TanecikScenes.Level.HAZIRLA, "Kötü", "",
                new TanecikScenes.Step(TanecikScenes.Kind.OPEN, "ELEK", "", ""),
                new TanecikScenes.Step(TanecikScenes.Kind.TYPE, "etKg2", "", "150"),
                new TanecikScenes.Step(TanecikScenes.Kind.SELECT, "btnSaveElek", "Kaydediyorum", ""),
                new TanecikScenes.Step(TanecikScenes.Kind.STOP, "", "", ""));
        check(!SceneCheck.errors(pressSave).isEmpty(), "KÖTÜ SAHNE: Kaydet'e basmaya çalışan reddedilir");
        check(!SceneCheck.allowed(pressSave, true), "KÖTÜ SAHNE izin verilse de oynamaz");
        TanecikScenes.Scene delete = scene(TanecikScenes.Level.ZARARSIZ, TanecikScenes.Kind.SELECT, "btnDeleteSale");
        check(!SceneCheck.errors(delete).isEmpty(), "Sil'e dokunan reddedilir");
        TanecikScenes.Scene pay = scene(TanecikScenes.Level.ZARARSIZ, TanecikScenes.Kind.SELECT, "btnPaymentSave");
        check(!SceneCheck.errors(pay).isEmpty(), "ödeme düğmesine dokunan reddedilir");
        TanecikScenes.Scene teachTap = scene(TanecikScenes.Level.OGRET, TanecikScenes.Kind.SELECT, "btnElekVoice");
        check(!SceneCheck.errors(teachTap).isEmpty(), "ÖĞRET sahnesi düğmeye dokunamaz");
        TanecikScenes.Scene showType = scene(TanecikScenes.Level.GOSTER, TanecikScenes.Kind.TYPE, "etKg2");
        check(!SceneCheck.errors(showType).isEmpty(), "GÖSTER sahnesi alana yazamaz");
        TanecikScenes.Scene prepare = scene(TanecikScenes.Level.HAZIRLA, TanecikScenes.Kind.SELECT, "chipPayNone");
        check(SceneCheck.errors(prepare).isEmpty(), "HAZIRLA ödeme durumunu seçebilir (kayıt değil; İP7)");
        TanecikScenes.Scene harmless = scene(TanecikScenes.Level.ZARARSIZ, TanecikScenes.Kind.SELECT, "chipFilterMonth");
        check(SceneCheck.errors(harmless).isEmpty(), "zararsız iş denetimden geçer");
        check(!SceneCheck.allowed(harmless, false), "ZARARSIZ İŞ izinsiz oynamaz");
        check(SceneCheck.allowed(harmless, true), "ZARARSIZ İŞ izinle oynar");
        TanecikScenes.Scene noStop = new TanecikScenes.Scene("durmayan", ActionCatalog.ELEK, TanecikScenes.Level.GOSTER, "x", "",
                new TanecikScenes.Step(TanecikScenes.Kind.OPEN, "ELEK", "", ""));
        check(!SceneCheck.errors(noStop).isEmpty(), "STOP ile bitmeyen sahne reddedilir");
        TanecikScenes.Scene unknown = new TanecikScenes.Scene("bilinmeyen", ActionCatalog.ELEK, TanecikScenes.Level.GOSTER, "x", "",
                new TanecikScenes.Step(TanecikScenes.Kind.OPEN, "YOK_EKRAN", "", ""),
                new TanecikScenes.Step(TanecikScenes.Kind.STOP, "", "", ""));
        check(!SceneCheck.errors(unknown).isEmpty(), "bilinmeyen ekran reddedilir");
    }

    static TanecikScenes.Scene scene(TanecikScenes.Level level, TanecikScenes.Kind kind, String target) {
        return new TanecikScenes.Scene("deneme", ActionCatalog.ELEK, level, "Deneme", "",
                new TanecikScenes.Step(TanecikScenes.Kind.OPEN, "SALE", "", ""),
                new TanecikScenes.Step(kind, target, "", "x"),
                new TanecikScenes.Step(TanecikScenes.Kind.STOP, "", "", ""));
    }

    /** Her sahne var olan ekran ve öğelere bağlı. */
    static void binding() throws IOException {
        for (TanecikScenes.Scene s : TanecikScenes.all()) {
            for (TanecikScenes.Step st : s.steps) {
                if (st.kind == TanecikScenes.Kind.OPEN) {
                    String cls = TanecikScenes.SCREENS.get(st.target);
                    check(cls != null && exists(cls + ".java"), "ekran var: " + s.id + " → " + st.target + " (" + cls + ")");
                    continue;
                }
                if (st.target.isEmpty()) {
                    continue;
                }
                check(bound(st.target), "öğe ekranda var: " + s.id + " → " + st.target);
            }
        }
        check(!bound("btnYokBoyleBirDugme"), "olmayan öğe bulunmaz (denetim çalışıyor)");
    }

    static boolean bound(String target) {
        if (target.startsWith("sahne:")) {
            for (java.lang.reflect.Field f : TanecikScenes.class.getDeclaredFields()) {
                try {
                    if (f.getType() == String.class && target.equals(f.get(null))) {
                        return javaSources.contains("setTag(TanecikScenes." + f.getName() + ")");
                    }
                } catch (IllegalAccessException e) {
                    return false;
                }
            }
            return false;
        }
        return resources.contains("@+id/" + target + "\"") || resources.contains("name=\"" + target + "\" type=\"id\"")
                || resources.contains("type=\"id\" name=\"" + target + "\"");
    }

    static boolean exists(String file) throws IOException {
        try (Stream<Path> files = Files.walk(root.resolve("app/src/main/java"))) {
            return files.anyMatch(p -> p.getFileName().toString().equals(file));
        }
    }

    static String readAll(Path dir, String ext) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(ext)).map(p -> {
                try {
                    return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return "";
                }
            }).collect(Collectors.joining("\n"));
        }
    }

}
