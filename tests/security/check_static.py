"""Güvenlik sertleştirmesinin statik kontrolleri: kurallar ile istemci kodunun birbirine uyumu ve
diğer modüllerin kurallarının değişmediği. Firebase emülatör testi DEĞİLDİR; kuralların gerçek
davranışı functions/test/posts.rules.test.js ve storage.rules.test.js ile emülatörde doğrulanmalıdır.

Kullanım: python3 tests/security/check_static.py [karşılaştırma-commit'i]
Karşılaştırma commit'i verilmezse incelenmiş son kural sürümü olan moderasyon commit'i (c41c2da) kullanılır;
sertleştirmeden önceki 731ef3e de verilebilir (o zaman 1. bölümdeki karşılaştırma moderasyon farkını gösterir)."""
from pathlib import Path
import re, subprocess, sys

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java'
APP = JAVA / 'com/zeytindefteri/app'
BASE = sys.argv[1] if len(sys.argv) > 1 else 'c41c2da'
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
# Yorum kurallarına yalnızca moderatör kaldırması koruması eklendi (sahibi kaldırılan yanıtı geri açamaz).
MOD_GUARD = "          && resource.data.get('moderated_by', '') == ''\n"
def strip_mod(t): return t.replace(MOD_GUARD, '')
check(strip_mod(comments) == strip_mod(base_comments), 'yorum kuralları moderasyon koruması dışında değişmiş')
# Yorumda yazar moderasyon alanlarına dokunamaz, susturulan yazamaz.
check(comments.count('hasAny(modKeys())') >= 2 and 'notMuted()' in comments, 'yorumda moderasyon alanı koruması yok')
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
mod_keys = set(re.findall(r"'(\w+)'", re.search(r'function modKeys\(\) \{(.*?)\}', rules, re.S).group(1)))
check(mod_keys == {'status', 'report_count', 'moderated_by', 'moderated_at'}, f'moderasyon alanları değişmiş: {sorted(mod_keys)}')
check(fields - written <= {'is_active'} | mod_keys, f'kuralda istemcinin yazmadığı beklenmedik alan: {sorted(fields - written - mod_keys)}')
check(not (written & {'moderated_by', 'moderated_at'}), 'istemci moderatör alanı yazıyor')
check("!('moderated_by' in keys) && !('moderated_at' in keys)" in post_only, 'yazar moderatör alanı yazabilir')
check('affectedKeys().hasAny(modKeys())' in update, 'yazar kendi gönderisini onaylayabilir')
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
# Sunucu tarafı silme yalnız genişleyebilir: 731ef3e'ye göre hiçbir satır çıkarılmamış olmalı.
srv_diff = subprocess.run(['git', 'diff', '731ef3e', '--', 'functions/account'], cwd=ROOT, capture_output=True, text=True).stdout
removed = [l for l in srv_diff.splitlines() if l.startswith('-') and not l.startswith('---')]
check(not removed, f'sunucu hesap silme kodundan satır çıkarılmış: {removed[:3]}')
acc = read(APP / 'AccountDeletion.java')
acc_code = re.sub(r'(?m)^\s*//.*$', '', acc)  # yalnız satır yorumları (URL'deki // kalır)
order = ['setLeagueProfile("", false', 'setContributionOptIn(false', '/deleteAccount', 'LocalAccountWipe.wipe(',
         'clearPreferences(activity, uid)', 'TezgahRescue.wipe(activity, uid)', 'finishSignedOut(activity, "Hesabın']
