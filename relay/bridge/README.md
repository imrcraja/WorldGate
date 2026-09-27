# WorldGate protocol bridge

This directory packages the maintained protocol stack used by WorldGate instead of reimplementing Minecraft packet translation.

Components:
- ViaProxy for the standalone Java proxy and version routing.
- ViaVersion and ViaBackwards through the ViaProxy stack for Java version translation.
- Geyser-ViaProxy for Bedrock-to-Java translation.
- ViaBedrock in the ViaProxy stack for Bedrock protocol support.

Environment:
- Required: BACKEND_HOST and BACKEND_PORT.
- Optional: JAVA_BIND_ADDRESS (default 0.0.0.0) and JAVA_BIND_PORT (default 25568).

The Bedrock listener is supplied by the Geyser-ViaProxy plugin and uses UDP. A deployment that exposes only TCP cannot serve Bedrock clients.

The existing WorldGate WebSocket relay remains separate and must not be replaced by this protocol bridge.

Do not store Firebase private keys, Floodgate key.pem, or other credentials in this image or repository.