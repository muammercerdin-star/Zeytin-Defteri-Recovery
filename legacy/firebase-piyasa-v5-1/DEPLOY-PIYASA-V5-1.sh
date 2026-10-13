#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
PROJECT_ID="zeytin-defteri"
ONLY="firestore:rules,firestore:indexes,functions:zeytinMarketElekWritten,functions:zeytinMarketConsentWritten,functions:zeytinMarketGardenWritten,functions:zeytinMarketBackfill,functions:zeytinMarketPublish,functions:zeytinMarketRefreshWindows"

echo "============================================================"
echo " Zeytin Defteri - Piyasa V5.1 kontrollu Firebase deploy"
echo " Hedef proje: ${PROJECT_ID}"
echo " Kapsam: Firestore rules/indexes + SADECE Piyasa functionlari"
echo "============================================================"

command -v node >/dev/null 2>&1 || { echo "HATA: Node.js bulunamadi."; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "HATA: npm bulunamadi."; exit 1; }
command -v firebase >/dev/null 2>&1 || {
  echo "HATA: Firebase CLI bulunamadi."
  echo "Once Firebase CLI kur ve firebase login ile giris yap; sonra bu scripti tekrar calistir."
  exit 1
}

NODE_MAJOR="$(node -p "process.versions.node.split('.')[0]")"
if [ "$NODE_MAJOR" -lt 22 ]; then
  echo "HATA: Node 22+ gerekli. Mevcut: $(node -v)"
  exit 1
fi

node tools/preflight-market-deploy.js

if [ ! -d functions/node_modules ]; then
  echo "functions/node_modules yok; npm ci calistiriliyor..."
  npm --prefix functions ci
fi

npm --prefix functions run check
npm --prefix functions test

echo "Firebase oturumu kontrol ediliyor..."
if ! firebase login:list >/dev/null 2>&1; then
  echo "HATA: Firebase CLI oturumu yok. 'firebase login' calistir ve tekrar dene."
  exit 1
fi

echo "Deploy basliyor. Hedef proje sabit: ${PROJECT_ID}"
firebase deploy --project "$PROJECT_ID" --only "$ONLY"

echo "============================================================"
echo " DEPLOY TAMAMLANDI"
echo " Sonraki adim: uygulamada Anonim Katkiyi Ac ve Elek kaydini"
echo " buluta senkronla. 5+ farkli uretici olmadan anonim sonuc"
echo " yayinlanmamasi BEKLENEN davranistir."
echo "============================================================"
