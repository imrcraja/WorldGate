# WorldGate protocol bridge

WorldGate uses maintained protocol implementations rather than reimplementing Minecraft packet translation.

Components:
- ViaProxy for the standalone Java proxy and version routing.
- ViaVersion/ViaBackwards/ViaLegacy/ViaAprilFools through the ViaProxy stack.
- Geyser-ViaProxy for Bedrock-to-Java translation.
- ViaBedrock in the ViaProxy stack for Bedrock protocol support.

## Runtime configuration

Required:
- `BACKEND_HOST`
- `BACKEND_PORT`

Optional:
- `JAVA_BIND_ADDRESS` (default `0.0.0.0`)
- `JAVA_BIND_PORT` (default `25568`)
- `TARGET_VERSION` (default `auto-detect`)

The container exposes:
- Java/TCP: `25568`
- Bedrock/UDP: `19132`

A public Bedrock endpoint requires a deployment platform that actually exposes public UDP. A TCP-only web service cannot serve Bedrock clients.

## Important architecture rule

The existing WorldGate WebSocket relay is preserved. This bridge is the protocol-translation layer and does not replace the WebSocket relay.

For one backend server, set `BACKEND_HOST` and `BACKEND_PORT` to that server. Multi-room routing must be provided by the deployment/control plane; the bridge must not contain hardcoded room credentials or Firebase private keys.

Do not store Firebase private keys, Floodgate `key.pem`, access tokens, or other secrets in this image or repository.
