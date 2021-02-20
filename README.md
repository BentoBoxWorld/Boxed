# Boxed

A game mode where you are boxed into a tiny space that only expands by completing advancements.

** Warning this game mode is still in development and newer versions will not necessarily be compatible with older versions ***

## Required Plugin

Requires WorldGeneratorAPI plugin. [Download the correct one for your server here.](https://github.com/rutgerkok/WorldGeneratorApi/releases)

## Required Addons

* InvSwitcher - keeps advancements, inventory, etc. separate between worlds on a server
* Border - shows where the border is of the box and the maximum border

## How to install

### Quick Start
1. Place Boxed addon into the BentoBox addons folder along with InvSwitcher and Border.
2. Place the WorldGeneratorAPI plugin into your plugins folder.
3. Restart the server - new worlds will be created. There may be a delay.
4. Stop the server
5. Edit the Border addon's config.yml. It should have these settings:

```
# Use barrier blocks. If false, the border is indicated by particles only.
use-barrier-blocks: false
# 
# Default border behavior
show-by-default: true
# 
# Show max-protection range border. This is a visual border only and not a barrier.
show-max-border: true
```
6. Restart the server
7. Login
8. Type `/boxed` to start


* You will start by a tree.
* The only area you can operate on is your box that shows as a blue particle border.
* To make your box bigger, complete advancements.
* Check your progress with the Advancements screen, (L-key).
* Monsters can be hurt outside your box with weapons and potions.
* You can trade with villagers anywhere.
* Boats can be used anywhere.
* Items can be dropped and picked up anywhere.
* Workbenches can be used anywhere.

* The box owner can move the box using enderpearls thrown from within the box. Beware! It's a one-way trip.


