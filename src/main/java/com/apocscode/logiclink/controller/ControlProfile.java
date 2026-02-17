package com.apocscode.logiclink.controller;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Data class representing a controller's motor and auxiliary redstone bindings.
 * Stored in item NBT under "ControlProfile".
 * <p>
 * Replaces the old 4-slot AxisConfig system with 12 motor + 8 aux bindings.
 * <p>
 * Motor bindings map gamepad directions to hub-connected motors/drives:
 *   Slot 0-3: Left stick (Up/Down/Left/Right)
 *   Slot 4-7: Right stick (Up/Down/Left/Right)
 *   Slot 8-11: LT/RT/LB/RB
 * <p>
 * Aux bindings map gamepad buttons to redstone link channels:
 *   Slot 0-3: D-pad (Up/Down/Left/Right)
 *   Slot 4-7: Face buttons (A/B/X/Y)
 * Keyboard mapping: number keys 1-8.
 */
public class ControlProfile {

    public static final int MAX_MOTOR_BINDINGS = 12;
    public static final int MAX_AUX_BINDINGS = 8;

    /** Motor/Drive bindings: hub device → gamepad direction mapping. */
    private final MotorBinding[] motorBindings = new MotorBinding[MAX_MOTOR_BINDINGS];

    /** Auxiliary redstone link bindings: gamepad buttons → redstone signals. */
    private final AuxBinding[] auxBindings = new AuxBinding[MAX_AUX_BINDINGS];

    /** Gamepad direction labels for the 12 motor binding slots. */
    public static final String[] MOTOR_AXIS_LABELS = {
        "L Up", "L Down", "L Left", "L Right",
        "R Up", "R Down", "R Left", "R Right",
        "LT", "RT", "LB", "RB"
    };

    /** Keyboard key labels for the 12 motor directions. */
    public static final String[] MOTOR_AXIS_KEYS = {
        "W", "S", "A", "D",
        "\u2191", "\u2193", "\u2190", "\u2192",
        "Q", "E", "Z", "C"
    };

    /** Gamepad button labels for the 8 aux binding slots. */
    public static final String[] AUX_LABELS = {
        "D-Up", "D-Down", "D-Left", "D-Right",
        "A Btn", "B Btn", "X Btn", "Y Btn"
    };

    /** Keyboard key labels for the 8 aux bindings. */
    public static final String[] AUX_KEYS = {
        "NP1", "NP2", "NP3", "NP4", "NP5", "NP6", "NP7", "NP8"
    };

    public ControlProfile() {
        for (int i = 0; i < MAX_MOTOR_BINDINGS; i++) {
            motorBindings[i] = new MotorBinding();
        }
        for (int i = 0; i < MAX_AUX_BINDINGS; i++) {
            auxBindings[i] = new AuxBinding();
        }
    }

    // ==================== Accessors ====================

    public MotorBinding getMotorBinding(int index) {
        return motorBindings[index];
    }

    public AuxBinding getAuxBinding(int index) {
        return auxBindings[index];
    }

    public MotorBinding[] getMotorBindings() {
        return motorBindings;
    }

    public AuxBinding[] getAuxBindings() {
        return auxBindings;
    }

    // ==================== NBT Serialization ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag motorList = new ListTag();
        for (int i = 0; i < MAX_MOTOR_BINDINGS; i++) {
            motorList.add(motorBindings[i].save());
        }
        tag.put("Motors", motorList);

        ListTag auxList = new ListTag();
        for (int i = 0; i < MAX_AUX_BINDINGS; i++) {
            auxList.add(auxBindings[i].save());
        }
        tag.put("Aux", auxList);

