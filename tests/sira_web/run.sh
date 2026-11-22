#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
node --no-warnings tests/sira_web/test.mjs
