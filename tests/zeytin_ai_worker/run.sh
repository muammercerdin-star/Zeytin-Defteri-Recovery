#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd)
cd "$ROOT/cloud/zeytin-ai" && node test.mjs
