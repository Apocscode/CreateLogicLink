# CreateLogicLink Development Session Log

This file maintains a persistent record of each development session so context is never lost.

---

## Session 1 — 2026-02-11 — Patchouli Documentation Rewrite

### Commits
- `9103016` — Rewrite Train Controller (8 pages, all fields), Storage Controller (7 pages, full Lua API), Storage Interface (5 pages, full Lua API + comparison)

### Summary
Complete rewrite of all Patchouli guide book entries with correct field descriptions, full Lua API documentation, and proper comparisons.

---

## Session 2 — 2026-02-15 — CTC Integration & Logic Remote/Contraption Remote

### Commits
- `8357d19` — Add CTC integration: Logic Remote item, Contraption Remote block, textures & models
- `31846e1` — Standalone remote control: GUI + direct drive/motor control without CTC
- `265693d` — Add standalone CTC controller port: Logic Remote with gamepad input, redstone link control, and frequency config GUI
- `f0ebc71` — Replace contraption_remote model with Create contraption_controls shape + logic remote controller face on top
- `b05c7bc` — Fix Logic Remote right-click feedback + Contraption Remote 3D buttons/joysticks
- `09c3bdf` — Contraption Remote block seat/mount mechanic

### Summary
Introduced two major features:
- **Logic Remote item** — handheld gamepad controller for Create's Redstone Link network and LogicLink drives/motors. 50 ghost slots (15 buttons × 2 + 10 axes × 2), gamepad input via GLFW, frequency config GUI.
- **Contraption Remote block** — block-based controller that reads gamepad input and sends it to bound contraption targets. Initially used seat/mount mechanic.

Created models, textures, blockstates, and initial GUI for both.

---

## Session 3 — 2026-02-16 (Morning) — CTC Feature Parity & GUI Polish

### Commits
- `96a51a3` — Logic Remote: WASD motor control, hub linking, motor config GUI, bug report button
- `656a077` — Contraption Remote: tight directional hitbox + 3D controls like remote item
- `4e56261` — Contraption Remote: replace seat mechanic with click-to-activate
- `63f3622` — CTC-style movement lock + Freq Config nav button
- `76e71ea` — Motor Config GUI overhaul: CTC-style layout with per-slot speed/direction
- `43fe73d` — Fix hub device discovery, add hub labels, CTC palette, aux keybinds
- `44e9b9d` — Rewrite LogicRemoteScreen as exact CTC GUI copy with 2-page tabs
- `087d3a9` — Fix GUI background rendering and page switching
- `2e51026` — Match CTC right-click pattern for Logic Remote

### Summary
Major push to match CTC (Create Tweaked Controllers) feature parity:
- Replaced seat mechanic with click-to-activate on Contraption Remote
- Rewrote LogicRemoteScreen as exact CTC-style GUI with 2-page tabs
- Added WASD motor control, hub linking, motor config GUI
- CTC-style movement lock when controller is active
- Tight directional VoxelShape hitboxes for Contraption Remote
- Hub device auto-discovery, labels, CTC color palette

---

## Session 4 — 2026-02-16 (Afternoon) — Textures, Models & Custom Renderer

### Commits
- `ad29b95` — Custom Logic Motor model with red edges and shaft stub
- `03186ee` — Use top-down controller texture for contraption remote tray
- `2e47ad5` — Improve contraption remote block/item textures and models
- `11b9c11` — Add LogicRemoteItemRenderer for floating controller animation

### Summary
Focused on visual polish and the critical right-click rendering fix:

**Texture work:**
- Extended `tools/GenerateTextures.java` with `generateContraptionRemoteTop()` — 32×32 gamepad texture with body, joysticks, d-pad, XYAB buttons, start/select, ∀ branding
- Brightened color palette for better visibility on the block tray (body grey 0x3C→0x50, tray bg 0x50→0x78, buttons brighter)
- Fixed UV viewport layout — original had features outside visible area
- Updated item model with `controller_top` texture and 3D button elements matching block model
- Deleted dead texture files (contraption_remote_front/side.png)

**Custom renderer (key fix):**
- Created `LogicRemoteItemRenderer` extending Create's `CustomRenderedItemModelRenderer`
- **Root cause**: Create's `LinkedControllerItemRenderer` checks `LinkedControllerClientHandler.MODE` — LogicLink never sets that. The new renderer checks `RemoteClientHandler.MODE` instead
- CTC-style floating transform: controller rises up in first-person view when active (`equipProgress` LerpedFloat, translate + rotate toward player)
- 15 button depression LerpedFloats track `RemoteClientHandler.buttonStates`
- BIND mode pulsating light flicker
- Active mode uses `renderSolidGlowing()` for powered look
- `tick()` hooked into `RemoteClientTickHandler`, `resetButtons()` called from `onReset()`

