# Create: Logic Link Peripheral — NeoForge Mod

This is a Minecraft 1.21.1 NeoForge mod that bridges Create's logistics system with CC:Tweaked.

## Key Info
- **Mod ID:** logiclink
- **Package:** com.apocscode.logiclink
- **Java:** 21, NeoForge 21.1.77, Gradle 9.2.1 (ModDevGradle 2.0.140)
- **Dependencies:** Create 6.0.8, CC:Tweaked 1.117.0, Create Factory Abstractions (runtime)
- **Build:** `$env:JAVA_HOME = ""; .\gradlew.bat clean build --no-daemon`
- **Deploy:** Copy `build/libs/logiclink-0.1.0.jar` to ATM10 mods folder
- **Full docs:** See MOD_DOCUMENTATION.md in project root

## Project Structure
- `LogicLink.java` — Mod entry point
- `ModRegistry.java` — Block/item/BE registration
- `block/` — LogicLinkBlock, LogicSensorBlock + entities + block items
- `peripheral/` — LogicLinkPeripheral (14 Lua functions), LogicSensorPeripheral (7 Lua functions), CreateBlockReader, PeripheralProvider
- `network/SensorNetwork.java` — Static sensor registry by frequency UUID

## Two Blocks
1. **Logic Link** — Connects to Create logistics network, provides `logiclink` CC peripheral (inventory monitoring, gauge reading, item requests, sensor discovery)
2. **Logic Sensor** — Attaches to Create machines, reads kinetic/inventory/fluid data, discoverable wirelessly via Logic Link's getSensors()

## Important Notes
- JAVA_HOME must be cleared before building
- Factory Abstractions compatibility uses reflection to GenericLogisticsManager
- KineticBlockEntity stress/capacity fields are protected — accessed via reflection
- ATM10 mods path: `C:\Users\travf\curseforge\minecraft\Instances\All the Mods 10 - ATM10\mods\`
- CC computer #29 save path: `...\saves\Train2map\computercraft\computer\29\`
