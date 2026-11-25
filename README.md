# Boxed

<!-- BentoBox stats badges: https://download.bentobox.world/admin (Badges tab) -->
[![Servers](https://download.bentobox.world/badge/Boxed/servers.svg)](https://download.bentobox.world/) [![Popularity](https://download.bentobox.world/badge/Boxed/rank.svg)](https://download.bentobox.world/) [![Downloads](https://download.bentobox.world/badge/Boxed/downloads.svg)](https://download.bentobox.world/) [![Minecraft](https://download.bentobox.world/badge/Boxed/mc.svg)](https://download.bentobox.world/)

A game mode where you are boxed into a tiny space that only expands by completing advancements.

## BentoBox Requirements

* Requires BentoBox 3.25.0 or later (Snapshots can be downloaded here: [https://ci.bentobox.world](https://ci.bentobox.world)). BentoBox 3.16.1+ is needed for native region-file cleanup of deleted boxes — see [Reclaiming Disk Space](#reclaiming-disk-space-deleted-island-chunks).
* InvSwitcher - keeps advancements, inventory, etc. separate between worlds on a server.
* Border - shows the box

### First start
The first time Boxed starts it builds its worlds from a hidden seed world and saves the result as template files in `plugins/BentoBox/addons/Boxed/areas/`. This takes around 30 seconds on a modern machine and happens only once. After that the seed world is not loaded at all and Boxed starts in a few seconds. No special amount of RAM is needed.

## How to install

### Quick Start

1. Place Boxed addon into the BentoBox addons folder along with InvSwitcher and Border (use the latest versions!).
2. (Optional) Install the Datapack for custom advancements - https://github.com/BentoBoxWorld/BoxedDataPack/
3. Restart the server - new worlds will be created. The first start takes a little longer.
4. Login.
5. Type `/boxed` to start.
6. Turn off advancement announcements with `/gamerule announceAdvancements false` to avoid spam, or set `boxed.broadcast-advancements: true` in `config.yml` to have Boxed broadcast them instead.


* You will start next to a tree. There is a chest with some handy items in it. (This is the island blueprint)
* The only area you can operate on is your box that shows as a border.
* To make your box bigger, complete advancements.
* Check your progress with the Advancements screen, (L-key).
* Monsters do not spawn by default outside your box, but your box becomes bigger, and it only takes one block to spawn a mob!
* The box owner can move the box using enderpearls thrown from within the box. Beware! It's a one-way trip.
* The island settings have an option to allow box moving by other ranks (look for the Composter box icon)

### Other comments
* Visitors are not generally protected.
* You can trade with villagers anywhere.
* Boats can be used anywhere.
* Items can be dropped and picked up anywhere.
* Workbenches can be used anywhere.

## Advanced Config

### config.yml
The config is very similar to BSkyBlock, AcidIsland, etc.

Each player will have a land of their own to explore up to the limit of the island distance value. The default is 320, so the land will be 640 x 640 blocks. The land is semi-random, but each player will get roughly the same layout (see the biomes config). Structures such as villages, broken nether gates, shipwrecks, etc. are random and so some players may get them, others not. Strongholds are switched off and do not exist. Each player's land is surrounded by seas of different temperatures. If the border is not solid, then players can theoretically explore other lands.

*World Seed*
The world seed is used to generate the lands. It is recommended to keep this value. If you change it the land may be very different. Note that changing the seed mid-game requires a full reset of your databases and worlds.

Want to use your own terrain or a custom-built map instead? See [Using a Custom Map as the Seed](#using-a-custom-map-as-the-seed).

*Key Boxed-specific settings:*

| Setting | Default | Description |
|---------|---------|-------------|
| `boxed.ignore-advancements` | `false` | If `true`, advancements will not change the size of the box. |
| `boxed.broadcast-advancements` | `false` | If `true`, Boxed will broadcast new advancements. Recommended: set the game rule `/gamerule announceAdvancements false` and use this setting instead. |
| `boxed.deny-visitor-advancements` | `true` | If `true`, visitors cannot earn advancements. Note: visitors will still receive other rewards such as experience. |
| `world.allow-structures` | `false` | Allow vanilla structures to generate in the seed world. |

### Areas (areas.yml)

An *area* is a kind of land that a box can be made in. Every box in the same area has exactly the same land; boxes in different areas have different land, biomes and structures. Out of the box there is one area called `default`.

To add an area, run `/boxadmin areas create <name> [seed]` and restart the server. The command adds the area to `areas.yml`, gives it a biomes file (a copy of `biomes.yml` to edit) and an empty structures file, and makes a blueprint bundle with the same name. Players who pick that bundle get a box in that area; bundles that do not match an area use the default area. The land of the new area is built once at the restart (about 20 seconds) and saved in `areas/<name>/`.

You can also do the same by hand: add the area to `areas.yml` and make a blueprint bundle whose unique id is the area's name.

| Command | What it does |
|---------|--------------|
| `/boxadmin areas` | Lists the areas with their row, seed and number of boxes. |
| `/boxadmin areas tp <area>` | Takes you to the land where the area's next box will be, so you can look at it and place structures before any player has a box there. |
| `/boxadmin areas create <name> [seed]` | Makes a new area and its blueprint bundle. Needs a restart. |
| `/boxadmin areas rebuild <area> [regenerate]` | Builds the area's land again at the next restart. With `regenerate` the seed worlds are thrown away first, which is needed after changing the area's seed or biomes. Without it, the land is rebuilt from the seed worlds as they are, which keeps changes made to them by hand. |
| `/boxadmin areas clean [x z]` | Puts the land of the box position you are in (or the one at x, z) back the way it was. Use it after trying out structures in a position nobody has a box in. Positions with a box in use are refused. |
| `/boxadmin areas check <area>` | Plays out how a box grows in the area and reports where players get stuck. See [Checking that an area can be played](#checking-that-an-area-can-be-played). |

Areas can be chained so that a player's box in one area has to reach a certain size before the next area is unlocked, and can be closed to new boxes:

| Setting | Description |
|---------|-------------|
| `requires` | The area that unlocks this one. Leave out for an area anyone can start in. |
| `unlocks-next-at` | The size a player's box in this area must reach to unlock the areas that require it. Set it per area to make some harder than others. 0 (the default) means having a box there is enough. |
| `enabled` | Set to `false` to stop new boxes being made in the area. Existing boxes are not affected. |

A player can have one box in each area. They need to be allowed more than one box (`world.concurrent-area` in `config.yml`) to have boxes in more than one area at a time. Boxed asks InvSwitcher to give each box its own advancements and inventory, and asks Border to keep players inside their box, so you do not have to set those up. Both addons list what was asked for in the console at startup, and each has a `game-mode-requests` section in its config where you can refuse.

```yaml
areas:
  default:
    biomes: biomes.yml
    structures: structures.yml
    unlocks-next-at: 100
  icelands:
    seed: 1234567890
    biomes: biomes-icelands.yml
    structures: structures-icelands.yml
    requires: default
```

Each area owns one east-west row of the world, recorded in `areas/grid.yml`, and its boxes are placed along that row. `/boxadmin place` saves structures to the structures file of the area you are standing in. Requires BentoBox 3.25.0 or later.

Notes:
* Do not remove an area that has boxes in it, and do not edit `areas/grid.yml`.
* Changing an area's seed or biomes after it is built does nothing until you run `/boxadmin areas rebuild <area> regenerate` and restart.
* A row that no area owns has the default area's land. If players have already seen the edge of such a row, those chunks keep the default land after a new area is given that row.

### Blueprint

There is one blueprint "island" that is used to generate the tree, chest and blocks below down to y = 5. The default height of the surface is about y = 65, so the blueprint has to be about 60 blocks tall. If you make any good blueprints, please share them!

### advancements.yml
This file contains all the advancements and how much your box should grow if you get one. The file can contain custom advancements if you have them.

There are settings at the top of the file:

| Setting | Default | Description |
|---------|---------|-------------|
| `settings.default-root-increase` | `0` | Score applied when a root (tab-opening) advancement is earned. Typically left at 0 to avoid rewarding players simply for unlocking a new tab. |
| `settings.unknown-advancement-increase` | `1` | Default box increase for any advancement not listed in this file. Useful for custom advancements added via a data pack — you don't need to list every new advancement manually. |
| `settings.unknown-recipe-increase` | `0` | Default box increase for recipe advancements not listed in this file. |
| `settings.automatic-scoring` | `true` | If `true`, uses a proprietary algorithm to automatically score advancements. If `false`, each advancement must be scored manually. |

Example:
```yaml
# Lists how many blocks the box will increase when advancement occurs
settings:
  default-root-increase: 0
  unknown-advancement-increase: 1
  unknown-recipe-increase: 0
  automatic-scoring: true
advancements:
  'minecraft:adventure/adventuring_time': 1
  'minecraft:adventure/arbalistic': 1
  'minecraft:adventure/bullseye': 1
  'minecraft:adventure/hero_of_the_village': 1
  'minecraft:adventure/honey_block_slide': 1
  'minecraft:adventure/kill_a_mob': 1
  ...
```
  
### Checking that an area can be played

`/boxadmin areas check <area>` answers the question "can a player in this area actually grow their box?". It looks at the area's land, the starter blueprint and the area's structures file, then plays the game out on paper: starting from a new box, it earns every advancement whose needs are inside the box, grows the box by their scores, and repeats until nothing more can be earned.

The command prints one line, for example:

```
The box stops growing at size 263 of 320. 48 advancements earned for 262 points, 1 out of reach, 48 impossible (276 points).
```

and writes the full report to `areas/<area>/reachability.txt`. The report lists:

* the advancements earned in each round and the box size at the time,
* the advancements that were still out of reach when the box stopped growing, with what was missing and the box size at which it first appears,
* the advancements that are impossible in the area, with the reason,
* the box size at which each biome, mob and structure first comes into the box.

What each advancement needs is set in `requirements.yml`, which is written to the Boxed folder the first time you run the command. It is a model written by hand: it lists the raw resources an advancement comes down to (blocks, biomes, mobs, structures, other advancements), not every crafting step, and some entries are simplified. Mobs cannot be seen in the land itself, so the file also says which biomes and structures bring which mobs. Correct it to suit your server and your data pack. The check takes around half a minute and does not slow the server down.

An advancement that scores points but can never be earned is worth either removing from `advancements.yml` or making possible, for example by placing a structure with `/boxadmin place`.


### biomes.yml
The player's land has biomes and they are defined here. It's not possible to define where the biomes are right now, only what affect they have on the terrain.

* height: the default height is 8. Lower numbers will produce lower land, higher higher land.
* scale: this is how smooth the land will be. Smaller numbers are more jagged, larger numbers are flatter.

Setting ocean biomes to higher height numbers will result in the ocean floor being above the sea level and creating land.

A lot of these numbers are rough guesses right now and if you come up with better values, please share them!

### structures.yml
This file records which Minecraft structures should be placed in each new player's box when their area is first created. Structures are stored relative to the island center and are placed in overworld (`normal`) and nether sections.

Admins can place structures in-game using the `/boxadmin place` command:

```
/boxadmin place <structure> [x y z] [ROTATION] [MIRROR] [NO_MOBS]
```

| Argument | Description |
|----------|-------------|
| `<structure>` | Minecraft structure name (tab-complete to see available structures) |
| `[x y z]` | Coordinates where the structure should be placed. Use `~` for the current position. For `y` you can also use `ground` to sit the structure on the highest solid block, ignoring water and trees (the sea bed at sea), or `surface` for the top of whatever is there (the water surface at sea). Add an offset to sink or raise it, for example `ground-1` or `surface+3`. |
| `[ROTATION]` | Optional rotation: `NONE`, `CLOCKWISE_90`, `CLOCKWISE_180`, `COUNTERCLOCKWISE_90` |
| `[MIRROR]` | Optional mirror: `NONE`, `LEFT_RIGHT`, `FRONT_BACK` |
| `[NO_MOBS]` | Optional flag to suppress mob spawning from this structure |

To undo the last placed structure: `/boxadmin place undo`

The structure is saved to the structures file of the area you are in and will be placed in all future boxes of that area. You do not need to be in a box: use `/boxadmin areas tp <area>` to go to an area's land before anyone has a box there. Operators, and players with the Border addon's `boxed.border.bypass` permission, can walk around outside a box; other admins are put in spectator mode.


## Using a Custom Map as the Seed

Boxed does **not** have an "import map" button, but because of how it works you can still get your own custom terrain into player boxes. This section explains how.

### How seeding actually works

Boxed uses two worlds:

1. A hidden **seed world** — `<worldname>/seed` (overworld) and `<worldname>/seed_nether` (nether). This is a normal world generated from the numeric world seed.
2. The **game world** — `<worldname>` and `<worldname>_nether` — that players actually play in.

The **first time** the server starts, Boxed reads the chunks around the centre of the seed world and saves them, with their biomes, as template files: `plugins/BentoBox/addons/Boxed/areas/default/normal.bxt` and `nether.bxt`. Each player's box is then served a copy of that terrain from the template. The seed world is unloaded once the templates are built and is not loaded again.

Two facts make custom maps possible:

* The templates are built from the seed world **on disk**, so anything you change in the seed world can be turned into new templates.
* The templates **copy the seed world's biomes as-is**. There is no biome remapping, so whatever biomes are in the seed world are what players get.

**To rebuild the templates** after changing the seed world, run `/boxadmin areas rebuild default` and restart the server (or stop the server, delete the `.bxt` files and start it again). The seed world's saved chunks are read again and anything not yet generated is generated.

Two limits to keep in mind:

* The seed-world centre is fixed at **x = 0, z = 0** (y ≈ 64). Your custom content must be built around 0,0.
* Only a square of radius **island distance** (the `world.island-distance` value, default 320 → a 640 × 640 area) around 0,0 is copied. Anything outside that is just the surrounding seas and is never used.
* Once a game-world chunk has been generated and saved to disk, it is loaded from disk and is **no longer** taken from the seed world. So changing the seed world only affects boxes/areas that have **not yet been generated**. To push changes into already-explored areas you must regenerate those game-world chunks (see methods below).

> **Always back up your worlds before trying any of this.** These are unsupported, manual techniques.

### Method 1 — Match an existing world's seed (easiest)

If you just want the same *terrain* as a world you already like, set Boxed's seed to that world's numeric seed.

1. Find the seed of the world you like (`/seed` in that world).
2. In `config.yml` set:
   ```yaml
   world:
     generator:
       seed: <that-number>
   ```
3. Optionally enable vanilla structures with `world.allow-structures: true`.
4. Start with **fresh** Boxed worlds (the seed cannot be changed mid-game — delete the Boxed worlds and database, or set this up before first boot).

Notes: this reproduces vanilla terrain for that seed. Boxed still applies its own biome overlay (`biomes.yml`) to the box area, and structures only appear if `world.allow-structures` is enabled. This does **not** let you import hand-built creations — for that use Method 2 or 3.

### Method 2 — Edit the seed world by hand

You can go into the seed world and build, paste, or remove whatever you like.

1. Use a world-management plugin (e.g. Multiverse) to load and teleport into `<worldname>/seed`. It is not loaded by default.
2. Build or WorldEdit/paste your changes **around 0,0**, within the island-distance radius.
3. Stop the server, delete the `.bxt` template files (see above) and start it again. Boxed rebuilds the templates from the seed world, so your edits appear in any box that is generated **after** the restart.

To apply your edits to boxes/areas that already exist, you must regenerate those game-world chunks while leaving the seed world untouched — for example by deleting the relevant region files in `<worldname>` / `<worldname>_nether`, or by resetting/deleting the affected islands so BentoBox reaps their region files (see [Reclaiming Disk Space](#reclaiming-disk-space-deleted-island-chunks)). When the chunks regenerate they are re-copied from the seed world, which is the source of truth.

### Method 3 — Drop in your own world as the seed

This replaces the generated seed world with a world you built or downloaded.

1. Build or obtain a normal (vanilla) Minecraft world. Arrange the terrain you want **centred on 0,0**, covering at least the island-distance radius (default 320 blocks in every direction from 0,0).
2. Stop the server.
3. Copy your world's region files into Boxed's seed world folders, replacing what's there:
   * Overworld: your `region/` → `<worldname>/seed/region/`
   * Nether: your `DIM-1/region/` → `<worldname>/seed_nether/region/`
4. Delete the game-world folders and the `.bxt` template files so they are rebuilt from your new seed world on next boot:
   * `<worldname>` and `<worldname>_nether`
   * `plugins/BentoBox/addons/Boxed/areas/default/*.bxt`
   * Leave the seed worlds (`<worldname>/seed`, `<worldname>/seed_nether`) in place.
5. Start the server. Boxed copies your imported chunks — terrain **and biomes** — into the freshly generated game world.

Biomes: because the game world copies the seed world's biomes unchanged, your imported biomes carry over automatically. The `biomes.yml` overlay only affects chunks that Boxed *generates*; it does not touch the pre-existing chunks you dropped in, so your map's biomes are preserved as-is.

### Tips and gotchas

* The copy radius is `world.island-distance`. If you increase it, your custom content must cover the larger area.
* BentoBox's native region cleanup never touches the seed worlds, so your custom terrain is safe. If you run a third-party chunk-cleaner like Regionerator, exempt the seed worlds (see [Reclaiming Disk Space](#reclaiming-disk-space-deleted-island-chunks)) or your custom seed terrain may be deleted and boxes will stop matching.
* All boxes share the same captured terrain, so every player gets the same custom map layout.


## Flags

Boxed registers two flags unique to this gamemode.

### ALLOW_MOVE_BOX (World Setting)
Controls whether box-moving via ender pearl is enabled at all in this world. This is a world-level toggle visible in the BentoBox admin settings.

* **Type:** World Setting
* **Default:** Enabled

### MOVE_BOX (Protection Flag)
Controls which rank of island member is allowed to move the box by throwing ender pearls from within it. Only shown and active when `ALLOW_MOVE_BOX` is enabled.

* **Type:** Protection (Island Setting)
* **Default:** Owner only
* **Icon:** Composter

Players can find this setting under `/box settings` (look for the Composter icon).


## Placeholders

The following PlaceholderAPI placeholders are registered by Boxed:

| Placeholder | Description |
|-------------|-------------|
| `%boxed_island_advancements%` | The number of advancements earned by the player's island (based on the player's island membership). |
| `%boxed_visited_island_advancements%` | The number of advancements earned by the island the player is currently standing on. |


## Custom Advancements
To find out how to add custom advancements to your server, watch the tutorial video [here](https://www.youtube.com/watch?v=zNzQvIbweQs)!

Download the official [Boxed DataPack](https://github.com/BentoBoxWorld/BoxedDataPack) for extra custom advancements.


## Reclaiming Disk Space (Deleted Island Chunks)

**As of BentoBox 3.16.1, BentoBox reclaims disk space natively — you no longer need a third-party plugin for this.** (The region-file housekeeping sweep was added in 3.15.0; admin deletes were routed through it in 3.16.1.)

### Boxed cleans up its own boxes

When a box is deleted or reset, Boxed puts its land back the way it was: every chunk of the box's position that has been generated, in the overworld and the Nether, is compared with the area's template and anything different is restored. Structures, mobs and dropped items are removed too. This is done a little each tick and takes around a minute for a well-explored box. When it finishes, the console says so and the position is free for a new box, which gets its structures placed afresh. Boxes waiting to be cleaned when the server stops are picked up when it starts again.

This does not depend on the region-file housekeeping described below, which Boxed worlds rarely benefit from: neighbouring box positions share region files, so a region can almost never be deleted while any neighbour is in use. The housekeeping is still safe to leave on.

### How BentoBox housekeeping works

When an island is deleted or reset, BentoBox does **not** wipe blocks immediately. Instead it *soft-deletes* the island: the owner is cleared, the box is locked, and the island is flagged `deletable` in the database. The actual world data is reclaimed later by deleting the `.mca` region files from disk — far cheaper than regenerating chunks block-by-block.

A background **housekeeping sweep** does the reaping automatically. It is on by default:

| Setting (BentoBox `config.yml`) | Default | Description |
|---------|---------|-------------|
| `island.deletion.housekeeping.deleted-sweep.enabled` | `true` | Reaps region files for islands BentoBox itself soft-deleted (e.g. `/box reset`, admin delete). Never touches active or unvisited islands. |
| `island.deletion.housekeeping.deleted-sweep.interval-hours` | `24` | How often the deleted-island sweep runs. |
| `island.deletion.housekeeping.age-sweep.enabled` | `false` | Opt-in: also reaps region files older than `min-age-days`, even if never reset. Use to reclaim abandoned boxes. |
| `island.deletion.housekeeping.age-sweep.min-age-days` | `60` | Minimum age before the age-based sweep will reap a region. |

A region is only reaped if **every** island overlapping it is deletable — a single active neighbour protects the whole region. To reap immediately instead of waiting for the next sweep, run:

```
/boxadmin purge deleted
```

### Your seed worlds are safe

The sweep only scans gamemode island worlds and only the region files belonging to **deletable islands**. Boxed's seed worlds (`boxed_world/seed`, `boxed_world/seed_nether`) contain no islands, so they are never scanned and never reaped — your captured seed terrain is left intact. No exemption configuration is required.

> The old advice to set `deletion.keep-previous-island-on-reset` no longer applies — that setting is ignored in current BentoBox; deletion is handled by the housekeeping sweep described above.

### Regionerator (legacy / optional)

You can still run [Regionerator](https://github.com/Jikoo/Regionerator) if you prefer, but it is no longer necessary for Boxed. If you do use it, exempt the seed worlds so it doesn't delete the terrain the templates are built from. Add these entries to the `worlds` section of the Regionerator config:

```yaml
worlds:
  boxed_world/seed:
    days-till-flag-expires: -1
  boxed_world/seed_nether:
    days-till-flag-expires: -1
  default:
    days-till-flag-expires: 0
```