        return tag;
    }

    public static ControlProfile load(CompoundTag tag) {
        ControlProfile profile = new ControlProfile();

        if (tag.contains("Motors")) {
            ListTag motorList = tag.getList("Motors", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(motorList.size(), MAX_MOTOR_BINDINGS); i++) {
                profile.motorBindings[i] = MotorBinding.load(motorList.getCompound(i));
            }
        }

        if (tag.contains("Aux")) {
            ListTag auxList = tag.getList("Aux", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(auxList.size(), MAX_AUX_BINDINGS); i++) {
                profile.auxBindings[i] = AuxBinding.load(auxList.getCompound(i));
            }
        }

        return profile;
    }

    // ==================== Item Stack Helpers ====================

    /**
     * Read ControlProfile from an item's custom data.
     * Falls back to migrating old AxisConfig format if present.
     */
    public static ControlProfile fromItem(ItemStack stack) {
        if (stack.isEmpty()) return new ControlProfile();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("ControlProfile")) {
            return load(tag.getCompound("ControlProfile"));
        }
        // Migration: try to read old AxisConfig format
        return migrateFromAxisConfig(tag);
    }

    /**
     * Save ControlProfile to an item's custom data.
     */
    public static void saveToItem(ItemStack stack, ControlProfile profile) {
        if (stack.isEmpty()) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put("ControlProfile", profile.save());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * Migrate old 4-slot AxisConfig to new 12-slot ControlProfile.
     * Old bidirectional slots: LeftX=0, LeftY=1, RightX=2, RightY=3
     * New unidirectional: L Up=0, L Down=1, L Left=2, L Right=3, R Up=4, etc.
     * Maps old bidirectional axes to new positive-direction slots.
     */
    private static ControlProfile migrateFromAxisConfig(CompoundTag tag) {
        ControlProfile profile = new ControlProfile();
        if (!tag.contains("AxisConfig")) return profile;

        ListTag axisList = tag.getList("AxisConfig", Tag.TAG_COMPOUND);
        // Old: 0=LeftX, 1=LeftY, 2=RightX, 3=RightY
        // New: 0=LUp, 1=LDown, 2=LLeft, 3=LRight, 4=RUp, 5=RDown, 6=RLeft, 7=RRight
        int[] oldToNew = {3, 0, 7, 4}; // LeftX→LRight, LeftY→LUp, RightX→RRight, RightY→RUp
        for (int i = 0; i < Math.min(axisList.size(), 4); i++) {
            CompoundTag slot = axisList.getCompound(i);
            if (slot.contains("X")) {
                int newIdx = oldToNew[i];
                MotorBinding mb = profile.motorBindings[newIdx];
                mb.targetPos = new BlockPos(slot.getInt("X"), slot.getInt("Y"), slot.getInt("Z"));
                mb.targetType = slot.getString("Type");
                mb.reversed = slot.getBoolean("Reversed");
                mb.speed = Math.max(1, Math.min(256, slot.getInt("Speed")));
                if (slot.contains("Sequential")) mb.sequential = slot.getBoolean("Sequential");
                if (slot.contains("Distance")) mb.distance = slot.getInt("Distance");
            }
        }
        return profile;
    }

    // ==================== Compat: AxisSlot array for RemoteClientHandler ====================

    /**
     * Convert to the AxisSlot array format for RemoteClientHandler.
     * Returns all 12 motor bindings as AxisSlots (one per direction).
     */
    public com.apocscode.logiclink.client.MotorConfigScreen.AxisSlot[] toAxisSlots() {
        com.apocscode.logiclink.client.MotorConfigScreen.AxisSlot[] slots =
                new com.apocscode.logiclink.client.MotorConfigScreen.AxisSlot[MAX_MOTOR_BINDINGS];
        for (int i = 0; i < MAX_MOTOR_BINDINGS; i++) {
            slots[i] = new com.apocscode.logiclink.client.MotorConfigScreen.AxisSlot();
            MotorBinding mb = motorBindings[i];
            if (mb.hasTarget()) {
                slots[i].targetPos = mb.targetPos;
                slots[i].targetType = mb.targetType;
                slots[i].reversed = mb.reversed;
                slots[i].speed = mb.speed;
                slots[i].sequential = mb.sequential;
                slots[i].distance = mb.distance;
            }
        }
        return slots;
    }

    // ==================== Data Classes ====================

    /**
     * A single motor/drive binding to a hub-connected device.
     */
    public static class MotorBinding {
        public BlockPos targetPos = null;
        public String targetType = "";
        public String label = "";
        public int speed = 64;          // 1-256 RPM
        public boolean reversed = false;
        public boolean sequential = false;
        public int distance = 10;       // blocks, used when sequential=true

        public boolean hasTarget() {
            return targetPos != null;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (targetPos != null) {
                tag.putInt("X", targetPos.getX());
                tag.putInt("Y", targetPos.getY());
                tag.putInt("Z", targetPos.getZ());
                tag.putString("Type", targetType);
                tag.putString("Label", label);
                tag.putInt("Speed", speed);
                tag.putBoolean("Reversed", reversed);
                tag.putBoolean("Sequential", sequential);
                tag.putInt("Distance", distance);
            }
            return tag;
        }

        public static MotorBinding load(CompoundTag tag) {
            MotorBinding mb = new MotorBinding();
            if (tag.contains("X")) {
                mb.targetPos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
                mb.targetType = tag.getString("Type");
                mb.label = tag.contains("Label") ? tag.getString("Label") : "";
                mb.speed = Math.max(1, Math.min(256, tag.getInt("Speed")));
                if (mb.speed < 1) mb.speed = 64;
                mb.reversed = tag.getBoolean("Reversed");
                mb.sequential = tag.getBoolean("Sequential");
                mb.distance = tag.getInt("Distance");
                if (mb.distance < 1) mb.distance = 10;
            }
            return mb;
        }
    }

    /**
     * A single auxiliary redstone link binding.
     * Each aux channel transmits a configurable power level (1-15)
     * through Create's redstone link network. Can be momentary (hold)
     * or constant (toggle on/off).
     * <p>
     * Frequency items are stored as registry name strings to avoid
     * RegistryAccess dependency in the data class.
     */
    public static class AuxBinding {
        public String label = "";
        public int power = 15;          // 1-15 redstone signal strength
        public boolean momentary = true; // true=hold-to-activate, false=toggle
        /** Frequency item 1 registry name (e.g. "minecraft:iron_ingot"). Empty = unset. */
        public String freqId1 = "";
        /** Frequency item 2 registry name. Empty = unset. */
        public String freqId2 = "";

        public boolean hasFrequency() {
            return !freqId1.isEmpty() && !freqId2.isEmpty();
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Label", label);
            tag.putInt("Power", power);
            tag.putBoolean("Momentary", momentary);
            if (!freqId1.isEmpty()) tag.putString("Freq1", freqId1);
            if (!freqId2.isEmpty()) tag.putString("Freq2", freqId2);
            return tag;
        }

        public static AuxBinding load(CompoundTag tag) {
            AuxBinding ab = new AuxBinding();
            ab.label = tag.contains("Label") ? tag.getString("Label") : "";
            ab.power = tag.getInt("Power");
            if (ab.power < 1 || ab.power > 15) ab.power = 15;
            ab.momentary = !tag.contains("Momentary") || tag.getBoolean("Momentary");
            ab.freqId1 = tag.contains("Freq1") ? tag.getString("Freq1") : "";
            ab.freqId2 = tag.contains("Freq2") ? tag.getString("Freq2") : "";
            return ab;
        }
    }
}
