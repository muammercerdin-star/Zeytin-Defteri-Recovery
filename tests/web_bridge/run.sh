#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
node --experimental-strip-types --no-warnings tests/web_bridge/bridge.test.mjs
