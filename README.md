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
- **Logic Remote Item** — Handheld gamepad controller that bridges keyboard/gamepad input to Create's Redstone Link network and LogicLink drives/motors. Bind buttons to redstone frequencies, map analog sticks to motor speeds, and configure up to 8 aux channels with variable power levels (1–15) and toggle/momentary modes. Right-click a Redstone Link to bind, right-click a Drive/Motor to add as target, shift-right-click to open the config GUI.
- **Contraption Remote Block** — Placeable controller block with integrated seat mechanics. Sit on an adjacent Create seat, then right-click the block to take control. Gamepad/keyboard input drives bound motors and fires redstone link signals — works on stationary builds and on moving contraptions. Shift-right-click while standing opens the config GUI, which stores the profile in the block entity (no held item needed). Supports the same 12 motor bindings and 8 aux redstone channels as the Logic Remote.
- **Gamepad Support** — Full Xbox-style gamepad input via GLFW. 15 buttons, 6 axes (left/right stick + triggers), auto-detection of connected controllers, multi-gamepad selection.

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

## Logic Remote

A handheld gamepad controller item that maps keyboard and gamepad input to Create's Redstone Link network and LogicLink drives/motors. No computer or Lua needed — all control is real-time from the player's input.

### Usage

| Action | Effect |
|--------|--------|
| **Right-click** (air) | Toggle ACTIVE mode — input is captured and sent as signals |
| **Shift + right-click** (air) | Open the Control Config GUI |
| **Right-click** on Redstone Link | Enter BIND mode — press a button/axis to bind it to that link's frequency |
| **Right-click** on Logic Drive / Creative Logic Motor | Add as a control target (up to 8) |
| **Shift + right-click** on Logic Link hub | Link the remote for device discovery |

### Control Config GUI

Three-panel configuration screen:

| Panel | Content |
|-------|---------|
| **Left — Devices** | Scrollable list of hub-connected motors/drives |
| **Center — Motor Bindings** | 12 slots mapping gamepad directions to motor speed/direction |
| **Right — Aux Bindings** | 8 slots mapping buttons to redstone link frequencies with power (1–15) and toggle/momentary mode |

**Motor binding slots:** L-Stick (W/S/A/D), R-Stick (↑/↓/←/→), LT/RT/LB/RB (Q/E/Z/C)
**Aux binding slots:** Numpad 1–8 (keyboard) or D-pad + face buttons (gamepad)

### Binding Flow

1. Link the remote to a Logic Link hub (shift-right-click the hub block)
2. Open the config GUI and assign motors/drives to binding slots
3. Set speed, direction, and sequential options per motor slot
4. For aux channels: pick two frequency items and set power level (1–15)
5. Right-click in air to activate — your input now controls everything in real time

## Contraption Remote

A placeable controller block that provides the same gamepad/keyboard control as the Logic Remote, but stored in a block entity. Designed for permanent installations and moving contraptions.

### Usage

| Action | Effect |
|--------|--------|
| **Shift + right-click** (standing) | Open the Control Config GUI (profile saved to block) |
| **Right-click** (seated on adjacent Create seat) | Activate controller — gamepad input is routed to bound targets |
| **Right-click** (standing, no item) | Show status in chat |
| **Dismount seat** (Shift) | Automatically deactivates controller |

### How It Works

