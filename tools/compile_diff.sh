#!/bin/bash
# Android'siz derleme karşılaştırması (İP3).
#
# app/src/main/java'nın tamamı Android kütüphanesi olmadan javac ile derlenir. Android sınıfları bulunamadığı için
# binlerce hata çıkar; bunlar her iki tarafta aynıdır. Aynı derleme, verilen başlangıç commit'inin kaynağıyla da
# yapılır ve hata listeleri karşılaştırılır. Hata kimliği: dosya + mesaj + sembol + konum + kaynak satırı
# (satır NUMARASI hariç). Başlangıçta olmayıp şimdi çıkan her hata GERÇEK hatadır (ör. İP2'deki priceChanged).
# Tek ayrım: yeni satırda çıkan hata, başlangıçta aynı mesaj + sembol + konumla zaten varsa (ör. yeni kodda
# getIntent() çağrısı: "method getIntent() / class ElekActivity" Android olmadığı için hep bulunamaz) bu
# "Android eksikliği" sayılır, ayrıca listelenir ama sonucu bozmaz.
#
# Kullanım: bash tools/compile_diff.sh <başlangıç-commit>   (ör. dalın açıldığı commit)
# Çıkış: 0 = yeni hata yok, 1 = yeni hata var (listelenir), 2 = kullanım/ortam hatası.
# Not: Bu bir Android derlemesi DEĞİLDİR; assembleDebug'ın yerini tutmaz, onun öncesinde yakalanabileni yakalar.
set -u
BASE=${1:-${ZD_COMPILE_BASE:-}}
if [ -z "$BASE" ]; then
  echo "Kullanım: bash tools/compile_diff.sh <başlangıç-commit>" >&2
  exit 2
fi
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
if ! git rev-parse --verify --quiet "$BASE^{commit}" >/dev/null; then
  echo "Commit bulunamadı: $BASE (gerekirse: git fetch --depth 50 origin <dal>)" >&2
  exit 2
fi
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
mkdir -p "$W/base" "$W/out_base" "$W/out_now"
git archive "$BASE" app/src/main/java | tar -x -C "$W/base"

compile() {
  # $1 = kaynak kökü, $2 = çıktı dizini, $3 = günlük
  local list="$W/$(basename "$2").list"
  (cd "$1" && find app/src/main/java -name '*.java' | sort) > "$list"
  (cd "$1" && javac -J-Dfile.encoding=UTF-8 -J-Dstderr.encoding=UTF-8 -J-Dsun.stderr.encoding=UTF-8 -encoding UTF-8 -nowarn -proc:none -Xmaxerrs 100000 -Xmaxwarns 0 \
      -XDshould-stop.ifError=FLOW -d "$2" @"$list") > "$3" 2>&1
}
compile "$W/base" "$W/out_base" "$W/base.log"
compile "$ROOT" "$W/out_now" "$W/now.log"

python3 - "$W/base.log" "$W/now.log" <<'PY'
import collections
import re
import sys

head = re.compile(r"^(app/src/main/java/\S+\.java):\d+: error: (.*)$")


def errors(path):
    lines = open(path, encoding="utf-8", errors="replace").read().split("\n")
    out = collections.Counter()
    shown = {}
    i = 0
    while i < len(lines):
        m = head.match(lines[i])
        if not m:
            i += 1
            continue
        file, msg = m.group(1), m.group(2).strip()
        src = lines[i + 1].strip() if i + 1 < len(lines) else ""
        extra = []
        j = i + 2
        if j < len(lines) and lines[j].strip() == "^":
            j += 1
        while j < len(lines) and not head.match(lines[j]) and lines[j].startswith("  "):
            extra.append(lines[j].strip())
            j += 1
        key = (file, msg, src, tuple(e for e in extra if e.startswith(("symbol", "location", "required", "found", "reason"))))
        out[key] += 1
        shown.setdefault(key, lines[i])
        i = j
    return out, shown


base, _ = errors(sys.argv[1])
now, shown = errors(sys.argv[2])
diff = now - base
gone = base - now
# Aynı mesaj + sembol + konum başlangıçta varsa: Android'in eksikliği (yeni satırda aynı bulunamayan sembol).
known = {(k[1], k[3]) for k in base}
android = collections.Counter({k: n for k, n in diff.items() if (k[1], k[3]) in known})
real = diff - android
print(f"Başlangıç: {sum(base.values())} hata · şimdi: {sum(now.values())} hata (Android sınıfları olmadan)")
print(f"Kaybolan (düzelen ya da taşınan): {sum(gone.values())}")
print(f"Android eksikliğine bağlı yeni satır: {sum(android.values())}")
for key in sorted(android):
    extra = " · ".join(key[3])
    print(f"  ~ {key[0].split('/')[-1]}: {key[1]} [{extra}]  ← {key[2][:90]}")


def show(items, mark):
    for key in sorted(items):
        file, msg, src, extra = key
        print(f"{mark} {shown[key]}")
        print(f"    {src}")
        for e in extra:
            print(f"    {e}")


if not real:
    print("YENİ HATA YOK")
    sys.exit(0)
print(f"YENİ HATA: {sum(real.values())}")
show(real, "-")
sys.exit(1)
PY
