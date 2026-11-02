#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
node tests/web_relay/test.mjs
