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