1. Place the Contraption Remote block
2. Place a Create seat nearby
3. Shift-right-click the block to configure motor and aux bindings
4. Sit on the seat, then right-click the Contraption Remote
5. Your keyboard/gamepad input now controls bound motors and fires redstone link signals
6. Works on stationary builds and on moving contraptions (assembled with Create's glue)

### Contraption Behavior

When the block is assembled onto a contraption:
- The controller stays active with a 10-tick grace period during assembly transitions
- Motor control packets target in-world block entities directly (independent of block position)
- Aux redstone signals use the embedded profile — no held-item or block-entity lookup needed
- The redstone link network is global (frequency-matched), so signals reach receivers anywhere in loaded chunks

## Crafting Recipes

All blocks and items use shaped crafting recipes with Create and vanilla ingredients.

### Logic Link
```
┌─────────────┬─────────────────┬─────────────┐
│             │   Ender Pearl   │             │
├─────────────┼─────────────────┼─────────────┤
│ Brass Ingot │ Andesite Casing │ Brass Ingot │
├─────────────┼─────────────────┼─────────────┤
│             │   Comparator    │             │
└─────────────┴─────────────────┴─────────────┘
```

### Logic Sensor
```
┌──────────────┬─────────────────┬──────────────┐
│              │    Observer     │              │
├──────────────┼─────────────────┼──────────────┤
│ Copper Ingot │ Andesite Casing │ Copper Ingot │
├──────────────┼─────────────────┼──────────────┤
│              │  Nether Quartz  │              │
└──────────────┴─────────────────┴──────────────┘
```

### Redstone Controller
```
┌─────────────┬─────────────────┬─────────────┐
│             │   Ender Pearl   │             │
├─────────────┼─────────────────┼─────────────┤
│ Brass Ingot │ Andesite Casing │ Brass Ingot │
├─────────────┼─────────────────┼─────────────┤
│             │ Redstone Block  │             │
└─────────────┴─────────────────┴─────────────┘
```

### Creative Logic Motor
```
┌─────────────┬───────────────┬─────────────┐
│             │  Ender Pearl  │             │
├─────────────┼───────────────┼─────────────┤
│ Brass Ingot │ Electron Tube │ Brass Ingot │
├─────────────┼───────────────┼─────────────┤
│             │     Shaft     │             │
└─────────────┴───────────────┴─────────────┘
```

### Logic Drive
```
┌─────────────┬─────────────────┬─────────────┐
│             │   Comparator    │             │
├─────────────┼─────────────────┼─────────────┤
│ Brass Ingot │ Andesite Casing │ Brass Ingot │
├─────────────┼─────────────────┼─────────────┤
│             │      Shaft      │             │
└─────────────┴─────────────────┴─────────────┘
```

### Train Controller
```
┌─────────────┬─────────────────┬─────────────┐
│ Ender Pearl │ Railway Casing  │ Ender Pearl │
├─────────────┼─────────────────┼─────────────┤
│ Brass Ingot │ Andesite Casing │ Brass Ingot │
├─────────────┼─────────────────┼─────────────┤
│             │   Comparator    │             │
└─────────────┴─────────────────┴─────────────┘
```

### Train Monitor
```
┌──────────────┬─────────────────┬──────────────┐
│ Tinted Glass │  Tinted Glass   │ Tinted Glass │
├──────────────┼─────────────────┼──────────────┤
│ Tinted Glass │ Railway Casing  │ Tinted Glass │
├──────────────┼─────────────────┼──────────────┤
│ Brass Ingot  │ Andesite Casing │  Brass Ingot │
└──────────────┴─────────────────┴──────────────┘
```

### Signal Diagnostic Tablet
```
┌─────────────┬────────────────┬─────────────┐
│             │  Tinted Glass  │             │
├─────────────┼────────────────┼─────────────┤
│ Brass Ingot │ Railway Casing │ Brass Ingot │
├─────────────┼────────────────┼─────────────┤
│             │  Comparator    │             │
└─────────────┴────────────────┴─────────────┘
```

### Logic Remote & Contraption Remote
Currently available via creative tab or `/give` command. Crafting recipes coming soon.

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
│   ├── LogicDriveBlockEntity.java         # Drive data storage
│   ├── LogicRemoteItem.java              # Handheld gamepad controller item
│   ├── LogicRemoteMenu.java              # 50-slot ghost frequency config menu
│   ├── ContraptionRemoteBlock.java       # Placeable gamepad controller block
│   └── ContraptionRemoteBlockEntity.java # Block entity with profile, targets, gamepad state
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
├── controller/
│   ├── ControlProfile.java               # Motor + aux binding configuration (12 motors, 8 aux)
│   ├── RemoteClientHandler.java          # Client-side controller state machine (IDLE/ACTIVE/BIND)
│   └── RemoteServerHandler.java          # Server-side signal dispatch
├── input/
│   ├── ControllerOutput.java             # Gamepad/keyboard input aggregation
│   └── GamepadInputs.java                # GLFW gamepad polling (15 buttons, 6 axes)
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
    ├── ControlConfigScreen.java           # Motor + aux binding config GUI (item & block mode)
    ├── MotorConfigScreen.java             # Legacy motor config screen
    └── ponder/
        ├── LogicLinkPonderPlugin.java     # PonderPlugin impl: 12 tags + scene registration
        ├── LogicLinkPonderScenes.java     # Registers 5 storyboards with tag highlighting
        └── LogicLinkSceneAnimations.java  # 5 animated Ponder scenes
```

## Acknowledgments

The **Logic Remote** and **Contraption Remote** controller systems are inspired by and partially ported from [**Create: Tweaked Controllers**](https://github.com/getItemFromBlock/Create-Tweaked-Controllers) by [getItemFromBlock](https://github.com/getItemFromBlock). The gamepad input handling, frequency-based redstone binding, and lectern-mounted controller mechanics originate from that project.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

MIT License — see [LICENSE](LICENSE) for details.
