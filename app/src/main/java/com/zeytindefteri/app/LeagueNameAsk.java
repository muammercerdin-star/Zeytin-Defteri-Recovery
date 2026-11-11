package com.zeytindefteri.app;

/**
 * Zeytin Ligi'nde "adınla görünmek ister misin?" sorusunun ne zaman sorulacağı (Android'e bağlı değil, test edilir).
 *
 * Ad gösterimi her zaman KULLANICININ SEÇİMİDİR (açık rıza, KVKK): varsayılan anonimdir, bu sınıf yalnız soruyu
 * doğru anda ve en fazla bir kez sordurur. İki an var:
 *   FIRST: Ön sıralama ilk kez göründüğünde (piyasaya katkısı açık kullanıcıya) bir kez.
 *   TOP3 : İlk soruda anonim kalmayı seçen kullanıcı sezonda ilk 3'e girince bir kez ("Bölgende 2.'sin!").
 * Pencere kapatılsa da "soruldu" sayılır; tekrar tekrar rahatsız etmez. Ayarlar'dan her zaman değiştirilebilir.
 */
public final class LeagueNameAsk {

    private LeagueNameAsk() { }

    public enum Kind { NONE, FIRST, TOP3 }

    /** Ayar anahtarları (kullanıcı kimliği eklenerek saklanır). */
    public static final String FIRST_KEY = "league_ask_first_";
    public static final String TOP3_KEY = "league_ask_top3_";

    /**
     * @param demo          tanıtım verisi mi (hiç sorulmaz)
     * @param signedIn      oturum açık mı
     * @param contributing  piyasaya anonim katkı açık mı (kapalıysa sıralamada yok, sormanın anlamı yok)
     * @param nameShown     ad zaten görünüyor mu
     * @param firstAsked    ilk soru daha önce soruldu mu
     * @param top3Asked     ilk 3 sorusu daha önce soruldu mu
     * @param myRank        sezonluk ön sıralamadaki yerim (listede yoksam 0)
     * @param askedThisView bu ekranda zaten bir soru gösterildi mi
     */
    public static Kind decide(boolean demo, boolean signedIn, boolean contributing, boolean nameShown,
                              boolean firstAsked, boolean top3Asked, int myRank, boolean askedThisView) {
        if (demo || !signedIn || !contributing || nameShown || askedThisView) return Kind.NONE;
        boolean podium = myRank >= 1 && myRank <= 3;
        if (!top3Asked && podium) return Kind.TOP3;   // ilk soru da sorulmadıysa gurur anı onun yerine geçer
        if (!firstAsked) return Kind.FIRST;
        return Kind.NONE;
    }

    public static String title(Kind kind, int rank, String district) {
        if (kind == Kind.TOP3) {
            String where = district == null || district.trim().isEmpty() ? "Bölgende" : district.trim() + " bölgesinde";
            return where + " " + rank + ".'sin! " + (rank == 1 ? "🥇" : rank == 2 ? "🥈" : "🥉");
        }
        return "Ligde adınla görünmek ister misin?";
    }

    public static String body(Kind kind) {
        String lead = kind == Kind.TOP3
                ? "Tebrikler! Şu an listede \"Anonim üretici\" olarak görünüyorsun. İstersen adınla görün.\n\n"
                : "";
        return lead
                + "Adınla görünürsen bölgendeki diğer üreticiler sıranı, puanını, en çok çıkan elek numaranı "
                + "ve ortalama kilogram fiyatını görür.\n\n"
                + "Kilon ve toplam tutarın kimseye GÖSTERİLMEZ.\n\n"
                + "Seçim senin; Ayarlar → Ligde Adımla Görüneyim'den istediğin zaman değiştirebilirsin.";
    }

    public static final String YES = "Adımla görüneyim";
    public static final String NO = "Anonim kalayım";
}
