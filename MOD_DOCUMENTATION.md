# Create: Logic Link Peripheral — Mod Documentation

## Overview

**Create: Logic Link Peripheral** is a Minecraft mod that bridges **Create**'s logistics system with **CC:Tweaked** (ComputerCraft). It provides programmable peripheral blocks that allow ComputerCraft computers to monitor inventory, read machine data, and request items from Create's logistics network — all through Lua scripts.

- **Mod ID:** `logiclink`
- **Package:** `com.apocscode.logiclink`
- **Minecraft:** 1.21.1
- **Mod Loader:** NeoForge (21.1.77 build / 21.1.217 runtime)
- **Gradle:** 9.2.1 with ModDevGradle 2.0.140
- **Java:** 21
- **GitHub:** https://github.com/Apocscode/CreateLogicLink

### Required Mods

| Mod | Version |
|-----|---------|
| Create | 6.0.8 |
| CC:Tweaked | 1.117.0 |
| Create Advanced Logistics | 0.3.0 |
| Create CC Logistics | 0.3.6 |
| Create Unlimited Logistics | 1.2.1 |
| Create Factory Abstractions | 1.4.8 (runtime) |

### Modpack

- **All The Mods 10 (ATM10)** — NeoForge

---

## Blocks

### Logic Link

A peripheral block that connects to Create's logistics network. Place it, right-click a Stock Link to copy the network frequency, then place the Logic Link next to a CC:Tweaked computer. The computer sees it as a `logiclink` peripheral.

- **Appearance:** Full cube, andesite casing variant (polished andesite top, andesite casing sides/bottom), netherite block sound
- **Map Color:** Emerald green (MapColor.EMERALD)
- **Placement:** Horizontal directional (4 faces: N/S/E/W)
- **Linking:** Right-click a Stock Link (or any linked Logic Link/Sensor) with the item to copy the frequency. Right-click air to clear.
- **Enchantment glow:** Item glows purple when tuned to a network

### Logic Sensor

A thin wireless sensor block that attaches flat to any surface (floor, wall, ceiling) like Create's Stock Link. It reads data from the machine it's attached to (inventory, fluids, kinetic speed/stress) and makes that data available wirelessly or via direct wired connection.

- **Appearance:** Thin pad (14×14×5 pixels), smooth stone top, polished andesite sides, andesite casing bottom
- **Map Color:** Cyan (MapColor.COLOR_CYAN), copper grate sound
- **Base Class:** `FaceAttachedHorizontalDirectionalBlock` — uses FACE (floor/wall/ceiling) + FACING (horizontal direction) blockstate properties, identical to Create's PackagerLinkBlock/Stock Link
- **Placement:** Attaches flat to any surface — floor, wall, or ceiling. No support block required (canSurvive always returns true).
- **Linking:** Same frequency system as Logic Link — right-click a Stock Link to copy frequency
- **Enchantment glow:** Item glows purple when tuned
- **Data refresh:** Every 20 ticks (1 second), reads the target block and caches the result

---

## Network Highlight System

When a player holds a tuned Logic Link or Logic Sensor item, all blocks on the same logistics network are highlighted with outlines matching Create's visual style.

### How It Works

1. **Server tick** (`LogicLink.onPlayerTick`): Every 5 ticks, checks if the player holds a tuned item in either hand. Extracts the frequency UUID.
2. **Position gathering** (`LinkNetwork.sendNetworkHighlight`): Collects positions from three sources:
   - Our Logic Link blocks (LinkNetwork registry)
   - Our Logic Sensor blocks (SensorNetwork registry)
   - **Create's logistics blocks** via `LogisticallyLinkedBehaviour.getAllPresent(freq)` — Stock Links, Packager Links, etc.
3. **Network packet** (`NetworkHighlightPayload`): Sends all positions to the client
4. **Client rendering** (`NetworkHighlightRenderer`): Draws outlines around each block

### Visual Style (Matches Create)

