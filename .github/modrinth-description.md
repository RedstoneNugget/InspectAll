![InspectAll panel](https://raw.githubusercontent.com/RedstoneNugget/InspectAll/main/docs/panel.png)

**Press H and see everything Minecraft knows about the block, item or entity you are looking at.** Client-side only: no server mod needed, works in singleplayer and on any server. German and English.

## What it shows

- **Blocks:** block state and properties, surroundings, block entity NBT, readable summaries for known blocks (bee nests, spawners, signs, chests …)
- **Items:** all data components, also as a ready-to-use `/give` command
- **Entities:** full entity NBT, health, effects, tags and more
- **SNBT viewer** with syntax highlighting and collapsible sections
- **Coordinate markers:** click "Mark" on any position (`flower_pos`, `hive_pos`, `pos`, X/Y/Z …) and get an outline with a beam and distance that is visible through walls
- **Search** (Ctrl+F) through all lines, **copy** SNBT, **export** to JSON

![Marker through a wall](https://raw.githubusercontent.com/RedstoneNugget/InspectAll/main/docs/marker.png)

## Controls

| Key | Function |
|---|---|
| **H** | Open/close the info panel for the current target |
| **G** | Lock/unlock the target. A locked panel stays open as an overlay while you play. |
| **J** | Remove all markers |

Target order: the item under the mouse in inventories, otherwise the entity or block you look at (up to 64 blocks), then the item in your hand. All keys can be rebound.

## What is visible on servers

- **Singleplayer:** everything, read from the integrated server.
- **Multiplayer:** what the server sends to the client. Fields the server does not send (e.g. chest contents, bees in a nest) are shown as "not sent by the server".
- **Multiplayer with OP:** full NBT via the same vanilla query F3+I uses (can be turned off).
- **Items** are always complete.

## Settings

Panel position, width, opacity, all colours, visible categories, tooltip hint, overlay when locked, reach, max SNBT lines, OP query. Optional: [Mod Menu](https://modrinth.com/mod/modmenu).

## Requirements

[Fabric Loader](https://fabricmc.net/use/) ≥ 0.19 and [Fabric API](https://modrinth.com/mod/fabric-api) for Minecraft 1.21.11.

---

### Deutsch

Drück **H** und sieh alle Daten zu Blöcken, Items und Entities: Blockstate, Komponenten, Block-Entity- und Entity-NBT mit Hervorhebung, Koordinaten-Markierungen durch Wände, Suche, Kopieren und JSON-Export. Nur Client, kein Server-Mod nötig.

Source code and license (MIT): [GitHub](https://github.com/RedstoneNugget/InspectAll)
