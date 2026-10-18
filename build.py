"""Uygulamalardaki yasal metinlerden GitHub Pages sitesi üretir.
Kullanım: python3 build.py <zeytin_repo> <durakgo_repo> <cikis_klasoru>"""
import html, os, re, sys

ZEYTIN, DURAKGO, OUT = sys.argv[1:4]
EMAIL = "muammercerdin@gmail.com"

CSS = """
:root{--bg:#f6f4ee;--card:#fffdf8;--ink:#1d2a1f;--muted:#5d665c;--line:#e2ddd0;--accent:#4d6b2f;--accent-soft:#eef2e4}
@media (prefers-color-scheme:dark){:root{--bg:#121611;--card:#1a2018;--ink:#e9eee4;--muted:#a6af9f;--line:#2c3429;--accent:#a9c97a;--accent-soft:#232c1d}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.65 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:760px;margin:0 auto;padding:28px 16px 64px}
.top{font-size:14px;margin-bottom:20px}
.top a{color:var(--accent);text-decoration:none}
h1{font-size:28px;line-height:1.25;margin:0 0 6px}
.meta{color:var(--muted);font-size:14px;margin:0 0 24px}
h2{font-size:19px;margin:32px 0 8px;padding-top:16px;border-top:1px solid var(--line)}
p,li{overflow-wrap:anywhere}
ul,ol{padding-left:22px}
a{color:var(--accent)}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:18px 20px;margin:14px 0}
.card h3{margin:0 0 6px;font-size:18px}
.card ul{margin:6px 0 0}
.note{background:var(--accent-soft);border-radius:12px;padding:14px 16px;margin:18px 0}
footer{margin-top:48px;color:var(--muted);font-size:13px}
"""


def page(title, body, back=None):
    nav = f'<div class="top"><a href="{back[0]}">← {html.escape(back[1])}</a></div>' if back else ""
    return f"""<!doctype html>
<html lang="tr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{html.escape(title)}</title>
<style>{CSS}</style></head>
<body><main>{nav}{body}
<footer>Geliştirici: Muammer Çerdin · <a href="mailto:{EMAIL}">{EMAIL}</a></footer>
</main></body></html>
"""


def inline(text):
    t = html.escape(text)
    t = re.sub(r"(https://[^\s<]+[^\s<.,;)])", r'<a href="\1">\1</a>', t)
    t = t.replace(html.escape(EMAIL), f'<a href="mailto:{EMAIL}">{EMAIL}</a>')
    return t


def convert(lines, heading):
    """Metni HTML'e çevirir. heading(line) başlıksa başlık metnini, değilse None döndürür."""
    out, in_list = [], False
    for raw in lines:
        line = raw.strip()
        if not line:
            if in_list:
                out.append("</ul>"); in_list = False
            continue
        h = heading(line)
        if h is not None:
            if in_list:
                out.append("</ul>"); in_list = False
            out.append(f"<h2>{inline(h)}</h2>")
        elif line.startswith("- "):
            if not in_list:
                out.append("<ul>"); in_list = True
            out.append(f"<li>{inline(line[2:])}</li>")
        else:
            if in_list:
                out.append("</ul>"); in_list = False
            out.append(f"<p>{inline(line)}</p>")
    if in_list:
        out.append("</ul>")
    return "\n".join(out)


def write(rel, content):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


# ---------- Zeytin Defteri ----------
legal = open(os.path.join(ZEYTIN, "app/src/main/java/com/zeytindefteri/app/LegalDocuments.java"), encoding="utf-8").read()
version = re.search(r'VERSION = "([^"]+)"', legal).group(1)
date = re.search(r'DATE = "([^"]+)"', legal).group(1)
controller = re.search(r'CONTROLLER_NAME = "([^"]+)"', legal).group(1)
email = re.search(r'CONTACT_EMAIL = "([^"]+)"', legal).group(1)
assert email == EMAIL, email

ZD_BACK = ("index.html", "Zeytin Defteri")
for name, slug in (("legal_privacy", "gizlilik"), ("legal_kvkk", "kvkk"), ("legal_terms", "kullanim-kosullari")):
    text = open(os.path.join(ZEYTIN, f"app/src/main/res/raw/{name}.txt"), encoding="utf-8").read()
    text = (text.replace("{{VERI_SORUMLUSU}}", controller).replace("{{EPOSTA}}", email)
                .replace("{{SURUM}}", version).replace("{{TARIH}}", date))
    assert "{{" not in text, name
    lines = text.splitlines()
    title, meta = lines[0].strip(), lines[1].strip()
    body = f"<h1>Zeytin Defteri · {inline(title)}</h1><p class=\"meta\">{inline(meta)}</p>"
    body += convert(lines[2:], lambda l: l[2:] if l.startswith("# ") else None)
    write(f"zeytin-defteri/{slug}.html", page(f"{title} · Zeytin Defteri", body, ZD_BACK))