- **Colors:** Alternating blue — `0x708DAD` and `0x90ADCD`, switching every ~8 game ticks
- **Shape:** Renders the actual block VoxelShape AABBs (not full cubes), so non-cubic blocks get correctly-shaped outlines
- **Line width:** 2.0 pixels, solid alpha (no pulsing)
- **Shrink:** -1/128 block inward, matching Create's `inflate(-1/128f)`
- **Timeout:** Outlines disappear 500ms after last packet (when player stops holding the item)

### What Gets Highlighted

- ✅ Logic Link blocks
- ✅ Logic Sensor blocks
- ✅ Create Stock Links
- ✅ Create Packager Links
- ✅ Any block with `LogisticallyLinkedBehaviour`
- ❌ Gauges (speed/stress/factory) — these are kinetic/display blocks, not logistics network participants. Create's own highlight system also doesn't highlight them.

---

## Lua API — `logiclink` peripheral

Wrap with: `local link = peripheral.wrap("logiclink")`

### Network Status

| Function | Returns | Description |
|----------|---------|-------------|
| `isLinked()` | `boolean` | Whether connected to a Create logistics network |
| `getPosition()` | `{x, y, z}` | Position of this Logic Link block |
| `getNetworkID()` | `string` or `nil` | Network frequency UUID |
| `getNetworkInfo()` | `table` | Summary: `{linked, networkId, position, itemTypes, totalItems}` |
| `refresh()` | — | Force refresh cached inventory (normally auto-refreshes every 2s) |

### Inventory Monitoring

| Function | Returns | Description |
|----------|---------|-------------|
| `list()` | `[{name, count, displayName}, ...]` | All items on the network |
| `getItemCount(itemName)` | `number` | Count of a specific item by registry name |
| `getItemTypeCount()` | `number` | Number of distinct item types |
| `getTotalItemCount()` | `number` | Total sum of all items across all stacks |

### Factory Gauges & Links

| Function | Returns | Description |
|----------|---------|-------------|
| `getGauges()` | `[gauge, ...]` | All Factory Panel gauges on the network |
| `getLinks()` | `[link, ...]` | All logistics links with type detection |
| `getSensors()` | `[sensor, ...]` | All Logic Sensors on the same frequency |

**Gauge table fields:** `item`, `targetAmount`, `currentStock`, `promised`, `satisfied`, `promisedSatisfied`, `waitingForNetwork`, `redstonePowered`, `address`, `slot`, `position`

**Link table fields:** `type` (packager_link, redstone_requester, stock_ticker, or block name), `position`

**Sensor table fields:** `position`, `targetPosition`, `data` (see sensor data below)

### Item Requests

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `requestItem(name, count, address)` | item registry name, amount, delivery address | `boolean` | Request a single item type to be packaged and delivered |
| `requestItems(items, address)` | table of `{name, count}`, delivery address | `boolean` | Request multiple item types in a single order |

Items are delivered through Create's logistics system (Frogports, packagers, etc.).

---

## Lua API — `logicsensor` peripheral

Wrap with: `local sensor = peripheral.wrap("logicsensor")`

| Function | Returns | Description |
|----------|---------|-------------|
| `isLinked()` | `boolean` | Whether linked to a logistics network |
| `getNetworkID()` | `string` or `nil` | Network frequency UUID |
| `getPosition()` | `{x, y, z}` | Position of this sensor block |
| `getTargetPosition()` | `{x, y, z}` | Position of the machine it's reading |
| `getData()` | `table` | Cached data from target block (fast, no world access) |
| `getTargetData()` | `table` | Force immediate fresh read from target (runs on main thread) |
| `refresh()` | — | Force refresh of cached data |

### Sensor Data Fields

The data table returned by `getData()` / `getTargetData()` includes:

**Always present:**
- `block` — Registry name (e.g. `"create:basin"`)
- `blockName` — Display name (e.g. `"Basin"`)
- `position` — `{x, y, z}`

