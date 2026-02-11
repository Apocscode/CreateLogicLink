# Create: Logic Link Peripheral — Mod Documentation

## Overview

**Create: Logic Link Peripheral** is a Minecraft mod that bridges **Create**'s logistics system with **CC:Tweaked** (ComputerCraft). It provides programmable peripheral blocks that allow ComputerCraft computers to monitor inventory, read machine data, request items, control redstone wirelessly, drive programmable motors, and manage Create Storage — all through Lua scripts.

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

### Optional Mods (Soft Dependencies)

| Mod | Version | Enables |
|-----|---------|---------|
| Create: Storage | 1.1.7 | `storage_controller` + `storage_interface` peripherals |

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

### Redstone Controller

A peripheral block that provides programmatic control over Create's Redstone Link wireless network. One block can manage unlimited frequency channels simultaneously — all from Lua, no block GUI.

- **Appearance:** Full cube, polished blackstone top, andesite casing sides/bottom, netherite block sound
- **Map Color:** Fire red (MapColor.FIRE)
- **Placement:** Horizontal directional (4 faces: N/S/E/W)
- **No frequency linking:** Channels are created dynamically from Lua scripts
- **Persistence:** Channels survive server restarts (saved to NBT)
- **Range:** Subject to Create's `linkRange` config, same as physical Redstone Links

### Creative Logic Motor

A CC:Tweaked-controlled rotation source with unlimited stress capacity. Generates rotation directly — no external power needed.

- **Appearance:** Uses Create's creative motor model
- **Base Class:** `DirectionalKineticBlock` with `IBE<CreativeLogicMotorBlockEntity>`
- **Block Entity:** Extends `GeneratingKineticBlockEntity` — builds a kinetic source network
- **Placement:** Directional (all 6 faces: N/S/E/W/UP/DOWN), shaft exits from FACING direction
- **Speed Range:** -256 to 256 RPM, set from Lua
- **Stress:** Unlimited capacity (creative-tier)
- **Sequences:** Supports timed rotation steps (rotate X degrees at Y RPM), wait steps, and speed-change steps with optional looping

### Logic Motor

A CC:Tweaked-controlled rotation modifier that sits inline on a shaft. Functions as a programmable clutch + gearshift + sequenced gearshift in one block.

- **Appearance:** Uses Create's sequenced gearshift model with colored directional markers:
  - **Orange stripes** = INPUT (drive) side — connect your power source here
  - **Light blue stripes** = OUTPUT side — CC-controlled modified rotation exits here
- **Base Class:** `KineticBlock` with `IBE<LogicMotorBlockEntity>`
- **Block Entity:** Extends `SplitShaftBlockEntity` — splits rotation into two halves with a configurable ratio
- **Block State:** `HORIZONTAL_FACING` (N/S/E/W) indicates output direction + `ACTIVE` boolean
- **Placement:** Horizontal only, FACING = output direction, opposite = input
- **Model:** Multipart blockstate with base gearshift model + overlay marker models
- **Speed Modifier:** -16.0 to 16.0× (doubles, halves, reverses, or zeroes input speed)
- **Clutch:** `enable()`/`disable()` connects/disconnects rotation
- **Reverse:** `setReversed()` flips rotation direction
- **Sequences:** Rotate steps (degrees at modifier), wait steps, modifier-change steps with optional looping
- **Kinematics Debounce:** Rapid Lua property changes are coalesced into a single kinetic network update per tick to prevent block popping

---

## Train Monitor & Signal Diagnostics

### Train Monitor Block

A CTC-style train network overview block with a GUI that displays a real-time network map, train positions, station labels, and signal diagnostics.

- **Appearance:** Full cube, industrial monitor style
- **GUI:** Scrollable/zoomable network map with train dots, station labels, signal icons
- **Diagnostics:** Scans for junction unsignaled, no-path, and signal conflict issues
- **Data Source:** `TrainNetworkDataReader` — reads Create's internal train graph

### Signal Diagnostic Tablet

A handheld item for field debugging of train signal issues. Right-click to scan the network, then open the tablet GUI to view issues sorted by distance.

- **Usage:** Right-click to scan → opens GUI with diagnostics sorted closest-first
- **Highlight Buttons:** Each diagnostic row has clickable buttons to toggle in-world ghost box highlights
- **Color Legend:** Displayed in the GUI header for quick reference

### Signal Highlight Colors

