#!/bin/sh
# Hesap silme: sunucu servisi (sahte Firestore), yerel silme (gerçek SQLite), bağlantı kontrolü.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
node --test functions/test/account.test.js
python3 tests/account/check_local_wipe.py
python3 tests/account/check_wiring.py
