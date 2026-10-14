"""Güvenlik sertleştirmesinin statik kontrolleri: kurallar ile istemci kodunun birbirine uyumu ve
diğer modüllerin kurallarının değişmediği. Firebase emülatör testi DEĞİLDİR; kuralların gerçek
davranışı functions/test/posts.rules.test.js ve storage.rules.test.js ile emülatörde doğrulanmalıdır.

Kullanım: python3 tests/security/check_static.py [karşılaştırma-commit'i]
Karşılaştırma commit'i verilmezse sertleştirmeden önceki commit (731ef3e) kullanılır."""
from pathlib import Path
import re, subprocess, sys

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java'
APP = JAVA / 'com/zeytindefteri/app'
BASE = sys.argv[1] if len(sys.argv) > 1 else '731ef3e'
failures, checks = [], 0

def check(ok, msg):
    global checks
    checks += 1
    if not ok:
        failures.append(msg)

def read(p): return Path(p).read_text(encoding='utf8')
def at_base(rel):
    return subprocess.run(['git', 'show', f'{BASE}:{rel}'], cwd=ROOT, capture_output=True, text=True, check=True).stdout

def block(text, start):
    """'match /x {' satırından başlayan bloğu süslü parantez dengesine göre döndürür."""
    i = text.index(start)
    depth, j = 0, i + len(start) - 1  # başlangıç satırının son '{' karakteri ({postId} değil)
    while True:
        if text[j] == '{': depth += 1
        elif text[j] == '}':
            depth -= 1
            if depth == 0: return text[i:j + 1]
        j += 1

rules = read(ROOT / 'firestore.rules')
posts = block(rules, 'match /social_posts/{postId} {')
comments = block(posts, 'match /comments/{commentId} {')
post_only = posts.replace(comments, '')

# 1) Diğer modüllerin kuralları değişmedi: social_posts gönderi bölümü dışında dosya aynı.
base_rules = at_base('firestore.rules')
base_posts = block(base_rules, 'match /social_posts/{postId} {')
base_comments = block(base_posts, 'match /comments/{commentId} {')
check(rules.replace(posts, '<POSTS>') == base_rules.replace(base_posts, '<POSTS>'),
      'social_posts dışında firestore.rules değişmiş (diğer modüller etkilenebilir)')
check(comments == base_comments, 'yorum kuralları değişmiş')
check('allow read: if request.auth != null;' in post_only, 'gönderi okuma kuralı değişmiş')
check(re.search(r'allow delete: if request\.auth != null && resource\.data\.user_id == request\.auth\.uid;', post_only),
      'gönderi silme kuralı değişmiş')
check(not re.search(r'if\s+true', rules), 'firestore.rules içinde koşulsuz izin var')

# 2) Gönderi kuralları: kimlik, sahiplik, tarih, metin, beyaz liste
create = re.search(r'allow create:(.*?);', post_only, re.S).group(1)
update = re.search(r'allow update:(.*?)// Delete has no', post_only, re.S).group(1)  # açıklamada ';' var
check('request.resource.data.user_id == request.auth.uid' in create, 'oluşturmada sahiplik kontrolü yok')
check('request.resource.data.cloud_id == postId' in create, 'belge kimliği kontrolü yok')
check('notInFuture(request.resource.data.created_at)' in create, 'oluşturma zamanı kontrolü yok')
check('keys().hasOnly(postFields())' in create and 'affectedKeys().hasOnly(postFields())' in update, 'alan beyaz listesi yok')
check("!('user_id' in request.resource.data.diff(resource.data).affectedKeys())" in update, 'sahip değiştirilebilir')
check('resource.data.user_id == request.auth.uid' in update, 'güncellemede sahiplik kontrolü yok')
check('validPostFields(' in create and 'validPostFields(' in update, 'alan doğrulaması bağlı değil')
check("request.auth.token.get('name', '')" in post_only and "'Üretici'" in post_only, 'ad oturum belgesinden doğrulanmıyor')
check('request.time.toMillis() + 600000' in post_only, 'gelecek tarih toleransı yok/değişmiş')
check("d.is_real_sale == 0" in post_only, 'gerçek satış işareti korunmuyor')
limit = int(re.search(r'd\.content\.size\(\) <= (\d+)', post_only).group(1))

# 3) Kural ↔ istemci uyumu
fields = set(re.findall(r"'(\w+)'", re.search(r'function postFields\(\) \{(.*?)\}', post_only, re.S).group(1)))
types = set(re.findall(r"'(\w+)'", re.search(r'function postTypes\(\) \{(.*?)\}', post_only, re.S).group(1)))
required = set(re.findall(r"'(\w+)'", re.search(r'hasAll\(\[(.*?)\]\)', create, re.S).group(1)))
scr = read(JAVA / 'SocialCloudRepository.java')
publish = scr[scr.index('public void publishLocalPost('):scr.index('// FIRESTORE -> SQLITE')]
publish_code = re.sub(r'//[^\n]*', '', re.sub(r'/\*.*?\*/', '', publish, flags=re.S))  # yorumlar yazım değildir
written = set(re.findall(r'data\.put\(\s*"(\w+)"', publish_code))
check(written <= fields, f'istemcinin yazdığı alan kuralda yok: {sorted(written - fields)}')
check(required <= written, f'kuralın zorunlu alanını istemci yazmıyor: {sorted(required - written)}')
check(fields - written <= {'is_active'}, f'kuralda istemcinin yazmadığı beklenmedik alan: {sorted(fields - written)}')
cp = read(JAVA / 'CreatePostActivity.java')
codes = set(re.findall(r'"([A-Z_]{3,})"', cp[cp.index('getPostTypeCode('):cp.index('getCursorString(')]))
quick = set(re.findall(r'"([A-Z_]+)"', re.search(r'Arrays\.asList\(("MARKET".*?)\)', scr).group(1))) | {'GENERAL'}
check(codes | quick <= types, f'istemcinin gönderi türü kuralda yok: {sorted((codes | quick) - types)}')
policy = read(JAVA / 'MeydanPostPolicy.java')
check(f'MAX_CONTENT = {limit};' in policy, 'istemci ve kural metin sınırı farklı')
check(f'android:maxLength="{limit}"' in read(ROOT / 'app/src/main/res/layout/activity_create_post.xml'), 'giriş alanında sınır yok')
check('DEFAULT_AUTHOR = "Üretici"' in policy, 'varsayılan ad kuralla farklı')

