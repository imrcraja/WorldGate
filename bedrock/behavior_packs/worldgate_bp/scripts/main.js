import { world, system } from "@minecraft/server";
import { ActionFormData, MessageFormData } from "@minecraft/server-ui";

const MENU_TITLE = "WorldGate";
const BACKEND_URL = "https://api.worldgate.ryzn.pro";
const SEEN = new Set();
let compatibility = null;

function safe(fn) {
  try { return fn(); }
  catch (error) { console.warn("[WorldGate] " + (error?.stack ?? error)); }
}

async function loadCompatibility() {
  try {
    const response = await fetch(BACKEND_URL + "/v1/public/compatibility");
    if (!response.ok) throw new Error("HTTP " + response.status);
    compatibility = await response.json();
  } catch (error) {
    console.warn("[WorldGate] compatibility service unavailable: " + error);
  }
}

function compatibilityText() {
  if (!compatibility) return "Compatibility service is temporarily unavailable. Local WorldGate features remain available.";
  return [
    "Java: " + compatibility.policy.java.targetRange,
    "Bedrock: " + compatibility.policy.bedrock.targetRange,
    "Java ↔ Bedrock: " + (compatibility.policy.crossPlay ? "supported through the bridge" : "unavailable"),
    "Automatic version negotiation: " + (compatibility.policy.automaticVersionNegotiation ? "enabled" : "disabled")
  ].join("\n");
}

function showMessage(player, title, body) {
  safe(() => new MessageFormData().title(title).body(body).button1("OK").button2("Close").show(player));
}

function showSettings(player) {
  safe(async () => {
    const result = await new ActionFormData()
      .title("WorldGate Settings")
      .body("Bedrock client settings")
      .button("Open WorldGate on spawn: ON")
      .button("Notifications: ON")
      .button("Compatibility")
      .button("Platform: Bedrock / Mobile")
      .button("Back")
      .show(player);

    if (result.canceled || result.selection === 4) {
      showWorldGate(player);
      return;
    }
    if (result.selection === 2) {
      showMessage(player, "Compatibility", compatibilityText());
      return;
    }
    showMessage(player, "Settings", "These controls are local-safe settings. Account and network settings are managed by the WorldGate service.");
  });
}

function showWorldGate(player) {
  safe(async () => {
    const result = await new ActionFormData()
      .title(MENU_TITLE)
      .body("WorldGate • Bedrock / Mobile")
      .button("Friends")
      .button("Wardrobe")
      .button("Notifications")
      .button("Elite")
      .button("Profile")
      .button("Settings")
      .button("Compatibility")
      .button("Close")
      .show(player);

    if (result.canceled) return;
    switch (result.selection) {
      case 0: showMessage(player, "Friends", "WorldGate friends are shared through the WorldGate bridge. Bedrock players are marked as mobile/Bedrock."); break;
      case 1: showMessage(player, "Wardrobe", "WorldGate wardrobe is connected to the shared profile layer. Cosmetic delivery is handled by the WorldGate service."); break;
      case 2: showMessage(player, "Notifications", "WorldGate notifications are synchronized through the service when an account session is available."); break;
      case 3: showMessage(player, "Elite", "Elite profile and coins are linked to the WorldGate account layer."); break;
      case 4: showMessage(player, "Profile", "Platform: Bedrock / Mobile\nPlayer: " + player.name); break;
      case 5: showSettings(player); break;
      case 6: showMessage(player, "Compatibility", compatibilityText()); break;
      default: break;
    }
  });
}

system.run(() => { loadCompatibility(); });

world.afterEvents.playerSpawn.subscribe((event) => {
  const player = event.player;
  if (!event.initialSpawn || !player?.isValid()) return;
  if (SEEN.has(player.id)) return;
  SEEN.add(player.id);
  system.runTimeout(() => {
    if (player.isValid()) showWorldGate(player);
  }, 20);
});

world.afterEvents.itemUse.subscribe((event) => {
  if (event.itemStack?.typeId !== "minecraft:compass") return;
  system.run(() => {
    if (event.source?.isValid()) showWorldGate(event.source);
  });
});
