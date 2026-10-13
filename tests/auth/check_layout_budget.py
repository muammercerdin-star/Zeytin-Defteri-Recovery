#!/usr/bin/env python3
"""Static height estimate for the registration screen (register mode, keyboard closed).

This is NOT an Android render. It resolves activity_auth_premium.xml + auth_premium.xml styles
and estimates each view's height in dp so layout regressions are caught on a host machine.

Reference viewport (Honor X9 5G screenshots, 13.10.2026): 905x1998 px screenshot, a 48dp field
measures 115 px -> 2.40 px/dp, so the device is ~377dp wide. Status bar ends at ~105 px and the
window ends above the gesture bar at ~1952 px -> usable viewport ~770dp.
"""
import pathlib, re, subprocess, sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
A = '{http://schemas.android.com/apk/res/android}'
VIEWPORT_DP = 770
CONTENT_WIDTH_DP = 377 - 48           # LoginActivity: min(360, screenWidthDp - 48)
LINE = 1.17                            # includeFontPadding=false line height factor
CHAR = 0.52                            # average glyph width / sp for Turkish UI text


def dim(v, default=0.0):
    if v is None:
        return default
    m = re.match(r'(-?[\d.]+)(dp|sp|px)?$', v.strip())
    return float(m.group(1)) if m else default


def load(layout_xml, values_xml):
    styles, strings = {}, {}
    vals = ET.fromstring(values_xml)
    for s in vals.findall('string'):
        strings[s.get('name')] = s.text or ''
    for st in vals.findall('style'):
        styles[st.get('name')] = {i.get('name').replace('android:', ''): i.text for i in st.findall('item')}
    return ET.fromstring(layout_xml), styles, strings


def attrs(el, styles):
    a = {}
    st = el.get('style')
    if st:
        a.update(styles.get(st.split('/')[-1], {}))
    a.update({k.replace(A, ''): v for k, v in el.attrib.items() if k.startswith(A)})
    return a


def text_height(a, strings, width, scale):
    sp = dim(a.get('textSize'), 14) * scale
    txt = a.get('text') or a.get('hint') or ''
    if txt.startswith('@string/'):
        txt = strings.get(txt.split('/')[-1], '')
    lines = 1 if a.get('singleLine') == 'true' else max(1, -(-int(len(txt) * CHAR * sp) // max(1, int(width))))
    if a.get('maxLines'):
        lines = min(lines, int(a['maxLines']))
    return lines * sp * LINE + (lines - 1) * dim(a.get('lineSpacingExtra'))


def measure(el, styles, strings, width, scale, hidden):
    a = attrs(el, styles)
    if a.get('id', '').split('/')[-1] in hidden or a.get('visibility') == 'gone':
        return 0.0
    pad = dim(a.get('padding'))
    pt = dim(a.get('paddingTop'), pad); pb = dim(a.get('paddingBottom'), pad)
    pl = dim(a.get('paddingStart'), pad); pr = dim(a.get('paddingEnd'), pad)
    inner = width - pl - pr
    tag = el.tag
    h = a.get('layout_height')
    if h and h.endswith('dp'):
        return dim(h)
    if tag in ('LinearLayout', 'FrameLayout', 'ScrollView'):
        kids = []
        for c in el:
            ca = attrs(c, styles)
            if ca.get('id', '').split('/')[-1] in hidden or ca.get('visibility') == 'gone':
                kids.append(0.0); continue
            m = dim(ca.get('layout_margin'))
            mt = dim(ca.get('layout_marginTop'), m); mb = dim(ca.get('layout_marginBottom'), m)
            ms = dim(ca.get('layout_marginStart'), m)
            kids.append(measure(c, styles, strings, inner - ms, scale, hidden) + mt + mb)
        horizontal = tag == 'LinearLayout' and a.get('orientation') == 'horizontal'
        content = (max(kids) if kids else 0) if (horizontal or tag != 'LinearLayout') else sum(kids)
        return max(dim(a.get('minHeight')), content + pt + pb)
    if tag in ('TextView', 'EditText'):
        return max(dim(a.get('minHeight')), text_height(a, strings, inner, scale) + pt + pb)
    return max(dim(a.get('minHeight')), pt + pb)


def estimate(layout_xml, values_xml, scale=1.0, mode='register'):
    root, styles, strings = load(layout_xml, values_xml)
    hidden = {'auth_forgot'} if mode == 'register' else {'auth_name_group', 'auth_confirm_group', 'auth_strength', 'auth_legal'}
    content = root.find('.//*[@{}id="@+id/auth_content"]'.format(A))
    return measure(content, styles, strings, CONTENT_WIDTH_DP, scale, hidden)


def from_git(rev, path):
    try:
        return subprocess.run(['git', 'show', f'{rev}:{path}'], cwd=ROOT, capture_output=True, text=True, check=True).stdout
    except Exception:
        return None


LAYOUT = 'app/src/main/res/layout/activity_auth_premium.xml'
VALUES = 'app/src/main/res/values/auth_premium.xml'
cur = ((ROOT / LAYOUT).read_text(encoding='utf-8'), (ROOT / VALUES).read_text(encoding='utf-8'))
base_rev = sys.argv[1] if len(sys.argv) > 1 else '4f37b20f962e29a236cf903a7d8c9676016bded1'
base = (from_git(base_rev, LAYOUT), from_git(base_rev, VALUES))

print(f'Reference viewport: {VIEWPORT_DP}dp (Honor X9 5G, measured from screenshots)')
for scale in (1.0, 1.15, 1.3):
    now = estimate(*cur, scale=scale)
    before = estimate(*base, scale=scale) if all(base) else float('nan')
    fits = 'SIĞAR' if now <= VIEWPORT_DP else 'KAYDIRMA GEREKİR'
    print(f'font x{scale:<4}: önce {before:6.1f}dp  ->  şimdi {now:6.1f}dp  [{fits}]')
login = estimate(*cur, mode='login')
print(f'Giriş modu (x1.0): {login:6.1f}dp')

root, _, _ = load(*cur)
scroll_ok = root.tag == 'ScrollView' and root.get(A + 'fillViewport') == 'true'
assert scroll_ok, 'ScrollView fallback must remain for small screens / large fonts / keyboard'
assert estimate(*cur) <= VIEWPORT_DP - 12, 'register screen must fit the reference viewport with margin at x1.0'
for name in ('auth_name', 'auth_email', 'auth_password', 'auth_confirm'):
    el = root.find('.//*[@{}id="@+id/{}"]'.format(A, name))
    assert el.get(A + 'text') is None, f'{name} must not be prefilled'
print('PASS: layout budget (static estimate, not a device render).')
