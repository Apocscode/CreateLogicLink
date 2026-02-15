package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Logic Remote — a handheld item that bridges CTC controller input to LogicLink drives/motors.
 * <p>
 * Usage:
 * <ol>
 *   <li>Sneak + right-click a CTC Tweaked Lectern to link it as input source</li>
 *   <li>Right-click a Logic Drive or Creative Logic Motor to add it as a target</li>
 *   <li>Sneak + right-click air to clear all bindings</li>
 * </ol>
 * <p>
 * While held, the remote reads controller input from the linked lectern and
 * applies mapped axis/button values to bound drives and motors every tick.
 * </p>
 * <p>
 * Default mappings (configurable via CC peripheral):
 * <ul>
 *   <li>Left stick Y (axis 2) → Drive speed modifier (-16 to 16)</li>
 *   <li>Right trigger (axis 5) → Motor speed (0 to 256)</li>
 *   <li>Button 1 (A) → Drive enable/disable</li>
 *   <li>Button 2 (B) → Motor enable/disable</li>
 *   <li>Button 4 (Y) → Drive reverse toggle</li>
 * </ul>
 */
public class LogicRemoteItem extends Item {

    /** Maximum number of drive/motor targets that can be bound. */
    public static final int MAX_TARGETS = 8;

    /** Hub scan range for discovering targets. */
    public static final int SCAN_RANGE = 64;

    public LogicRemoteItem(Properties properties) {
        super(properties);
    }