The tablet and in-world ghost box renderer use consistent color coding:

| Color | Box Style | Meaning |
|-------|-----------|--------|
| **Green** | Solid outline | Place a regular signal here |
| **Cyan** | Solid outline | Place a chain signal here |
| **Red** | Pulsing outline + cross | Signal conflict — remove or fix |
| **Yellow** | — (GUI only) | Warning severity (unsignaled junction) |

### Ghost Box Rendering

- Boxes render at real Minecraft world coordinates from Create's `TrackNodeLocation` / `SignalBoundary` data
- Double-box style: outer box with slight padding + inner box for visual weight
- Conflict markers include a cross pattern on top
- Render distance: 128 blocks from camera
- Pulsing alpha animation for visibility
- Client-side only — managed by `SignalHighlightManager` singleton, read by `SignalGhostRenderer`
- No dependency on Train Monitor block entity being chunk-loaded

### Signal Diagnostic Types

| Type | Severity | Description |
|------|----------|-------------|
| `JUNCTION_UNSIGNALED` | WARN | Junction node has no signals — trains may deadlock |
| `NO_PATH` | CRIT | Train cannot find a path to its destination |
| `SIGNAL_CONFLICT` | CRIT | Two signals on the same segment cause conflicts |

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
- ❌ Gauges (speed/stress/factory) — these are kinetic/display blocks, not logistics network participants

---

## Lua API — `logiclink` peripheral (14 functions)

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

### Item Requests

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `requestItem(name, count, address)` | item registry name, amount, delivery address | `boolean` | Request a single item type to be packaged and delivered |
| `requestItems(items, address)` | table of `{name, count}`, delivery address | `boolean` | Request multiple item types in a single order |

---

## Lua API — `logicsensor` peripheral (7 functions)

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
- `isKinetic = true`, `speed`, `theoreticalSpeed`, `overStressed`, `hasKineticNetwork`, `stressCapacity`, `networkStress`

**Blocks with inventory** (basins, depots, vaults, etc.):
- `hasInventory = true`, `inventorySize`, `totalItems`, `inventory` — `[{name, displayName, count, maxCount, slot}, ...]`

**Blocks with fluid storage** (basins, tanks, etc.):
- `hasFluidStorage = true`, `tankCount`, `tanks` — `[{tank, fluid, amount, capacity, percentage}, ...]`

**Blaze Burners:**
- `isBlazeBurner = true`, `heatLevel` — (none, smouldering, fading, kindled, seething)

---

## Lua API — `redstone_controller` peripheral (8 functions)

Wrap with: `local rc = peripheral.wrap("redstone_controller")`

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `setOutput(item1, item2, power)` | two item registry names, signal 0–15 | — | Transmit a redstone signal on a frequency pair |
| `getInput(item1, item2)` | two item registry names | `number` (0–15) | Read signal from external transmitters |
| `getOutput(item1, item2)` | two item registry names | `number` (0–15) | Read current transmit power |
| `removeChannel(item1, item2)` | two item registry names | — | Remove a channel entirely |
| `getChannels()` | — | `[{item1, item2, mode, power}, ...]` | List all active channels |
| `setAllOutputs(power)` | signal 0–15 | — | Set all transmit channels to one value |
| `clearChannels()` | — | — | Remove all channels |
| `getPosition()` | — | `{x, y, z}` | Block position |

---

## Lua API — `creative_logic_motor` peripheral (16 functions)

Wrap with: `local motor = peripheral.wrap("creative_logic_motor")`

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `setSpeed(rpm)` | -256 to 256 | — | Set motor speed in RPM |
| `getSpeed()` | — | `number` | Current target speed |
| `getActualSpeed()` | — | `number` | Actual network rotation speed |
| `enable()` | — | — | Enable the motor |
| `disable()` | — | — | Disable the motor |
| `stop()` | — | — | Set speed to 0 and disable |
| `isEnabled()` | — | `boolean` | Whether motor is enabled |
| `isRunning()` | — | `boolean` | Enabled and speed ≠ 0 |
| `clearSequence()` | — | — | Clear all sequence steps |
| `addRotateStep(deg, rpm)` | degrees, speed 1-256 | — | Add rotation step |
| `addWaitStep(ticks)` | 1-6000 | — | Add wait step (20 ticks = 1 second) |
| `addSpeedStep(rpm)` | -256 to 256 | — | Add speed-change step |
| `runSequence(loop)` | boolean | — | Run the sequence |
| `stopSequence()` | — | — | Stop running sequence |
| `isSequenceRunning()` | — | `boolean` | Whether a sequence is active |
| `getSequenceSize()` | — | `number` | Number of queued steps |