write("zeytin-defteri/hesap-silme.html", page("Hesap ve veri silme · Zeytin Defteri", f"""
<h1>Zeytin Defteri · Hesap ve veri silme</h1>
<p class="meta">Geliştirici: {controller} · Sürüm {version} · {date}</p>
<p>Zeytin Defteri hesabını ve ilişkili tüm verilerini istediğin zaman kalıcı olarak silebilirsin. İki yol var:</p>
<div class="card"><h3>1. Uygulama içinden (anında)</h3>
<ol><li>Zeytin Defteri'ni aç ve hesabınla giriş yap.</li>
<li><b>Ayarlar</b> sekmesine git.</li>
<li><b>Hesabımı Sil</b> satırına dokun ve onayla.</li></ol>
<p>Güvenliğin için son birkaç dakika içinde giriş yapmış olman gerekir; uygulama gerekirse yeniden giriş yapmanı ister.</p></div>
<div class="card"><h3>2. E-posta ile (uygulamaya erişemiyorsan)</h3>
<p>Hesabında kayıtlı e-posta adresinden <a href="mailto:{EMAIL}?subject=Zeytin%20Defteri%20hesap%20silme%20talebi">{EMAIL}</a> adresine “Zeytin Defteri hesap silme talebi” konulu bir e-posta gönder. Talebin, kimliğin doğrulandıktan sonra en geç 30 gün içinde işleme alınır ve sonuç sana e-postayla bildirilir.</p></div>
<h2>Silinen veriler</h2>
<ul><li>Giriş hesabın (ad, e-posta, kullanıcı kimliği)</li>
<li>Buluttaki bahçe, işlem, hasat, elek, satış, stok ve salamura kayıtların</li>
<li>Meydan gönderilerin; başka gönderilere yazdığın yanıtların içeriği silinir ve “Silinmiş kullanıcı” olarak görünür</li>
<li>Engelleme listen ve verdiğin şikâyetler</li>
<li>Zeytin Ligi kayıtların ve bu cihazdan gönderilmiş anonim piyasa katkıların</li>
<li>Bildirim anahtarın ve telefondaki uygulama verilerin</li></ul>
<h2>Saklanabilecek veriler</h2>
<ul><li>Başka kullanıcıların senin hakkında verdiği şikâyet kayıtları, güvenlik ve moderasyon amacıyla saklanabilir.</li>
<li>Kimlik içermeyen toplu piyasa istatistikleri (bölge ortalamaları) silinmez; bunlar seninle ilişkilendirilemez.</li>
<li>Hizmet sağlayıcıların (Google Firebase, Supabase) yedeklerinde kalan kopyalar, sağlayıcıların kendi yedekleme süreçleri içinde silinir.</li></ul>
<div class="note">Hesabını silmeden yalnızca bazı verilerini silmek istersen: bahçe ve işlem kayıtlarını, Meydan gönderilerini uygulama içinden tek tek silebilir; anonim piyasa katkısını <b>Ayarlar → Piyasaya Anonim Katkı</b> bölümünden kapatabilirsin. Ayrıntılar için <a href="gizlilik.html">Gizlilik Politikası</a>.</div>
""", ZD_BACK))

write("zeytin-defteri/index.html", page("Zeytin Defteri", f"""
<h1>Zeytin Defteri</h1>
<p class="meta">Zeytin üreticileri için bahçe, hasat, elek ve satış defteri</p>
<div class="card"><ul>
<li><a href="gizlilik.html">Gizlilik Politikası</a></li>
<li><a href="kvkk.html">KVKK Aydınlatma Metni</a></li>
<li><a href="kullanim-kosullari.html">Kullanım Koşulları</a></li>
<li><a href="hesap-silme.html">Hesap ve veri silme</a></li></ul></div>
""", ("../index.html", "Tüm uygulamalar")))

