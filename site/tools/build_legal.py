#!/usr/bin/env python3
"""Uygulamanın tek kaynak metinlerinden deterministik statik site üretir (Python 3)."""
import argparse
import html
import json
from pathlib import Path
import re
from urllib.parse import quote, urlsplit
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SITE = ROOT / 'site'
JAVA = ROOT / 'app/src/main/java/com/zeytindefteri/app/LegalDocuments.java'
RAW = ROOT / 'app/src/main/res/raw'


def java_string(value):
    # Java String literal escapes; unicode characters are already UTF-8.
    return re.sub(r'\\(u[0-9a-fA-F]{4}|[btnfr"\'\\])',
                  lambda m: chr(int(m[1][1:], 16)) if m[1].startswith('u') else
                  {'b': '\b', 't': '\t', 'n': '\n', 'f': '\f', 'r': '\r', '"': '"', "'": "'", '\\': '\\'}[m[1]], value)


def constants():
    source = JAVA.read_text(encoding='utf-8')
    return {k: java_string(v) for k, v in re.findall(
        r'static final String (\w+)\s*=\s*"((?:\\.|[^"\\])*)"\s*;', source)}


def method_string(name):
    source = JAVA.read_text(encoding='utf-8')
    body = re.search(r'static String ' + name + r'\(\)\s*\{\s*return (.*?);\s*\}', source, re.S)[1]
    if re.sub(r'"(?:\\.|[^"\\])*"|\s|\+', '', body):
        raise ValueError(f'{name}: desteklenmeyen Java ifadesi; üreticiyi güncelleyin')
    return ''.join(java_string(v) for v in re.findall(r'"((?:\\.|[^"\\])*)"', body))


def fill(text, values):
    if text is None:
        return ''
    # String.trim() removes characters <= U+0020, as in LegalDocuments.fill().
    controller = values['CONTROLLER_NAME'].strip(''.join(chr(i) for i in range(33)))
    controller = controller if len(controller) > 1 else values['CONTROLLER_PENDING']
    for key, value in [('VERI_SORUMLUSU', controller), ('EPOSTA', values['CONTACT_EMAIL']),
                       ('SURUM', values['VERSION']), ('TARIH', values['DATE'])]:
        text = text.replace('{{' + key + '}}', value)
    return text


def blocks(text):
    """Satırları/metni değiştirmeden başlık, liste ve paragraf yapısına dönüştürür."""
    out, paragraph, listing = [], [], False
    def flush():
        if paragraph:
            out.append('<p>' + '<br>\n'.join(html.escape(x) for x in paragraph) + '</p>')
            paragraph.clear()
    for line in text.splitlines():
        if not line.startswith('- ') and listing:
            out.append('</ul>')
            listing = False
        if line.startswith('# '):
            flush()
            number = re.match(r'# (\d+)\.', line)
            anchor = f' id="bolum-{number[1]}"' if number else ''
            out.append(f'<h2{anchor}>' + html.escape(line[2:]) + '</h2>')
        elif line.startswith('- '):
            flush()
            if not listing:
                out.append('<ul>')
                listing = True
            out.append('<li>' + html.escape(line[2:]) + '</li>')
        elif not line:
            flush()
        else:
            paragraph.append(line)
    if listing:
        out.append('</ul>')
    flush()
    return '\n'.join(out)