### Files Changed
| File | Change |
|------|--------|
| `client/LogicRemoteItemRenderer.java` | **New** — Custom renderer with floating animation + button lerps |
| `block/LogicRemoteItem.java` | Use new `LogicRemoteItemRenderer` instead of Create's `LinkedControllerItemRenderer` |
| `client/RemoteClientTickHandler.java` | Added `LogicRemoteItemRenderer.tick()` call |
| `controller/RemoteClientHandler.java` | Added `LogicRemoteItemRenderer.resetButtons()` in `onReset()` |
| `tools/GenerateTextures.java` | Brighter color palette for contraption_remote_top |
| `textures/block/contraption_remote_top.png` | Regenerated with brighter colors |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` (492,750 bytes) → ATM10 mods folder

---

## Session 5 — 2026-02-16 (Evening) — Exact CTC Visual Parity

### Commits
- `9e82106` — Make Logic Remote exactly match CTC Tweaked Controller

### Summary
Complete overhaul to make the Logic Remote visually and behaviorally identical to CTC's Tweaked Linked Controller:

**Item Renderer rewrite:**
- Rewrote `LogicRemoteItemRenderer` with CTC's exact rendering pattern
- **Idle state**: renders complete controller model (body + antennas + all buttons baked in)
- **Active state**: renders powered base PartialModel (glowing body + antennas) + individual button/joystick/trigger PartialModels with depression animations
- First-person floating transform handles both single-hand (larger displacement) and dual-hand (smaller displacement) holding
- Bind mode pulsating light flicker
- Added `earlyTick()` for equip progress separate from button input tick

**Partial models (CTC-exact structure):**
- Copied all CTC model JSONs adapted for `logiclink` namespace: `powered.json`, `button.json`, `joystick.json`, `trigger.json`, `button_a/b/x/y.json` (Xbox face buttons)
- Updated `item.json` to match CTC's complete 14KB Blockbench model with antennas, all buttons, joysticks, triggers
- Added `logic_remote_powered.png` texture for active/glowing state
- All models reference `logiclink:item/logic_remote` textures + `create:block/redstone_antenna` for antennas

**Right-click GUI removed:**
- Shift+right-click no longer opens the frequency config panel — just toggles active mode like CTC
- Updated tooltip to reflect new behavior

**Contraption Remote block TESR:**
- Created `ContraptionRemoteRenderer` (`SafeBlockEntityRenderer`) that renders the full 3D controller item model on the block's angled tray
- Uses `LogicRemoteItemRenderer.renderInLectern()` with CTC-style transforms (translate + rotateY for facing + offset + rotateZ for tray angle)
- Registered in `LogicLinkClientSetup`
- Removed all baked controller elements (joysticks, buttons, d-pad, start/select) from `contraption_remote.json` block model — now rendered dynamically by the TESR

### Files Changed
| File | Change |
|------|--------|
| `client/LogicRemoteItemRenderer.java` | Complete rewrite with PartialModel system and CTC rendering pattern |
| `client/ContraptionRemoteRenderer.java` | **New** — Block entity renderer for 3D controller on tray |
| `client/LogicLinkClientSetup.java` | Register ContraptionRemoteRenderer |
| `client/RemoteClientTickHandler.java` | Added `earlyTick()` call |
| `block/LogicRemoteItem.java` | Removed shift+RC GUI, updated tooltip |
| `models/item/logic_remote/item.json` | Replaced with CTC's full controller model |
| `models/item/logic_remote/powered.json` | **New** — Powered base partial model |
| `models/item/logic_remote/button.json` | **New** — Generic button partial |
| `models/item/logic_remote/joystick.json` | **New** — Joystick partial (stem + nub) |
| `models/item/logic_remote/trigger.json` | **New** — Trigger partial |
| `models/item/logic_remote/button_a/b/x/y.json` | **New** — Xbox face button partials |
| `textures/item/logic_remote_powered.png` | **New** — Powered/active texture |
| `models/block/contraption_remote.json` | Removed baked controller elements |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` (503,956 bytes) → ATM10 mods folder

*Last updated: 2026-02-16 — Session 5*

---

## Session 6 — 2026-02-16 — Fix Block TESR Position & Remove GUI

### Commits
- `4ab3fe3` — Fix controller position on block, remove right-click GUI

### Summary
Fixed three in-game bugs reported after Session 5 testing:

1. **Controller model too low on Contraption Remote block** — Raised y-translate from 1.0 to 1.25 in `ContraptionRemoteRenderer`
2. **Controller model offset depends on block facing direction** — Root cause: initial translate used z=0.6875 (not centered), so the Y rotation pivoted it off-center differently for each facing. Fixed by centering at z=0.5 before rotating, then applying the z-offset (-0.1875) in rotated local space
3. **Right-click GUI still opening on Logic Remote item** — Removed `implements MenuProvider` from `LogicRemoteItem`, deleted `createMenu()` and `getDisplayName()` methods. Moved MenuProvider to inline anonymous class in `OpenFreqConfigPayload` handler (freq config still works if reachable)

