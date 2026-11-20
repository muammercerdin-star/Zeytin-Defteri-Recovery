"""Uygulamaya gömülü kontrol bankasını (res/raw/kontrol_bankasi.tsv) cümle bankasının "doğru" satırlarından üretir.
Tek kaynak: tests/sentence_bank/cumleler.tsv. Eşitliği tests/community denetler. Kullanım: python3 tools/make_control_bank.py"""
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[1]
src = root / "tests/sentence_bank/cumleler.tsv"
dst = root / "app/src/main/res/raw/kontrol_bankasi.tsv"


def real_names():
    path = root / "tools/pack/gercek_adlar.txt"
    out = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            out.add(line.strip().lower())
    return out


def has_real_name(sentence, names):
    words = "".join(ch.lower() if ch.isalpha() else " " for ch in sentence.replace("İ", "i").replace("I", "ı")).split()
    return any(w.startswith(n) for w in words for n in names)


def build():
    names = real_names()
    lines = ["# Kontrol bankası (İP8): bilgi paketi bu cümlelerin sonucunu değiştirirse uygulanmaz.",
             "# tests/sentence_bank/cumleler.tsv'nin 'dogru' satırlarından üretilir (tools/make_control_bank.py); elle düzenleme.",
             "# Uydurma örnek cümlelerdir; gerçek kişi adı ya da kişisel veri yoktur (testle denetlenir)."]
    for line in src.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        c = line.split("\t")
        if len(c) >= 4 and c[3] == "dogru" and c[0] != "cumle" and not has_real_name(c[0], names):
            lines.append("\t".join(c[:3]))
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    text = build()
    if len(sys.argv) > 1 and sys.argv[1] == "--check":
        ok = dst.exists() and dst.read_text(encoding="utf-8") == text
        print(("PASS" if ok else "FAIL") + " kontrol bankası cümle bankasıyla aynı")
        sys.exit(0 if ok else 1)
    dst.write_text(text, encoding="utf-8")
    print("yazıldı:", dst, len(text.splitlines()) - 3, "cümle")