# ---------- DurakGo ----------
text = open(os.path.join(DURAKGO, "app/src/main/res/raw/legal_privacy.txt"), encoding="utf-8").read()
lines = text.splitlines()
meta = next(l for l in lines if l.startswith("Son Güncelleme"))
rest = [l for l in lines[1:] if l != meta]
num = re.compile(r"^(\d+)\. (.{2,60})$")
body = f"<h1>DurakGo · Gizlilik Politikası</h1><p class=\"meta\">{inline(meta)}</p>"
body += convert(rest, lambda l: l if num.match(l) else None)
DG_BACK = ("index.html", "DurakGo")
write("durakgo/gizlilik.html", page("Gizlilik Politikası · DurakGo", body, DG_BACK))

write("durakgo/veri-silme.html", page("Veri silme · DurakGo", f"""
<h1>DurakGo · Veri silme</h1>
<p class="meta">Geliştirici: Muammer Çerdin · S.S. 66 No'lu Sarıgöl Motorlu Taşıyıcılar Kooperatifi için</p>
<p>DurakGo'da yolcular için hesap yoktur ve uygulama içinden hesap açılamaz. Verilerini şu şekilde silebilirsin:</p>
<div class="card"><h3>Yolcular</h3>
<p>Tüm DurakGo verilerin (sefer hatırlatmaları, tercihler, önbellek) yalnızca telefonunda durur. Silmek için:</p>
<ol><li>Telefonda <b>Ayarlar → Uygulamalar → DurakGo → Depolama</b> bölümünden <b>Verileri temizle</b>'ye dokun, ya da</li>
<li>Uygulamayı kaldır.</li></ol>
<p>Sunucuda tutulan bildirim kaydının (bildirim anahtarı ve bildirim tercihleri) da silinmesini istersen aşağıdaki e-posta yolunu kullan.</p></div>
<div class="card"><h3>Şoförler ve yöneticiler</h3>
<p>Şoför ve yönetici hesaplarını kooperatif açar ve kapatır. Hesabının ve ilişkili verilerin (oturum kayıtları, sefer ve gecikme kayıtları, yönetici e-postası) silinmesi için kooperatife başvurabilir ya da aşağıdaki adrese yazabilirsin. Canlı konum kaydı zaten her sefer bitiminde temizlenir.</p></div>
<div class="card"><h3>E-posta ile talep</h3>
<p><a href="mailto:{EMAIL}?subject=DurakGo%20veri%20silme%20talebi">{EMAIL}</a> adresine “DurakGo veri silme talebi” konulu bir e-posta gönder; şoför ya da yöneticiysen kodunu veya e-posta adresini yaz. Talebin, kimliğin doğrulandıktan sonra en geç 30 gün içinde işleme alınır.</p></div>
<h2>Saklanabilecek veriler</h2>
<ul><li>Kişiyle ilişkilendirilmeyen sefer operasyon kayıtları (hangi seferin ne zaman yapıldığı, gecikme ve iptal bilgisi) kooperatifin hizmet kaydı olarak saklanabilir.</li>
<li>Hizmet sağlayıcıların (Supabase, Google) yedeklerinde kalan kopyalar, sağlayıcıların kendi yedekleme süreçleri içinde silinir.</li></ul>
<div class="note">Ayrıntılar için <a href="gizlilik.html">Gizlilik Politikası</a>.</div>
""", DG_BACK))

write("durakgo/index.html", page("DurakGo", """
<h1>DurakGo</h1>
<p class="meta">Sarıgöl – Alaşehir – Salihli sefer saatleri, canlı araç konumu ve duyurular</p>
<div class="card"><ul>
<li><a href="gizlilik.html">Gizlilik Politikası</a></li>
<li><a href="veri-silme.html">Veri silme</a></li></ul></div>
""", ("../index.html", "Tüm uygulamalar")))

write("index.html", page("Muammer Çerdin · Uygulamalar", """
<h1>Uygulamalar</h1>
<p class="meta">Muammer Çerdin tarafından geliştirilen Android uygulamaları</p>
<div class="card"><h3><a href="zeytin-defteri/index.html">Zeytin Defteri</a></h3>
<p>Zeytin üreticileri için bahçe, hasat, elek ve satış defteri.</p></div>
<div class="card"><h3><a href="durakgo/index.html">DurakGo</a></h3>
<p>Sarıgöl – Alaşehir – Salihli sefer saatleri, canlı araç konumu ve duyurular.</p></div>
"""))
write(".nojekyll", "")
print("tamam")
