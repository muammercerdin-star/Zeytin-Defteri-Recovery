#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
PYTHONDONTWRITEBYTECODE=1 python3 tests/site/check_site.py
