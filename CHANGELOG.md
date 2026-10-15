# Changelog

## 1.3.7 for Minecraft 1.21.1

- Added Trinkets support on Fabric. Trinkets now go into the grave and return to their original slots instead of dropping on the ground. (breakinblocks/Graveless#1)
- Fixed curios kept on death, such as with the Curios `keepCurios` option, keeping a hidden Graveless marker that could stop them stacking.

## 1.3.6 for Minecraft 1.21.1

- Fixed graves storing negative xp and returning nothing for players above about 21,800 levels. (breakinblocks/Graveless#3)

## 1.3.5 for Minecraft 1.21.1

- Fixed soulbound items going into the grave instead of staying with the player on NeoForge. Graveless emptied the inventory as soon as the player died, so mods that keep soulbound gear by pulling it out of the death drops, such as Ars Elemental, found nothing to keep. The inventory is now collected from the death drops after other mods have taken what they keep, and each item still returns to its original slot.
- Fixed items being duplicated when a player was killed with a magnetic weapon, such as Irregular Implements' Magnetic enchantment. The magnet put the drops into the killer's inventory and the grave saved a second copy of them. Graveless now takes the death drops before lowest-priority magnets see them, and leaves out any drop that was already picked up.
- Items taken into a grave are now emptied out of the death drops, so a mod that spawns cancelled drops anyway can no longer duplicate them.
- A death whose drops never arrive is now saved to a grave at the end of the same tick instead of on respawn, before any respawn handler can copy items from the old inventory.

## 1.3.4 for Minecraft 1.21.1

- Fixed a grave's items and xp being lost when it was claimed just as the player died, such as clicking the ghost the moment a mob dealt the killing blow. The claim put everything into the dead player's inventory, which is thrown away on respawn. Claims, extractions and xp reclaims from a dead player are now ignored and the grave stays put.
- Admin restores and `/graveless restore` now refuse while the target player is dead instead of handing the grave to them.


## 1.3.3 for Minecraft 1.21.1

- Fixed a death losing every item when the drop event never arrived, such as a disconnect or crash between dying and the grave being written. The pending items are now saved on respawn or logout instead of discarded.


## 1.3.2 for Minecraft 1.21.1

- Fixed the Spirit Compass blocking full inventory recovery and displacing restored items from their original slots.
- Fixed restore failures discarding items still waiting in a grave.
- Fixed Accessories recovery bypassing keep-inventory rules, death callbacks, and explicit drop rules.
- Fixed Accessories fallback displacing items from their original slots and restored stacks exceeding slot limits.
- Fixed trusted players being unable to view and extract grave contents in the browser.
- Fixed ghosts persisting across dimension changes and respawns on Fabric.
- Fixed through-wall claiming when the line of sight requirement is disabled.
- Fixed Spirit Ward ending before its configured linger expires, including browser recovery, and removing potion effects.
- Fixed sneaking and clicking above the item grid extracting an item.


## 1.3.1 for Minecraft 1.21.1

- Backported the mod to Minecraft 1.21.1 (NeoForge 21.1 and Fabric).
- Added Accessories support on both loaders. Equipped accessories are stored in the grave and returned to
  their slots on claim, respecting each accessory's keep and destroy rules.
- Ghost and grave browser rendering were rebuilt on the 1.21.1 render APIs. The outline pass is replaced by
  a glow aura that stays visible through walls when the ghost is hidden behind terrain.

## 1.3.1

- Fixed clicking a ghost doing nothing. A bad cooldown check blocked every claim, sneak-to-open and extraction.

## 1.3.0

- Sneak and click a ghost to open that grave in the browser instead of claiming it.
- Sneak + left click a slot in the browser to take back a single stack. Owners and granted players need to be
  in claim range, operators do not.
- Added a button to reclaim a grave's stored xp without the items.

## 1.2.1

- Fixed grave claiming often taking several clicks. Clicks register with a block behind the ghost, and
  right-click claiming works while holding an item.
- Added a `show_beam` client setting to turn off the light beam above graves.

## 1.2.0

- Added `max_backups_per_player` to cap the per-death backup files under `world/graveless/<uuid>/`. Defaults to
  `-1`, which keeps every file as before.
- Added `/graveless prune player <player> [keep]` and `/graveless prune all [keep]` to trim an existing archive.
  Falls back to the configured limit and refuses to run while retention is unlimited.
- Added a game test suite: `./gradlew :neoforge:runGameTestServer`.

## 1.1.0

- Added Fabric support. Graveless now ships `graveless-neoforge-*` and `graveless-fabric-*`.
- Fabric needs Fabric API and Forge Config API Port. Configs use the same format and file names on both loaders,
  so an existing `graveless-server.toml` carries over. Curios is NeoForge only; Accessories is waiting on an
  Accessories build for 26.1.
- Spirit Ward is now off by default. Existing configs are untouched; set `protection_enabled = true` to
  turn it back on.

## 1.0.0

Initial release.
