#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
node --no-warnings tests/sira_worker/test.mjs
