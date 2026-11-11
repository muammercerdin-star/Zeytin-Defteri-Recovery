-- Zeytin Defteri — private tablolarında RLS (ek kilit). 11 Kasım 2026'da canlıda çalıştırıldı.
--
-- private şeması Data API'ye açık değildir; uygulama bu tablolara yalnız security definer fonksiyonlarla
-- (tablo sahibinin yetkisiyle) ulaşır. RLS'yi açmak bu fonksiyonları etkilemez (sahip RLS'ye takılmaz),
-- ama biri ileride private'ı yanlışlıkla API'ye açarsa tablolar yine okunamaz/değiştirilemez (politika yok = erişim yok).
-- Tekrar çalıştırmak zararsız.

alter table private.league_profiles      enable row level security;
alter table private.market_config        enable row level security;
alter table private.market_contributions enable row level security;
