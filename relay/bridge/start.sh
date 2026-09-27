#!/bin/sh
set -eu

: "${BACKEND_HOST:?BACKEND_HOST is required}"
: "${BACKEND_PORT:?BACKEND_PORT is required}"

JAVA_BIND_ADDRESS="${JAVA_BIND_ADDRESS:-0.0.0.0}"
JAVA_BIND_PORT="${JAVA_BIND_PORT:-25568}"

mkdir -p /opt/worldgate/run /opt/worldgate/plugins

# Generate the current ViaProxy config schema on first boot.
if [ ! -f /opt/worldgate/run/viaproxy.yml ]; then
  cd /opt/worldgate/run
  java -jar /opt/worldgate/ViaProxy.jar config viaproxy.yml || true
fi

cd /opt/worldgate
exec java -jar /opt/worldgate/ViaProxy.jar cli \
  --bind-address "${JAVA_BIND_ADDRESS}" \
  --bind-port "${JAVA_BIND_PORT}" \
  --target-address "${BACKEND_HOST}" \
  --target-port "${BACKEND_PORT}"