# Create: Logic Link Peripheral

A Minecraft NeoForge mod that bridges **Create mod's** logistical system with **ComputerCraft (CC:Tweaked)**, providing programmable peripherals to monitor and control Create's logistics network.

## Overview

Logic Link adds a peripheral block that, when placed adjacent to Create logistics components (depots, vaults, smart chutes, funnels, etc.), allows ComputerCraft computers and turtles to query inventory contents, monitor item flows, and interact with the Create network programmatically.

## Requirements

| Dependency | Version | Purpose |
|---|---|---|
| Minecraft | 1.21.1 | Base game |
| NeoForge | 21.1.77+ | Mod loader |
| Create | 6.0.8+ | Logistics system |
| CC: Tweaked | 1.117.0+ | Peripheral API |

## Building

### Prerequisites
- **Java 21 JDK** (Microsoft OpenJDK recommended)
- **IDE**: IntelliJ IDEA or Eclipse (recommended)

### Setup
```bash
# Clone the repository
git clone https://github.com/apocscode/CreateLogicLink.git
cd CreateLogicLink

# Generate IDE project files and download dependencies
# For IntelliJ IDEA, simply open the project — Gradle import is automatic.

# Build the mod
./gradlew build

# Run the client (dev environment)
./gradlew runClient

# Run the server (dev environment)
./gradlew runServer

# Generate data (blockstates, models, loot tables, etc.)
./gradlew runData
```

## Lua API Reference

When a ComputerCraft computer is adjacent to a Logic Link block:

```lua
local link = peripheral.wrap("logiclink")

-- Get the block's position
local pos = link.getPosition()
print("Logic Link at: " .. pos.x .. ", " .. pos.y .. ", " .. pos.z)

-- Get connected Create components
local components = link.getConnectedComponents()

-- Count a specific item across all connected inventories
local ironCount = link.getItemCount("minecraft:iron_ingot")
print("Iron ingots: " .. ironCount)

-- Get total items across all connected inventories
local total = link.getTotalItemCount()
print("Total items: " .. total)

-- Get network status
local info = link.getNetworkInfo()
print("Connected components: " .. info.connectedCount)
```

## Project Structure

```
src/main/java/com/apocscode/logiclink/
├── LogicLink.java                          # Main mod entrypoint
├── ModRegistry.java                        # Block/Item/BlockEntity registration
├── block/
│   ├── LogicLinkBlock.java                 # The Logic Link block
│   └── LogicLinkBlockEntity.java           # Block entity (scans adjacent Create blocks)
└── peripheral/
    ├── LogicLinkPeripheral.java            # CC:Tweaked peripheral (Lua API)
    └── LogicLinkPeripheralProvider.java    # Registers peripheral with CC:Tweaked
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

MIT License — see [LICENSE](LICENSE) for details.
