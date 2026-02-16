package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.network.HubNetwork;
import com.apocscode.logiclink.network.IHubDevice;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity for the Creative Logic Motor. Generates rotation at any
 * speed (-256 to 256 RPM) with unlimited stress capacity — all
 * controllable from CC:Tweaked Lua scripts.
 * <p>
 * Extends Create's {@link GeneratingKineticBlockEntity} to act as a
 * proper kinetic rotation source that integrates with shafts, gears,
 * belts, and all other kinetic components.
 * </p>
 * <h3>Features:</h3>
 * <ul>
 *   <li>Set speed on the fly (instant or with degree-based rotation)</li>
 *   <li>Sequence system — queue up rotate/wait/speed steps</li>
 *   <li>Event-driven — fires CC events on sequence completion</li>
 * </ul>
 */
public class CreativeLogicMotorBlockEntity extends GeneratingKineticBlockEntity implements IHubDevice {

    public static final int MAX_SPEED = 256;

    /** Current target speed set by Lua. */
    private int targetSpeed = 0;

    /** Whether the motor is enabled. */
    private boolean enabled = false;

    /** User-assigned label for hub identification. */
    private String hubLabel = "";

    /** Whether this device has registered with HubNetwork. */
    private boolean hubRegistered = false;

    // ==================== Sequence System ====================

    /** Queued sequence instructions. */
    private final List<MotorInstruction> sequence = new ArrayList<>();

    /** Current position in sequence (-1 = not running). */
    private int sequenceIndex = -1;

    /** Ticks remaining for current step (wait or rotate). */
    private int sequenceTimer = 0;

    /** Degrees remaining for current rotate step. */
    private float sequenceDegreesRemaining = 0;

    /** Whether to loop the sequence. */
    private boolean sequenceLoop = false;

    /** Speed used by current rotate step. */
    private int sequenceStepSpeed = 0;

    // ==================== Constructor ====================

