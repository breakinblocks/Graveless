# Changelog

## 1.2.1

Fixed grave claiming often taking several clicks. Clicks aimed at a ghost now register even when a block is
behind it, and right-click claiming works while holding an item.

Added a `show_beam` client setting to turn off the vertical light beam above graves.

## 1.2.0

Added a retention policy for the per-death backup files under `world/graveless/<uuid>/`. The new server setting
`max_backups_per_player` defaults to `-1`, which keeps every file exactly as before. Set it to a number and the
oldest files are deleted as soon as a new death pushes a player over the limit.

Added `/graveless prune player <player> [keep]` and `/graveless prune all [keep]` for operators, so an existing
archive can be trimmed without waiting for the next death. With no keep count the command uses the configured
limit and refuses to run while retention is unlimited.

Added a game test suite covering capture, restore, claiming, ghost sync, Spirit Ward, the spirit compass, the
admin actions, the backup archive and the commands. Run it with `./gradlew :neoforge:runGameTestServer`.

## 1.1.0

Added Fabric support. Graveless now ships two jars, `graveless-neoforge-*` and `graveless-fabric-*`.

The Fabric build needs Fabric API and Forge Config API Port. Config files use the same format and the same
file names on both loaders, so an existing `graveless-server.toml` carries over unchanged. Curios support is
NeoForge only for now; Accessories support on Fabric is waiting on an Accessories build for 26.1.

Spirit Ward is now off by default. Existing `graveless-server.toml` files keep whatever they already have, so
this only affects fresh installs; set `protection_enabled = true` to turn it back on.

## 1.0.0

Initial release.
