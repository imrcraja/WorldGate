#!/bin/sh
set -eu

: "${BACKEND_HOST:?BACKEND_HOST is required}"
: "${BACKEND_PORT:?BACKEND_PORT is required}"

JAVA_BIND_ADDRESS="${JAVA_BIND_ADDRESS:-0.0.0.0}"
JAVA_BIND_PORT="${JAVA_BIND_PORT:-25568}"
TARGET_VERSION="${TARGET_VERSION:-auto-detect}"

case "${JAVA_BIND_PORT}" in
  ''|*[!0-9]*) echo "JAVA_BIND_PORT must be numeric" >&2; exit 2 ;;
esac
case "${BACKEND_PORT}" in
  ''|*[!0-9]*) echo "BACKEND_PORT must be numeric" >&2; exit 2 ;;
esac

mkdir -p /opt/worldgate/run /opt/worldgate/plugins
cd /opt/worldgate

exec java -jar /opt/worldgate/ViaProxy.jar cli \
  --bind-address "${JAVA_BIND_ADDRESS}:${JAVA_BIND_PORT}" \
  --target-address "${BACKEND_HOST}:${BACKEND_PORT}" \
  --target-version "${TARGET_VERSION}"
