# Create: Logic Link Peripheral

A Minecraft NeoForge mod that bridges **Create mod's** logistics system with **CC:Tweaked** (ComputerCraft). Monitor inventory across an entire logistics network, read machine data from sensors, request item deliveries, control redstone wirelessly, drive programmable motors, and manage Create Storage — all from Lua scripts.

## Features

- **Logic Link Block** — Full-size peripheral block that connects to Create's logistics network. Query inventory, enumerate gauges/links, and request item deliveries via Lua (14 functions).
- **Logic Sensor Block** — Thin surface-mount sensor that reads Create machine data (inventory, fluids, kinetic speed/stress, Blaze Burner heat). Attaches to any surface like a Stock Link (7 functions).
- **Redstone Controller Block** — Programmatic control over Create's Redstone Link wireless network. One block manages unlimited frequency channels from Lua — no GUI, no physical Redstone Links needed (8 functions).
- **Creative Logic Motor** — CC-controlled rotation source with unlimited stress capacity. Set speed, direction, and run timed sequences from Lua (16 functions).
- **Logic Drive** — CC-controlled rotation modifier that sits inline on a shaft. Acts as a programmable clutch + gearshift + sequenced gearshift. Orange-marked input side, light-blue output side (17 functions).
- **Create Storage Integration** *(optional)* — Storage Controller peripheral (14 functions) and Storage Interface peripheral (10 functions) for the Create: Storage mod. Automatically available when that mod is installed.
- **Train Monitor Block** — CTC-style train network overview with real-time map display, signal diagnostics, and conflict detection. Renders a scrollable/zoomable network map on a Create-themed GUI.
- **Signal Diagnostic Tablet** — Handheld item for field debugging. Right-click to scan the train network, then view diagnostics sorted by distance. Click per-issue highlight buttons to toggle in-world ghost boxes at signal locations.
- **In-World Signal Highlights** — Color-coded ghost boxes rendered at real world coordinates:
  - **Green** = Place a regular signal here
  - **Cyan** = Place a chain signal here
  - **Red** = Signal conflict (remove/fix)
  - **Yellow** = Warning (unsignaled junction)
- **Network Highlighting** — Hold a linked item to see all blocks on the network outlined in Create's signature blue.
- **Item Requests** — Send items through Create's packaging/delivery system from Lua scripts.
- **Wireless Sensor Discovery** — Logic Links can discover all Logic Sensors on the network via `getSensors()`.
- **Ponder Tutorials** — All 5 blocks have animated Create-style (W) key tutorials with tagged categories.

## Requirements

| Dependency | Version | Purpose |
|---|---|---|
| Minecraft | 1.21.1 | Base game |
| NeoForge | 21.1.77+ | Mod loader |
| Create | 6.0.8+ | Logistics network & kinetics |
| CC:Tweaked | 1.117.0+ | ComputerCraft peripheral API |
| Create Advanced Logistics | 0.3.0+ | Extended logistics |
| Create CC Logistics | 0.3.6+ | Create ↔ CC bridge |
| Create Unlimited Logistics | 1.2.1+ | Logistics extensions |

**Optional:**

| Dependency | Version | Purpose |
|---|---|---|
| Create: Storage | 1.1.7+ | Storage Controller & Interface peripherals |

Developed and tested with the **All The Mods 10 (ATM10)** modpack.

## Getting Started

1. **Craft a Logic Link** — cross-shaped recipe (Ender Pearl, Brass Ingots, Andesite Casing, Comparator)
2. **Right-click a Stock Link** (or any linked block) with the Logic Link item to copy the network frequency. The item glows purple when linked.
3. **Place the Logic Link** next to a CC:Tweaked computer
4. **Wrap the peripheral** in a Lua script:

```lua
local link = peripheral.wrap("logiclink")
print("Linked: " .. tostring(link.isLinked()))

-- List all items on the network
for _, item in ipairs(link.list()) do
    print(item.displayName .. " x" .. item.count)
end
```

### Adding Sensors

1. **Right-click a Stock Link** with a Logic Sensor item to copy the same frequency
2. **Place the sensor** on the face of a Create machine (basin, depot, shaft, tank, etc.)
3. **Read sensor data** from the Logic Link:

```lua
local link = peripheral.wrap("logiclink")
for _, s in ipairs(link.getSensors()) do
    print(s.data.blockName .. " - Speed: " .. (s.data.speed or "N/A"))
    if s.data.inventory then
        for _, item in ipairs(s.data.inventory) do
            print("  " .. item.displayName .. " x" .. item.count)
        end
    end
end
```

Or wire a computer directly to the sensor:

```lua
local sensor = peripheral.wrap("logicsensor")
local data = sensor.getData()
print(data.blockName .. " at " .. data.position.x .. "," .. data.position.y .. "," .. data.position.z)
```

