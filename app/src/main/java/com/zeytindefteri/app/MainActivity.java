package com.zeytindefteri.app;

/**
 * Zeytin Defteri ana giriş ekranı.
 *
 * Yeni ürün yönünde ana deneyim "Meydan"dır. ProducerFeedActivity mevcut
 * sosyal/Firebase veri katmanını koruyarak bu ekranı üretir. MainActivity'yi
 * burada ince tutmak, eski 4.000+ satırlık dashboard bağımlılığını ortadan
 * kaldırır ve tüm giriş noktalarının tek, tutarlı ekrana gelmesini sağlar.
 */
public class MainActivity extends ProducerFeedActivity {
    // Bilerek boş: tüm ana ekran davranışı ProducerFeedActivity içinde.
}
