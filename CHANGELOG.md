# Changelog

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