### Files Changed
| File | Change |
|------|--------|
| `client/ContraptionRemoteRenderer.java` | Center z at 0.5, raise y to 1.25, offset z after rotation |
| `block/LogicRemoteItem.java` | Remove `implements MenuProvider`, `createMenu()`, `getDisplayName()`, unused imports |
| `network/OpenFreqConfigPayload.java` | Inline anonymous MenuProvider instead of casting LogicRemoteItem |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` (504,633 bytes) → ATM10 mods folder

---

## Session 6b — 2026-02-16 — Adjust Block Offset & Restore Shift+RC GUI

### Commits
- `8128dae` — Fix block model offset, restore shift+RC GUI

### Summary
In-game testing showed the controller model was still off-center on the block (equal in all directions now, but shifted), and the shift+right-click frequency config GUI was incorrectly removed (only plain right-click should have been removed).

1. **Block model offset** — Adjusted post-rotation translate from `(0.28, 0, -0.1875)` to `(0.125, 0, 0)`. Removed the erroneous z-compensation and reduced forward offset by ~2.5 pixels
2. **Restored shift+right-click GUI** — Re-added `implements MenuProvider`, `createMenu()`, `getDisplayName()` to `LogicRemoteItem`. Added `isShiftKeyDown()` check in `use()`: shift+RC opens freq config GUI, plain RC toggles active mode

### Files Changed
| File | Change |
|------|--------|
| `client/ContraptionRemoteRenderer.java` | Post-rotation offset adjusted to `(0.125, 0, 0)` |
| `block/LogicRemoteItem.java` | Restore MenuProvider, add shift check in `use()` |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` (505,172 bytes) → ATM10 mods folder

### Additional Commits
- `eb34ac0` — Remove controller toggle from Contraption Remote block right-click
  - `ContraptionRemoteBlock.java`: Right-click no longer activates controller mode. Shift+RC still shows status. Removed `toggleControllerClient()`, `RemoteClientHandler` import, `@OnlyIn` imports.
  - **Jar**: `logiclink-0.1.0.jar` (504,996 bytes) → ATM10 mods folder
- `96196d8` — Remove active mode toggle from plain right-click on Logic Remote
  - `LogicRemoteItem.java`: Plain right-click now does nothing (was calling `RemoteClientHandler.toggle()` which showed a WASD overlay panel). Shift+right-click still opens freq config GUI.

*Last updated: 2026-02-16 — Session 6b*

---

## Session 6c — 2026-02-16 — Seated Controller Station

### Commits
- `caba8d3` — Remove WASD HUD overlay from Logic Remote
- `dc020ba` — Seated controller station: Contraption Remote activates from Create seat with animated buttons

### Summary
Transformed the Contraption Remote block into a seated controller station — player sits on a Create seat in front of the block, right-clicks to activate, and sees the same animated button/joystick model as the Logic Remote item. Both gamepad and WASD+Space keyboard input supported.

**Activation gate:**
- `ContraptionRemoteBlock`: Right-click now checks `player.isPassenger()` — only activates controller mode when the player is seated (on any entity, including Create seats). Unseat first to exit.

**Keyboard input (WASD+Space):**
- `RemoteClientHandler`: When in block mode (`activeBlockPos != null`), reads GLFW key state after gamepad fill. W=forward (axis 1 positive), S=backward (axis 1 negative), A=left (axis 0 negative), D=right (axis 0 positive), Space=A button. Gamepad takes priority if axis already has a value.

**Client-synced render state:**
- `ContraptionRemoteBlockEntity`: Added `renderButtonStates` (short) and `renderAxisStates` (int) fields, synced via `saveAdditional`/`getUpdateTag`/`getUpdatePacket`. Updated in `applyGamepadInput()` with `level.sendBlockUpdated()`. Cleared when gamepad times out.

**Animated buttons on block:**
- `LogicRemoteItemRenderer`: Added separate `blockButtons` LerpedFloat list (15 entries) driven by `blockButtonStates` static field. `tick()` now chases both item-mode (`buttons`) and block-mode (`blockButtons`) independently. `renderButton()`/`renderJoystick()` accept `buttonList` parameter to select which animation source to use.
- `ContraptionRemoteRenderer`: Reads `be.getRenderButtonStates()`/`getRenderAxisStates()`, calls `setBlockRenderState()`, passes `renderDepression=true` to `renderInLectern()`.

**HUD overlay removal:**
- Removed WASD overlay layer registration from `LogicLinkClientSetup` (commit `caba8d3`).

