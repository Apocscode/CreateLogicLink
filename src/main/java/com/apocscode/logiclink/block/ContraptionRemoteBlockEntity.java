package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.compat.TweakedControllerCompat;
import com.apocscode.logiclink.compat.TweakedControllerReader;
import com.apocscode.logiclink.network.HubNetwork;
import com.apocscode.logiclink.network.IHubDevice;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity for the Contraption Remote Control block.
 * <p>
 * Stores bindings to:
 * <ul>
 *   <li>A CTC Tweaked Lectern (input source)</li>
 *   <li>Up to 8 Logic Drives / Creative Logic Motors (output targets)</li>
 * </ul>
 * <p>
 * Every tick (throttled), reads controller state from the lectern and
 * applies axis/button mappings to the bound drives and motors.
 * </p>
 */
public class ContraptionRemoteBlockEntity extends BlockEntity {

    /** Maximum targets this block can control. */
    public static final int MAX_TARGETS = 8;

    /** Linked lectern position (controller input). */
    private BlockPos lecternPos = null;

    /** Target drive/motor positions and types. */
    private final List<TargetEntry> targets = new ArrayList<>();

    /** Whether this block is actively controlling (has valid input + targets). */
    private boolean active = false;

    /** Tick counter for throttling updates. */
    private int tickCounter = 0;

    /** User-assigned label. */
    private String label = "";

    // ==================== Direct Control State (standalone, no CTC needed) ====================
    /** Current drive speed modifier set via GUI. */
    private float directDriveModifier = 0f;
    /** Current drive enabled state set via GUI. */
    private boolean directDriveEnabled = false;
    /** Current drive reversed state set via GUI. */
    private boolean directDriveReversed = false;
    /** Current motor speed set via GUI. */
    private int directMotorSpeed = 0;
    /** Current motor enabled state set via GUI. */
    private boolean directMotorEnabled = false;
    /** Whether direct control values have been updated and need applying. */
    private boolean directControlDirty = false;