---

## Lua API — `logic_motor` peripheral (17 functions)

Wrap with: `local motor = peripheral.wrap("logic_motor")`

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `enable()` | — | — | Enable (pass rotation through) |
| `disable()` | — | — | Disable (disconnect, clutch off) |
| `isEnabled()` | — | `boolean` | Whether motor is enabled |
| `setReversed(bool)` | boolean | — | Reverse output rotation |
| `isReversed()` | — | `boolean` | Whether reversed |
| `setModifier(mult)` | -16.0 to 16.0 | — | Set speed multiplier |
| `getModifier()` | — | `number` | Current speed modifier |
| `getInputSpeed()` | — | `number` | Input RPM from external source |
| `getOutputSpeed()` | — | `number` | Output RPM after modifier |
| `clearSequence()` | — | — | Clear all sequence steps |
| `addRotateStep(deg, mod)` | degrees, modifier 0.1-16.0 | — | Add rotation step |
| `addWaitStep(ticks)` | 1-6000 | — | Add wait step |
| `addModifierStep(mod)` | -16.0 to 16.0 | — | Add modifier-change step |
| `runSequence(loop)` | boolean | — | Run the sequence |
| `stopSequence()` | — | — | Stop running sequence |
| `isSequenceRunning()` | — | `boolean` | Whether a sequence is active |
| `getSequenceSize()` | — | `number` | Number of queued steps |

### Logic Motor vs Create's Sequenced Gearshift

| Feature | Sequenced Gearshift | Logic Motor |
|---------|---------------------|-------------|
| Configuration | Manual GUI, fixed cards | Lua scripts, dynamic at runtime |
| Trigger | Redstone signal | Any Lua condition (sensor data, time, etc.) |
| Instruction limit | 5 slots | Unlimited (ArrayList) |
| Clutch | Needs separate block | Built-in `enable()`/`disable()` |
| Gearshift | Needs separate block | Built-in `setReversed()` |
| Speed modifier | Fixed ratios | Arbitrary -16× to 16× |
| Loop mode | No | Yes (`runSequence(true)`) |
| Conditional logic | No | Via Lua scripts |
| Requires | Redstone source | CC:Tweaked computer |

---

## Lua API — `storage_controller` peripheral (14 functions) — *Requires Create: Storage*

Wrap with: `local sc = peripheral.wrap("storage_controller")`

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `isConnected()` | — | `boolean` | Whether connected to storage boxes |
| `getBoxCount()` | — | `number` | Number of connected storage boxes |
| `getPosition()` | — | `{x, y, z}` | Block position |
| `size()` | — | `number` | Number of slots in the network |
| `list()` | — | `table` | All items (slot → item info) |
| `getItemDetail(slot)` | 1-based slot | `table` or `nil` | Detailed item info |
| `getBoxes()` | — | `[box, ...]` | All boxes with capacity, filter, void upgrade |
| `getTotalItemCount()` | — | `number` | Total items across all boxes |
| `getTotalCapacity()` | — | `number` | Total max capacity |
| `getItemCount(name)` | item registry name | `number` | Count of a specific item |
| `getItemSummary()` | — | `[item, ...]` | Unique items with total counts |
| `findItems(query)` | partial name | `[item, ...]` | Search by name (case-insensitive) |
| `pushItems(to, slot, limit?, toSlot?)` | target peripheral, slot | `number` | Push to adjacent inventory |
| `pullItems(from, slot, limit?, toSlot?)` | source peripheral, slot | `number` | Pull from adjacent inventory |

---

## Lua API — `storage_interface` peripheral (10 functions) — *Requires Create: Storage*

Wrap with: `local si = peripheral.wrap("storage_interface")`

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `isConnected()` | — | `boolean` | Whether connected to a controller |
| `getPosition()` | — | `{x, y, z}` | Block position |
| `getControllerPosition()` | — | `{x, y, z}` or `nil` | Connected controller position |
| `size()` | — | `number` | Slots in connected network |
| `list()` | — | `table` | All items in connected network |
| `getItemDetail(slot)` | 1-based slot | `table` or `nil` | Item details by slot |
| `getBoxCount()` | — | `number` | Connected boxes via controller |
| `getItemCount(name)` | item registry name | `number` | Count of specific item |
| `getItemSummary()` | — | `[item, ...]` | Unique items with totals |
| `findItems(query)` | partial name | `[item, ...]` | Search by name (case-insensitive) |

