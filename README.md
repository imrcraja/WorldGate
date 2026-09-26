# WorldGate

Play Minecraft Java Edition with friends over the internet, straight from any
world — no dedicated server required. Built by **RC RAJA GAMER 2.0**.

- Channel: https://youtube.com/@RCRAJAGAMER2.0
- Issues: https://github.com/imrcraja/WorldGate/issues

## What it does

- Host your current world and let friends join over the internet (not just LAN)
- Cracked (offline-mode) and premium accounts play together in the same world
- Firebase-backed room codes and a friends list, so distance doesn't matter
- Custom-mod support, just like normal Fabric
- In-game "WorldGate" button in the Escape menu, with color-coded live ping in Tab
- Works with **any Fabric Loader version** — every module below declares a
  loader version *range*, not a single pinned version, so Loader updates
  won't break it

## Current Minecraft target

The active production target is **Minecraft 26.1.2**. The repository intentionally builds only
the 26.1.2 module right now; older version modules are not part of the current production
release and can be added back later without changing the shared backend/network architecture.

```
WorldGate/
├── common/        <- shared Firebase, networking, room, chat and account logic
└── v26_1_2/       <- Minecraft 26.1.2, Mojang mappings
```

`gradle build` at the repo root builds the active 26.1.2 jar. GitHub Actions uploads the
`WorldGate-26.1.2` artifact.

New Minecraft-version modules should be added only after the 26.1.2 production checklist is
complete.

## Status: WorldGate 26.1.2 production integration

| Feature | Status |
|---|---|
| Mod skeleton and Escape-menu WorldGate button | Integrated |
| Firebase anonymous auth + Realtime Database | Integrated |
| Room creation/join bookkeeping | Integrated |
| Host-to-player relay bridge | Integrated |
| WebSocket relay service under `relay/` | Integrated and hardened with protocol/integrity/rate-limit checks |
| Friends, presence and realtime room state | Integrated |
| Realtime chat and emote state | Integrated |
| Elite profile and server-authoritative Elite badges | Integrated |
| Elite Coin wallet, catalog and shop | Integrated |
| Claim Center, daily/activity rewards and mailbox | Integrated |
| Elite Coin gifting by UID or friend-row selection | Integrated |
| Red-dot claim/mail notification refresh | Integrated |
| Real-money Elite Coin checkout | Pending by design; payment gateway is intentionally deferred |
| Live skin/cape replacement and full body emote animation | Separate version-specific work remains |

The current ecosystem integration is intentionally scoped to Minecraft 26.1.2. Other Minecraft-version modules are intentionally deferred until the 26.1.2 production checklist is complete.

## Building

Requires JDK 25 for Minecraft 26.1.2 — the included GitHub Actions workflow installs it automatically, so pushing
to `main` or opening a PR is enough; no local dev environment needed.

```bash
gradle build
```

## Before it will fully work

1. Open **Firebase Console → Realtime Database** for the `worldgate-be4a0`
   project and copy the exact database URL shown at the top of that page into
   `common/src/main/java/com/rcraja/worldgate/Constants.java`
   (`FIREBASE_DATABASE_URL` — it may include a region, e.g.
   `...-default-rtdb.asia-southeast1.firebasedatabase.app`).
2. Apply the Realtime Database security rules that restrict rooms/friends to
   signed-in (anonymous) users (shared earlier in this project's chat history).

## Credits

- **Author:** RC RAJA GAMER 2.0
- License: MIT (see `LICENSE`)


## Elite Coin (Minecraft 26.1.2)

WorldGate 26.1.2 now includes the Elite Coin client read model and shop screen. Coin balances, catalog data and item purchases are server-authoritative through the WorldGate backend. The included Elite Coin artwork is stored at `assets/worldgate/elite/elite-coin.png`. Real-money coin top-ups remain provider-neutral until a legitimate payment gateway is configured; the client never treats a payment intent as a completed purchase.


## Security hardening

The 26.1.2 release now includes a relay-side integrity gate, protocol versioning, payload limits, room expiry, per-IP connection throttling and automatic temporary blocking for repeated invalid handshakes. The client also locks WorldGate multiplayer features when the relay rejects the official artifact hash. This is defense in depth, not an unbreakable guarantee: a modified client can always alter local code, so the relay/backend remain the enforcement boundary.

The backend adds rate limiting, temporary IP bans, security-event logging, hardened response headers and a continuous watchdog. A scheduled GitHub Actions security workflow also runs dependency auditing, regression tests and credential-pattern checks.

For production integrity enforcement, configure the relay with `WORLDGATE_ALLOWED_MOD_SHA256` set to the SHA-256 of the exact official 26.1.2 jar. Never put a secret signing key in the mod.