    public CreativeLogicMotorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ==================== Behaviours ====================

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // No ScrollValueBehaviour — all control is via CC peripheral
    }

    // ==================== Kinetic Source ====================

    @Override
    public float getGeneratedSpeed() {
        if (!enabled) return 0;
        // During a sequence rotate step, use the step's speed
        if (sequenceIndex >= 0 && sequenceIndex < sequence.size()) {
            MotorInstruction instr = sequence.get(sequenceIndex);
            if (instr.type == MotorInstruction.Type.ROTATE) {
                return sequenceStepSpeed;
            }
            if (instr.type == MotorInstruction.Type.WAIT) {
                return 0; // Stopped during wait
            }
        }
        return targetSpeed;
    }

    // ==================== Lua-Callable Methods ====================

    /**
     * Sets the motor speed. Immediately updates the kinetic network.
     * @param speed RPM from -256 to 256
     */
    public void setMotorSpeed(int speed) {
        this.targetSpeed = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, speed));
        if (enabled && sequenceIndex < 0) {
            updateGeneratedRotation();
        }
        setChanged();
    }

    public int getMotorSpeed() {
        return targetSpeed;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        updateGeneratedRotation();
        setChanged();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public float getActualSpeed() {
        return getSpeed();
    }

    public float getStressCapacityValue() {
        return calculateAddedStressCapacity();
    }

    public float getStressUsageValue() {
        if (level == null) return 0;
        return lastStressApplied;
    }

    // ==================== Sequence API ====================

    public void clearSequence() {
        sequence.clear();
        sequenceIndex = -1;
        sequenceTimer = 0;
        sequenceDegreesRemaining = 0;
        sequenceLoop = false;
        setChanged();
    }

    /**
     * Add a rotate step: rotate the given degrees at the given speed.
     * Positive degrees = forward, negative = reverse.
     */
    public void addRotateStep(float degrees, int speed) {
        sequence.add(new MotorInstruction(MotorInstruction.Type.ROTATE, degrees, speed));
        setChanged();
    }

    /**
     * Add a wait step: pause for the given number of ticks.
     * 20 ticks = 1 second.
     */
    public void addWaitStep(int ticks) {
        sequence.add(new MotorInstruction(MotorInstruction.Type.WAIT, ticks, 0));
        setChanged();
    }

    /**
     * Add a speed-change step: set the continuous speed to this value.
     */
    public void addSpeedStep(int speed) {
        sequence.add(new MotorInstruction(MotorInstruction.Type.SET_SPEED, 0, speed));
        setChanged();
    }

    /**
     * Start running the sequence.
     * @param loop If true, loop forever until stopped.
     */
    public void runSequence(boolean loop) {
        if (sequence.isEmpty()) return;
        this.sequenceLoop = loop;
        this.sequenceIndex = 0;
        this.enabled = true;
        startCurrentStep();
        updateGeneratedRotation();
        setChanged();
    }

    public void stopSequence() {
        sequenceIndex = -1;
        sequenceTimer = 0;
        sequenceDegreesRemaining = 0;
        updateGeneratedRotation();
        setChanged();
    }

    public boolean isSequenceRunning() {
        return sequenceIndex >= 0;
    }

    public int getSequenceSize() {
        return sequence.size();
    }

    // ==================== Tick (sequence processing) ====================

    @Override
    public void tick() {
        super.tick();

        if (level == null) return;

        // Register with hub network on both client and server
        if (!hubRegistered) {
            HubNetwork.register(this);
            hubRegistered = true;
        }

        if (level.isClientSide) return;

        if (sequenceIndex < 0 || sequenceIndex >= sequence.size()) return;

        MotorInstruction instr = sequence.get(sequenceIndex);

        switch (instr.type) {
            case ROTATE:
                // Consume degrees based on current speed per tick
                // 1 RPM = 6 degrees/second = 0.3 degrees/tick
                float degreesPerTick = Math.abs(sequenceStepSpeed) * 0.3f;
                sequenceDegreesRemaining -= degreesPerTick;
                if (sequenceDegreesRemaining <= 0) {
                    advanceSequence();
                }
                break;

            case WAIT:
                sequenceTimer--;
                if (sequenceTimer <= 0) {
                    advanceSequence();
                }
                break;

            case SET_SPEED:
                // Instant — set speed and advance immediately
                targetSpeed = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, instr.speedOrTicks));
                advanceSequence();
                break;
        }
    }

    private void startCurrentStep() {
        if (sequenceIndex < 0 || sequenceIndex >= sequence.size()) return;
        MotorInstruction instr = sequence.get(sequenceIndex);
        switch (instr.type) {
            case ROTATE:
                sequenceDegreesRemaining = Math.abs(instr.degreesOrTicks);
                // Direction based on sign of degrees
                int absSpeed = Math.max(1, Math.abs(instr.speedOrTicks));
                sequenceStepSpeed = instr.degreesOrTicks >= 0 ? absSpeed : -absSpeed;
                break;
            case WAIT:
                sequenceTimer = (int) instr.degreesOrTicks;
                break;
            case SET_SPEED:
                // Handled instantly in tick
                break;
        }
        updateGeneratedRotation();
    }

    private void advanceSequence() {
        sequenceIndex++;
        if (sequenceIndex >= sequence.size()) {
            if (sequenceLoop) {
                sequenceIndex = 0;
            } else {
                sequenceIndex = -1;
                updateGeneratedRotation();
                // Fire a CC event — computers can listen for "motor_sequence_done"
                return;
            }
        }
        startCurrentStep();
    }

    // ==================== IHubDevice ====================

    @Override
    public String getHubLabel() { return hubLabel; }

    @Override
    public void setHubLabel(String label) {
        this.hubLabel = label != null ? label : "";
        setChanged();
        notifyUpdate();  // Sync to client for goggle tooltip
    }

    @Override
    public String getDeviceType() { return "creative_motor"; }

    @Override
    public BlockPos getDevicePos() { return getBlockPos(); }

    // ==================== Goggle Tooltip ====================

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        // Always show Creative Logic Motor status
        tooltip.add(Component.literal("    ")
            .append(Component.literal("Creative Logic Motor").withStyle(ChatFormatting.WHITE)));

        BlockPos pos = getBlockPos();
        tooltip.add(Component.literal("    ")
            .append(Component.literal("Pos: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.WHITE)));

        if (!hubLabel.isEmpty()) {
            tooltip.add(Component.literal("    ")
                .append(Component.literal("Label: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(hubLabel).withStyle(ChatFormatting.AQUA)));
        }

        String state = enabled ? "Active" : "Disabled";
        ChatFormatting stateColor = enabled ? ChatFormatting.GREEN : ChatFormatting.RED;
        tooltip.add(Component.literal("    ")
            .append(Component.literal("State: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(state).withStyle(stateColor)));

        tooltip.add(Component.literal("    ")
            .append(Component.literal("Target: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(targetSpeed + " RPM").withStyle(ChatFormatting.AQUA)));

        return true;
    }

    // ==================== Cleanup ====================

    public void onRemoved() {
        HubNetwork.unregister(this);
    }

    // ==================== NBT Persistence ====================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("TargetSpeed", targetSpeed);
        tag.putBoolean("Enabled", enabled);
        if (!hubLabel.isEmpty()) {
            tag.putString("HubLabel", hubLabel);
        }
        tag.putBoolean("SequenceLoop", sequenceLoop);
        tag.putInt("SequenceIndex", sequenceIndex);
        tag.putInt("SequenceTimer", sequenceTimer);
        tag.putFloat("SequenceDegreesRemaining", sequenceDegreesRemaining);
        tag.putInt("SequenceStepSpeed", sequenceStepSpeed);

        // Save sequence instructions
        ListTag instrList = new ListTag();
        for (MotorInstruction instr : sequence) {
            CompoundTag instrTag = new CompoundTag();
            instrTag.putString("Type", instr.type.name());
            instrTag.putFloat("Value", instr.degreesOrTicks);
            instrTag.putInt("Speed", instr.speedOrTicks);
            instrList.add(instrTag);
        }
        tag.put("Sequence", instrList);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        targetSpeed = tag.getInt("TargetSpeed");
        enabled = tag.getBoolean("Enabled");
        hubLabel = tag.getString("HubLabel");
        hubRegistered = false;
        sequenceLoop = tag.getBoolean("SequenceLoop");
        sequenceIndex = tag.getInt("SequenceIndex");
        sequenceTimer = tag.getInt("SequenceTimer");
        sequenceDegreesRemaining = tag.getFloat("SequenceDegreesRemaining");
        sequenceStepSpeed = tag.getInt("SequenceStepSpeed");

        // Load sequence instructions
        sequence.clear();
        ListTag instrList = tag.getList("Sequence", Tag.TAG_COMPOUND);
        for (int i = 0; i < instrList.size(); i++) {
            CompoundTag instrTag = instrList.getCompound(i);
            MotorInstruction.Type type = MotorInstruction.Type.valueOf(instrTag.getString("Type"));
            float value = instrTag.getFloat("Value");
            int speed = instrTag.getInt("Speed");
            sequence.add(new MotorInstruction(type, value, speed));
        }
    }

    // ==================== Instruction Data Class ====================

    /**
     * A single instruction in a motor sequence.
     */
    public static class MotorInstruction {
        public enum Type { ROTATE, WAIT, SET_SPEED }

        public final Type type;
        /** For ROTATE: degrees. For WAIT: ticks. For SET_SPEED: unused. */
        public final float degreesOrTicks;
        /** For ROTATE/SET_SPEED: RPM. For WAIT: unused. */
        public final int speedOrTicks;

        public MotorInstruction(Type type, float degreesOrTicks, int speedOrTicks) {
            this.type = type;
            this.degreesOrTicks = degreesOrTicks;
            this.speedOrTicks = speedOrTicks;
        }
    }
}