## Lua API — `logiclink`

| Function | Returns | Description |
|----------|---------|-------------|
| `isLinked()` | boolean | Connected to a logistics network? |
| `getPosition()` | {x,y,z} | Block position |
| `getNetworkID()` | string/nil | Network frequency UUID |
| `getNetworkInfo()` | table | Summary with linked, networkId, position, itemTypes, totalItems |
| `refresh()` | — | Force refresh cached inventory |
| `list()` | [{name, count, displayName}] | All items on the network |
| `getItemCount(name)` | number | Count of a specific item |
| `getItemTypeCount()` | number | Number of distinct item types |
| `getTotalItemCount()` | number | Total items across all stacks |
| `getGauges()` | [gauge] | Factory Panel gauges on the network |
| `getLinks()` | [link] | All logistics links with type detection |
| `getSensors()` | [sensor] | All Logic Sensors on the same frequency |
| `requestItem(name, count, addr)` | boolean | Request a single item delivery |
| `requestItems(items, addr)` | boolean | Request multiple items in one order |

## Lua API — `logicsensor`

| Function | Returns | Description |
|----------|---------|-------------|
| `isLinked()` | boolean | Linked to a network? |
| `getNetworkID()` | string/nil | Network frequency UUID |
| `getPosition()` | {x,y,z} | Sensor position |
| `getTargetPosition()` | {x,y,z} | Attached machine position |
| `getData()` | table | Cached target block data |
| `getTargetData()` | table | Fresh read from target (main thread) |
| `refresh()` | — | Force refresh cached data |

### Sensor Data Fields

Data always includes `block` (registry name), `blockName` (display name), `position`.

Additional fields by block type:
- **Kinetic:** `speed`, `theoreticalSpeed`, `overStressed`, `stressCapacity`, `networkStress`
- **Inventory:** `inventorySize`, `totalItems`, `inventory` (list of items with slot/count/maxCount)
- **Fluids:** `tankCount`, `tanks` (list with fluid/amount/capacity/percentage)
- **Blaze Burner:** `heatLevel` (none/smouldering/fading/kindled/seething)

## Network Highlighting

Hold a linked Logic Link or Logic Sensor item to see all blocks on the network highlighted with Create-style alternating blue outlines. This includes:
- Our Logic Link and Logic Sensor blocks
- Create's Stock Links, Packager Links, and other logistics blocks

The outlines follow actual block shapes (not full cubes) and match Create's visual style.

## Redstone Controller

Place a Redstone Controller next to a CC:Tweaked computer to control Create's Redstone Link network from code:

```lua
local rc = peripheral.wrap("redstone_controller")

-- Transmit signal 15 on channel (granite, granite)
rc.setOutput("minecraft:granite", "minecraft:granite", 15)

-- Read signal from external transmitters
local power = rc.getInput("minecraft:red_dye", "minecraft:diamond")

-- List all active channels
for _, ch in ipairs(rc.getChannels()) do
    print(ch.item1 .. " + " .. ch.item2 .. " = " .. ch.mode .. " @ " .. ch.power)
end

-- Kill switch: zero all outputs
rc.setAllOutputs(0)
```

No frequency linking needed — channels are created on first use. One block replaces unlimited physical Redstone Links.

## Lua API — `redstone_controller`

| Function | Returns | Description |
|----------|---------|-------------|
| `setOutput(item1, item2, power)` | — | Transmit 0–15 on a frequency pair |
| `getInput(item1, item2)` | number | Read signal from external transmitters |
| `getOutput(item1, item2)` | number | Read current transmit power |
| `removeChannel(item1, item2)` | — | Remove a channel |
| `getChannels()` | [channel] | List all channels with mode and power |
| `setAllOutputs(power)` | — | Set all transmit channels to one value |
| `clearChannels()` | — | Remove all channels |
| `getPosition()` | {x,y,z} | Block position |

## Creative Logic Motor

A CC-controlled rotation source with unlimited stress capacity. No external power needed — generates rotation directly from Lua commands.

```lua
local motor = peripheral.wrap("creative_logic_motor")

-- Basic speed control
motor.setSpeed(128)        -- 128 RPM forward
motor.setSpeed(-64)        -- 64 RPM reverse
motor.stop()               -- Speed 0 + disable

-- Sequenced rotation
motor.clearSequence()
motor.addRotateStep(90, 32)   -- Rotate 90° at 32 RPM
motor.addWaitStep(20)         -- Wait 1 second (20 ticks)
motor.addRotateStep(-90, 64)  -- Rotate back at 64 RPM
motor.runSequence(false)      -- Run once (true = loop)
```

## Lua API — `creative_logic_motor`

