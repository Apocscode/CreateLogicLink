# Create: Logic Link Peripheral — Mod Documentation

## Overview

**Create: Logic Link Peripheral** is a Minecraft mod that bridges **Create**'s logistics system with **CC:Tweaked** (ComputerCraft). It provides programmable peripheral blocks that allow ComputerCraft computers to monitor inventory, read machine data, and request items from Create's logistics network — all through Lua scripts.

- **Mod ID:** `logiclink`
- **Package:** `com.apocscode.logiclink`
- **Minecraft:** 1.21.1
- **Mod Loader:** NeoForge (21.1.77 build / 21.1.217 runtime)
- **Gradle:** 9.2.1 with ModDevGradle 2.0.140
- **Java:** 21

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

- **Appearance:** Emerald green (MapColor.EMERALD), netherite block sound
- **Placement:** Horizontal directional (4 faces: N/S/E/W)
- **Linking:** Right-click a Stock Link with the Logic Link item to copy the frequency. Right-click air to clear.

### Logic Sensor

A thin wireless sensor block that attaches to any face of a Create machine. It reads data from the machine it's attached to (inventory, fluids, kinetic speed/stress) and makes that data available:
1. **Wired** — place a CC:Tweaked computer adjacent to it, access as `logicsensor` peripheral
2. **Wireless** — link it to the same network frequency as a Logic Link, then call `getSensors()` on the Logic Link to discover all sensors remotely

- **Appearance:** Cyan (MapColor.COLOR_CYAN), copper grate sound, thin slab shape (12×12×3 pixels)
- **Placement:** All 6 directions (attaches to any block face)
- **Linking:** Same frequency system as Logic Link — right-click a Stock Link to copy frequency
- **Data refresh:** Every 20 ticks (1 second), reads the target block and caches the result

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
├── gradle.properties                     -- Mod metadata, configuration-cache=false
├── src/main/java/com/apocscode/logiclink/
│   ├── LogicLink.java                    -- Mod entry point, event handlers
│   ├── ModRegistry.java                  -- Block/item/BE/creative tab registration
│   ├── block/
│   │   ├── LogicLinkBlock.java           -- Logic Link block (HorizontalDirectionalBlock)
│   │   ├── LogicLinkBlockEntity.java     -- Stores frequency, caches network inventory
│   │   ├── LogicLinkBlockItem.java       -- Item with frequency linking on right-click
│   │   ├── LogicSensorBlock.java         -- Logic Sensor block (DirectionalBlock, 6 faces)
│   │   ├── LogicSensorBlockEntity.java   -- Stores frequency, caches target block data
│   │   └── LogicSensorBlockItem.java     -- Item with frequency linking on right-click
│   ├── peripheral/
│   │   ├── LogicLinkPeripheral.java      -- CC:Tweaked peripheral (14 Lua functions)
│   │   ├── LogicLinkPeripheralProvider.java -- Registers both peripherals with CC
│   │   ├── LogicSensorPeripheral.java    -- CC:Tweaked peripheral (7 Lua functions)
│   │   └── CreateBlockReader.java        -- Reads Create block data via reflection
│   └── network/
│       └── SensorNetwork.java            -- Static sensor registry by frequency UUID
├── src/main/resources/
│   ├── assets/logiclink/
│   │   ├── blockstates/
│   │   │   ├── logic_link.json           -- 4 horizontal variants
│   │   │   └── logic_sensor.json         -- 6 directional variants
│   │   ├── models/block/
│   │   │   ├── logic_link.json           -- Cube with oxidized copper textures
│   │   │   └── logic_sensor.json         -- Thin slab with waxed oxidized copper
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

## What Has Been Done

### Phase 1 — Core Logic Link (Complete)

1. **Logic Link block and block entity** — Connects to Create logistics networks via frequency UUID. Caches inventory with 2-second auto-refresh.
2. **Frequency linking system** — Right-click Stock Links to copy frequency, right-click air to clear. Tooltip shows linked status.
3. **CC:Tweaked peripheral registration** — Logic Link appears as `logiclink` peripheral when adjacent to a computer.
4. **9 read-only Lua functions** — `isLinked`, `getPosition`, `getNetworkID`, `list`, `getItemCount`, `getItemTypeCount`, `getTotalItemCount`, `refresh`, `getNetworkInfo`.
5. **2 item request functions** — `requestItem`, `requestItems` with auto-detection of Create Factory Abstractions mod (uses reflection to route through GenericLogisticsManager when present).
6. **Factory Abstractions compatibility fix** — Factory Abstractions uses @Overwrite on LogisticsManager.attemptToSend(). Fixed by detecting the mod at runtime and using reflection to call GenericLogisticsManager directly.

