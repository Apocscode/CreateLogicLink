package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.compat.TweakedControllerCompat;
import com.apocscode.logiclink.compat.TweakedControllerReader;
import com.apocscode.logiclink.network.IHubDevice;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * Logic Remote — a handheld controller for LogicLink drives and motors.
 * <p>
 * Works standalone (opens a GUI with virtual controls) and optionally
 * integrates with Create: Tweaked Controllers for gamepad input.
 * <p>
 * Usage:
 * <ol>
 *   <li>Right-click a Logic Drive or Creative Logic Motor to add it as a target (up to 8)</li>
 *   <li>Right-click air to open the control GUI</li>
 *   <li>Sneak + right-click air to clear all bindings</li>
 *   <li>(Optional) Sneak + right-click a CTC Tweaked Lectern to link gamepad input</li>
 * </ol>
 * <p>
 * The GUI provides virtual sliders and buttons to control bound devices:
 * <ul>
 *   <li>Drive speed modifier slider (-16 to 16)</li>
 *   <li>Drive enable/disable and reverse toggles</li>
 *   <li>Motor speed slider (-256 to 256 RPM)</li>
 *   <li>Motor enable/disable toggle</li>
 *   <li>Emergency stop</li>
 * </ul>
 * <p>
 * When CTC is installed and a lectern is linked, the remote also reads
 * gamepad axis/button input and applies it continuously while held.
 */
public class LogicRemoteItem extends Item {

    /** Maximum number of drive/motor targets that can be bound. */
    public static final int MAX_TARGETS = 8;

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

        // Sneak + right-click a CTC lectern = link as gamepad input source (optional)
        if (player.isShiftKeyDown()) {
            if (TweakedControllerCompat.isLoaded() && TweakedControllerReader.isTweakedLectern(level, pos)) {
                setLinkedLectern(stack, pos);
                player.displayClientMessage(
                    Component.literal("Linked gamepad input from Lectern at " + pos.toShortString())
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

        // Sneak + right-click air = clear bindings
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                clearBindings(stack);
                player.displayClientMessage(
                    Component.literal("Cleared all remote bindings")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResultHolder.success(stack);
        }

        // Right-click air = open control GUI (client-side)
        if (level.isClientSide) {
            openRemoteScreen();
        }
        return InteractionResultHolder.success(stack);
    }

    /**
     * Opens the Logic Remote control screen on the client.
     * Called only on the client side via a separate class to avoid
     * loading client-only classes on the server.
     */
    private void openRemoteScreen() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            LogicRemoteScreenOpener.open();
        }
    }

    // ==================== CTC Gamepad Tick (Optional) ====================

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        if (!selected && player.getOffhandItem() != stack) return;

        // Throttle to every 2 ticks
        if (level.getGameTime() % 2 != 0) return;

        // CTC gamepad integration only when CTC is installed and lectern is linked
        if (!TweakedControllerCompat.isLoaded()) return;

        BlockPos lecternPos = getLinkedLectern(stack);
        if (lecternPos == null) return;

        // Read controller state from CTC lectern
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
        float axisY = data.getAxis(1);
        float modifier = axisY * 16.0f;
        modifier = Math.round(modifier * 2.0f) / 2.0f;
        if (Math.abs(modifier) < 0.25f) modifier = 0;
        drive.setSpeedModifier(modifier);
        drive.setMotorEnabled(data.getButton(0));
        drive.setReversed(data.getButton(3));
    }

    private void applyToMotor(TweakedControllerReader.ControllerData data, CreativeLogicMotorBlockEntity motor) {
        float trigger = data.getAxis(5);
        float speed = trigger * 256.0f;
        float leftTrigger = data.getAxis(4);
        if (leftTrigger > 0.1f) {
            speed = -leftTrigger * 256.0f;
        }
        speed = Math.round(speed);
        if (Math.abs(speed) < 4) speed = 0;
        motor.setMotorSpeed((int) speed);
        motor.setEnabled(data.getButton(1));
    }

    // ==================== NBT Storage ====================

    private CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag();
    }

    private void saveTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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

        for (int i = 0; i < targets.size(); i++) {
            CompoundTag t = targets.getCompound(i);
            if (t.getInt("X") == pos.getX() && t.getInt("Y") == pos.getY() && t.getInt("Z") == pos.getZ()) {
                return true;
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

        List<TargetBinding> targets = getTargets(stack);
        if (!targets.isEmpty()) {
            tooltip.add(Component.literal("Targets: " + targets.size())
                .withStyle(ChatFormatting.AQUA));
            for (TargetBinding t : targets) {
                String icon = "drive".equals(t.type) ? "\u25B6" : "\u2699";
                tooltip.add(Component.literal("  " + icon + " " + t.type + " at " + t.pos.toShortString())
                    .withStyle(ChatFormatting.DARK_AQUA));
            }
        } else {
            tooltip.add(Component.literal("No targets bound")
                .withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Right-click: Open control GUI")
            .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Click Drive/Motor: Add target")
            .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Sneak + click air: Clear all")
            .withStyle(ChatFormatting.DARK_GRAY));

        BlockPos lectern = getLinkedLectern(stack);
        if (lectern != null) {
            tooltip.add(Component.literal("Gamepad: Lectern at " + lectern.toShortString())
                .withStyle(ChatFormatting.GREEN));
        } else if (TweakedControllerCompat.isLoaded()) {
            tooltip.add(Component.literal("Sneak + click Lectern: Link gamepad")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ==================== Data ====================

    public record TargetBinding(BlockPos pos, String type) {}
}
