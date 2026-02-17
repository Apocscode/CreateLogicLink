package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.network.HubNetwork;
import com.apocscode.logiclink.network.IHubDevice;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity for the Logic Drive - a survival-friendly CC-controlled
 * rotation generator that requires external kinetic input to operate.
 * <p>
 * Architecture: <b>Consumer + Generator</b>
 * <ul>
 *   <li><b>Input side</b> (FACING.opposite): reads rotation speed from the
 *       adjacent kinetic block. The drive is NOT kinetically connected to the
 *       input network - it only "senses" rotation on its input face.</li>
 *   <li><b>Output side</b> (FACING): generates rotation at
 *       inputSpeed x modifier, acting as an independent kinetic source with
 *       limited stress capacity (256 SU).</li>
 * </ul>
 * Because the input and output are on separate kinetic networks, direction
 * changes never cause speed conflicts or block breakage.
 * </p>
 *
 * Extends Create's {@link GeneratingKineticBlockEntity} so the output side
 * behaves like a proper kinetic source (similar to a Creative Motor, but
 * with finite stress capacity and input-dependent operation).
 */
public class LogicDriveBlockEntity extends GeneratingKineticBlockEntity implements IHubDevice {

    /** Base stress capacity provided when input rotation is present (in SU). */
    public static final float STRESS_CAPACITY = 256.0f;

    /** Speed multiplier applied to input rotation to determine output speed. */
    private float speedModifier = 1.0f;

    /** Whether the drive is enabled (generates output). */
    private boolean enabled = true;

    /** Whether to reverse output rotation direction. */
    private boolean reversed = false;

    /** Cached input rotation speed read from the adjacent input-side block. */
    private float cachedInputSpeed = 0;

    /** User-assigned label for hub identification. */
    private String hubLabel = "";

    /** Whether this device has registered with HubNetwork. */
    private boolean hubRegistered = false;

    // ==================== Sequence System ====================

    private final List<DriveInstruction> sequence = new ArrayList<>();
    private int sequenceIndex = -1;
    private int sequenceTimer = 0;
    private float sequenceDegreesRemaining = 0;
    private boolean sequenceLoop = false;

    /** Modifier override during a sequence rotate step. */
    private float sequenceModifierOverride = 1.0f;

    // ==================== Constructor ====================

    public LogicDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ==================== Behaviours ====================

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    // ==================== Input Rotation Reading ====================

    /**
     * Reads the rotation speed of the kinetic block adjacent to the input face.
     * The drive is NOT kinetically connected to this block - it only reads its speed.
     *
     * Validates that:
     * 1. A KineticBlockEntity exists on the input face
     * 2. That block has a shaft pointing toward our input face
     * 3. The shaft axes are aligned
     *
     * @return The input neighbor's rotation speed in RPM, or 0 if no valid input.
     */
    private float readInputRotation() {
        if (level == null) return 0;
        BlockState state = getBlockState();
        if (!state.hasProperty(LogicDriveBlock.FACING)) return 0;

        Direction outputFace = state.getValue(LogicDriveBlock.FACING);
        Direction inputFace = outputFace.getOpposite();
        Direction.Axis ourAxis = outputFace.getAxis();

        BlockPos inputPos = worldPosition.relative(inputFace);
        BlockEntity inputBe = level.getBlockEntity(inputPos);
        if (inputBe instanceof KineticBlockEntity kbe) {
            BlockState neighborState = kbe.getBlockState();
            if (neighborState.getBlock() instanceof KineticBlock kinBlock) {
                // Check the neighbor has a shaft pointing toward us on the same axis
                if (kinBlock.hasShaftTowards(level, inputPos, neighborState, inputFace.getOpposite())
                        && kinBlock.getRotationAxis(neighborState) == ourAxis) {
                    return kbe.getSpeed();
                }
            }
        }
        return 0;
    }

    // ==================== Kinetic Generation ====================

    /**
     * Returns the speed this drive generates on the output side.
     * <p>
     * Output = cachedInputSpeed x modifier x (reversed ? -1 : 1)
     * <br>Clamped to +/-256 RPM (Create's hard limit).
     * <p>
     * During sequences, the step's modifier override is used instead.
     */
    @Override
    public float getGeneratedSpeed() {
        if (!enabled) return 0;
        if (cachedInputSpeed == 0) return 0;

        // During a sequence, use the sequence's modifier
        if (sequenceIndex >= 0 && sequenceIndex < sequence.size()) {
            DriveInstruction instr = sequence.get(sequenceIndex);
            if (instr.type == DriveInstruction.Type.ROTATE) {
                float output = cachedInputSpeed * sequenceModifierOverride;
                return Math.max(-256, Math.min(256, output));
            }
            if (instr.type == DriveInstruction.Type.WAIT) {
                return 0; // Stop during wait
            }
        }

        float mod = speedModifier;
        if (reversed) mod = -mod;

        float output = cachedInputSpeed * mod;
        return Math.max(-256, Math.min(256, output));
    }

