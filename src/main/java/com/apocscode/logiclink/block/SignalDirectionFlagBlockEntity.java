package com.apocscode.logiclink.block;

import com.apocscode.logiclink.LogicLink;
import com.apocscode.logiclink.ModRegistry;
import com.simibubi.create.content.trains.track.ITrackBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SignalDirectionFlagBlockEntity extends BlockEntity {

    public enum FlagMode {
        ONE_WAY_FORWARD,
        ONE_WAY_REVERSE,
        BIDIRECTIONAL;

        public FlagMode next() {
            return switch (this) {
                case ONE_WAY_FORWARD -> ONE_WAY_REVERSE;
                case ONE_WAY_REVERSE -> BIDIRECTIONAL;
                case BIDIRECTIONAL -> ONE_WAY_FORWARD;
            };
        }
    }

    @Nullable private BlockPos anchorTrackPos;
    private FlagMode mode = FlagMode.ONE_WAY_FORWARD;

    public SignalDirectionFlagBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SIGNAL_DIRECTION_FLAG_BE.get(), pos, state);
    }

    @Nullable
    public BlockPos getAnchorTrackPos() {
        return anchorTrackPos;
    }

    public FlagMode getMode() {
        return mode;
    }

    public boolean isForTrack(BlockPos trackPos) {
        return anchorTrackPos != null && anchorTrackPos.equals(trackPos);
    }

    public void cycleMode(Player player) {
        mode = mode.next();
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        if (player instanceof ServerPlayer sp) {
            String anchorText = anchorTrackPos == null ? "none" : anchorTrackPos.toShortString();
            sp.sendSystemMessage(Component.literal("[Signal Flag] mode=" + mode + ", anchor=" + anchorText));
        }
    }

    public void initializeFromPlacement(@Nullable Player placer) {
        if (level == null) return;

        BlockState state = getBlockState();
        Direction facing = state.hasProperty(SignalDirectionFlagBlock.FACING)
                ? state.getValue(SignalDirectionFlagBlock.FACING)
                : Direction.NORTH;

        BlockPos foundTrack = findNearestTrack(level, worldPosition, 2);
        if (foundTrack == null) {
            LogicLink.LOGGER.warn("SignalFlag: no track found near {} during placement", worldPosition);
            anchorTrackPos = null;
            setChanged();
            return;
        }

        anchorTrackPos = foundTrack;

        // Resolve default one-way direction from player-facing vs track axis.
        BlockState trackState = level.getBlockState(foundTrack);
        if (trackState.getBlock() instanceof ITrackBlock trackBlock) {
            List<Vec3> axes = trackBlock.getTrackAxes(level, foundTrack, trackState);
            if (!axes.isEmpty()) {
                Vec3 axis = axes.get(0).normalize();
                Vec3 facingVec = new Vec3(facing.getStepX(), 0, facing.getStepZ());
                double dot = axis.dot(facingVec);
                mode = dot >= 0 ? FlagMode.ONE_WAY_FORWARD : FlagMode.ONE_WAY_REVERSE;
            }
        }

        setChanged();
        level.sendBlockUpdated(worldPosition, state, state, 3);

        if (placer instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal("[Signal Flag] anchored to track " + foundTrack.toShortString() + " mode=" + mode));
        }
    }

    public boolean initializeFromTrackTargetingData(CompoundTag blockEntityData, @Nullable Player placer) {
        if (level == null) return false;
        if (!blockEntityData.contains("TargetTrack")) return false;

        java.util.Optional<BlockPos> relativeTargetOptional = NbtUtils.readBlockPos(blockEntityData, "TargetTrack");
        if (relativeTargetOptional.isEmpty()) return false;
        BlockPos relativeTarget = relativeTargetOptional.get();
        BlockPos absoluteTarget = worldPosition.offset(relativeTarget);
        if (!(level.getBlockState(absoluteTarget).getBlock() instanceof ITrackBlock)) {
            return false;
        }

        anchorTrackPos = absoluteTarget;
        boolean targetDirection = blockEntityData.getBoolean("TargetDirection");
        // Create stores TargetDirection directly from the selected track direction.
        mode = targetDirection ? FlagMode.ONE_WAY_FORWARD : FlagMode.ONE_WAY_REVERSE;

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        if (placer instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal("[Signal Flag] anchored to selected track "
                    + absoluteTarget.toShortString() + " mode=" + mode));
        }

        return true;
    }

    @Nullable
    public FlagOverride getOverride() {
        if (anchorTrackPos == null) {
            return null;
        }

        return switch (mode) {
            case ONE_WAY_FORWARD -> new FlagOverride(anchorTrackPos, true, false);
            case ONE_WAY_REVERSE -> new FlagOverride(anchorTrackPos, false, false);
            case BIDIRECTIONAL -> new FlagOverride(anchorTrackPos, true, true);
        };
    }

    public record FlagOverride(BlockPos trackPos, boolean forward, boolean bidirectional) {
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (anchorTrackPos != null) {
            tag.putLong("AnchorTrack", anchorTrackPos.asLong());
        }
        tag.putString("Mode", mode.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        anchorTrackPos = tag.contains("AnchorTrack") ? BlockPos.of(tag.getLong("AnchorTrack")) : null;
        if (tag.contains("Mode")) {
            try {
                mode = FlagMode.valueOf(tag.getString("Mode"));
            } catch (IllegalArgumentException ignored) {
                mode = FlagMode.ONE_WAY_FORWARD;
            }
        } else {
            mode = FlagMode.ONE_WAY_FORWARD;
        }
    }

    @Nullable
    private static BlockPos findNearestTrack(Level level, BlockPos origin, int radius) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    if (!(level.getBlockState(p).getBlock() instanceof ITrackBlock))
                        continue;
                    double d = p.distSqr(origin);
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    public void resetAnchor() {
        anchorTrackPos = null;
        mode = FlagMode.ONE_WAY_FORWARD;
        setChanged();
    }
}