---

## Project Structure

```
F:\Controller\CreateLogicLink\
├── build.gradle                          -- NeoForge mod build config
├── gradle.properties                     -- Mod metadata
├── README.md                             -- GitHub README
├── MOD_DOCUMENTATION.md                  -- This file
├── LICENSE                               -- MIT License
├── generate_ponder_nbt.ps1               -- Generates Ponder schematic .nbt files
├── src/main/java/com/apocscode/logiclink/
│   ├── LogicLink.java                    -- Mod entry point, PlayerTickEvent for highlights
│   ├── ModRegistry.java                  -- Block/item/BE/creative tab registration
│   ├── block/
│   │   ├── LogicLinkBlock.java           -- Logic Link block (HorizontalDirectionalBlock)
│   │   ├── LogicLinkBlockEntity.java     -- Stores frequency, caches network inventory
│   │   ├── LogicLinkBlockItem.java       -- Item with frequency linking on right-click
│   │   ├── LogicSensorBlock.java         -- Sensor (FaceAttachedHorizontalDirectionalBlock)
│   │   ├── LogicSensorBlockEntity.java   -- Stores frequency, caches target block data
│   │   ├── LogicSensorBlockItem.java     -- Item with frequency linking on right-click
│   │   ├── RedstoneControllerBlock.java  -- Redstone Controller (HorizontalDirectionalBlock)
│   │   ├── RedstoneControllerBlockEntity.java -- Manages virtual redstone link channels
│   │   ├── CreativeLogicMotorBlock.java  -- Creative Motor (DirectionalKineticBlock)
│   │   ├── CreativeLogicMotorBlockEntity.java -- Speed control, sequences (GeneratingKineticBE)
│   │   ├── LogicMotorBlock.java          -- Logic Motor (KineticBlock, HORIZONTAL_FACING)
│   │   └── LogicMotorBlockEntity.java    -- Modifier, clutch, sequences (SplitShaftBE)
│   ├── peripheral/
│   │   ├── LogicLinkPeripheral.java      -- CC:Tweaked peripheral (14 Lua functions)
│   │   ├── LogicLinkPeripheralProvider.java -- Registers all peripherals with CC
│   │   ├── LogicSensorPeripheral.java    -- CC:Tweaked peripheral (7 Lua functions)
│   │   ├── RedstoneControllerPeripheral.java -- CC:Tweaked peripheral (8 Lua functions)
│   │   ├── CreativeLogicMotorPeripheral.java -- CC:Tweaked peripheral (16 Lua functions)
│   │   ├── LogicMotorPeripheral.java     -- CC:Tweaked peripheral (17 Lua functions)
│   │   ├── CreateBlockReader.java        -- Reads Create block data via reflection
│   │   ├── StoragePeripheralCompat.java  -- Soft-dependency guard for Create Storage
│   │   └── storage/
│   │       ├── StorageControllerPeripheral.java -- CC:Tweaked peripheral (14 Lua functions)
│   │       └── StorageInterfacePeripheral.java  -- CC:Tweaked peripheral (10 Lua functions)
│   ├── network/
│   │   ├── LinkNetwork.java              -- Static link registry + highlight packet sender
│   │   ├── SensorNetwork.java            -- Static sensor registry by frequency UUID
│   │   ├── NetworkHighlightPayload.java  -- Server→client packet with block positions
│   │   └── VirtualRedstoneLink.java      -- IRedstoneLinkable impl for virtual channels
│   └── client/
│       ├── NetworkHighlightRenderer.java -- Client-side outline renderer (Create-style)
│       ├── LogicLinkClientSetup.java     -- Registers Ponder plugin on FMLClientSetupEvent
│       └── ponder/
│           ├── LogicLinkPonderPlugin.java     -- PonderPlugin impl: 12 tags + scene registration
│           ├── LogicLinkPonderScenes.java     -- Registers 5 storyboards with tag highlighting
│           └── LogicLinkSceneAnimations.java  -- 5 animated Ponder scenes
├── src/main/resources/
│   ├── assets/logiclink/
│   │   ├── blockstates/
│   │   │   ├── logic_link.json           -- 4 horizontal variants
│   │   │   ├── logic_sensor.json         -- 12 face+facing variants
│   │   │   ├── redstone_controller.json  -- 4 horizontal variants
│   │   │   ├── creative_logic_motor.json -- 6 directional variants (Create creative motor)
│   │   │   └── logic_motor.json          -- Multipart: base gearshift + input/output markers
│   │   ├── models/block/
│   │   │   ├── logic_link.json           -- Cube: polished andesite top, andesite casing sides
│   │   │   ├── logic_sensor.json         -- Thin pad: smooth stone top, polished andesite sides
│   │   │   ├── redstone_controller.json  -- Cube: polished blackstone top, andesite casing sides
│   │   │   ├── motor_input_marker.json   -- Orange concrete stripe overlay (drive input side)
│   │   │   └── motor_output_marker.json  -- Light blue concrete stripe overlay (CC output side)
│   │   ├── models/item/
│   │   │   ├── creative_logic_motor.json
│   │   │   ├── logic_link.json
│   │   │   ├── logic_motor.json
│   │   │   ├── logic_sensor.json
│   │   │   └── redstone_controller.json
│   │   ├── lang/en_us.json               -- English translations + Ponder lang keys
│   │   └── ponder/
│   │       ├── logic_link/overview.nbt            -- 7×4×7 schematic
│   │       ├── logic_sensor/overview.nbt          -- 7×4×7 schematic
│   │       ├── redstone_controller/overview.nbt   -- 7×3×7 schematic
│   │       ├── creative_logic_motor/overview.nbt  -- 7×4×7 schematic
│   │       └── logic_motor/overview.nbt           -- 7×4×7 schematic
│   └── data/logiclink/
│       ├── loot_table/blocks/            -- All 5 blocks drop themselves
│       └── recipe/                       -- All 5 blocks have shaped crafting recipes
└── build/libs/logiclink-0.1.0.jar        -- Compiled mod jar
```