### Phase 2 — Gauges & Links (Complete)

7. **`getGauges()` function** — Enumerates all Factory Panel gauges on the network. Reads panel data including item filter, target amount, current stock, promised amounts, satisfaction status, redstone state, and address.
8. **`getLinks()` function** — Lists all logistics links on the network with type detection (packager_link, redstone_requester, stock_ticker, or raw block name).

### Phase 3 — Logic Sensor (Complete)

9. **Logic Sensor block** — Thin directional block (6 faces) that attaches to Create machines. Uses waxed oxidized copper textures.
10. **Logic Sensor block entity** — Stores frequency UUID, registers with SensorNetwork, caches target block data with 20-tick refresh.
11. **Logic Sensor block item** — Same frequency linking mechanic as Logic Link.
12. **SensorNetwork static registry** — ConcurrentHashMap with WeakReferences, tracks sensors by frequency UUID. Cleaned up on server stop.
13. **CreateBlockReader utility** — Reads data from Create blocks using reflection (KineticBlockEntity speed/stress/capacity) and NeoForge capabilities (IItemHandler, IFluidHandler). Supports Blaze Burner heat levels.
14. **Logic Sensor peripheral** — CC:Tweaked peripheral type `logicsensor` with 7 Lua functions for direct wired access.
15. **`getSensors()` on Logic Link** — Queries SensorNetwork to discover all sensors on the same frequency, returns their position, target position, and cached data.
16. **Resource files** — Blockstates (6 variants), block/item models, loot table, and lang translations for the sensor.
17. **Server stop cleanup** — SensorNetwork.clear() called on ServerStoppingEvent.

### Verified In-Game

- All 14 Logic Link Lua functions working
- All 7 Logic Sensor Lua functions working
- Sensor wirelessly discovered by Logic Link via getSensors()
- Basin data read successfully (inventory showing Birch Log items)
- Sensor correctly identifies target block position from facing direction

---

## What Still Needs To Be Done

### High Priority

- [ ] **Custom textures** — Both blocks currently use vanilla oxidized copper textures as placeholders. Need proper custom textures for Logic Link (emerald green) and Logic Sensor (cyan).
- [ ] **Recipes** — No crafting recipes defined yet. Need data-generation or JSON recipes for both blocks.
- [ ] **Testing getGauges()** — Factory Panel gauge reading has not been verified in-game yet. Needs a factory setup with active gauges.
- [ ] **Testing getLinks()** — Link enumeration not verified in-game yet.
- [ ] **Testing requestItem / requestItems** — Item request functions need re-verification after the recent code changes.

### Medium Priority

- [ ] **Kinetic sensor testing** — Verify sensor reads kinetic data (speed, stress) from rotating Create blocks (shafts, mechanical press, saw, etc.).
- [ ] **Fluid sensor testing** — Verify sensor reads fluid data from fluid-holding blocks (tanks, basins with fluids).
- [ ] **Blaze Burner sensor testing** — Verify heat level reading works.
- [ ] **Multiple sensor support** — Test with multiple sensors on the same network to verify SensorNetwork handles them correctly.
- [ ] **Sensor on all 6 faces** — Verify sensor model/collision renders correctly when placed on all 6 block faces (up, down, north, south, east, west).

### Low Priority / Future Features

- [ ] **Redstone integration** — Expose redstone output from sensor data (e.g. emit signal when basin is full or machine is overstressed).
- [ ] **Monitor display support** — Create pre-built Lua programs that display sensor/gauge data on CC:Tweaked monitors.
- [ ] **Pocket computer support** — Wireless modem integration for reading sensor data from pocket computers.
- [ ] **Sensor filtering** — Allow sensors to report only specific data types (inventory only, kinetic only, etc.) to reduce network traffic.
- [ ] **Create block-specific readers** — Add specialized data readers for more Create blocks:
  - Mechanical Press (processing progress)
  - Deployer (held item, processing state)
  - Mechanical Crafter (recipe progress, pattern)
  - Millstone / Crushing Wheel (processing progress)
  - Sequenced Gearshift (current instruction, step)
  - Smart Chute / Smart Pipe (filter configuration)
  - Trains (schedule, station, speed)
  - Display Link / Display Board data
  - Contraption state (assembled/disassembled, blocks)
- [ ] **Data pack integration** — Allow modpack makers to configure which blocks the sensor can read.
- [ ] **Mod version bump** — Currently 0.1.0, increment when features stabilize.
- [ ] **CurseForge / Modrinth publishing** — Package and publish when ready.

---

## Build & Deploy

### Build

```powershell
$env:JAVA_HOME = ""
cd F:\Controller\CreateLogicLink
.\gradlew.bat clean build --no-daemon
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
