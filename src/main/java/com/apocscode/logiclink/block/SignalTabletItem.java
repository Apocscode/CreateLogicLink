package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.peripheral.TrainNetworkDataReader;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Handheld Signal Diagnostic Tablet.
 * Right-click to scan the train network and open a diagnostic screen
 * showing signal issues, conflicts, and suggested placements.
 * Updates automatically after signal repairs are made (re-scan on right-click).
 */
public class SignalTabletItem extends Item {

    /** NBT key for cached diagnostic data */
    public static final String TAG_SCAN_DATA = "SignalScanData";
    public static final String TAG_SCAN_TIME = "ScanTime";

    public SignalTabletItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverLevel) {
            // Server: perform a fresh network scan and store diagnostics in item data
            CompoundTag scanData = TrainNetworkDataReader.scanDiagnosticsOnly(serverLevel);
            if (scanData != null) {
                // Store player position for distance-sorted display
                scanData.putDouble("playerX", player.getX());
                scanData.putDouble("playerY", player.getY());
                scanData.putDouble("playerZ", player.getZ());

                CompoundTag wrapper = new CompoundTag();
                wrapper.put(TAG_SCAN_DATA, scanData);
                wrapper.putLong(TAG_SCAN_TIME, level.getGameTime());
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(wrapper));
                player.sendSystemMessage(Component.literal(
                        "\u00A7a[Signal Tablet]\u00A7r Scan complete: "
                        + scanData.getInt("issueCount") + " issue(s) found"));
            } else {
                player.sendSystemMessage(Component.literal(
                        "\u00A7c[Signal Tablet]\u00A7r No train network found"));
            }
        }

        if (level.isClientSide) {
            // Client: open the screen
            openTabletScreen(stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void openTabletScreen(ItemStack stack) {
        com.apocscode.logiclink.client.SignalTabletScreen.openFromItem(stack);
    }

    /** Extract scan data from the item's CustomData component. */
    public static CompoundTag getScanData(ItemStack stack) {
        CustomData cd = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag wrapper = cd.copyTag();
        if (wrapper.contains(TAG_SCAN_DATA)) {
            return wrapper.getCompound(TAG_SCAN_DATA);
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.literal("\u00A77Right-click to scan signal network"));
        CompoundTag data = getScanData(stack);
        if (data != null) {
            int issues = data.getInt("issueCount");
            if (issues > 0) {
                tips.add(Component.literal("\u00A7c" + issues + " issue(s) detected"));
            } else {
                tips.add(Component.literal("\u00A7aNo issues detected"));
            }
        } else {
            tips.add(Component.literal("\u00A78Not yet scanned"));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Enchantment glint when issues are found
        CompoundTag data = getScanData(stack);
        return data != null && data.getInt("issueCount") > 0;
    }
}
