# Boxed

A game mode where you are boxed into a tiny space that only expands by completing advancements.

## BentoBox Requirements

* Requires BentoBox 1.23.0 or later (Snapshots can be downloaded here: [https://ci.bentobox.world](https://ci.bentobox.world))
* InvSwitcher - keeps advancements, inventory, etc. separate between worlds on a server.
* Border - shows the box

### Warning!!
Boxed requires **a lot of RAM** and can take up to **10 minutes** to boot up for the first time as it pre-generates the worlds. After the initial start, it will start up much quicker. With 12GB of RAM running on a fast ARM-based system, it takes ~ 8 minutes for the first boot. If you do not have enough RAM then weird things will happen to your server including strange errors about chunks and things like that. To dedicate enough RAM to your JVM, use the correct flags during startup. Here is my `start.sh` for running on Paper 1.19.4:
```
#!/bin/sh
java -Xms12G -Xmx12G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true  -jar paper-1.19.4.jar nogui
```

## How to install

### Quick Start

1. Place Boxed addon into the BentoBox addons folder along with InvSwitcher and Border (use the latest versions!).
2. (Optional) Install the Datapack for custom advancements - https://github.com/BentoBoxWorld/BoxedDataPack/
3. Restart the server - new worlds will be created. This will take a while!
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

*Key Boxed-specific settings:*

| Setting | Default | Description |
|---------|---------|-------------|
| `boxed.ignore-advancements` | `false` | If `true`, advancements will not change the size of the box. |
| `boxed.broadcast-advancements` | `false` | If `true`, Boxed will broadcast new advancements. Recommended: set the game rule `/gamerule announceAdvancements false` and use this setting instead. |
| `boxed.deny-visitor-advancements` | `true` | If `true`, visitors cannot earn advancements. Note: visitors will still receive other rewards such as experience. |
| `world.allow-structures` | `false` | Allow vanilla structures to generate in the seed world. |

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
| `[x y z]` | Coordinates where the structure should be placed. Use `~` for the current position. |
| `[ROTATION]` | Optional rotation: `NONE`, `CLOCKWISE_90`, `CLOCKWISE_180`, `COUNTERCLOCKWISE_90` |
| `[MIRROR]` | Optional mirror: `NONE`, `LEFT_RIGHT`, `FRONT_BACK` |
| `[NO_MOBS]` | Optional flag to suppress mob spawning from this structure |

To undo the last placed structure: `/boxadmin place undo`

When a structure is placed via this command while standing in a player box, it is automatically saved to `structures.yml` and will be placed in all future boxes.


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


## Using Regionerator

*Note: This plugin is designed to delete unused regions of your world! Make sure you take backups if you use it! Use at your own risk!*

[Regionerator](https://github.com/Jikoo/Regionerator) is a plugin that gradually deletes unused chunks to keep world sizes low. It supports BentoBox and respects box boundaries. It can be used to delete box chunks so that they can be regenerated. As Boxed uses seed worlds to copy from, these can appear to be unused by Regionerator and deleted, which means that startup becomes very slow. To avoid this, set the seed worlds as exempt from its deletions by adding these entries to the `worlds` section of the Regionerator config file:

```yaml
worlds:
  boxed_world/seed_base:
    days-till-flag-expires: -1
  boxed_world/seed:
    days-till-flag-expires: -1
  default:
    days-till-flag-expires: 0
```

To get the most out of Regionerator, change the BentoBox `config.yml` to *not* delete chunks when an island is removed. This leaves deletion up to Regionerator and it will clean up the chunks if the unused area is large enough. Set `keep-previous-island-on-reset: true`:

```yaml
deletion:
    keep-previous-island-on-reset: true
```


