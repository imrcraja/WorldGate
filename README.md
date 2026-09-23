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

## Why this repo has more than one folder

A single mod jar cannot run on every Minecraft version — each release changes
internal class names, and 26.1+ switched to a completely different (unobfuscated,
Mojang-mapped) system than the 1.21.x/Yarn-mapped era before it. There is no
way around building **one module per target version**; anyone who claims
otherwise is not being straight with you. What *can* be shared is all the
non-Minecraft logic (Firebase, networking, room codes) — that lives once in
`common/` and every version module reuses it as-is.

```
WorldGate/
├── common/        <- Firebase client, RoomManager, mod init, ping-color logic
│                     (plain Java, zero Minecraft dependency, shared by all)
├── v26_1_2/       <- Minecraft 26.1.2, Mojang mappings   (the version you play)
└── v1_21_1/       <- Minecraft 1.21.1, Yarn mappings     (most-played version
                       across servers/YouTubers right now, ~21% share)
```

`gradle build` at the repo root builds **both** jars in one pass; the GitHub
Actions workflow does the same and uploads each as a separate artifact
(`WorldGate-26.1.2`, `WorldGate-1.21.1`).

### Adding another version (e.g. 1.20.1, 1.21.4)

The pattern is always the same:
1. Copy `v1_21_1/` to a new folder (e.g. `v1_20_1/`)
2. Update `gradle.properties` with that version's Minecraft/Yarn/Loader/Fabric
   API numbers (check https://fabricmc.net/develop for current ones)
3. Fix any class names in `WorldGateScreen.java` / the mixins that changed
   between versions (usually minor)
4. Add the folder name to the root `settings.gradle`

Tell me which version to add next and I'll build that module the same way.

## Status: WorldGate 26.1.2 integration

| Feature | Status |
|---|---|
| Mod skeleton and Escape-menu WorldGate button | Integrated |
| Firebase anonymous auth + Realtime Database | Integrated |
| Room creation/join bookkeeping | Integrated |
| Host-to-player relay bridge | Integrated |
| WebSocket relay service under `relay/` | Integrated and hardened |
| Friends, presence and realtime room state | Integrated |
| Realtime chat and emote state | Integrated |
| Elite profile and server-authoritative Elite badges | Integrated |
| Elite Coin wallet, catalog and shop | Integrated |
| Claim Center, daily/activity rewards and mailbox | Integrated |
| Elite Coin gifting by UID or friend-row selection | Integrated |
| Red-dot claim/mail notification refresh | Integrated |
| Real-money Elite Coin checkout | Coming Soon; no gateway is treated as a completed payment |
| Live skin/cape replacement and full body emote animation | Separate version-specific work remains |

The current ecosystem integration is intentionally scoped to Minecraft 26.1.2. The 1.21.1 module remains present and is not part of this integration pass.

## Building

Requires JDK 25 (for the 26.1.2 module) and JDK 21 (for the 1.21.1 module) —
the included GitHub Actions workflow installs both automatically, so pushing
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