### Files Changed
| File | Change |
|------|--------|
| `block/ContraptionRemoteBlock.java` | Right-click gated by `isPassenger()`, restored `toggleControllerClient()` |
| `controller/RemoteClientHandler.java` | WASD+Space keyboard input merge in block mode |
| `block/ContraptionRemoteBlockEntity.java` | Added `renderButtonStates`/`renderAxisStates` with sync + getters |
| `client/ContraptionRemoteRenderer.java` | Read BE state, set block render state, `renderDepression=true` |
| `client/LogicRemoteItemRenderer.java` | Separate `blockButtons` list, parameterized render helpers |
| `client/LogicLinkClientSetup.java` | Removed WASD overlay registration |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` → ATM10 mods folder

---

## Session 6d — 2026-02-16 — Restore Logic Remote Right-Click Active Mode

### Commits
- `8024fe4` — Restore right-click active mode toggle on Logic Remote item

### Summary
Restored plain right-click on the Logic Remote item to toggle active controller mode (like CTC's Tweaked Controller). The joystick/button floating animation and depression rendering were already functional — only the `toggleActive()` call in `use()` had been disabled in Session 6b.

- **Plain right-click**: Toggles controller ACTIVE/IDLE mode with sound + chat message
- **Shift+right-click**: Opens frequency config GUI (unchanged)

### Files Changed
| File | Change |
|------|--------|
| `block/LogicRemoteItem.java` | Restored `toggleActive()` call on plain right-click |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` → ATM10 mods folder

---

## Session 6e — 2026-02-16 — Contraption Remote: Activate-Only + Auto-Idle + Antenna Glow

### Commits
- `65aaacd` — Contraption Remote: activate-only right-click, auto-idle on dismount, antenna glow

### Summary
Three behavior changes to the Contraption Remote block:

1. **Right-click only activates** — `toggleForBlock()` replaced with `activateForBlock()`. Right-clicking the block while seated only activates controller mode; cannot toggle off by right-clicking again.
2. **Auto-idle on seat dismount** — Added `!player.isPassenger()` check in `RemoteClientHandler.tick()` block-mode validation. When the player gets up from the seat, controller automatically goes IDLE with sound + chat message.
3. **Antenna glow on active/off on idle** — Added `renderActive` boolean synced to client via `saveAdditional`/`getUpdateTag`. Set `true` in `applyGamepadInput()`, cleared to `false` on gamepad timeout. Renderer reads `be.isRenderActive()` to choose powered base (glowing `redstone_antenna_powered` texture) or normal model (non-glowing `redstone_antenna` texture).

### Files Changed
| File | Change |
|------|--------|
| `controller/RemoteClientHandler.java` | `activateForBlock()` (activate-only), auto-idle on `!isPassenger()` |
| `block/ContraptionRemoteBlock.java` | Call `activateControllerClient()` instead of toggle |
| `block/ContraptionRemoteBlockEntity.java` | Added `renderActive` boolean sync + getter |
| `client/ContraptionRemoteRenderer.java` | Use `be.isRenderActive()` for active/antenna glow |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` → ATM10 mods folder

---

## Session 6f — 2026-02-16 — Joystick Tilt Animation

### Commits
- `bc6f4be` — Joystick tilt animation for both item and block controller

### Summary
Added smooth joystick tilt animation that visually emulates analog stick movement on the controller model. Works for both the handheld Logic Remote item and the Contraption Remote block.

- **4 axis LerpedFloats** per mode (item + block): Left X, Left Y, Right X, Right Y
- **Axis decoding**: 5-bit packed values (sign bit + 4-bit magnitude 0-15) decoded to -1.0..1.0 float
- **Tilt rendering**: Up to 15° rotation around X axis (forward/back) and Z axis (left/right), pivoting around the joystick base center
- **Smooth chase**: Uses `LerpedFloat.Chaser.EXP` at 0.4f speed for natural analog feel
- Item-mode reads from `RemoteClientHandler.axisStates`, block-mode reads from synced BE `blockAxisStates`

### Files Changed
| File | Change |
|------|--------|
| `client/LogicRemoteItemRenderer.java` | Added `joystickAxes`/`blockJoystickAxes` LerpedFloat arrays, `tickJoystickAxes()` decoder, tilt rotation in `renderJoystick()` |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` → ATM10 mods folder

---

## Session 7 — 2026-02-17 — Control Profile System: 8 Motors + 8 Aux Redstone

### Commits
- `aee043d` — feat: Control Profile system - 8 motor bindings, 8 aux redstone channels

### Summary
Complete motor/drive control and redstone aux system replacing the legacy 4-slot AxisConfig:

1. **ControlProfile data class** — 8 `MotorBinding` slots (targetPos, targetType, label, speed 1-256, reversed, sequential, distance) + 8 `AuxBinding` slots (label, power 1-15, momentary/constant, freqId pair). Full NBT serialization with migration path from old AxisConfig format. Stored in item CustomData under "ControlProfile" key.

2. **ControlConfigScreen** — Full-screen 400×260 GUI with 3-panel layout: left panel shows hub-connected devices (scrollable, 12 rows via HubNetwork discovery), center panel has 8 motor binding slots with speed/direction/sequential editing, right panel has 8 aux redstone slots with power level and momentary/constant toggle.

