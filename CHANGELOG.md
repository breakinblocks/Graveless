# Changelog

## 1.1.0

Added Fabric support. Graveless now ships two jars, `graveless-neoforge-*` and `graveless-fabric-*`.

The Fabric build needs Fabric API and Forge Config API Port. Config files use the same format and the same
file names on both loaders, so an existing `graveless-server.toml` carries over unchanged. Curios support is
NeoForge only for now; Accessories support on Fabric is waiting on an Accessories build for 26.1.

Spirit Ward is now off by default. Existing `graveless-server.toml` files keep whatever they already have, so
this only affects fresh installs; set `protection_enabled = true` to turn it back on.

## 1.0.0

Initial release.