| Function | Returns | Description |
|----------|---------|-------------|
| `setSpeed(rpm)` | — | Set speed -256 to 256 RPM |
| `getSpeed()` | number | Current target speed |
| `getActualSpeed()` | number | Actual network rotation speed |
| `enable()` | — | Enable the motor |
| `disable()` | — | Disable the motor |
| `stop()` | — | Set speed to 0 and disable |
| `isEnabled()` | boolean | Whether motor is enabled |
| `isRunning()` | boolean | Enabled and speed ≠ 0 |
| `clearSequence()` | — | Clear all sequence steps |
| `addRotateStep(deg, rpm)` | — | Add rotation step (degrees at RPM) |
| `addWaitStep(ticks)` | — | Add wait step (1-6000 ticks) |
| `addSpeedStep(rpm)` | — | Add speed-change step |
| `runSequence(loop)` | — | Run the sequence (loop=true repeats) |
| `stopSequence()` | — | Stop running sequence |
| `isSequenceRunning()` | boolean | Whether a sequence is active |
| `getSequenceSize()` | number | Number of queued steps |

## Logic Drive

A CC-controlled rotation modifier that sits inline on a shaft. Combines clutch + gearshift + sequenced gearshift in one block. The **orange-marked** side is the drive input, the **light-blue** side is the CC-controlled output.

```lua
local motor = peripheral.wrap("logic_drive")

-- Basic control
motor.enable()                 -- Pass rotation through
motor.setModifier(2.0)         -- Double speed
motor.setReversed(true)        -- Reverse direction
motor.disable()                -- Disconnect (clutch off)

-- Read speeds
print("In: " .. motor.getInputSpeed() .. " Out: " .. motor.getOutputSpeed())

-- Sequenced operation
motor.clearSequence()
motor.addRotateStep(180, 1.0)    -- Rotate 180° at 1× modifier
motor.addWaitStep(40)            -- Wait 2 seconds
motor.addRotateStep(-180, 2.0)   -- Rotate back at 2× speed
motor.runSequence(true)          -- Loop forever
```

## Lua API — `logic_drive`

| Function | Returns | Description |
|----------|---------|-------------|
| `enable()` | — | Enable (pass rotation through) |
| `disable()` | — | Disable (disconnect, clutch off) |
| `isEnabled()` | boolean | Whether motor is enabled |
| `setReversed(bool)` | — | Reverse output rotation |
| `isReversed()` | boolean | Whether reversed |
| `setModifier(mult)` | — | Speed multiplier -16.0 to 16.0 |
| `getModifier()` | number | Current speed modifier |
| `getInputSpeed()` | number | Input RPM from external source |
| `getOutputSpeed()` | number | Output RPM (after modifier) |
| `clearSequence()` | — | Clear all sequence steps |
| `addRotateStep(deg, mod)` | — | Add rotation step (degrees at modifier) |
| `addWaitStep(ticks)` | — | Add wait step (1-6000 ticks) |
| `addModifierStep(mod)` | — | Add modifier-change step |
| `runSequence(loop)` | — | Run the sequence (loop=true repeats) |
| `stopSequence()` | — | Stop running sequence |
| `isSequenceRunning()` | boolean | Whether a sequence is active |
| `getSequenceSize()` | number | Number of queued steps |

## Create Storage Integration (Optional)

When the **Create: Storage** mod (`fxntstorage`) is installed, two additional peripherals become available automatically:

### Lua API — `storage_controller`

| Function | Returns | Description |
|----------|---------|-------------|
| `isConnected()` | boolean | Whether connected to storage boxes |
| `getBoxCount()` | number | Number of connected storage boxes |
| `getPosition()` | {x,y,z} | Block position |
| `size()` | number | Number of slots in the network |
| `list()` | table | All items (slot → item info) |
| `getItemDetail(slot)` | table/nil | Detailed info for item in slot |
| `getBoxes()` | [box] | All boxes with capacity, filter, void upgrade info |
| `getTotalItemCount()` | number | Total items across all boxes |
| `getTotalCapacity()` | number | Total max capacity |
| `getItemCount(name)` | number | Count of a specific item |
| `getItemSummary()` | [item] | All unique items with totals |
| `findItems(query)` | [item] | Search by partial name (case-insensitive) |
| `pushItems(toName, slot, limit?, toSlot?)` | number | Push items to adjacent inventory |
| `pullItems(fromName, slot, limit?, toSlot?)` | number | Pull items from adjacent inventory |

### Lua API — `storage_interface`

| Function | Returns | Description |
|----------|---------|-------------|
| `isConnected()` | boolean | Whether connected to a controller |
| `getPosition()` | {x,y,z} | Block position |
| `getControllerPosition()` | {x,y,z}/nil | Connected controller position |
| `size()` | number | Slots in the connected network |
| `list()` | table | All items in connected network |
| `getItemDetail(slot)` | table/nil | Item details by slot |
| `getBoxCount()` | number | Connected boxes via controller |
| `getItemCount(name)` | number | Count of specific item |
| `getItemSummary()` | [item] | Unique items with totals |
| `findItems(query)` | [item] | Search by partial name |

