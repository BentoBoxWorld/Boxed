# Boxed

A game mode where you are boxed into a tiny space that only expands by completing advancements.

## BentoBox Requirements

* Requires BentoBox 1.23.0 or later (Snapshots can be downloaded here: [https://ci.bentobox.world](https://ci.bentobox.world))
* InvSwitcher - keeps advancements, inventory, etc. separate between worlds on a server.
* Border - shows the box

### Warning!!
Boxed requires **a lot of RAM** and can take up to **10 minutes** to boot up for the first time as it pre-generates the worlds. After the initial start, it will start up much quicker. With 12GB of RAM running on a fast ARM-based system, it takes ~ 8 minutes for the first boot. If you do not have enough RAM then weird things will happen to you server including strange errors about chunks and things like that. To dedicate enough RAM to your JVM, use the correct flags during startup. Here is my `start.sh` for running on Paper 1.19.4:
```
#!/bin/sh
java -Xms12G -Xmx12G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true  -jar paper-1.19.4.jar nogui
```

## How to install

### Quick Start

1. Place Boxed addon into the BentoBox addons folder along with InvSwitcher and Border (use the latest versions!).
2. Place the WorldGeneratorAPI and WorldBorderAPI plugins into your plugins folder.
3. Make sure you are using BentoBox 1.16.0-SNAPSHOT or later.
4. Restart the server - new worlds will be created. There may be a delay.
5. Login
6. Type `/boxed` to start.
7. Turn off advancement announcements `/gamerule announceAdvancements false` otherwise there is a lot of spam from the server when players get advancements.


* You will start by a tree. The is a chest with some handy items in it. (This is the island blueprint)
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

Each player will have a land of their own to explore up to the limit of the island distance value. The default is 400, so the land will be 800 x 800 blocks. The land is semi-random, but each player will get roughly the same layout (see the biomes config). Structures such as villages, broken nether gates, shipwrecks, etc. are random and so some players may get them, others not. In a future version, switching off structures will be a config option. Strongholds are switched off and do not exist. Each player's land is surrounded by seas of different temperatures. If the border is not solid, then players can theoretically explore other lands.

*World Seed*
The world seed is what it is used to generate the lands. I recommend keeping this value. If you change it the land may be very different.

### Blueprint

There is one blueprint "island" that is used to generate the tree, chest and blocks below down to y = 5. The default height of the surface is about y = 65, so the blueprint has to be about 60 blocks tall. If you make any good blueprints, please share them!

### advancements.yml
This file contains all the advancements and how much your box should grow if you get one. The file can contain custom advancements if you have them. The default is for most recipe advancements to give nothing.

Example:
```
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


## Custom Advancements
To find out how to add custom advacements to your server, watch the tutorial video [here](https://www.youtube.com/watch?v=zNzQvIbweQs)! 