    // ==================== Interaction ====================

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide || player == null) return InteractionResult.PASS;

        // Sneak + right-click a lectern = link as input source
        if (player.isShiftKeyDown()) {
            if (TweakedControllerCompat.isLoaded() && TweakedControllerReader.isTweakedLectern(level, pos)) {
                setLinkedLectern(stack, pos);
                player.displayClientMessage(
                    Component.literal("Linked to Tweaked Lectern at " + pos.toShortString())
                        .withStyle(ChatFormatting.GREEN), true);
                return InteractionResult.SUCCESS;
            }
        }

        // Right-click a drive or motor = add as target
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IHubDevice device) {
            String type = device.getDeviceType();
            if ("drive".equals(type) || "creative_motor".equals(type)) {
                if (addTarget(stack, pos, type)) {
                    String label = device.getHubLabel().isEmpty() ? type : device.getHubLabel();
                    player.displayClientMessage(
                        Component.literal("Bound " + label + " at " + pos.toShortString())
                            .withStyle(ChatFormatting.AQUA), true);
                } else {
                    player.displayClientMessage(
                        Component.literal("Max targets reached (" + MAX_TARGETS + ")")
                            .withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) return InteractionResultHolder.pass(stack);

        // Sneak + right-click air = clear bindings
        if (player.isShiftKeyDown()) {
            clearBindings(stack);
            player.displayClientMessage(
                Component.literal("Cleared all remote bindings")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    // ==================== Tick Logic ====================

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        if (!selected && player.getOffhandItem() != stack) return; // only active in hand 

        // Throttle to every 2 ticks
        if (level.getGameTime() % 2 != 0) return;

        if (!TweakedControllerCompat.isLoaded()) return;

        BlockPos lecternPos = getLinkedLectern(stack);
        if (lecternPos == null) return;

        // Read controller state from lectern
        TweakedControllerReader.ControllerData data = TweakedControllerReader.readFromLectern(level, lecternPos);
        if (data == null || !data.hasUser()) return;

        // Apply to all bound targets
        List<TargetBinding> targets = getTargets(stack);
        for (TargetBinding target : targets) {
            applyControllerToTarget(level, data, target);
        }
    }

    private void applyControllerToTarget(Level level, TweakedControllerReader.ControllerData data, TargetBinding target) {
        BlockEntity be = level.getBlockEntity(target.pos);
        if (be == null || be.isRemoved()) return;

        if (be instanceof LogicDriveBlockEntity drive) {
            applyToDrive(data, drive);
        } else if (be instanceof CreativeLogicMotorBlockEntity motor) {
            applyToMotor(data, motor);
        }
    }

    private void applyToDrive(TweakedControllerReader.ControllerData data, LogicDriveBlockEntity drive) {
        // Left stick Y (axis 1, 0-indexed) → speed modifier (-16 to 16)
        float axisY = data.getAxis(1);
        float modifier = axisY * 16.0f;
        // Quantize to 0.5 steps to reduce kinetic network thrashing
        modifier = Math.round(modifier * 2.0f) / 2.0f;
        if (Math.abs(modifier) < 0.25f) modifier = 0;

        drive.setSpeedModifier(modifier);

        // Button A (index 0) → enable toggle (only on press edge, handled by state)
        drive.setMotorEnabled(data.getButton(0));

        // Button Y (index 3) → reverse
        drive.setReversed(data.getButton(3));
    }

    private void applyToMotor(TweakedControllerReader.ControllerData data, CreativeLogicMotorBlockEntity motor) {
        // Right trigger (axis 5, 0-indexed) → speed (0 to 256)
        float trigger = data.getAxis(5);
        float speed = trigger * 256.0f;
        // Left trigger (axis 4) → negative speed
        float leftTrigger = data.getAxis(4);
        if (leftTrigger > 0.1f) {
            speed = -leftTrigger * 256.0f;
        }
        // Quantize to whole RPM
        speed = Math.round(speed);
        if (Math.abs(speed) < 4) speed = 0;

        motor.setMotorSpeed((int) speed);

        // Button B (index 1) → enable
        motor.setEnabled(data.getButton(1));
    }

    // ==================== NBT Storage ====================

    private CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag();
    }

    private void saveTag(ItemStack stack, CompoundTag tag) {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public void setLinkedLectern(ItemStack stack, BlockPos pos) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putInt("LecternX", pos.getX());
        tag.putInt("LecternY", pos.getY());
        tag.putInt("LecternZ", pos.getZ());
        saveTag(stack, tag);
    }

    public BlockPos getLinkedLectern(ItemStack stack) {
        CompoundTag tag = getOrCreateTag(stack);
        if (!tag.contains("LecternX")) return null;
        return new BlockPos(tag.getInt("LecternX"), tag.getInt("LecternY"), tag.getInt("LecternZ"));
    }

    public boolean addTarget(ItemStack stack, BlockPos pos, String type) {
        CompoundTag tag = getOrCreateTag(stack);
        ListTag targets = tag.getList("Targets", Tag.TAG_COMPOUND);
        if (targets.size() >= MAX_TARGETS) return false;

        // Check for duplicates
        for (int i = 0; i < targets.size(); i++) {
            CompoundTag t = targets.getCompound(i);
            if (t.getInt("X") == pos.getX() && t.getInt("Y") == pos.getY() && t.getInt("Z") == pos.getZ()) {
                return true; // Already bound
            }
        }

        CompoundTag entry = new CompoundTag();
        entry.putInt("X", pos.getX());
        entry.putInt("Y", pos.getY());
        entry.putInt("Z", pos.getZ());
        entry.putString("Type", type);
        targets.add(entry);
        tag.put("Targets", targets);
        saveTag(stack, tag);
        return true;
    }

    public List<TargetBinding> getTargets(ItemStack stack) {
        CompoundTag tag = getOrCreateTag(stack);
        ListTag targets = tag.getList("Targets", Tag.TAG_COMPOUND);
        List<TargetBinding> result = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            CompoundTag t = targets.getCompound(i);
            result.add(new TargetBinding(
                new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z")),
                t.getString("Type")
            ));
        }
        return result;
    }

    public void clearBindings(ItemStack stack) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.remove("LecternX");
        tag.remove("LecternY");
        tag.remove("LecternZ");
        tag.remove("Targets");
        saveTag(stack, tag);
    }

    // ==================== Tooltip ====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        BlockPos lectern = getLinkedLectern(stack);
        if (lectern != null) {
            tooltip.add(Component.literal("Input: Lectern at " + lectern.toShortString())
                .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("No input linked")
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Sneak + click a Tweaked Lectern to link")
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        List<TargetBinding> targets = getTargets(stack);
        if (!targets.isEmpty()) {
            tooltip.add(Component.literal("Targets: " + targets.size())
                .withStyle(ChatFormatting.AQUA));
            for (TargetBinding t : targets) {
                tooltip.add(Component.literal("  " + t.type + " at " + t.pos.toShortString())
                    .withStyle(ChatFormatting.DARK_AQUA));
            }
        } else {
            tooltip.add(Component.literal("No targets bound")
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Click a Drive or Motor to bind")
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        tooltip.add(Component.literal("Sneak + click air to clear")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    // ==================== Data Classes ====================

    public record TargetBinding(BlockPos pos, String type) {}
}
