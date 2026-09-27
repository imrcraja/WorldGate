#!/bin/sh
set -eu

: "${BACKEND_HOST:?BACKEND_HOST is required}"
: "${BACKEND_PORT:?BACKEND_PORT is required}"

JAVA_BIND_ADDRESS="${JAVA_BIND_ADDRESS:-0.0.0.0}"
JAVA_BIND_PORT="${JAVA_BIND_PORT:-25568}"

mkdir -p /opt/worldgate/run /opt/worldgate/plugins

# ViaProxy accepts all configuration options directly in CLI mode.
# AUTO_DETECT_PROTOCOL lets ViaProxy negotiate the backend protocol.
cd /opt/worldgate
exec java -jar /opt/worldgate/ViaProxy.jar cli \
  --bind-address "${JAVA_BIND_ADDRESS}:${JAVA_BIND_PORT}" \
  --target-address "${BACKEND_HOST}:${BACKEND_PORT}" \
  --target-version auto-detect