3. **3rd tab button** — Added to LogicRemoteConfigScreen using `AllIcons.I_CONFIG_OPEN`, positioned at x+67. Opens the new ControlConfigScreen. Also added bug report button (`I_PRIORITY_VERY_HIGH`) linking to GitHub issues.

4. **Sequential movement** — Wired in MotorAxisPayload handler: when sequential=true && distance>0, calls `clearSequence()` → `addRotateStep(degrees, speed)` → `runSequence(false)` on both LogicDriveBlockEntity and CreativeLogicMotorBlockEntity.

5. **8-axis motor keys** — RemoteClientHandler expanded from 4 WASD booleans to `float[8] motorKeyValues`: A/D (Left X), W/S (Left Y), ←/→ (Right X), ↑/↓ (Right Y), Q (LT), E (RT), Z (LB), C (RB). Added `computeKeyAxis()` and `keyValue()` helpers.

6. **Aux redstone channels** — Number keys 1-8 toggle aux channels. Created `AuxRedstonePayload` (8-bit channel mask) registered in LogicLink. Server handler reads ControlProfile, converts freq IDs to Create Frequency objects, sends via `RemoteServerHandler.receivePressed()`.

7. **Overlay expanded** — HUD overlay panel enlarged from 140×90 to 160×110, shows up to 8 motor binding labels with axis key indicators.

### Files Changed
| File | Change |
|------|--------|
| `controller/ControlProfile.java` | **NEW** — 8 MotorBinding + 8 AuxBinding data class, NBT, migration |
| `client/ControlConfigScreen.java` | **NEW** — 400×260 3-panel config GUI with hub device discovery |
| `network/AuxRedstonePayload.java` | **NEW** — 8-channel aux redstone packet (1-byte payload) |
| `client/LogicRemoteConfigScreen.java` | Added 3rd tab button (I_CONFIG_OPEN) + bug report button |
| `controller/RemoteClientHandler.java` | 8-axis motor keys, ControlProfile loading, aux redstone handling |
| `network/MotorAxisPayload.java` | Wired sequential movement (clearSequence/addRotateStep/runSequence) |
| `LogicLink.java` | Registered AuxRedstonePayload in network payloads |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` → ATM10 mods folder

---

## Session 7b — 2026-02-16 — Bug Fixes: Persistence, Save Feedback, Button Overlap

### Commits
- `7991fc7` — fix: ControlProfile persistence, save feedback, button overlap

### Summary
Fixed 3 bugs reported after in-game testing of the Control Profile system:

1. **ControlProfile not persisting** — Root cause: ControlConfigScreen is a plain `Screen`, not a container menu, so `saveToItem()` only modified the client-side ItemStack which was never synced to the server. Created `SaveControlProfilePayload` (client→server packet) that sends the profile NBT to the server, which applies it to the held item + writes legacy AxisConfig. `saveProfile()` now calls `PacketDistributor.sendToServer()`.

2. **Save button no feedback** — Added visual flash (1.5s bright green + "✔ Saved" label), experience orb pickup sound, and actionbar message. `saveFlashTicks` counter decremented in render loop.

3. **Button overlap on main GUI** — Tab buttons (firstTab, secondTab, thirdTab) and action buttons (nextDevice at x+78, etc.) overlapped because thirdTab at x+67 (18px wide → x+85) crossed into nextDevice territory, and bugButton at x+49 overlapped secondTab at x+42. Fix: tightened tab spacing to 20px (tab2→x+37, tab3→x+57), moved bugButton to extra area outside the main GUI background (x+width+6).

### Files Changed
| File | Change |
|------|--------|
| `network/SaveControlProfilePayload.java` | **NEW** — Client→server packet to persist ControlProfile NBT to held item |
| `client/ControlConfigScreen.java` | saveProfile() sends packet to server; save flash feedback with sound |
| `client/LogicRemoteConfigScreen.java` | Fixed tab button spacing (20px), moved bugButton to extra area |
| `LogicLink.java` | Registered SaveControlProfilePayload |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` → ATM10 mods folder

---

## Session 7c — 2026-02-17 — Auto-Save, Seated Interaction, Button Layout

### Commits
- `df79728` — fix: auto-save persistence, seated interaction, button layout

### Summary
Fixed 3 remaining issues from in-game testing:

1. **Auto-save persistence** — Added `autoSave()` method to ControlConfigScreen that sends `SaveControlProfilePayload` to the server immediately on every change (assign device, clear slot, toggle direction, commit speed/power edit, toggle momentary/constant, scroll speed/power). Profile now persists across GUI close/reopen without requiring manual Save button click.

2. **Contraption Remote seated interaction** — Removed `isShiftKeyDown()` checks (shift dismounts from Create seats, making shift+click impossible while seated). Now context-based: seated right-click = activate controller mode, standing right-click = show status in chat. No modifier key needed.