pos = [acc_code.find(x) for x in order]
check(all(p >= 0 for p in pos), f'hesap silme adımı eksik: {[x for x, p in zip(order, pos) if p < 0]}')
check('FirebaseAuth.getInstance().signOut()' in acc_code, 'silme sonunda oturum kapanmıyor')
check('TezgahRescue.wipe(activity, "")' in acc_code, 'misafir silmede kurtarma kopyası kalıyor')
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
# 7b) Supabase ayarı repoda değil, local.properties → BuildConfig yoluyla gelir; repoda gerçek değer yok.
cfg = read(APP / 'MarketBackendConfig.java')
check('PLACEHOLDER_URL = "https://REPLACE_ME.supabase.co"' in cfg and 'PLACEHOLDER_KEY = "sb_publishable_REPLACE_ME"' in cfg,
      'yer tutucular değişmiş')
check('buildValue("SUPABASE_URL", PLACEHOLDER_URL)' in cfg and 'buildValue("SUPABASE_PUBLISHABLE_KEY", PLACEHOLDER_KEY)' in cfg
      and 'Class.forName("com.zeytindefteri.app.BuildConfig")' in cfg, 'değerler BuildConfig\'ten okunmuyor')
check('PUBLIC_MARKET_MIN_PRODUCERS = 5;' in cfg, 'gizlilik eşiği değişmiş')
gradle = read(ROOT / 'app/build.gradle')
check("rootProject.file('local.properties')" in gradle and "buildConfigField 'String', 'SUPABASE_URL'" in gradle
      and "buildConfigField 'String', 'SUPABASE_PUBLISHABLE_KEY'" in gradle and 'buildConfig true' in gradle,
      'build.gradle local.properties değerlerini BuildConfig\'e yazmıyor')
check(re.search(r'^local\.properties$', read(ROOT / '.gitignore'), re.M), 'local.properties git\'e gidebilir')
check('BuildConfig' in read(ROOT / 'app/proguard-rules.pro'), 'kod küçültmede BuildConfig korunmuyor')
check(not re.search(r'^\s*org\.gradle\.java\.home\s*=', read(ROOT / 'gradle.properties'), re.M), 'makineye özel JDK yolu repoda')
tracked = subprocess.run(['git', 'ls-files', '-z'], cwd=ROOT, capture_output=True, text=True).stdout.split('\0')
leaks = []
for rel in filter(None, tracked):
    path = ROOT / rel
    if not path.is_file() or path.stat().st_size > 2_000_000: continue
    try: text = path.read_text(encoding='utf8')
    except (UnicodeDecodeError, OSError): continue
    for key in re.findall(r'sb_(?:publishable|secret)_[A-Za-z0-9_-]{8,}', text):
        if 'REPLACE_ME' not in key and key != 'sb_publishable_TEST_ONLY_not_a_real_key': leaks.append(f'{rel}: {key[:18]}…')
    if rel.startswith('app/') and re.search(r'https://(?!REPLACE_ME\.)(?!abcdefghijklmnopqrst\.)[a-z0-9]+\.supabase\.co', text):
        leaks.append(f'{rel}: gerçek Supabase adresi')
check(not leaks, f'repoda Supabase değeri var: {leaks[:5]}')
# Yetki kodu (rol verme/alma, oturum doğrulama, authorize) değişmemeli. İçerik kaldırma işlemi
# service dosyalarına eklendi; authorize gövdesi ise aynı kalmalı.
roles = subprocess.run(['git', 'diff', BASE, '--', 'functions/moderation/roles.js', 'functions/moderation/role-functions.js',
                        'supabase/functions/moderation-api/roles.mjs', 'supabase/functions/moderation-api/firebase.ts',
                        'app/src/main/java/com/zeytindefteri/app/ModerationAccess.java'],
                       cwd=ROOT, capture_output=True, text=True).stdout
check(roles == '', 'moderasyon yetki kodu değişmiş')
def authorize_body(text):
    i = text.index('async function authorize('); return text[i:text.index('\n}\n', i)]
for rel in ['functions/moderation/service.js', 'supabase/functions/moderation-api/service.mjs']:
    check(authorize_body(read(ROOT / rel)) == authorize_body(at_base(rel)), f'{rel}: moderatör yetki denetimi değişmiş')

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