---

## Implementation History

### Phase 1 — Core Logic Link

1. **Logic Link block and block entity** — Connects to Create logistics networks via frequency UUID.
2. **Frequency linking system** — Right-click Stock Links to copy frequency, right-click air to clear.
3. **CC:Tweaked peripheral registration** — Logic Link appears as `logiclink` peripheral.
4. **9 read-only Lua functions** — `isLinked`, `getPosition`, `getNetworkID`, `list`, `getItemCount`, `getItemTypeCount`, `getTotalItemCount`, `refresh`, `getNetworkInfo`.
5. **2 item request functions** — `requestItem`, `requestItems`.
6. **Factory Abstractions compatibility** — Detects mod at runtime, routes through GenericLogisticsManager via reflection.

### Phase 2 — Gauges & Links

7. **`getGauges()` function** — Enumerates Factory Panel gauges on the network.
8. **`getLinks()` function** — Lists all logistics links with type detection.

### Phase 3 — Logic Sensor

9. **Logic Sensor block** (FaceAttachedHorizontalDirectionalBlock) with frequency linking.
10. **SensorNetwork static registry** — ConcurrentHashMap with WeakReferences.
11. **CreateBlockReader utility** — Reads Create block data via reflection and NeoForge capabilities.
12. **Logic Sensor peripheral** — 7 Lua functions for direct wired access.
13. **`getSensors()` on Logic Link** — Wireless sensor discovery across the network.

### Phase 4 — Textures & Visual Identity

14. **Custom block textures** — Andesite casing variants for Logic Link/Sensor/Redstone Controller.

### Phase 5 — Network Highlight System

15. **Held-item Network Highlighting** — Create-style alternating blue outlines on all network blocks.
16. **LinkNetwork + SensorNetwork registries** — Server-side WeakReference tracking.
17. **NetworkHighlightPayload** — Custom server→client packet.
18. **NetworkHighlightRenderer** — Client-side VoxelShape outline rendering.

### Phase 6 — Sensor Block Rewrite (FaceAttached)

19. **FaceAttachedHorizontalDirectionalBlock** — 12 blockstate variants (3 faces × 4 directions).
20. **Correct VoxelShapes** — Floor/ceiling/wall-specific hit boxes.
21. **Correct target position** — FACE-based target detection.

### Phase 7 — Redstone Controller

