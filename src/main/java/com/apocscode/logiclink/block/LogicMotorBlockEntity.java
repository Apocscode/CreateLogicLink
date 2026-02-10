package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity for the Logic Motor (standard version).
 * Takes external rotation input and modifies the output — acting as a
 * programmable clutch, gearshift, and sequenced gearshift in one.
 * <p>
 * Extends Create's {@link SplitShaftBlockEntity} which splits rotation
 * into two halves. The {@link #getRotationSpeedModifier(Direction)} method
 * controls what happens to the output side: 0 = off, 1 = pass through,
 * -1 = reverse, 2 = double speed, etc.
 * </p>
 */
public class LogicMotorBlockEntity extends SplitShaftBlockEntity {

    /** Speed multiplier applied to input rotation. */
    private float speedModifier = 1.0f;

    /** Whether the motor is enabled (passes rotation through). */
    private boolean enabled = true;

    /** Whether to reverse input rotation. */
    private boolean reversed = false;

    /** Deferred kinetic network update — coalesces rapid Lua changes into one tick. */
    private boolean kinematicsNeedUpdate = false;

    // ==================== Sequence System ====================

    private final List<MotorInstruction> sequence = new ArrayList<>();
    private int sequenceIndex = -1;
    private int sequenceTimer = 0;
    private float sequenceDegreesRemaining = 0;
    private boolean sequenceLoop = false;

    /** Modifier override during a sequence rotate step. */
    private float sequenceModifierOverride = 1.0f;

    // ==================== Constructor ====================

    public LogicMotorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ==================== Behaviours ====================

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    // ==================== Speed Modification (core mechanic) ====================

    /**
     * Called by Create's kinetic network to determine the speed ratio
     * between the two shaft halves. This is the core of the split shaft:
     * <ul>
     *   <li>Return 1.0 = pass through unchanged</li>
     *   <li>Return -1.0 = reverse direction</li>
     *   <li>Return 0.0 = disconnect (clutch off)</li>
     *   <li>Return 2.0 = double speed (at cost of stress)</li>
     * </ul>
     */
    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (!enabled) return 0;

        // If running a sequence, use the sequence's modifier
        if (sequenceIndex >= 0 && sequenceIndex < sequence.size()) {
            MotorInstruction instr = sequence.get(sequenceIndex);
            if (instr.type == MotorInstruction.Type.ROTATE) {
                return sequenceModifierOverride;
            }
            if (instr.type == MotorInstruction.Type.WAIT) {
                return 0; // Stop during wait
            }
        }

        float mod = speedModifier;
        if (reversed) mod = -mod;

        // The source side should return 1.0 (unchanged), the output side
        // gets the modifier. SplitShaftBlockEntity already handles which
        // side is which via getSourceFacing().
        if (face == getSourceFacing()) return 1.0f;
        return mod;
    }

    // ==================== Lua-Callable Methods ====================

    public void setMotorEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        updateBlockState();
        kinematicsNeedUpdate = true;
        setChanged();
    }

    public boolean isMotorEnabled() {
        return enabled;
    }

    public void setReversed(boolean reversed) {
        if (this.reversed == reversed) return;
        this.reversed = reversed;
        kinematicsNeedUpdate = true;
        setChanged();
    }

    public boolean isReversed() {
        return reversed;
    }

    public void setSpeedModifier(float modifier) {
        float clamped = Math.max(-16.0f, Math.min(16.0f, modifier));
        if (this.speedModifier == clamped) return;
        this.speedModifier = clamped;
        kinematicsNeedUpdate = true;
        setChanged();
    }

    public float getSpeedModifier() {
        return speedModifier;
    }

    public float getInputSpeed() {
        return getSpeed();
    }

    public float getOutputSpeed() {
        if (!enabled) return 0;
        float mod = speedModifier;
        if (reversed) mod = -mod;
        return getSpeed() * mod;
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
            detachKinetics();
            attachKinetics();
        }
        setChanged();
    }

    public void addRotateStep(float degrees, float modifier) {
        sequence.add(new MotorInstruction(MotorInstruction.Type.ROTATE, degrees, modifier));
        setChanged();
    }

    public void addWaitStep(int ticks) {
        sequence.add(new MotorInstruction(MotorInstruction.Type.WAIT, ticks, 0));
        setChanged();
    }

    public void addModifierStep(float modifier) {
        sequence.add(new MotorInstruction(MotorInstruction.Type.SET_MODIFIER, 0, modifier));
        setChanged();
    }

    public void runSequence(boolean loop) {
        if (sequence.isEmpty()) return;
        this.sequenceLoop = loop;
        this.sequenceIndex = 0;
        this.enabled = true;
        startCurrentStep();
        detachKinetics();
        attachKinetics();
        setChanged();
    }

    public void stopSequence() {
        boolean wasRunning = sequenceIndex >= 0;
        sequenceIndex = -1;
        sequenceTimer = 0;
        sequenceDegreesRemaining = 0;
        if (wasRunning) {
            detachKinetics();
            attachKinetics();
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

        if (level == null || level.isClientSide) return;

        // Coalesce rapid Lua property changes into a single kinetic update per tick
        if (kinematicsNeedUpdate) {
            kinematicsNeedUpdate = false;
            detachKinetics();
            attachKinetics();
        }

        if (sequenceIndex < 0 || sequenceIndex >= sequence.size()) return;

        MotorInstruction instr = sequence.get(sequenceIndex);

        switch (instr.type) {
            case ROTATE:
                float inputSpeed = Math.abs(getSpeed());
                if (inputSpeed > 0) {
                    float degreesPerTick = inputSpeed * Math.abs(sequenceModifierOverride) * 0.3f;
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
        MotorInstruction instr = sequence.get(sequenceIndex);
        switch (instr.type) {
            case ROTATE:
                sequenceDegreesRemaining = Math.abs(instr.degreesOrTicks);
                sequenceModifierOverride = instr.modifierOrValue >= 0 ? Math.abs(instr.modifierOrValue) : -Math.abs(instr.modifierOrValue);
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
        detachKinetics();
        attachKinetics();
    }

    private void advanceSequence() {
        sequenceIndex++;
        if (sequenceIndex >= sequence.size()) {
            if (sequenceLoop) {
                sequenceIndex = 0;
            } else {
                sequenceIndex = -1;
                detachKinetics();
                attachKinetics();
                return;
            }
        }
        startCurrentStep();
    }

    private void updateBlockState() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(LogicMotorBlock.ACTIVE)) {
                level.setBlock(worldPosition, state.setValue(LogicMotorBlock.ACTIVE, enabled), 3);
            }
        }
    }

    // ==================== NBT Persistence ====================

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("SpeedModifier", speedModifier);
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("Reversed", reversed);
        tag.putBoolean("SequenceLoop", sequenceLoop);
        tag.putInt("SequenceIndex", sequenceIndex);
        tag.putInt("SequenceTimer", sequenceTimer);
        tag.putFloat("SequenceDegreesRemaining", sequenceDegreesRemaining);
        tag.putFloat("SequenceModifierOverride", sequenceModifierOverride);

        ListTag instrList = new ListTag();
        for (MotorInstruction instr : sequence) {
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
        sequenceLoop = tag.getBoolean("SequenceLoop");
        sequenceIndex = tag.getInt("SequenceIndex");
        sequenceTimer = tag.getInt("SequenceTimer");
        sequenceDegreesRemaining = tag.getFloat("SequenceDegreesRemaining");
        sequenceModifierOverride = tag.getFloat("SequenceModifierOverride");

        sequence.clear();
        ListTag instrList = tag.getList("Sequence", Tag.TAG_COMPOUND);
        for (int i = 0; i < instrList.size(); i++) {
            CompoundTag instrTag = instrList.getCompound(i);
            MotorInstruction.Type type = MotorInstruction.Type.valueOf(instrTag.getString("Type"));
            float value = instrTag.getFloat("Value");
            float modifier = instrTag.getFloat("Modifier");
            sequence.add(new MotorInstruction(type, value, modifier));
        }
    }

    // ==================== Instruction Data Class ====================

    public static class MotorInstruction {
        public enum Type { ROTATE, WAIT, SET_MODIFIER }

        public final Type type;
        public final float degreesOrTicks;
        public final float modifierOrValue;

        public MotorInstruction(Type type, float degreesOrTicks, float modifierOrValue) {
            this.type = type;
            this.degreesOrTicks = degreesOrTicks;
            this.modifierOrValue = modifierOrValue;
        }
    }
}
