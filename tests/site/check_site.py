#!/usr/bin/env python3
"""Statik sitenin sözleşmesi; Java kaynak davranışı bağımsız JVM oracle ile karşılaştırılır."""
import html
from html.parser import HTMLParser
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import tempfile
from urllib.parse import parse_qs, unquote, urlsplit
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
SITE = ROOT / 'site'
spec = importlib.util.spec_from_file_location('build_legal', SITE / 'tools/build_legal.py')
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)

class Document(HTMLParser):
    def __init__(self, text):
        super().__init__(convert_charrefs=True)
        self.tags, self.text = [], []
        self.feed(text)
    def handle_starttag(self, tag, attrs):
        self.tags.append((tag, dict(attrs)))
        self.text.append(' ')
    def handle_endtag(self, tag):
        self.text.append(' ')
    def handle_data(self, data):
        self.text.append(data)


def normal(text):
    return ' '.join(text.split())


def test_site():
    subprocess.run(['python3', str(SITE / 'tools/build_legal.py'), '--check'], check=True)
    config = json.loads((SITE / 'config.json').read_text())
    # Execute the real Android-free Java class, rather than trusting the generator's parser.
    source = ROOT / 'app/src/main/java/com/zeytindefteri/app/LegalDocuments.java'
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        oracle = tmp / 'SiteLegalOracle.java'
        oracle.write_text('''package com.zeytindefteri.app;
import java.nio.file.*;
public class SiteLegalOracle {
 public static void main(String[] args) throws Exception {
  if (args[0].equals("email")) System.out.print(LegalDocuments.CONTACT_EMAIL);
  else if (args[0].equals("subject")) System.out.print(LegalDocuments.deletionSubject());
  else if (args[0].equals("body")) System.out.print(LegalDocuments.deletionBody());
  else System.out.print(LegalDocuments.fill(Files.readString(Path.of(args[0]))));
 }
}''')
        subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-encoding', 'UTF-8', '-d', str(tmp), str(source), str(oracle)], check=True)
        def java(arg):
            return subprocess.check_output(['java', '-cp', str(tmp), 'com.zeytindefteri.app.SiteLegalOracle', str(arg)]).decode()
        assert config['CONTACT_EMAIL'] == java('email'), 'E-posta uygulamayla farklı'
        for filename, raw in [('gizlilik.html', 'legal_privacy.txt'), ('kvkk.html', 'legal_kvkk.txt'), ('kosullar.html', 'legal_terms.txt')]:
            filled = java(build.RAW / raw)
            assert filled == build.fill((build.RAW / raw).read_text(), build.constants()), 'fill() Java ile farklı'
            doc = Document((SITE / filename).read_text())
            visible = normal(''.join(doc.text))
            # Each whole paragraph must remain contiguous; remove only Markdown presentation markers.
            for paragraph in filled.strip().split('\n\n'):
                clean = re.sub(r'(?m)^(# |- )', '', paragraph)
                assert normal(clean) in visible, (filename, clean)
            assert '{{' not in visible
        deletion = Document((SITE / 'hesap-silme.html').read_text())
        mailtos = [a['href'] for tag, a in deletion.tags if tag == 'a' and a.get('href', '').startswith('mailto:') and '?' in a['href']]
        assert len(mailtos) == 1
        url = urlsplit(mailtos[0])
        query = parse_qs(url.query, keep_blank_values=True)
        assert unquote(url.path) == java('email')
        assert query == {'subject': [java('subject')], 'body': [java('body')]}, 'Silme mailto Java ile farklı'
        retention = re.search(r'(?ms)^# 10\. Saklama ve silme\n(.*?)(?=^# 11\.)', java(build.RAW / 'legal_privacy.txt'))[1]
        assert normal(re.sub(r'(?m)^- ', '', retention)) in normal(''.join(deletion.text))
    docs = {p.name: Document(p.read_text()) for p in SITE.glob('*.html')}
    assert len(docs) == 7
    for filename, doc in docs.items():
        assert any(t == 'html' and a.get('lang') == 'tr' for t, a in doc.tags), filename
        assert any(t == 'title' for t, a in doc.tags), filename
        for key, value in [('name', 'description'), ('property', 'og:title'), ('property', 'og:description')]:
            assert any(t == 'meta' and a.get(key) == value and a.get('content', '').strip() for t, a in doc.tags), (filename, value)
        assert re.search(r'<title>[^<]+</title>', (SITE / filename).read_text())
        assert not any(t in ('script', 'iframe', 'form', 'style') for t, a in doc.tags), filename
        for tag, attrs in doc.tags:
            assert 'style' not in attrs and not any(k.startswith('on') for k in attrs)
            if tag == 'img':
                assert 'alt' in attrs
            for attribute in ('href', 'src', 'srcset', 'action', 'poster', 'data'):
                if attribute not in attrs:
                    continue
                address = attrs[attribute]
                parsed = urlsplit(address)
                if parsed.scheme == 'mailto':
                    continue
                assert not parsed.scheme and not parsed.netloc, (filename, address)
                target = SITE / unquote(parsed.path.lstrip('/')) if parsed.path else SITE / filename
                assert target.is_file(), (filename, target)
                if parsed.fragment:
                    target_doc = docs.get(target.name)
                    assert target_doc and any(a.get('id') == parsed.fragment for _, a in target_doc.tags), address
        assert not re.search(r"google\s+play['’]den\s+indir|play\.google\.com", (SITE / filename).read_text(), re.I)
    css = (SITE / 'style.css').read_text()
    assert '@import' not in css and not re.search(r'url\s*\(', css, re.I)
    assert ':focus-visible' in css and '@media' in css
    headers = (SITE / '_headers').read_text()
    for policy in ["Content-Security-Policy: default-src 'self'", "img-src 'self' data:", "style-src 'self'", "frame-ancestors 'none'", 'Referrer-Policy: no-referrer', 'X-Content-Type-Options: nosniff', 'camera=()', 'microphone=()', 'geolocation=()']:
        assert policy in headers, policy
    size = sum(p.stat().st_size for p in SITE.rglob('*') if p.is_file() and '__pycache__' not in p.parts)
    # Yeni üç ekran görseli toplam 100 KB altında olacak; yasal içerik testleri değişmez.
    assert size < 280_000, size
    photo_dir = SITE / 'assets/screenshots'
    photos = [photo_dir / name for name in ('ana-sayfa.webp', 'bahcelerim.webp', 'elek-kaydi.webp')]
    exists = [p.is_file() for p in photos]
    homepage = (SITE / 'index.html').read_text(encoding='utf-8')
    if any(exists):
        assert all(exists), 'Gerçek ekran galerisi yalnız eksiksiz üçlü setle yayınlanmalı'
        assert sum(p.stat().st_size for p in photos) <= 100_000
        for p in photos:
            b = p.read_bytes()
            assert b[:4] == b'RIFF' and b[8:12] == b'WEBP', p
        assert 'id="gercek-ekranlar"' in homepage
        for p in photos:
            assert ('/assets/screenshots/' + p.name) in homepage
    else:
        assert 'id="gercek-ekranlar"' not in homepage
        assert '/assets/screenshots/' not in homepage
    sitemap = ET.parse(SITE / 'sitemap.xml')
    for loc in sitemap.findall('.//{*}loc'):
        assert loc.text.startswith(config['SITE_URL'].rstrip('/') + '/')
        assert (SITE / urlsplit(loc.text).path.lstrip('/')).is_file()
    assert 'Sitemap: ' + config['SITE_URL'].rstrip('/') + '/sitemap.xml' in (SITE / 'robots.txt').read_text()
    vector = ET.parse(ROOT / 'app/src/main/res/drawable/ic_olive_logo.xml').getroot()
    svg = ET.parse(SITE / 'favicon.svg').getroot()
    ns = '{http://schemas.android.com/apk/res/android}'
    assert [(p.get(ns+'pathData'), p.get(ns+'fillColor')) for p in vector] == [(p.get('d'), p.get('fill')) for p in svg]
    print(f'PASS: 7 HTML, Java fill/paragraflar/mailto, bağlantılar, metadata, CSP, SVG; site {size:,} bayt')

if __name__ == '__main__':
    test_site()
