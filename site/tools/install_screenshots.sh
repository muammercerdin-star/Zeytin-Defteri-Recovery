#!/bin/sh
# Gerçek ekran paketi yalnızca work/site dalına güvenli biçimde eklenir.
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
[ "$(git branch --show-current)" = "work/site" ] || {
  echo "HATA: Yalnız work/site dalında çalıştırılabilir." >&2; exit 1;
}
[ "$#" -eq 1 ] || { echo "Kullanım: sh site/tools/install_screenshots.sh /yol/zeytin_gercek_ekranlar_paket.zip" >&2; exit 1; }
ZIP="$1"
[ -f "$ZIP" ] || { echo "HATA: ZIP bulunamadı: $ZIP" >&2; exit 1; }
command -v unzip >/dev/null || { echo "HATA: unzip kurulu değil." >&2; exit 1; }
command -v python3 >/dev/null || { echo "HATA: python3 kurulu değil." >&2; exit 1; }
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT HUP INT TERM
for name in ana-sayfa.webp bahcelerim.webp elek-kaydi.webp; do
  unzip -p "$ZIP" "$name" > "$TMP/$name"
  [ -s "$TMP/$name" ] || { echo "HATA: eksik ekran $name" >&2; exit 1; }
done
mkdir -p site/assets/screenshots
for name in ana-sayfa.webp bahcelerim.webp elek-kaydi.webp; do
  cp "$TMP/$name" "site/assets/screenshots/$name"
done
python3 site/tools/build_legal.py
sh tests/site/run.sh
echo "Görseller eklendi, site üretildi ve testler geçti. Commit/push YAPILMADI."
echo "Kontrol: git diff -- site/index.html && git status --short"