    /**
     * Stress capacity provided by the drive on the output network.
     * Only available when input rotation is present.
     */
    @Override
    public float calculateAddedStressCapacity() {
        float capacity = cachedInputSpeed != 0 ? STRESS_CAPACITY : 0;
        this.lastCapacityProvided = capacity;
        return capacity;
    }

    // ==================== Lua-Callable Methods ====================

    public void setMotorEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        updateBlockState();
        updateGeneratedRotation();
        setChanged();
    }

    public boolean isMotorEnabled() {
        return enabled;
    }

    public void setReversed(boolean reversed) {
        if (this.reversed == reversed) return;
        this.reversed = reversed;
        updateGeneratedRotation();
        setChanged();
    }

    public boolean isReversed() {
        return reversed;
    }

    public void setSpeedModifier(float modifier) {
        float clamped = Math.max(-16.0f, Math.min(16.0f, modifier));
        if (this.speedModifier == clamped) return;
        this.speedModifier = clamped;
        updateGeneratedRotation();
        setChanged();
    }

    public float getSpeedModifier() {
        return speedModifier;
    }

    /**
     * Gets the input rotation speed from the adjacent block on the input face.
     * Reads the neighbor's speed, NOT our own network speed.
     */
    public float getInputSpeed() {
        return cachedInputSpeed;
    }

    /**
     * Gets the output rotation speed (our generated speed on the output network).
     */
    public float getOutputSpeed() {
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
        boolean wasRunning = sequenceIndex >= 0;
        sequence.clear();
        sequenceIndex = -1;
        sequenceTimer = 0;
        sequenceDegreesRemaining = 0;
        sequenceLoop = false;
        if (wasRunning) {
            updateGeneratedRotation();
        }
        setChanged();
    }

    public void addRotateStep(float degrees, float modifier) {
        sequence.add(new DriveInstruction(DriveInstruction.Type.ROTATE, degrees, modifier));
        setChanged();
    }

    public void addWaitStep(int ticks) {
        sequence.add(new DriveInstruction(DriveInstruction.Type.WAIT, ticks, 0));
        setChanged();
    }

    public void addModifierStep(float modifier) {
        sequence.add(new DriveInstruction(DriveInstruction.Type.SET_MODIFIER, 0, modifier));
        setChanged();
    }

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
        boolean wasRunning = sequenceIndex >= 0;
        sequenceIndex = -1;
        sequenceTimer = 0;
        sequenceDegreesRemaining = 0;
        if (wasRunning) {
            updateGeneratedRotation();
        }
        setChanged();
    }

    public boolean isSequenceRunning() {
        return sequenceIndex >= 0;
    }

    public int getSequenceSize() {
        return sequence.size();
    }

    // ==================== Tick ====================

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

        // Check if input rotation has changed
        float newInput = readInputRotation();
        if (newInput != cachedInputSpeed) {
            cachedInputSpeed = newInput;
            updateGeneratedRotation();
        }

        // Sequence processing
        if (sequenceIndex < 0 || sequenceIndex >= sequence.size()) return;

        DriveInstruction instr = sequence.get(sequenceIndex);

        switch (instr.type) {
            case ROTATE:
                // Use generated speed (which factors in input + modifier)
                float outputSpeed = Math.abs(getGeneratedSpeed());
                if (outputSpeed > 0) {
                    float degreesPerTick = outputSpeed * 0.3f;
                    sequenceDegreesRemaining -= degreesPerTick;
                    if (sequenceDegreesRemaining <= 0) {
                        advanceSequence();
                    }
                }
                break;

            case WAIT:
                sequenceTimer--;
                if (sequenceTimer <= 0) {
                    advanceSequence();
                }
                break;

            case SET_MODIFIER:
                speedModifier = instr.modifierOrValue;
                advanceSequence();
                break;
        }
    }

    private void startCurrentStep() {
        if (sequenceIndex < 0 || sequenceIndex >= sequence.size()) return;
        DriveInstruction instr = sequence.get(sequenceIndex);
        switch (instr.type) {
            case ROTATE:
                sequenceDegreesRemaining = Math.abs(instr.degreesOrTicks);
                sequenceModifierOverride = Math.abs(instr.modifierOrValue);
                // Direction from sign of degrees
                if (instr.degreesOrTicks < 0) {
                    sequenceModifierOverride = -sequenceModifierOverride;
                }
                break;
            case WAIT:
                sequenceTimer = (int) instr.degreesOrTicks;
                break;
            case SET_MODIFIER:
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
                return;
            }
        }
        startCurrentStep();
    }

    private void updateBlockState() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(LogicDriveBlock.ACTIVE)) {
                level.setBlock(worldPosition, state.setValue(LogicDriveBlock.ACTIVE, enabled), 3);
            }
        }
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
    public String getDeviceType() { return "drive"; }

    @Override
    public BlockPos getDevicePos() { return getBlockPos(); }

    // ==================== Goggle Tooltip ====================

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        tooltip.add(Component.literal("    ")
            .append(Component.literal("Logic Drive").withStyle(ChatFormatting.WHITE)));

        BlockPos pos = getBlockPos();
        tooltip.add(Component.literal("    ")
            .append(Component.literal("Pos: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).withStyle(ChatFormatting.WHITE)));

        if (!hubLabel.isEmpty()) {
            tooltip.add(Component.literal("    ")
                .append(Component.literal("Label: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(hubLabel).withStyle(ChatFormatting.AQUA)));
        }

        String stateStr = enabled ? (reversed ? "Reversed" : "Active") : "Disabled";
        ChatFormatting stateColor = enabled ? (reversed ? ChatFormatting.GOLD : ChatFormatting.GREEN) : ChatFormatting.RED;
        tooltip.add(Component.literal("    ")
            .append(Component.literal("State: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(stateStr).withStyle(stateColor)));

        tooltip.add(Component.literal("    ")
            .append(Component.literal("Modifier: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("x" + speedModifier).withStyle(ChatFormatting.AQUA)));

        tooltip.add(Component.literal("    ")
            .append(Component.literal("Input: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(cachedInputSpeed + " RPM").withStyle(
                cachedInputSpeed != 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));

        tooltip.add(Component.literal("    ")
            .append(Component.literal("Output: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(getSpeed() + " RPM").withStyle(ChatFormatting.AQUA)));

        tooltip.add(Component.literal("    ")
            .append(Component.literal("Capacity: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(calculateAddedStressCapacity() + " su").withStyle(ChatFormatting.YELLOW)));

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
        tag.putFloat("SpeedModifier", speedModifier);
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("Reversed", reversed);
        tag.putFloat("CachedInputSpeed", cachedInputSpeed);
        if (!hubLabel.isEmpty()) {
            tag.putString("HubLabel", hubLabel);
        }
        tag.putBoolean("SequenceLoop", sequenceLoop);
        tag.putInt("SequenceIndex", sequenceIndex);
        tag.putInt("SequenceTimer", sequenceTimer);
        tag.putFloat("SequenceDegreesRemaining", sequenceDegreesRemaining);
        tag.putFloat("SequenceModifierOverride", sequenceModifierOverride);

        ListTag instrList = new ListTag();
        for (DriveInstruction instr : sequence) {
            CompoundTag instrTag = new CompoundTag();
            instrTag.putString("Type", instr.type.name());
            instrTag.putFloat("Value", instr.degreesOrTicks);
            instrTag.putFloat("Modifier", instr.modifierOrValue);
            instrList.add(instrTag);
        }
        tag.put("Sequence", instrList);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        speedModifier = tag.getFloat("SpeedModifier");
        enabled = tag.getBoolean("Enabled");
        reversed = tag.getBoolean("Reversed");
        cachedInputSpeed = tag.getFloat("CachedInputSpeed");
        hubLabel = tag.getString("HubLabel");
        hubRegistered = false;
        sequenceLoop = tag.getBoolean("SequenceLoop");
        sequenceIndex = tag.getInt("SequenceIndex");
        sequenceTimer = tag.getInt("SequenceTimer");
        sequenceDegreesRemaining = tag.getFloat("SequenceDegreesRemaining");
        sequenceModifierOverride = tag.getFloat("SequenceModifierOverride");

        sequence.clear();
        ListTag instrList = tag.getList("Sequence", Tag.TAG_COMPOUND);
        for (int i = 0; i < instrList.size(); i++) {
            CompoundTag instrTag = instrList.getCompound(i);
            DriveInstruction.Type type = DriveInstruction.Type.valueOf(instrTag.getString("Type"));
            float value = instrTag.getFloat("Value");
            float modifier = instrTag.getFloat("Modifier");
            sequence.add(new DriveInstruction(type, value, modifier));
        }
    }

    // ==================== Instruction Data Class ====================

    public static class DriveInstruction {
        public enum Type { ROTATE, WAIT, SET_MODIFIER }

        public final Type type;
        public final float degreesOrTicks;
        public final float modifierOrValue;

        public DriveInstruction(Type type, float degreesOrTicks, float modifierOrValue) {
            this.type = type;
            this.degreesOrTicks = degreesOrTicks;
            this.modifierOrValue = modifierOrValue;
        }
    }
}
