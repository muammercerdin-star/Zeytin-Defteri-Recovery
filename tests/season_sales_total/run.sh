#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
python3 tests/season_sales_total/check.py