## Building from Source

```bash
git clone https://github.com/Apocscode/CreateLogicLink.git
cd CreateLogicLink
./gradlew build
```

Output: `build/libs/logiclink-0.1.0.jar`

**Note:** Clear `JAVA_HOME` if builds fail — Gradle auto-detects the JDK.

## Project Structure

```
src/main/java/com/apocscode/logiclink/
├── LogicLink.java                         # Mod entry point, PlayerTickEvent for highlights
├── ModRegistry.java                       # Block/item/BE/creative tab registration
├── block/
│   ├── LogicLinkBlock.java                # Logic Link (HorizontalDirectionalBlock)
│   ├── LogicLinkBlockEntity.java          # Network frequency, inventory cache
│   ├── LogicLinkBlockItem.java            # Frequency linking item
│   ├── LogicSensorBlock.java              # Sensor (FaceAttachedHorizontalDirectionalBlock)
│   ├── LogicSensorBlockEntity.java        # Frequency, target data cache
│   ├── LogicSensorBlockItem.java          # Frequency linking item
│   ├── RedstoneControllerBlock.java       # Redstone Controller (HorizontalDirectionalBlock)
│   ├── RedstoneControllerBlockEntity.java # Virtual redstone link channels
│   ├── CreativeLogicMotorBlock.java       # Creative Motor (DirectionalKineticBlock)
│   ├── CreativeLogicMotorBlockEntity.java # Speed control, sequences (GeneratingKineticBE)
│   ├── TrainMonitorBlock.java             # Train Monitor block
│   ├── TrainMonitorBlockEntity.java       # Train network data, diagnostics cache
│   ├── TrainMonitorMenu.java              # Container menu for monitor GUI
│   ├── SignalTabletItem.java              # Handheld signal diagnostic scanner
│   ├── TrainControllerBlock.java          # Train Controller block
│   ├── TrainControllerBlockEntity.java    # Train control logic
│   ├── LogicDriveBlock.java               # Logic Drive block
│   └── LogicDriveBlockEntity.java         # Drive data storage
├── peripheral/
│   ├── LogicLinkPeripheral.java           # 14 Lua functions
│   ├── LogicLinkPeripheralProvider.java   # Registers all peripherals with CC
│   ├── LogicSensorPeripheral.java         # 7 Lua functions
│   ├── RedstoneControllerPeripheral.java  # 8 Lua functions
│   ├── CreativeLogicMotorPeripheral.java  # 16 Lua functions
│   ├── LogicDrivePeripheral.java          # Logic Drive Lua functions (17 functions)
│   ├── TrainControllerPeripheral.java     # Train controller Lua functions
│   ├── TrainNetworkDataReader.java        # Train network scanner & diagnostics engine
│   ├── CreateBlockReader.java             # Reads Create block data via reflection
│   ├── StoragePeripheralCompat.java       # Soft-dependency guard for Create Storage
│   └── storage/
│       ├── StorageControllerPeripheral.java  # 14 Lua functions (Create Storage)
│       └── StorageInterfacePeripheral.java   # 10 Lua functions (Create Storage)
├── network/
│   ├── LinkNetwork.java                   # Link registry + highlight packet sender
│   ├── SensorNetwork.java                 # Sensor registry by frequency UUID
│   ├── NetworkHighlightPayload.java       # Server→client highlight packet
│   └── VirtualRedstoneLink.java           # IRedstoneLinkable impl for virtual channels
└── client/
    ├── NetworkHighlightRenderer.java      # Create-style outline renderer
    ├── SignalGhostRenderer.java           # In-world signal highlight boxes
    ├── SignalHighlightManager.java        # Client-side highlight toggle store
    ├── SignalTabletScreen.java            # Signal Diagnostic Tablet GUI
    ├── TrainMonitorRenderer.java          # Train Monitor block entity renderer
    ├── TrainMonitorScreen.java            # Train Monitor GUI with CTC map
    ├── TrainMapRenderer.java              # Network map rendering engine
    ├── TrainMapTexture.java               # Render-to-texture for map
    ├── LogicLinkClientSetup.java          # Registers Ponder plugin on FMLClientSetupEvent
    └── ponder/
        ├── LogicLinkPonderPlugin.java     # PonderPlugin impl: 12 tags + scene registration
        ├── LogicLinkPonderScenes.java     # Registers 5 storyboards with tag highlighting
        └── LogicLinkSceneAnimations.java  # 5 animated Ponder scenes
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

MIT License — see [LICENSE](LICENSE) for details.