**Kinetic blocks** (shafts, gearboxes, mechanical presses, saws, etc.):
- `isKinetic = true`
- `speed` — Current RPM
- `theoreticalSpeed` — Theoretical RPM
- `overStressed` — boolean
- `hasKineticNetwork` — boolean
- `stressCapacity` — Network stress capacity
- `networkStress` — Current network stress

**Blocks with inventory** (basins, depots, vaults, etc.):
- `hasInventory = true`
- `inventorySize` — Number of slots
- `totalItems` — Total item count
- `inventory` — `[{name, displayName, count, maxCount, slot}, ...]`

**Blocks with fluid storage** (basins, tanks, etc.):
- `hasFluidStorage = true`
- `tankCount` — Number of tanks
- `tanks` — `[{tank, fluid, amount, capacity, percentage}, ...]`

**Blaze Burners:**
- `isBlazeBurner = true`
- `heatLevel` — Heat level string (none, smouldering, fading, kindled, seething)

---

## Project Structure

```
F:\Controller\CreateLogicLink\
├── build.gradle                          -- NeoForge mod build config
├── gradle.properties                     -- Mod metadata
├── README.md                             -- GitHub README
├── MOD_DOCUMENTATION.md                  -- This file
├── LICENSE                               -- MIT License
├── src/main/java/com/apocscode/logiclink/
│   ├── LogicLink.java                    -- Mod entry point, PlayerTickEvent for highlights
│   ├── ModRegistry.java                  -- Block/item/BE/creative tab registration
│   ├── block/
│   │   ├── LogicLinkBlock.java           -- Logic Link block (HorizontalDirectionalBlock, full cube)
│   │   ├── LogicLinkBlockEntity.java     -- Stores frequency, caches network inventory
│   │   ├── LogicLinkBlockItem.java       -- Item with frequency linking on right-click
│   │   ├── LogicSensorBlock.java         -- Logic Sensor block (FaceAttachedHorizontalDirectionalBlock)
│   │   ├── LogicSensorBlockEntity.java   -- Stores frequency, caches target block data
│   │   └── LogicSensorBlockItem.java     -- Item with frequency linking on right-click
│   ├── peripheral/
│   │   ├── LogicLinkPeripheral.java      -- CC:Tweaked peripheral (14 Lua functions)
│   │   ├── LogicLinkPeripheralProvider.java -- Registers both peripherals with CC
│   │   ├── LogicSensorPeripheral.java    -- CC:Tweaked peripheral (7 Lua functions)
│   │   └── CreateBlockReader.java        -- Reads Create block data via reflection
│   ├── network/
│   │   ├── LinkNetwork.java              -- Static link registry + highlight packet sender
│   │   ├── SensorNetwork.java            -- Static sensor registry by frequency UUID
│   │   └── NetworkHighlightPayload.java  -- Server→client packet with block positions
│   └── client/
│       └── NetworkHighlightRenderer.java -- Client-side outline renderer (Create-style)
├── src/main/resources/
│   ├── assets/logiclink/
│   │   ├── blockstates/
│   │   │   ├── logic_link.json           -- 4 horizontal variants
│   │   │   └── logic_sensor.json         -- 12 face+facing variants (floor/wall/ceiling × 4)
│   │   ├── models/block/
│   │   │   ├── logic_link.json           -- Cube: polished andesite top, andesite casing sides
│   │   │   └── logic_sensor.json         -- Thin pad: smooth stone top, polished andesite sides
│   │   ├── models/item/
│   │   │   ├── logic_link.json
│   │   │   └── logic_sensor.json
│   │   └── lang/en_us.json               -- English translations
│   └── data/logiclink/loot_table/blocks/
│       ├── logic_link.json               -- Drops itself
│       └── logic_sensor.json             -- Drops itself
└── build/libs/logiclink-0.1.0.jar        -- Compiled mod jar
```

---

## Implementation History