3. **Button layout overhaul** — Motor config button (`I_CONFIG_OPEN`) moved from bottom tab row to header bar (top-right of GUI, `x+width-20, y-1`). Bug report button (`I_DISABLE` icon for warning feel) moved to extra area top-right (`x+width+6, y+2`), well above the main GUI.

### Files Changed
| File | Change |
|------|--------|
| `client/ControlConfigScreen.java` | Added `autoSave()` on every change (10 call sites) |
| `block/ContraptionRemoteBlock.java` | Removed shift+click, context-based: seated=activate, standing=status |
| `client/LogicRemoteConfigScreen.java` | Motor config button → header, bug button → top-right extra area with I_DISABLE |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` → ATM10 mods folder

---

## Session 7d — 2026-02-17 — Critical Persistence Fix (saveData nuking CustomData)

### Commits
- `d69975d` — fix: LogicRemoteMenu.saveData() was destroying all CustomData

### Summary
Motor/drive list and settings were STILL not persisting across GUI close/reopen despite auto-save working correctly. Added debug logging to ControlConfigScreen.init(), autoSave(), and SaveControlProfilePayload.handle().

**Root cause**: `LogicRemoteMenu.saveData()` (called when the frequency config container screen closes) created a brand new `CompoundTag()`, put only `"Items"` into it, and replaced the ENTIRE CustomData component. This destroyed ControlProfile, AxisConfig, HubLinked, HubX/Y/Z, HubLabel every time.

**Fix**: Changed `saveData()` to read existing CustomData first via `contentHolder.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()`, then merge the `"Items"` key into the existing tag instead of replacing it.

### Files Changed
| File | Change |
|------|--------|
| `controller/LogicRemoteMenu.java` | Fixed `saveData()` to preserve existing CustomData (was creating fresh CompoundTag) |
| `client/ControlConfigScreen.java` | Added debug logging to init() and autoSave() |
| `network/SaveControlProfilePayload.java` | Added debug logging to handler |

---

## Session 7e — 2026-02-17 — Fix Motor Control in Block Mode (Seated)

### Commits
- `966fa0d` — Fix: ControlProfile motor bindings now work in Block Mode (seated)

### Summary
After persistence was confirmed working, user reported motors assigned in ControlConfigScreen were not being controlled when using the Contraption Remote block while seated.

**Root cause**: `RemoteClientHandler.activateForBlock()` set `cachedAxisConfig = null`, so the motor axis control loop (which sends `MotorAxisPayload` to each bound motor) never executed in Block Mode. ControlProfile motor bindings were ONLY used in Item Mode (via `toggle()`).

**Fix (2 changes to RemoteClientHandler)**:
1. `activateForBlock()` now loads `cachedProfile` and `cachedAxisConfig` from the player's held LogicRemoteItem (same pattern as `toggle()`)
2. Motor Axis Control loop and Aux Redstone Channels loop moved OUTSIDE the block/item if/else branch — they now run in BOTH modes

Block Mode now sends: SeatInputPayload (block entity targets) + MotorAxisPayload (ControlProfile motors) + AuxRedstonePayload (aux channels).

### Files Changed
| File | Change |
|------|--------|
| `controller/RemoteClientHandler.java` | Load ControlProfile in activateForBlock(); motor+aux loops run in both modes |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` → ATM10 mods folder

---

## Session 7f — 2026-02-17 — Gamepad Analog Joystick → Motor Control

### Commits
- `931d677` — Fix: Gamepad joystick/trigger analog input now controls motors

### Summary
User confirmed keyboard W/S works for motor control, but gamepad joystick does not. Also requested 2 motors per joystick (left X + left Y, right X + right Y).

**Root cause**: Motor axis control loop only sampled keyboard via `computeKeyAxis()`/`keyValue()`. Gamepad analog stick values from `GamepadInputs.GetAxis()` were never read or merged into `motorKeyValues[]`.

**Fix (2 files)**:

1. **RemoteClientHandler** — After keyboard sampling, merge gamepad analog values into `motorKeyValues[]` with keyboard priority and 0.15 deadzone:
   - Left stick X/Y (GLFW axes 0,1) → `motorKeyValues[0,1]` (Y inverted for GLFW convention)
   - Right stick X/Y (GLFW axes 2,3) → `motorKeyValues[2,3]` (Y inverted)
   - Left/Right trigger (GLFW axes 4,5) → `motorKeyValues[4,5]` (normalized from [-1,1] to [0,1])
   - Left/Right bumper (buttons 4,5) → `motorKeyValues[6,7]`

2. **MotorAxisPayload handler** — Proportional analog speed control:
   - Continuous mode: replaced `Math.signum(axisValue)` with raw `axisValue` for proportional motor speed (half-push = half speed, full push = full speed, keyboard still sends ±1.0 for full speed)
   - Sequential mode: raised trigger threshold from 0.01f to 0.5f to prevent accidental analog triggers