    public ContraptionRemoteBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CONTRAPTION_REMOTE_BE.get(), pos, state);
    }

    // ==================== Server Tick ====================

    public void serverTick() {
        if (level == null || level.isClientSide) return;

        tickCounter++;
        if (tickCounter < 2) return; // every 2 ticks
        tickCounter = 0;

        if (targets.isEmpty()) {
            active = false;
            return;
        }

        // Priority 1: CTC gamepad input (when CTC is installed and lectern is linked)
        if (TweakedControllerCompat.isLoaded() && lecternPos != null) {
            TweakedControllerReader.ControllerData data = TweakedControllerReader.readFromLectern(level, lecternPos);
            if (data != null && data.hasUser()) {
                active = true;
                for (TargetEntry target : targets) {
                    applyToTarget(data, target);
                }
                return;
            }
        }

        // Priority 2: Direct control from GUI (standalone)
        if (directControlDirty) {
            active = true;
            directControlDirty = false;
            for (TargetEntry target : targets) {
                applyDirectControl(target);
            }
            return;
        }

        // If we had direct controls set, keep applying them
        if (directDriveEnabled || directMotorEnabled || directDriveModifier != 0 || directMotorSpeed != 0) {
            active = true;
            for (TargetEntry target : targets) {
                applyDirectControl(target);
            }
        } else {
            active = false;
        }
    }

    private void applyToTarget(TweakedControllerReader.ControllerData data, TargetEntry target) {
        if (level == null) return;
        BlockEntity be = level.getBlockEntity(target.pos);
        if (be == null || be.isRemoved()) return;

        if (be instanceof LogicDriveBlockEntity drive) {
            // Left stick Y → modifier, A → enable, Y → reverse
            float axisY = data.getAxis(1);
            float modifier = Math.round(axisY * 16.0f * 2.0f) / 2.0f;
            if (Math.abs(modifier) < 0.25f) modifier = 0;
            drive.setSpeedModifier(modifier);
            drive.setMotorEnabled(data.getButton(0));
            drive.setReversed(data.getButton(3));
        } else if (be instanceof CreativeLogicMotorBlockEntity motor) {
            // Right trigger → positive speed, Left trigger → negative speed
            float speed = data.getAxis(5) * 256.0f;
            float leftTrigger = data.getAxis(4);
            if (leftTrigger > 0.1f) speed = -leftTrigger * 256.0f;
            speed = Math.round(speed);
            if (Math.abs(speed) < 4) speed = 0;
            motor.setMotorSpeed((int) speed);
            motor.setEnabled(data.getButton(1));
        }
    }

    /**
     * Apply direct control state (from GUI) to a target.
     */
    private void applyDirectControl(TargetEntry target) {
        if (level == null) return;
        BlockEntity be = level.getBlockEntity(target.pos);
        if (be == null || be.isRemoved()) return;

        if (be instanceof LogicDriveBlockEntity drive) {
            drive.setSpeedModifier(directDriveModifier);
            drive.setMotorEnabled(directDriveEnabled);
            drive.setReversed(directDriveReversed);
        } else if (be instanceof CreativeLogicMotorBlockEntity motor) {
            motor.setMotorSpeed(directMotorSpeed);
            motor.setEnabled(directMotorEnabled);
        }
    }

    // ==================== Direct Control Setters (called from packet handler) ====================

    public void setDirectDriveModifier(float modifier) {
        this.directDriveModifier = modifier;
        this.directControlDirty = true;
        setChanged();
    }

    public void setDirectDriveEnabled(boolean enabled) {
        this.directDriveEnabled = enabled;
        this.directControlDirty = true;
        setChanged();
    }

    public void setDirectDriveReversed(boolean reversed) {
        this.directDriveReversed = reversed;
        this.directControlDirty = true;
        setChanged();
    }

    public void setDirectMotorSpeed(int speed) {
        this.directMotorSpeed = speed;
        this.directControlDirty = true;
        setChanged();
    }

    public void setDirectMotorEnabled(boolean enabled) {
        this.directMotorEnabled = enabled;
        this.directControlDirty = true;
        setChanged();
    }

    // ==================== Binding Management ====================

    public void setLecternPos(BlockPos pos) {
        this.lecternPos = pos;
        setChanged();
    }

    public BlockPos getLecternPos() {
        return lecternPos;
    }

    public boolean addTarget(BlockPos pos, String type) {
        if (targets.size() >= MAX_TARGETS) return false;
        // Check for duplicates
        for (TargetEntry t : targets) {
            if (t.pos.equals(pos)) return true;
        }
        targets.add(new TargetEntry(pos, type));
        setChanged();
        return true;
    }

    public void removeTarget(BlockPos pos) {
        targets.removeIf(t -> t.pos.equals(pos));
        setChanged();
    }

    public void clearAll() {
        lecternPos = null;
        targets.clear();
        active = false;
        setChanged();
    }

    public List<TargetEntry> getTargets() {
        return targets;
    }

    public boolean isActive() {
        return active;
    }

    // ==================== Status ====================

    public void sendStatusToPlayer(ServerPlayer player) {
        player.displayClientMessage(Component.literal("=== Contraption Remote ===")
            .withStyle(ChatFormatting.GOLD), false);

        if (lecternPos != null) {
            player.displayClientMessage(Component.literal("  Input: Lectern at " + lecternPos.toShortString())
                .withStyle(ChatFormatting.GREEN), false);
        } else {
            player.displayClientMessage(Component.literal("  No input linked")
                .withStyle(ChatFormatting.GRAY), false);
        }

        if (!targets.isEmpty()) {
            player.displayClientMessage(Component.literal("  Targets: " + targets.size())
                .withStyle(ChatFormatting.AQUA), false);
            for (TargetEntry t : targets) {
                player.displayClientMessage(Component.literal("    " + t.type + " at " + t.pos.toShortString())
                    .withStyle(ChatFormatting.DARK_AQUA), false);
            }
        } else {
            player.displayClientMessage(Component.literal("  No targets bound")
                .withStyle(ChatFormatting.GRAY), false);
        }

        player.displayClientMessage(Component.literal("  Status: " + (active ? "ACTIVE" : "IDLE"))
            .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);

        if (lecternPos != null && TweakedControllerCompat.isLoaded()) {
            player.displayClientMessage(Component.literal("  Mode: CTC Gamepad (via Lectern)")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        } else {
            player.displayClientMessage(Component.literal("  Mode: GUI Direct Control")
                .withStyle(ChatFormatting.AQUA), false);
            if (directDriveModifier != 0 || directDriveEnabled || directMotorSpeed != 0 || directMotorEnabled) {
                player.displayClientMessage(Component.literal("  Drive: mod=" + String.format("%.1f", directDriveModifier)
                    + " en=" + directDriveEnabled + " rev=" + directDriveReversed)
                    .withStyle(ChatFormatting.YELLOW), false);
                player.displayClientMessage(Component.literal("  Motor: spd=" + directMotorSpeed
                    + " en=" + directMotorEnabled)
                    .withStyle(ChatFormatting.AQUA), false);
            }
        }
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (lecternPos != null) {
            tag.putInt("LecternX", lecternPos.getX());
            tag.putInt("LecternY", lecternPos.getY());
            tag.putInt("LecternZ", lecternPos.getZ());
        }

        if (!label.isEmpty()) {
            tag.putString("Label", label);
        }

        if (!targets.isEmpty()) {
            ListTag list = new ListTag();
            for (TargetEntry t : targets) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("X", t.pos.getX());
                entry.putInt("Y", t.pos.getY());
                entry.putInt("Z", t.pos.getZ());
                entry.putString("Type", t.type);
                list.add(entry);
            }
            tag.put("Targets", list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains("LecternX")) {
            lecternPos = new BlockPos(tag.getInt("LecternX"), tag.getInt("LecternY"), tag.getInt("LecternZ"));
        } else {
            lecternPos = null;
        }

        label = tag.getString("Label");

        targets.clear();
        if (tag.contains("Targets")) {
            ListTag list = tag.getList("Targets", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                targets.add(new TargetEntry(
                    new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")),
                    entry.getString("Type")
                ));
            }
        }
    }

    // ==================== Sync ====================

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ==================== Data ====================

    public record TargetEntry(BlockPos pos, String type) {}
}
