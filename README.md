# Create: Logic Link Peripheral

A Minecraft NeoForge mod that bridges **Create mod's** logistics system with **CC:Tweaked** (ComputerCraft). Monitor inventory across an entire logistics network, read machine data from sensors, request item deliveries, and highlight connected blocks — all from Lua scripts.

## Features

- **Logic Link Block** — Full-size peripheral block that connects to Create's logistics network. Query inventory, enumerate gauges/links, and request item deliveries via Lua (14 functions).
- **Logic Sensor Block** — Thin surface-mount sensor that reads Create machine data (inventory, fluids, kinetic speed/stress, Blaze Burner heat). Attaches to any surface like a Stock Link (7 functions).
- **Redstone Controller Block** — Programmatic control over Create's Redstone Link wireless network. One block manages unlimited frequency channels from Lua — no GUI, no physical Redstone Links needed (8 functions).
- **Network Highlighting** — Hold a linked item to see all blocks on the network outlined in Create's signature blue.
- **Item Requests** — Send items through Create's packaging/delivery system from Lua scripts.
- **Wireless Sensor Discovery** — Logic Links can discover all Logic Sensors on the network via `getSensors()`.

## Requirements

| Dependency | Version | Purpose |
|---|---|---|
| Minecraft | 1.21.1 | Base game |
| NeoForge | 21.1.77+ | Mod loader |
| Create | 6.0.8+ | Logistics network |
| CC:Tweaked | 1.117.0+ | ComputerCraft peripheral API |
| Create Advanced Logistics | 0.3.0+ | Extended logistics |
| Create CC Logistics | 0.3.6+ | Create ↔ CC bridge |
| Create Unlimited Logistics | 1.2.1+ | Logistics extensions |

Developed and tested with the **All The Mods 10 (ATM10)** modpack.

## Getting Started

1. **Craft a Logic Link** (recipes coming soon — currently creative-only)
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
├── LogicLink.java                    # Mod entry point, PlayerTickEvent for highlights
├── ModRegistry.java                  # Block/item/BE/creative tab registration
├── block/
│   ├── LogicLinkBlock.java           # Logic Link (HorizontalDirectionalBlock)
│   ├── LogicLinkBlockEntity.java     # Network frequency, inventory cache
│   ├── LogicLinkBlockItem.java       # Frequency linking item
│   ├── LogicSensorBlock.java         # Sensor (FaceAttachedHorizontalDirectionalBlock)
│   ├── LogicSensorBlockEntity.java   # Frequency, target data cache
│   ├── LogicSensorBlockItem.java     # Frequency linking item
│   ├── RedstoneControllerBlock.java  # Redstone Controller (HorizontalDirectionalBlock)
│   └── RedstoneControllerBlockEntity.java # Virtual redstone link channels
├── peripheral/
│   ├── LogicLinkPeripheral.java      # 14 Lua functions
│   ├── LogicLinkPeripheralProvider.java
│   ├── LogicSensorPeripheral.java    # 7 Lua functions
│   ├── RedstoneControllerPeripheral.java # 8 Lua functions
│   └── CreateBlockReader.java        # Reads Create block data via reflection
├── network/
│   ├── LinkNetwork.java              # Link registry + highlight packet sender
│   ├── SensorNetwork.java            # Sensor registry by frequency UUID
│   ├── NetworkHighlightPayload.java  # Server→client highlight packet
│   └── VirtualRedstoneLink.java      # IRedstoneLinkable impl for virtual channels
└── client/
    └── NetworkHighlightRenderer.java # Create-style outline renderer
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

MIT License — see [LICENSE](LICENSE) for details.