### Phase 1 — Core Logic Link

1. **Logic Link block and block entity** — Connects to Create logistics networks via frequency UUID. Caches inventory with 2-second auto-refresh.
2. **Frequency linking system** — Right-click Stock Links to copy frequency, right-click air to clear. Tooltip shows linked status. Items glow purple when tuned.
3. **CC:Tweaked peripheral registration** — Logic Link appears as `logiclink` peripheral when adjacent to a computer.
4. **9 read-only Lua functions** — `isLinked`, `getPosition`, `getNetworkID`, `list`, `getItemCount`, `getItemTypeCount`, `getTotalItemCount`, `refresh`, `getNetworkInfo`.
5. **2 item request functions** — `requestItem`, `requestItems` with auto-detection of Create Factory Abstractions mod (uses reflection to route through GenericLogisticsManager when present).
6. **Factory Abstractions compatibility** — Factory Abstractions uses @Overwrite on LogisticsManager.attemptToSend(). Fixed by detecting the mod at runtime and using reflection to call GenericLogisticsManager directly.

### Phase 2 — Gauges & Links

7. **`getGauges()` function** — Enumerates all Factory Panel gauges on the network. Reads panel data including item filter, target amount, current stock, promised amounts, satisfaction status, redstone state, and address.
8. **`getLinks()` function** — Lists all logistics links on the network with type detection (packager_link, redstone_requester, stock_ticker, or raw block name).

### Phase 3 — Logic Sensor

9. **Logic Sensor block** — Thin directional block that attaches to Create machines.
10. **Logic Sensor block entity** — Stores frequency UUID, registers with SensorNetwork, caches target block data with 20-tick refresh.
11. **Logic Sensor block item** — Same frequency linking mechanic as Logic Link.
12. **SensorNetwork static registry** — ConcurrentHashMap with WeakReferences, tracks sensors by frequency UUID.
13. **CreateBlockReader utility** — Reads data from Create blocks using reflection (KineticBlockEntity speed/stress/capacity) and NeoForge capabilities (IItemHandler, IFluidHandler). Supports Blaze Burner heat levels.
14. **Logic Sensor peripheral** — CC:Tweaked peripheral type `logicsensor` with 7 Lua functions for direct wired access.
15. **`getSensors()` on Logic Link** — Queries SensorNetwork to discover all sensors on the same frequency, returns their position, target position, and cached data.

### Phase 4 — Textures & Visual Identity

16. **Custom block textures** — Logic Link uses andesite casing variant (polished andesite top, andesite casing sides/bottom). Logic Sensor uses smooth stone top, polished andesite sides, andesite casing bottom. Both differentiated from Create's stock link blue.
17. **Model definitions** — Logic Link is a full cube (cube_bottom_top parent). Logic Sensor is a thin pad (custom elements [1,0,1]→[15,5,15]).

### Phase 5 — Network Highlight System

18. **Held-item highlight** — When holding a tuned Logic Link or Logic Sensor item, all blocks on the same network light up with Create-style outlines.
19. **Server-side PlayerTickEvent** — Every 5 ticks, checks if player holds a tuned item, extracts frequency, sends highlight packet.
20. **LinkNetwork registry** — Static ConcurrentHashMap tracking Logic Link block entities by frequency UUID with WeakReferences.
21. **NetworkHighlightPayload** — Custom server→client packet carrying a list of BlockPos.
22. **NetworkHighlightRenderer** — Client-side renderer using `RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS`. Draws actual block shape outlines with Create's alternating blue colors (0x708DAD / 0x90ADCD).
23. **Create network integration** — `sendNetworkHighlight()` queries `LogisticallyLinkedBehaviour.getAllPresent(freq)` to include all Create logistics blocks (Stock Links, Packager Links, etc.) alongside our own blocks.

### Phase 6 — Sensor Block Rewrite (FaceAttached)