22. **Redstone Controller block + entity** — Virtual channel management with NBT persistence.
23. **VirtualRedstoneLink** — Implements Create's `IRedstoneLinkable` interface.
24. **RedstoneControllerPeripheral** — 8 Lua functions for wireless redstone.

### Phase 8 — Crafting Recipes

25. **5 shaped crafting recipes** — Cross-pattern recipes for all blocks using Create + vanilla ingredients.

### Phase 9 — Ponder Tutorials (W Key)

26. **Ponder system** — Create-style animated tutorials for Logic Link, Logic Sensor, and Redstone Controller.
27. **9 Ponder tags** — Categorized with distinct item icons.
28. **3 scene schematics** — `.nbt` Structure Block files.

### Phase 10 — Creative Logic Motor

29. **CreativeLogicMotorBlock** — `DirectionalKineticBlock` with shaft output on FACING.
30. **CreativeLogicMotorBlockEntity** — Extends `GeneratingKineticBlockEntity`, speed -256 to 256 RPM.
31. **Sequence system** — Rotate steps (degrees at RPM), wait steps, speed-change steps with optional looping.
32. **CreativeLogicMotorPeripheral** — 16 Lua functions.

### Phase 11 — Logic Motor (Rotation Modifier)

33. **LogicMotorBlock** — `KineticBlock` with `HORIZONTAL_FACING` + `ACTIVE` properties.
34. **LogicMotorBlockEntity** — Extends `SplitShaftBlockEntity`, speed modifier -16× to 16×.
35. **Clutch + Gearshift + Sequenced Gearshift** — All in one block controlled from Lua.
36. **Directional markers** — Orange input stripes + light blue output stripes via multipart blockstate with overlay models.
37. **Kinematics debounce** — Rapid Lua changes coalesced to one kinetic update per tick.
38. **LogicMotorPeripheral** — 17 Lua functions.

### Phase 12 — Create Storage Integration (Optional)

39. **StorageControllerPeripheral** — 14 Lua functions for Create: Storage controller blocks.
40. **StorageInterfacePeripheral** — 10 Lua functions for storage interfaces.
41. **Soft dependency** — `compileOnly` jar, conditional registration via `ModList.get().isLoaded("fxntstorage")`.

### Phase 13 — Motor Ponder Scenes

42. **Motor Ponder schematics** — Generated `.nbt` files for both motor scenes.
43. **Motor Ponder tags** — 3 new tags: Rotation Output, Computer Control, Sequenced Operations.
44. **Motor Ponder scenes** — Creative motor overview + Logic motor overview with drive/output side explanation.

### Phase 14 — `mainThread` Fix & Stability

45. **`mainThread = true`** — Added to all 86 `@LuaFunction` methods across all 9 peripheral files. Fixes "Too long without yielding" crashes from accessing block entity state on CC's Lua thread.
46. **Sequence guard** — `stopSequence()` and `clearSequence()` only call `detachKinetics()/attachKinetics()` when a sequence was actually running.
47. **Server shutdown fix** — Verified `SensorNetwork` and `LinkNetwork` classes present in compiled jar.

---

## Crafting Recipes

### Logic Link
```
     [ ]         [Ender Pearl]    [ ]
[Brass Ingot] [Andesite Casing] [Brass Ingot]
     [ ]         [Comparator]     [ ]
```

### Logic Sensor
```
     [ ]          [Observer]       [ ]
[Copper Ingot] [Andesite Casing] [Copper Ingot]
     [ ]        [Nether Quartz]    [ ]
```

### Redstone Controller
```
     [ ]         [Ender Pearl]      [ ]
[Brass Ingot] [Andesite Casing] [Brass Ingot]
     [ ]       [Redstone Block]     [ ]
```

### Creative Logic Motor & Logic Motor
*(Recipes defined in data/logiclink/recipe/)*

---

## Ponder System (W Key Tutorials)

All 5 blocks have Create-style animated Ponder tutorials, accessible by pressing **(W)** while hovering over the item in JEI or inventory.

### Architecture

| Class | Purpose |
|---|---|
| `LogicLinkClientSetup` | Registers plugin via `PonderIndex.addPlugin()` on `FMLClientSetupEvent` |
| `LogicLinkPonderPlugin` | `PonderPlugin` implementation — registers 12 tags and 5 scenes |
| `LogicLinkPonderScenes` | Maps each block to a storyboard + schematic path + primary tag |
| `LogicLinkSceneAnimations` | 5 `PonderStoryBoard` methods with multi-step animated sequences |

