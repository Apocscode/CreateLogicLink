package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.apocscode.logiclink.controller.LogicRemoteMenu;
import com.apocscode.logiclink.controller.RemoteClientHandler;
import com.apocscode.logiclink.network.HubNetwork;
import com.apocscode.logiclink.network.IHubDevice;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.platform.CatnipServices;

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

    // ==================== Interaction (CTC-style) ====================

    /**
     * Fires BEFORE block interaction — exact same pattern as CTC's
     * TweakedLinkedControllerItem.onItemUseFirst().
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx)
    {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level world = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState hitState = world.getBlockState(pos);

        if (player.mayBuild())
        {
            if (player.isShiftKeyDown())
            {
                // Shift + right-click on Logic Link hub = link to hub
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof LogicLinkBlockEntity hub)
                {
                    if (!world.isClientSide)
                    {
                        String label = hub.getHubLabel();
                        linkToHub(stack, pos, label);
                        String displayName = label.isEmpty()
                                ? "Hub at " + pos.toShortString()
                                : "\"" + label + "\" (" + pos.toShortString() + ")";
                        player.displayClientMessage(
                                Component.literal("Linked to " + displayName)
                                        .withStyle(ChatFormatting.GREEN), true);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            else
            {
                // Right-click on Redstone Link = enter bind mode (client-side)
                if (AllBlocks.REDSTONE_LINK.has(hitState))
                {
                    if (world.isClientSide)
                        CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> this.toggleBindMode(ctx.getClickedPos()));
                    player.getCooldowns().addCooldown(this, 2);
                    return InteractionResult.SUCCESS;
                }

                // Right-click on a drive or motor = add as target (server-side)
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof IHubDevice device)
                {
                    String type = device.getDeviceType();
                    if ("drive".equals(type) || "creative_motor".equals(type))
                    {
                        if (!world.isClientSide)
                        {
                            if (addTarget(stack, pos, type))
                            {
                                String label = device.getHubLabel().isEmpty() ? type : device.getHubLabel();
                                player.displayClientMessage(
                                        Component.literal("Bound " + label + " at " + pos.toShortString())
                                                .withStyle(ChatFormatting.AQUA), true);
                            }
                            else
                            {
                                player.displayClientMessage(
                                        Component.literal("Max targets reached (" + MAX_TARGETS + ")")
                                                .withStyle(ChatFormatting.RED), true);
                            }
                        }
                        return InteractionResult.SUCCESS;
                    }
                }
            }
        }

        return use(world, player, ctx.getHand()).getResult();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand)
    {
        ItemStack heldItem = player.getItemInHand(hand);

        // Shift + right-click in main hand = open control panel GUI
        if (player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND)
        {
            if (!world.isClientSide && player instanceof ServerPlayer sp && player.mayBuild())
                sp.openMenu(this, buf -> {
                    ItemStack.STREAM_CODEC.encode(buf, heldItem);
                });
            return InteractionResultHolder.success(heldItem);
        }

        // Normal right-click = toggle active mode
        if (!player.isShiftKeyDown())
        {
            if (world.isClientSide)
                CatnipServices.PLATFORM.executeOnClientOnly(() -> this::toggleActive);
            player.getCooldowns().addCooldown(this, 2);
        }

        return InteractionResultHolder.pass(heldItem);
    }

    @OnlyIn(Dist.CLIENT)
    private void toggleBindMode(BlockPos pos)
    {
        RemoteClientHandler.toggleBindMode(pos);
    }

    @OnlyIn(Dist.CLIENT)
    private void toggleActive()
    {
        RemoteClientHandler.toggle();
    }

    // ==================== Hub Linking ====================

    /**
     * Store a link to a Logic Hub (Logic Link block) in the item's NBT.
     */
    public static void linkToHub(ItemStack stack, BlockPos hubPos, String hubLabel) {
        CompoundTag tag = getOrCreateTag(stack);
        tag.putInt("HubX", hubPos.getX());
        tag.putInt("HubY", hubPos.getY());
        tag.putInt("HubZ", hubPos.getZ());
        tag.putBoolean("HubLinked", true);
        tag.putString("HubLabel", hubLabel != null ? hubLabel : "");
        saveTag(stack, tag);
    }

    /**
     * Get the linked hub position, or null if not linked.
     */
    public static BlockPos getLinkedHub(ItemStack stack) {
        CompoundTag tag = getOrCreateTag(stack);
        if (!tag.getBoolean("HubLinked")) return null;
        return new BlockPos(tag.getInt("HubX"), tag.getInt("HubY"), tag.getInt("HubZ"));
    }

    /**
     * Get the label of the linked hub, or empty string if none.
     */
    public static String getLinkedHubLabel(ItemStack stack) {
        CompoundTag tag = getOrCreateTag(stack);
        return tag.contains("HubLabel") ? tag.getString("HubLabel") : "";
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

        BlockPos hub = getLinkedHub(stack);
        if (hub != null) {
            tooltip.add(Component.literal("Hub: " + hub.toShortString())
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("No hub linked")
                    .withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Right-click: Toggle controller (WASD)")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Shift + Right-click: Open control panel")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Shift + Click Logic Link: Link to hub")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Click Redstone Link: Bind button/axis")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Click Drive/Motor: Add target")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    // ==================== Data ====================

    public record TargetBinding(BlockPos pos, String type) {}
}