# 4) İstemci akışı
check('"user_name",\n                    safeOr' not in publish and 'data.put("user_name", authorNameFromClaims(' in publish,
      'gönderi adı oturum belgesinden alınmıyor')
check('user.getIdToken(false)' in publish, 'oturum belgesi alınmıyor')
check(re.search(r'"is_real_sale",\s*0\s*\)', publish), 'is_real_sale istemciden 0 yazılmıyor')
check(publish.index('isPublishableContent(') < publish.index('.set('), 'metin kontrolü yazmadan önce değil')
check('MeydanPostPolicy.isPublishableContent(content)' in cp, 'oluşturma ekranı metin sınırını kontrol etmiyor')
check('if (content.isEmpty()) {' in cp, 'oluşturma ekranı metinsiz gönderiye izin veriyor')
check('user.getIdToken(true);' in read(APP / 'ProfileActivity.java'), 'ad değişince oturum belgesi yenilenmiyor')
check('user.getIdToken(true);' in read(APP / 'LoginActivity.java'), 'kayıtta oturum belgesi yenilenmiyor')

# 5) Storage: istemci erişimi tamamen kapalı (Senaryo A) ve istemci Storage kullanmıyor
storage = read(ROOT / 'storage.rules')
allows = re.findall(r'allow [^;]*;', storage)
check(allows == ['allow read, write: if false;'], f'storage.rules beklenen tek kapalı kural değil: {allows}')
check('match /{allPaths=**}' in storage, 'storage kuralı tüm yolları kapsamıyor')
uses = [p.name for p in JAVA.rglob('*.java') if re.search(r'FirebaseStorage|StorageReference|putFile\(|putBytes\(', read(p))]
check(not uses, f'istemci Storage kullanıyor; kapalı kural onu bozar: {uses}')

# 6) Hesap silme korunur
acc_diff = subprocess.run(['git', 'diff', BASE, '--', 'functions/account', 'app/src/main/java/com/zeytindefteri/app/AccountDeletion.java'],
                          cwd=ROOT, capture_output=True, text=True).stdout
check(acc_diff == '', 'hesap silme kodu değişmiş')
check("deleteStoragePrefix(`social_posts/${uid}/`)" in read(ROOT / 'functions/account/service.js'), 'Storage temizliği kalkmış')
check("getStorage().bucket().deleteFiles" in read(ROOT / 'functions/account/functions.js'), 'Storage temizliği Admin SDK ile değil')

# 7) Moderasyon: geçersiz hedefe token gitmez
client = read(APP / 'ModerationBackendClient.java')
check(client.index('ModerationTransport.isConfigured(MarketBackendConfig.SUPABASE_URL)') < client.index('getIdToken('),
      'yapılandırma, token alınmadan önce kontrol edilmiyor')
check('ModerationTransport.send(' in client and 'openConnection' in client and 'new URL(' not in client,
      'istek ModerationTransport dışından açılıyor')
check('FirebaseNetworkException' in client, 'ağ hatası ayrı ele alınmıyor')
transport = read(APP / 'ModerationTransport.java')
check('setInstanceFollowRedirects(false)' in transport, 'yönlendirme izleniyor')
for name in ['ModerationBackendClient.java', 'ModerationTransport.java', 'ModerationActivity.java', 'ModeratorManagementActivity.java']:
    code = read(APP / name)
    check(not re.search(r'Log\.\w\([^;]*token', code, re.I) and 'printStackTrace' not in code, f'{name}: token/ayrıntı kaydı')
for name in ['ModerationActivity.java', 'ModeratorManagementActivity.java']:
    check('Code.UNAVAILABLE' in read(APP / name) and 'UNAVAILABLE_MESSAGE' in read(APP / name), f'{name}: anlaşılır hata mesajı yok')
check(read(APP / 'MarketBackendConfig.java') == at_base('app/src/main/java/com/zeytindefteri/app/MarketBackendConfig.java'),
      'MarketBackendConfig değişmiş (Supabase bağlantısı uydurulmamalı)')
roles = subprocess.run(['git', 'diff', BASE, '--', 'functions/moderation', 'supabase', 'app/src/main/java/com/zeytindefteri/app/ModerationAccess.java'],
                       cwd=ROOT, capture_output=True, text=True).stdout
check(roles == '', 'moderasyon yetki kodu değişmiş')

# 8) Yedi menü korunur
feed = read(APP / 'ProducerFeedActivity.java')
nav = re.findall(r'bottomNav\.addView\(navItem\([^,]+, "([^"]+)"', feed)
check(nav == ['Ana Sayfa', 'Bahçelerim', 'Elek', 'Zeytin Ligi', 'Sohbet', 'Ayarlar'] and 'bottomNav.addView(assistantItem' in feed,
      f'alt menü değişmiş: {nav}')

if failures:
    print('\n'.join('HATA: ' + f for f in failures))
    print(f'check_static: {checks} kontrolden {len(failures)} başarısız')
    sys.exit(1)
print(f'check_static: {checks} kontrol geçti (statik; emülatör testi değildir)')