### Tags (Left-Panel Icons)

| Tag ID | Icon | Shown On | Description |
|---|---|---|---|
| `sensor_inventories` | Chest | Logic Sensor | Inventory blocks |
| `sensor_kinetics` | Shaft | Logic Sensor | Kinetic components |
| `sensor_fluids` | Fluid Tank | Logic Sensor | Fluid storage |
| `sensor_heat` | Blaze Burner | Logic Sensor | Heat sources |
| `link_logistics` | Stock Link | Logic Link | Create logistics |
| `link_sensors` | Logic Sensor | Logic Link | Logic Sensors on network |
| `link_computers` | CC Computer | Logic Link | CC:Tweaked computers |
| `rc_redstone_links` | Redstone Link | Redstone Controller | Redstone Links |
| `rc_lamps` | Redstone Lamp | Redstone Controller | Redstone outputs |
| `motor_kinetics` | — | Both Motors | Rotation output |
| `motor_computers` | — | Both Motors | Computer control |
| `motor_sequences` | — | Both Motors | Sequenced operations |

### Scene Animations

**Logic Link Overview** (7×4×7) — Network bridge demonstration with inventory access

**Logic Sensor Overview** (7×4×7) — Sensor types on chest, barrel, shaft, basin

**Redstone Controller Overview** (7×3×7) — Controller with Redstone Links and lamps

**Creative Logic Motor Overview** (7×4×7) — Rotation source with shaft chain and machine

**Logic Motor Overview** (7×4×7) — Shows orange input side, light blue output side, modifier/sequence concepts

---

## Peripheral Summary

| Peripheral Type | Block | Functions | Category |
|---|---|---|---|
| `logiclink` | Logic Link | 14 | Logistics network |
| `logicsensor` | Logic Sensor | 7 | Machine data |
| `redstone_controller` | Redstone Controller | 8 | Wireless redstone |
| `creative_logic_motor` | Creative Logic Motor | 16 | Rotation source |
| `logic_motor` | Logic Motor | 17 | Rotation modifier |
| `storage_controller` | Storage Controller* | 14 | Storage network |
| `storage_interface` | Storage Interface* | 10 | Storage access |
| `train_monitor` | Train Monitor | — | Train network map |
| — | Signal Tablet (item) | — | Field signal diagnostics |
| **Total** | **9 blocks + 1 item** | **86+** | |

*\*Requires Create: Storage mod*

---

## Build & Deploy

### Build

```powershell
$env:JAVA_HOME = ""
cd F:\Controller\CreateLogicLink
.\gradlew.bat build --no-daemon
```

Output: `build/libs/logiclink-0.1.0.jar`

### Deploy

```powershell
Copy-Item "F:\Controller\CreateLogicLink\build\libs\logiclink-0.1.0.jar" `
    "C:\Users\travf\curseforge\minecraft\Instances\All the Mods 10 - ATM10\mods\logiclink-0.1.0.jar" -Force
```

Then restart the Minecraft instance.

### Generate Ponder Schematics

```powershell
powershell -ExecutionPolicy Bypass -File .\generate_ponder_nbt.ps1
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

### Control a Creative Logic Motor
```lua
local motor = peripheral.wrap("creative_logic_motor")
motor.setSpeed(128)
sleep(2)
motor.setSpeed(-64)
sleep(2)
motor.stop()
```

### Use Logic Motor as a programmable gearshift
```lua
local motor = peripheral.wrap("logic_motor")
motor.enable()
motor.setModifier(2.0)    -- Double speed
sleep(5)
motor.setReversed(true)   -- Reverse
sleep(5)
motor.disable()           -- Clutch off
```

### Control Create Redstone Links wirelessly
```lua
local rc = peripheral.wrap("redstone_controller")
rc.setOutput("minecraft:granite", "minecraft:granite", 15)
local power = rc.getInput("minecraft:red_dye", "minecraft:diamond")
print("Received: " .. power)
rc.setAllOutputs(0)
```

### Read Create Storage network
```lua
local sc = peripheral.wrap("storage_controller")
if sc then
    print("Boxes: " .. sc.getBoxCount())
    print("Items: " .. sc.getTotalItemCount())
    for _, item in ipairs(sc.getItemSummary()) do
        print("  " .. item.displayName .. " x" .. item.count)
    end
end
```