def render():
    config = json.loads((SITE / 'config.json').read_text(encoding='utf-8'))
    values = constants()
    if config['CONTACT_EMAIL'] != values['CONTACT_EMAIL']:
        raise ValueError('config CONTACT_EMAIL uygulamanın CONTACT_EMAIL değeriyle aynı olmalı')
    values['CONTACT_EMAIL'] = config['CONTACT_EMAIL']
    base = config['SITE_URL'].rstrip('/')
    parsed = urlsplit(base)
    if parsed.scheme != 'https' or not parsed.netloc or parsed.query or parsed.fragment:
        raise ValueError('SITE_URL geçerli bir HTTPS site adresi olmalı')
    esc = html.escape
    email = esc(config['CONTACT_EMAIL'])
    links = [('gizlilik.html', 'Gizlilik'), ('kvkk.html', 'KVKK'), ('kosullar.html', 'Kullanım Koşulları'),
             ('hesap-silme.html', 'Hesap silme'), ('iletisim.html', 'İletişim')]
    footer = '<footer><nav aria-label="Yasal bilgiler">' + ''.join(
        f'<a href="{href}">{label}</a>' for href, label in links) + '</nav><p>© 2026 Zeytin Defteri</p></footer>'
    def page(filename, title, description, body):
        return f'''<!doctype html>
<html lang="tr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{esc(title)} · Zeytin Defteri</title>
<meta name="description" content="{esc(description)}">
<meta property="og:title" content="{esc(title)} · Zeytin Defteri">
<meta property="og:description" content="{esc(description)}">
<meta property="og:type" content="website">
<meta property="og:locale" content="tr_TR">
<meta property="og:url" content="{esc(base)}/{filename}">
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<link rel="stylesheet" href="/style.css">
</head>
<body>
<a class="skip" href="#icerik">İçeriğe geç</a>
<div class="shell">
<header><a class="brand" href="/index.html"><img src="/favicon.svg" width="34" height="34" alt="">Zeytin Defteri</a><span class="header-note">Emeğinin kaydı.</span></header>
<main id="icerik">{body}</main>
{footer}
</div>
</body>
</html>
'''
    output = {}
    legal = {}
    for file, raw in [('gizlilik.html', 'legal_privacy.txt'), ('kvkk.html', 'legal_kvkk.txt'), ('kosullar.html', 'legal_terms.txt')]:
        text = fill((RAW / raw).read_text(encoding='utf-8'), values)
        legal[file] = text
        lines = text.splitlines()
        body = '<article class="document"><h1>' + esc(lines[0]) + '</h1>\n' + blocks('\n'.join(lines[1:])) + '</article>'
        output[file] = page(file, lines[0], f'Zeytin Defteri {lines[0]}; sürüm {values["VERSION"]}, {values["DATE"]}.', body)
    phone_rows = ''.join(f'<li><span class="grade grade-{i}">{badge}</span><span>{label}</span><strong>{kg} kg</strong></li>'
                         for i, (badge, label, kg) in enumerate([('★', 'Double', 5), ('1', '1 Numara', 15), ('2', '2 Numara', 15), ('3', '3 Numara', 20), ('4', '4 Numara', 25)]))
    cards = ''.join(f'<article class="feature"><span class="feature-icon" aria-hidden="true">{icon}</span><h3>{title}</h3><p>{desc}</p></article>'
                    for icon, title, desc in [('≡', 'Sesle elek', 'Elek numarasını ve kilosunu söyle, kaydını kolaylaştır.'), ('₺', 'Bölge fiyatı', 'Üreticilerin anonim katkılarıyla bölgesel fiyat bilgisini takip et.'), ('☁', 'Bahçe havası', 'Seçtiğin bahçenin hava durumunu takip et.'), ('★', 'Zeytin Ligi', 'Bölgendeki üreticiler arasında sıralamanı gör.')])
    output['index.html'] = page('index.html', 'Zeytinciliğin defteri cebinde.', 'Elek kaydını sesle gir, masrafını ve alacağını tut. Zeytin Defteri yakında Google Play’de.', f'''
<section class="hero" aria-labelledby="hero-title">
<div class="hero-copy"><p class="eyebrow">BAHÇEDEN ELEĞE, EMEĞİNİN YANINDA</p>
<h1 id="hero-title">Zeytinciliğin defteri <span>cebinde.</span></h1>
<p class="lead">Elek kaydını sesle gir, masrafını ve alacağını tut, bölgende zeytinin bugün kaça gittiğini gör. Ücretsiz.</p>
<a class="button" href="#ne-yapar">Ne yapar? <span aria-hidden="true">↓</span></a></div>
<figure class="phone-demo"><div class="phone"><div class="speaker" aria-hidden="true"></div><p class="phone-heading">ELEK · KİLO</p><ul class="grade-list">{phone_rows}</ul><p class="voice-hint">“3 numara 20 kilo” de, yazsın</p><div class="phone-bottom" aria-hidden="true"></div></div><figcaption>Örnek elek kaydı · gerçek kişi verisi içermez.</figcaption></figure>
</section>
<section class="coming-prices" aria-labelledby="prices-title"><span class="status-dot" aria-hidden="true"></span><div><h2 id="prices-title">Bölge fiyatları yakında burada</h2><p>Bölgesel fiyat bilgisi bu sitede henüz yayınlanmıyor.</p></div></section>
<section class="features" id="ne-yapar" aria-labelledby="features-title"><p class="eyebrow">ZEYTİN DEFTERİ</p><h2 id="features-title">Ne yapar?</h2><div class="feature-grid">{cards}</div></section>
<aside class="play-coming"><h2>Yakında Google Play'de.</h2><p>Bahçenin hesabı, telefonunda yanında.</p></aside>''')
    subject, body = method_string('deletionSubject'), method_string('deletionBody')
    mailto = 'mailto:' + config['CONTACT_EMAIL'] + '?subject=' + quote(subject, safe='') + '&body=' + quote(body, safe='')
    retention = re.search(r'(?m)^# 10\. Saklama ve silme\n(.*?)(?=^# 11\.)', legal['gizlilik.html'], re.S | re.M)[1].rstrip()
    output['hesap-silme.html'] = page('hesap-silme.html', 'Hesap ve veri silme', 'Zeytin Defteri hesabını uygulamadan silme adımları ve e-posta ile hesap silme talebi.', f'''
<article class="document"><h1>Hesap ve veri silme</h1><p>Zeytin Defteri hesabın için iki başvuru yolu:</p>
<h2>Uygulama içinden</h2><ol><li>Zeytin Defteri'nde hesabına giriş yap ve <strong>Ayarlar</strong> ekranını aç.</li><li><strong>Hesabımı Sil</strong> satırına dokun.</li><li><strong>Hesabını sil</strong> penceresindeki açıklamayı oku. Bu işlem geri alınamaz.</li><li>Onay alanına <strong>SİL</strong> yaz ve <strong>Hesabımı sil</strong> düğmesine dokun.</li></ol>
<p><strong>Yeniden giriş gerekli</strong> uyarısı çıkarsa çıkış yapıp tekrar giriş yap; ardından aynı adımları tekrarla. Başarılı işlem sonunda <strong>Hesabın ve verilerin silindi.</strong> mesajıyla giriş ekranına dönersin.</p>
<p>Misafir oturumunda bulut hesabı yoktur; uygulama yalnız bu telefondaki misafir verilerini silme onayı ister.</p>
<h2>Uygulamaya erişemiyorsan</h2><p>Hesabında kayıtlı e-posta adresinden <a href="mailto:{email}">{email}</a> adresine yaz. Uygulamadaki <strong>E-posta ile talep et</strong> seçeneğiyle aynı konu ve metni hazırlamak için:</p>
<p><a class="button" href="{esc(mailto)}">Hesap silme e-postasını hazırla</a></p><p>Bağlantı e-posta uygulamanı açar; mesajı kontrol edip sen gönderirsin. Açılmazsa aşağıdaki konu ve metni kopyalayabilirsin.</p>
<h3>Konu</h3><p class="mail-subject">{esc(subject)}</p><h3>Mesaj</h3><pre class="mail-body">{esc(body)}</pre>
<h2>Hangi veriler silinir, hangileri saklanabilir?</h2><p>Aşağıdaki metin, <a href="gizlilik.html#bolum-10">Gizlilik Politikası — 10. Saklama ve silme</a> bölümünden aynen alınmıştır. Sürüm {esc(values['VERSION'])} · {esc(values['DATE'])}</p><div class="retention">{blocks(retention)}</div></article>''')
    output['iletisim.html'] = page('iletisim.html', 'İletişim', 'Zeytin Defteri destek, öneri, hata bildirimi, hesap silme ve kişisel veri başvuruları için iletişim adresi.', f'''<article class="document"><p class="eyebrow">BİZE YAZ</p><h1>İletişim</h1><p>Uygulamayla ilgili soruların, önerilerin ve hata bildirimlerin için yazabilirsin. Hesap silme ve kişisel verilerine ilişkin başvurularını da bu adrese iletebilirsin.</p><p><a class="contact-email" href="mailto:{email}">{email}</a></p><p>Hesap silme başvurusu için <a href="hesap-silme.html">hesap silme adımlarını</a> inceleyebilirsin.</p></article>''')
    output['404.html'] = page('404.html', 'Sayfa bulunamadı', 'Aradığın sayfa bulunamadı. Zeytin Defteri ana sayfasına dönebilirsin.', '<section class="document"><p class="eyebrow">404</p><h1>Bu sayfayı bulamadık.</h1><p>Bağlantı değişmiş olabilir. Ana sayfadan devam edebilirsin.</p><a class="button" href="/index.html">Ana sayfaya dön</a></section>')
    output['robots.txt'] = f'User-agent: *\nAllow: /\nSitemap: {base}/sitemap.xml\n'
    pages = [x for x in output if x.endswith('.html') and x != '404.html']
    output['sitemap.xml'] = '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' + ''.join(f'  <url><loc>{esc(base)}/{name}</loc></url>\n' for name in pages) + '</urlset>\n'
    vector = ET.parse(ROOT / 'app/src/main/res/drawable/ic_olive_logo.xml').getroot()
    ns = '{http://schemas.android.com/apk/res/android}'
    paths = ''.join(f'  <path fill="{p.attrib[ns + "fillColor"]}" d="{p.attrib[ns + "pathData"]}"/>\n' for p in vector.findall('path'))
    output['favicon.svg'] = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">\n' + paths + '</svg>\n'
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Dosya yazmadan sapmaları denetle')
    args = parser.parse_args()
    output = render()
    stale = []
    for name, content in output.items():
        path = SITE / name
        if args.check:
            if not path.exists() or path.read_bytes() != content.encode('utf-8'):
                stale.append(name)
        else:
            path.write_bytes(content.encode('utf-8'))
    if stale:
        raise SystemExit('Üretim sapması: ' + ', '.join(stale))
    print(f'{len(output)} dosya: ' + ('kaynaklarla birebir aynı' if args.check else 'üretildi'))


if __name__ == '__main__':
    main()
