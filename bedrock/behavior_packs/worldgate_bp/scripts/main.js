import { world, system } from "@minecraft/server";
import { ActionFormData, MessageFormData } from "@minecraft/server-ui";

const MENU_TITLE = "WorldGate";
const SEEN = new Set();

function safe(fn) {
  try {
    return fn();
  } catch (error) {
    console.warn("[WorldGate] " + (error?.stack ?? error));
  }
}

function showMessage(player, title, body) {
  safe(() => new MessageFormData()
    .title(title)
    .body(body)
    .button1("OK")
    .button2("Close")
    .show(player));
}

function showSettings(player) {
  safe(async () => {
    const result = await new ActionFormData()
      .title("WorldGate Settings")
      .body("Bedrock client settings")
      .button("Open WorldGate on spawn: ON")
      .button("Notifications: ON")
      .button("Platform: Bedrock / Mobile")
      .button("Back")
      .show(player);

    if (result.canceled || result.selection === 3) {
      showWorldGate(player);
      return;
    }

    showMessage(
      player,
      "Settings",
      "These controls are local-safe settings. Account and network settings are managed by the WorldGate service."
    );
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
      .button("Close")
      .show(player);

    if (result.canceled) return;

    switch (result.selection) {
      case 0:
        showMessage(player, "Friends", "WorldGate friends are shared through the WorldGate bridge. Bedrock players are marked as mobile/Bedrock.");
        break;
      case 1:
        showMessage(player, "Wardrobe", "WorldGate wardrobe is connected to the shared profile layer. Cosmetic delivery is handled by the WorldGate service.");
        break;
      case 2:
        showMessage(player, "Notifications", "No new WorldGate notifications are available locally.");
        break;
      case 3:
        showMessage(player, "Elite", "Elite profile and coins are linked to the WorldGate account layer.");
        break;
      case 4:
        showMessage(player, "Profile", "Platform: Bedrock / Mobile\nPlayer: " + player.name);
        break;
      case 5:
        showSettings(player);
        break;
      default:
        break;
    }
  });
}

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
