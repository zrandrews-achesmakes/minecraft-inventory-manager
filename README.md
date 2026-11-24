# Inventory Manager (Fabric, Minecraft 26.3)

A Java Edition take on Mythicus' **Inventory Manager (Lite)** Bedrock add-on. It runs alongside the
Airships mod on the same Fabric server (Fabric Loader 0.20.1, Fabric API, Java 25).

## What it adds

### Sorter (item)
| Action | Result |
|---|---|
| Right-click **or** punch a chest, trapped/copper chest, barrel or shulker box | Sorts it (double chests are sorted as one) |
| Right-click or punch a donkey/mule/llama with a chest, a chest boat or a chest minecart | Sorts its chest |
| Right-click the air | Sorts your inventory (the hotbar is left alone by default) |
| Sneak + right-click the air | Switches mode: **Generic → Alphabetical → Color** |

Sorting merges partial stacks, then orders them:
- **Generic**: the game's own item order, so building blocks, tools, food, etc. end up grouped.
- **Alphabetical**: A–Z by displayed name (renamed items use their new name).
- **Color**: rainbow order (red → purple), then white/gray/black, then items with no clear color.

Unlike the Bedrock version, tools, potions, banners, beds and other unstackable items are sorted too.
Unopened loot chests get their loot generated first, just as if you opened them.

### Hot Deposit (block)
- **Punch it**: every item in your main inventory that a nearby chest/barrel/shulker box *already holds*
  goes into that container (nearest first). The hotbar is kept by default.
- **Right-click it**: shows the search range as a particle box.
- **Sneak + punch**: breaks it normally (axe is fastest).
- Range: 6 blocks in every direction (configurable).

New players get **1 Sorter + 3 Hot Deposit** the first time they join, as in the Bedrock add-on.

### Recipes
- Sorter: iron ingot over redstone over stick (vertical line).
- Hot Deposit: planks around a chest, with a hopper in the top middle.

## Config: `config/invmanager.json`
| Setting | Default | Meaning |
|---|---|---|
| `hotDepositRadius` | 6 | Blocks in each direction Hot Deposit searches |
| `hotDepositIncludesHotbar` | false | Also deposit from the hotbar |
| `sortIncludesHotbar` | false | Sorting your inventory also rearranges the hotbar |
| `giveStarterKit` | true | First-join kit |
| `punchCooldownTicks` | 8 | Delay between punches (holding attack doesn't spam) |

## Install
Put `inventory-manager-1.0.0.jar` in the server's `mods` folder **and** in each player's
`%appdata%\.minecraft\mods` (it adds an item and a block, so clients need it too, the same as Airships).

## Build & test
- `gradlew build`: compile and make the jar (`build/libs/`), runs the game tests too.
- `gradlew runGameTest`: 12 headless server tests (`src/gametest`): sorting modes, merging, double chests,
  the Sorter on blocks/donkeys/the air, mode cycling, Hot Deposit, and sneak-to-break.

## Known limits
- Chests on a flying airship are part of the ship entity, not real blocks, so the Sorter and Hot Deposit
  don't reach them while the ship is airborne.