24. **FaceAttachedHorizontalDirectionalBlock** — Rewrote LogicSensorBlock from DirectionalBlock to FaceAttachedHorizontalDirectionalBlock, matching Create's PackagerLinkBlock/Stock Link behavior.
25. **FACE + FACING properties** — Floor, wall, and ceiling attachment with 12 blockstate variants (3 faces × 4 horizontal directions).
26. **Correct VoxelShapes** — Explicit shape lookup using `state.getValue(FACE)` with dedicated shapes for floor (pad at y=0), ceiling (pad at y=16), and each wall direction.
27. **Correct target position** — `getTargetPos()` uses explicit FACE property: floor→below, ceiling→above, wall→FACING direction.
28. **Blockstate JSON** — 12 variants with correct x/y rotations: floor (x=0), ceiling (x=180), walls (x=90 + y rotation following Create's horizontalAngle convention: SOUTH=0, NORTH=180, EAST=270, WEST=90).
29. **No support block required** — `canSurvive()` always returns true, like Create's Stock Link.

---

## What Still Needs To Be Done

### High Priority

- [ ] **Custom textures** — Both blocks use vanilla/Create textures as placeholders. Need proper unique textures.
- [ ] **Recipes** — No crafting recipes defined yet. Need data-generation or JSON recipes for both blocks.

### Medium Priority

- [ ] **Testing getGauges()** — Factory Panel gauge reading not verified in-game yet.
- [ ] **Testing requestItem / requestItems** — Item request functions need re-verification.
- [ ] **Kinetic/fluid/blaze sensor testing** — Verify sensor reads all data types from various Create blocks.

### Low Priority / Future Features

- [ ] **Redstone integration** — Expose redstone output from sensor data
- [ ] **Monitor display support** — Pre-built Lua programs for CC:Tweaked monitors
- [ ] **Pocket computer support** — Wireless modem integration
- [ ] **Sensor filtering** — Report only specific data types to reduce traffic
- [ ] **Create block-specific readers** — Specialized data for: mechanical press, deployer, mechanical crafter, millstone, sequenced gearshift, trains, display links, contraptions
- [ ] **Mod version bump** — Currently 0.1.0, increment when features stabilize
- [ ] **CurseForge / Modrinth publishing** — Package and publish when ready

---

## Build & Deploy

### Build

```powershell
$env:JAVA_HOME = ""
cd F:\Controller\CreateLogicLink
.\gradlew.bat build --no-daemon
```

**Important:** `JAVA_HOME` must be cleared before building — Gradle needs to find its own JDK.

Output: `build/libs/logiclink-0.1.0.jar`

### Deploy

```powershell
Copy-Item "F:\Controller\CreateLogicLink\build\libs\logiclink-0.1.0.jar" "C:\Users\travf\curseforge\minecraft\Instances\All the Mods 10 - ATM10\mods\logiclink-0.1.0.jar" -Force
```

Then restart the Minecraft instance.

### CC:Tweaked Computer Files

In-game computer files are stored at:
```
C:\Users\travf\curseforge\minecraft\Instances\All the Mods 10 - ATM10\saves\Train2map\computercraft\computer\29\
```

---

## Quick Lua Examples

### List all items on the network
```lua
local link = peripheral.wrap("logiclink")
for _, item in ipairs(link.list()) do
    print(item.displayName .. " x" .. item.count)
end
```

### Read a sensor attached to a basin
```lua
local link = peripheral.wrap("logiclink")
local sensors = link.getSensors()
for _, s in ipairs(sensors) do
    print(s.data.blockName .. " at " .. s.targetPosition.x .. "," .. s.targetPosition.y .. "," .. s.targetPosition.z)
    for _, item in ipairs(s.data.inventory or {}) do
        print("  " .. item.displayName .. " x" .. item.count)
    end
end
```

### Request items from the network
```lua
local link = peripheral.wrap("logiclink")
link.requestItem("minecraft:iron_ingot", 64, "my_address")
```