**Joystick axis mapping** (already supported, now works with gamepad):
   - Left stick: slot 0 (left/right) + slot 1 (up/down) = 2 motors
   - Right stick: slot 2 (left/right) + slot 3 (up/down) = 2 motors
   - Triggers: slot 4 (LT) + slot 5 (RT) = 2 motors
   - Bumpers: slot 6 (LB) + slot 7 (RB) = 2 motors

### Files Changed
| File | Change |
|------|--------|
| `controller/RemoteClientHandler.java` | Merge gamepad analog values into motorKeyValues after keyboard sampling |
| `network/MotorAxisPayload.java` | Proportional axisValue for continuous mode; 0.5 threshold for sequential |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` → ATM10 mods folder
---

## Session 7g — 2026-02-17 — 12 Unidirectional Motor Slots + Scrollable Motor Panel

### Commits
- `2d30dca` — Add scrollable motor panel for 12 unidirectional motor slots

### Summary
Expanded the motor binding system from 8 bidirectional axes to 12 unidirectional directions. Each joystick direction now gets its own independent motor slot, enabling full 4-direction control per stick.

**Slot layout (12 total)**:
- Slots 0–3: Left stick — L Up (W), L Down (S), L Left (A), L Right (D)
- Slots 4–7: Right stick — R Up (↑), R Down (↓), R Left (←), R Right (→)
- Slots 8–11: Triggers/bumpers — LT (Q), RT (E), LB (Z), RB (C)

**Changes**:
1. **ControlProfile** — `MAX_MOTOR_BINDINGS` 8→12, expanded axis labels/keys arrays, updated `migrateFromAxisConfig()` with old→new index mapping
2. **RemoteClientHandler** — Rewrote keyboard sampling as 12 unidirectional `keyValue()` calls; gamepad merge splits each stick into 4 directions (up/down/left/right); overlay WASD derivation and renderKey indices updated
3. **ControlConfigScreen** — Added scrollable motor panel (`motorScrollOffset`, `MOTOR_ROWS_VISIBLE=8`); scroll indicators (▲/▼ arrows); speed scroll-wheel still works when hovering speed field; keyColors extended to 12; `saveLegacyAxisConfig` writes all 12 slots

### Files Changed
| File | Change |
|------|--------|
| `controller/ControlProfile.java` | MAX_MOTOR_BINDINGS 8→12, labels/keys expanded, migration updated |
| `controller/RemoteClientHandler.java` | 12 unidirectional keyboard+gamepad sampling, overlay/renderKey indices |
| `client/ControlConfigScreen.java` | Scrollable motor panel, scroll indicators, extended key colors |

### Deployed
- **Jar**: `logiclink-0.1.0.jar` → ATM10 mods folder

---

## Session 7h -- 2026-02-17 -- Logic Drive Consumer+Generator Architecture

### Commits
- `a7d5290` -- Rewrite Logic Drive as Consumer+Generator

### Summary
Rewrote LogicDriveBlockEntity from SplitShaftBlockEntity to GeneratingKineticBlockEntity. Input side reads neighbor speed without kinetic connection. Output side generates rotation independently (256 SU capacity). Direction changes no longer cause block breakage.

### Files Changed
| File | Change |
|------|--------|
| block/LogicDriveBlockEntity.java | Full rewrite: SplitShaft to GeneratingKinetic |
| block/LogicDriveBlock.java | hasShaftTowards output-only |
| peripheral/LogicDrivePeripheral.java | Added getStressCapacity and getStressUsage |
| peripheral/LogicLinkPeripheral.java | getRemoteMotorInfo includes stress for drives |

### Deployed
- Jar: logiclink-0.1.0.jar to ATM10 mods folder

---

## Session 7j -- 2026-02-17 -- Fix Stick Animation Direction + Motor Speed Override

### Commits
- `1d6634f` -- Fix stick animation direction + motor speed per-slot override

### Summary
1. **Joystick tilt animation reversed** — The 3D joystick stick on the Logic Remote was tilting the wrong direction. W (forward) tilted the stick toward the player instead of away. Fixed by negating `tiltX` and removing negation from `tiltZ` in `renderJoystick()`.
2. **Motor speed per-slot override** — When two direction slots (e.g., L Up and L Down) targeted the same motor with different speeds, pressing one direction would send both a spin packet (from the active slot) and a STOP packet (from the inactive slot with axisValue=0). The STOP always won because `keysChanged` was global. Fixed: zero-value packets are now only sent when that specific slot's key actually changed (released), not when any unrelated key changed.
3. **Legacy AxisConfig save** — Server-side SaveControlProfilePayload was capping legacy AxisConfig at 8 slots; now saves all 12.

### Files Changed
| File | Change |
|------|--------|
| client/LogicRemoteItemRenderer.java | Negate tiltX, un-negate tiltZ for correct stick direction |
| controller/RemoteClientHandler.java | Per-slot key change detection for motor axis packets |
| network/SaveControlProfilePayload.java | Legacy AxisConfig writes all 12 slots |

### Deployed
- Jar: logiclink-0.1.0.jar to ATM10 mods folder

### Commits
- `53c45d6` -- Swap drive marker colors: blue=input, orange=output
- `b87d50d` -- Fix ControlProfile: expand motor bindings from 8 to 12

### Summary
1. **Drive marker color swap** — Changed input marker to blue (light_blue_concrete), output marker to orange (orange_concrete). Updated LogicDriveBlock javadoc.
2. **ControlProfile 12-slot fix** — Session 7g's ControlProfile changes never actually applied. Fixed: MAX_MOTOR_BINDINGS 8→12, MOTOR_AXIS_LABELS to 12 entries (L Up/Down/Left/Right, R Up/Down/Left/Right, LT/RT/LB/RB), MOTOR_AXIS_KEYS to 12 entries (W/S/A/D, ↑/↓/←/→, Q/E/Z/C), migration maps old bidirectional axes to positive-direction slots. This resolves the mismatch with RemoteClientHandler (already float[12]) and enables the scrollable motor panel in ControlConfigScreen.

### Files Changed
| File | Change |
|------|--------|
| models/block/motor_input_marker.json | orange_concrete → light_blue_concrete |
| models/block/motor_output_marker.json | light_blue_concrete → orange_concrete |
| block/LogicDriveBlock.java | Javadoc: blue=input, orange=output |
| controller/ControlProfile.java | MAX_MOTOR_BINDINGS 8→12, labels/keys/migration updated |

### Deployed
- Jar: logiclink-0.1.0.jar to ATM10 mods folder

---

## Session 7k -- 2026-02-17 -- Fix Joystick Tilt Axes + Drive Speed Control

### Commits
- `d7e770b` -- Fix joystick tilt axes + drive speed from configured RPM

### Summary
1. **Joystick animation axes swapped** — Up/down input was tilting the 3D joystick stick left/right instead of forward/back. Root cause: Y-axis input was applied to `rotateX` and X-axis input to `rotateZ`, but the model's coordinate system has X running top→bottom and Z running right→left on the controller face. Swapped: Y input → `rotateZ` (forward/back tilt), X input → `rotateX` (left/right tilt). Also reordered rotations to rotateZ-then-rotateX for correct gimbal behavior.
2. **Drive speed ignoring configured RPM** — Setting axis speed to 5 RPM with a 256 RPM input source still ran the drive at 256. Root cause: modifier was `axisValue * max(1.0, speed/16.0)` — for any speed < 16, the modifier was always 1.0, making output = input speed unchanged. Fixed: modifier is now computed as `desiredSpeed / inputSpeed`, so output = `inputSpeed * (speed / inputSpeed)` = the configured speed exactly.

### Files Changed
| File | Change |
|------|--------|
| client/LogicRemoteItemRenderer.java | Swap tiltX↔tiltZ, reorder rotateZ before rotateX |
| network/MotorAxisPayload.java | Drive modifier = desiredSpeed/inputSpeed for both continuous and sequential |

### Deployed
- Jar: logiclink-0.1.0.jar to ATM10 mods folder

---

## Session 7l -- 2026-02-17 -- Fix Joystick Left/Right Tilt Inversion

### Commits
- `364c39c` -- Fix joystick left/right tilt inversion - negate X axis rotation

### Summary
Left/right joystick animation was still reversed after session 7k (A tilted right, D tilted left). The X-axis `tiltX` value needed negation to match the model's coordinate system.

### Files Changed
| File | Change |
|------|--------|
| client/LogicRemoteItemRenderer.java | Negate X-axis tiltX value for correct left/right direction |

### Deployed
- Jar: logiclink-0.1.0.jar to ATM10 mods folder

---

## Session 7m -- 2026-02-17 -- Sequential/Continuous Toggle + Distance Field in Motor Config GUI

### Commits
- `5dc0715` -- Add Sequential/Continuous toggle + distance field to motor config GUI

### Summary
Added interactive UI controls for sequential movement mode in the ControlConfigScreen motor slot rows. Each assigned motor row now shows a **[C]/[S] toggle button** on the bottom-left that switches between Continuous (hold key = proportional speed) and Sequential (tap key = rotate a fixed distance then stop). When Sequential is active, a **distance field** (1-64m) appears next to the toggle, editable by clicking (keyboard input) or scroll wheel (hold Shift for ±10). The backend already supported sequential mode via `MotorBinding.sequential`/`distance` and `MotorAxisPayload` — this commit exposes it in the GUI.

### Files Changed
| File | Change |
|------|--------|
| client/ControlConfigScreen.java | Added [C]/[S] toggle, distance field render/click/scroll/keyboard edit, commitDistanceEdit() |

### Deployed
- Jar: logiclink-0.1.0.jar to ATM10 mods folder
