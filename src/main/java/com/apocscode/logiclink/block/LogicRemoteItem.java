package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.controller.LogicRemoteMenu;
import com.apocscode.logiclink.controller.RemoteClientHandler;
import com.apocscode.logiclink.network.IHubDevice;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Logic Remote — a handheld gamepad controller for Create's Redstone Link
 * network AND LogicLink drives/motors.
 * <p>
 * Matches CTC's TweakedLinkedControllerItem behavior:
 * <ul>
 *   <li>Right-click: toggle active mode (gamepad → redstone link signals)</li>
 *   <li>Shift + right-click: open frequency configuration GUI (ghost item slots)</li>
 *   <li>Right-click on Redstone Link: enter bind mode (assign button/axis to link)</li>
 *   <li>Right-click on Logic Drive/Motor: add as drive/motor target</li>
 * </ul>
 * <p>
 * 50 ghost slots: 15 buttons × 2 frequency items + 10 axes × 2 frequency items.
 * Active mode reads GLFW gamepad input, encodes button/axis states, and sends
 * them to the server which maps frequencies to Create's Redstone Link network.
 */
public class LogicRemoteItem extends Item implements MenuProvider {

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

        if (player == null) return InteractionResult.PASS;

        BlockState hitState = level.getBlockState(pos);

        // Right-click on Create Redstone Link = enter bind mode (client-side)
        if (!player.isShiftKeyDown() && AllBlocks.REDSTONE_LINK.has(hitState)) {
            if (level.isClientSide) {
                toggleBindModeClient(pos);
            }
            player.getCooldowns().addCooldown(this, 2);
            return InteractionResult.SUCCESS;
        }

        // Right-click a drive or motor = add as target (server-side)
        if (!level.isClientSide) {
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
        }

        // Fall through to use() for normal right-click behavior
        return use(level, player, context.getHand()).getResult();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);

        // Shift + right-click in main hand = open frequency config menu
        if (player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
            if (!level.isClientSide && player instanceof ServerPlayer sp && player.mayBuild()) {
                sp.openMenu(this, buf -> {
                    ItemStack.STREAM_CODEC.encode(buf, heldItem);
                });
            }
            return InteractionResultHolder.success(heldItem);
        }

        // Normal right-click = toggle active mode (client-side)
        if (!player.isShiftKeyDown()) {
            if (level.isClientSide) {
                toggleActiveClient();
            }
            player.getCooldowns().addCooldown(this, 2);
        }

        return InteractionResultHolder.pass(heldItem);
    }

    @OnlyIn(Dist.CLIENT)
    private void toggleBindModeClient(BlockPos pos) {
        RemoteClientHandler.toggleBindMode(pos);
    }

    @OnlyIn(Dist.CLIENT)
    private void toggleActiveClient() {
        RemoteClientHandler.toggle();
    }

    // ==================== MenuProvider ====================

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        ItemStack heldItem = player.getMainHandItem();
        return LogicRemoteMenu.create(id, inv, heldItem);
    }

    @Override
    public Component getDisplayName() {
        return getDescription();
    }

    // ==================== Frequency Items (Ghost Inventory) ====================

    /**
     * Get the 50-slot ghost inventory for frequency items from a controller's NBT.
     * Slots 0-29: button frequency pairs (15 buttons × 2 items each)
     * Slots 30-49: axis frequency pairs (10 axes × 2 items each)
     */
    public static ItemStackHandler getFrequencyItems(ItemStack stack) {
        ItemStackHandler newInv = new ItemStackHandler(50);
        if (!(stack.getItem() instanceof LogicRemoteItem))
            throw new IllegalArgumentException("Cannot get frequency items from non-controller: " + stack);
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag invNBT = customData.copyTag().getCompound("Items");
        if (!invNBT.isEmpty())
            newInv.deserializeNBT(RegistryAccess.EMPTY, invNBT);
        return newInv;
    }

    /**
     * Get the frequency pair for a given button/axis slot index.
     * Slots 0-14: buttons, Slots 15-24: axes.
     */
    public static Couple<Frequency> toFrequency(ItemStack controller, int slot) {
        ItemStackHandler frequencyItems = getFrequencyItems(controller);
        return Couple.create(
                Frequency.of(frequencyItems.getStackInSlot(slot * 2)),
                Frequency.of(frequencyItems.getStackInSlot(slot * 2 + 1)));
    }

    // ==================== NBT Storage ====================

    private static CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag();
    }

    private static void saveTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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
        tag.remove("Targets");
        tag.remove("Items");
        saveTag(stack, tag);
    }

    // ==================== Tooltip ====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        List<TargetBinding> targets = getTargets(stack);
        if (!targets.isEmpty()) {
            tooltip.add(Component.literal("Drive/Motor Targets: " + targets.size())
                    .withStyle(ChatFormatting.AQUA));
            for (TargetBinding t : targets) {
                String icon = "drive".equals(t.type) ? "\u25B6" : "\u2699";
                tooltip.add(Component.literal("  " + icon + " " + t.type + " at " + t.pos.toShortString())
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Right-click: Toggle controller")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Shift + Right-click: Configure frequencies")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Click Redstone Link: Bind button/axis")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Click Drive/Motor: Add target")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    // ==================== Data ====================

    public record TargetBinding(BlockPos pos, String type) {}
}
