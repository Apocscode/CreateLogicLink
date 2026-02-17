/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ArrayListMultimap
 *  com.google.common.collect.Multimap
 *  it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap
 *  it.unimi.dsi.fastutil.objects.Object2BooleanMap
 *  net.createmod.catnip.data.Iterate
 *  net.createmod.catnip.data.UniqueLinkedList
 *  net.createmod.catnip.math.BBHelper
 *  net.createmod.catnip.math.BlockFace
 *  net.createmod.catnip.nbt.NBTHelper
 *  net.createmod.catnip.nbt.NBTProcessors
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Direction$Axis
 *  net.minecraft.core.HolderGetter
 *  net.minecraft.core.HolderLookup
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.core.IdMap
 *  net.minecraft.core.Vec3i
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.ListTag
 *  net.minecraft.nbt.NbtUtils
 *  net.minecraft.nbt.Tag
 *  net.minecraft.network.protocol.game.DebugPackets
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.ai.village.poi.PoiTypes
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.LevelAccessor
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.ButtonBlock
 *  net.minecraft.world.level.block.ChestBlock
 *  net.minecraft.world.level.block.DoorBlock
 *  net.minecraft.world.level.block.PressurePlateBlock
 *  net.minecraft.world.level.block.Rotation
 *  net.minecraft.world.level.block.SimpleWaterloggedBlock
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.StateHolder
 *  net.minecraft.world.level.block.state.properties.BlockStateProperties
 *  net.minecraft.world.level.block.state.properties.ChestType
 *  net.minecraft.world.level.block.state.properties.PistonType
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.level.chunk.HashMapPalette
 *  net.minecraft.world.level.levelgen.structure.BoundingBox
 *  net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate$StructureBlockInfo
 *  net.minecraft.world.level.material.FluidState
 *  net.minecraft.world.level.material.Fluids
 *  net.minecraft.world.level.material.PushReaction
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  net.minecraft.world.phys.shapes.BooleanOp
 *  net.minecraft.world.phys.shapes.CollisionContext
 *  net.minecraft.world.phys.shapes.Shapes
 *  net.minecraft.world.phys.shapes.VoxelShape
 *  net.neoforged.neoforge.registries.GameData
 *  org.apache.commons.lang3.tuple.MutablePair
 *  org.apache.commons.lang3.tuple.Pair
 *  org.jetbrains.annotations.Contract
 *  org.jetbrains.annotations.Nullable
 */
package com.simibubi.create.content.contraptions;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllTags;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ContraptionWorld;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlock;
import com.simibubi.create.content.contraptions.bearing.StabilizedContraption;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlock;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.chassis.AbstractChassisBlock;
import com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity;
import com.simibubi.create.content.contraptions.chassis.StickerBlock;
import com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonHeadBlock;
import com.simibubi.create.content.contraptions.piston.PistonExtensionPoleBlock;
import com.simibubi.create.content.contraptions.pulley.PulleyBlock;
import com.simibubi.create.content.contraptions.pulley.PulleyBlockEntity;
import com.simibubi.create.content.contraptions.render.ClientContraption;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.simibubi.create.content.logistics.crate.CreativeCrateBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.redstone.contact.RedstoneContactBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.infrastructure.config.AllConfigs;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.UniqueLinkedList;
import net.createmod.catnip.math.BBHelper;
import net.createmod.catnip.math.BlockFace;
import net.createmod.catnip.nbt.NBTHelper;
import net.createmod.catnip.nbt.NBTProcessors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.IdMap;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.GameData;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public abstract class Contraption {
    public Optional<List<AABB>> simplifiedEntityColliders;
    public AbstractContraptionEntity entity;
    public AABB bounds;
    public BlockPos anchor;
    public boolean stalled;
    public boolean hasUniversalCreativeCrate;
    public boolean disassembled;
    protected Map<BlockPos, StructureTemplate.StructureBlockInfo> blocks;
    protected Map<BlockPos, CompoundTag> updateTags;
    public Object2BooleanMap<BlockPos> isLegacy;
    protected List<MutablePair<StructureTemplate.StructureBlockInfo, MovementContext>> actors;
    protected Map<BlockPos, MovingInteractionBehaviour> interactors;
    protected List<ItemStack> disabledActors;
    protected List<AABB> superglue;
    protected List<BlockPos> seats;
    protected Map<UUID, Integer> seatMapping;
    protected Map<UUID, BlockFace> stabilizedSubContraptions;
    protected MountedStorageManager storage;
    protected Multimap<BlockPos, StructureTemplate.StructureBlockInfo> capturedMultiblocks;
    private Set<SuperGlueEntity> glueToRemove;
    private Map<BlockPos, Entity> initialPassengers;
    private List<BlockFace> pendingSubContraptions;
    private CompletableFuture<Void> simplifiedEntityColliderProvider;
    private final AtomicReference<ClientContraption> clientContraption = new AtomicReference();
    protected ContraptionWorld collisionLevel;

    public Contraption() {
        this.blocks = new HashMap<BlockPos, StructureTemplate.StructureBlockInfo>();
        this.updateTags = new HashMap<BlockPos, CompoundTag>();
        this.isLegacy = new Object2BooleanArrayMap();
        this.seats = new ArrayList<BlockPos>();
        this.actors = new ArrayList<MutablePair<StructureTemplate.StructureBlockInfo, MovementContext>>();
        this.disabledActors = new ArrayList<ItemStack>();
        this.interactors = new HashMap<BlockPos, MovingInteractionBehaviour>();
        this.superglue = new ArrayList<AABB>();
        this.seatMapping = new HashMap<UUID, Integer>();
        this.glueToRemove = new HashSet<SuperGlueEntity>();
        this.initialPassengers = new HashMap<BlockPos, Entity>();
        this.pendingSubContraptions = new ArrayList<BlockFace>();
        this.stabilizedSubContraptions = new HashMap<UUID, BlockFace>();
        this.simplifiedEntityColliders = Optional.empty();
        this.storage = new MountedStorageManager();
        this.capturedMultiblocks = ArrayListMultimap.create();
    }

    public ContraptionWorld getContraptionWorld() {
        if (this.collisionLevel == null) {
            this.collisionLevel = new ContraptionWorld(this.entity.level(), this);
        }
        return this.collisionLevel;
    }

    public abstract boolean assemble(Level var1, BlockPos var2) throws AssemblyException;

    public abstract boolean canBeStabilized(Direction var1, BlockPos var2);

    public abstract ContraptionType getType();

    protected boolean customBlockPlacement(LevelAccessor world, BlockPos pos, BlockState state) {
        return false;
    }

    protected boolean customBlockRemoval(LevelAccessor world, BlockPos pos, BlockState state) {
        return false;
    }

    protected boolean addToInitialFrontier(Level world, BlockPos pos, Direction forcedDirection, Queue<BlockPos> frontier) throws AssemblyException {
        return true;
    }

    public static Contraption fromNBT(Level world, CompoundTag nbt, boolean spawnData) {
        String type = nbt.getString("Type");
        Contraption contraption = ContraptionType.fromType(type);
        contraption.readNBT(world, nbt, spawnData);
        contraption.collisionLevel = new ContraptionWorld(world, contraption);
        contraption.gatherBBsOffThread();
        return contraption;
    }

    public boolean searchMovedStructure(Level world, BlockPos pos, @Nullable Direction forcedDirection) throws AssemblyException {
        this.initialPassengers.clear();
        UniqueLinkedList frontier = new UniqueLinkedList();
        HashSet<BlockPos> visited = new HashSet<BlockPos>();
        this.anchor = pos;
        if (this.bounds == null) {
            this.bounds = new AABB(BlockPos.ZERO);
        }
        if (!BlockMovementChecks.isBrittle(world.getBlockState(pos))) {
            frontier.add(pos);
        }
        if (!this.addToInitialFrontier(world, pos, forcedDirection, (Queue<BlockPos>)frontier)) {
            return false;
        }
        for (int limit = 100000; limit > 0; --limit) {
            if (frontier.isEmpty()) {
                return true;
            }
            if (this.moveBlock(world, forcedDirection, (Queue<BlockPos>)frontier, visited)) continue;
            return false;
        }
        throw AssemblyException.structureTooLarge();
    }

    public void onEntityCreated(AbstractContraptionEntity entity) {
        this.entity = entity;
        for (BlockFace blockFace : this.pendingSubContraptions) {
            Direction face = blockFace.getFace();
            StabilizedContraption subContraption = new StabilizedContraption(face);
            Level world = entity.level();
            BlockPos pos = blockFace.getPos();
            try {
                if (!subContraption.assemble(world, pos)) {
                }
            }
            catch (AssemblyException e) {}
            continue;
            subContraption.removeBlocksFromWorld(world, BlockPos.ZERO);
            OrientedContraptionEntity movedContraption = OrientedContraptionEntity.create(world, subContraption, face);
            BlockPos anchor = blockFace.getConnectedPos();
            movedContraption.setPos((float)anchor.getX() + 0.5f, anchor.getY(), (float)anchor.getZ() + 0.5f);
            world.addFreshEntity((Entity)movedContraption);
            this.stabilizedSubContraptions.put(movedContraption.getUUID(), new BlockFace(this.toLocalPos(pos), face));
        }
        this.storage.initialize();
        this.gatherBBsOffThread();
    }

    public void onEntityRemoved(AbstractContraptionEntity entity) {
        if (this.simplifiedEntityColliderProvider != null) {
            this.simplifiedEntityColliderProvider.cancel(false);
            this.simplifiedEntityColliderProvider = null;
        }
    }

    public void onEntityInitialize(Level world, AbstractContraptionEntity contraptionEntity) {
        if (world.isClientSide) {
            return;
        }
        for (OrientedContraptionEntity orientedCE : world.getEntitiesOfClass(OrientedContraptionEntity.class, contraptionEntity.getBoundingBox().inflate(1.0))) {
            if (!this.stabilizedSubContraptions.containsKey(orientedCE.getUUID())) continue;
            orientedCE.startRiding(contraptionEntity);
        }
        for (BlockPos seatPos : this.getSeats()) {
            int seatIndex;
            Entity passenger = this.initialPassengers.get(seatPos);
            if (passenger == null || (seatIndex = this.getSeats().indexOf(seatPos)) == -1) continue;
            contraptionEntity.addSittingPassenger(passenger, seatIndex);
        }
    }

    protected boolean moveBlock(Level world, @Nullable Direction forcedDirection, Queue<BlockPos> frontier, Set<BlockPos> visited) throws AssemblyException {
        Direction offset;
        Object attached;
        BlockEntity blockEntity;
        BlockPos pos = frontier.poll();
        if (pos == null) {
            return false;
        }
        visited.add(pos);
        if (world.isOutsideBuildHeight(pos)) {
            return true;
        }
        if (!world.isLoaded(pos)) {
            throw AssemblyException.unloadedChunk(pos);
        }
        if (this.isAnchoringBlockAt(pos)) {
            return true;
        }
        BlockState state = world.getBlockState(pos);
        if (!BlockMovementChecks.isMovementNecessary(state, world, pos)) {
            return true;
        }
        if (!this.movementAllowed(state, world, pos)) {
            throw AssemblyException.unmovableBlock(pos, state);
        }
        if (state.getBlock() instanceof AbstractChassisBlock && !this.moveChassis(world, pos, forcedDirection, frontier, visited)) {
            return false;
        }
        if (AllBlocks.BELT.has(state)) {
            this.moveBelt(pos, frontier, visited, state);
        }
        if (AllBlocks.WINDMILL_BEARING.has(state) && (blockEntity = world.getBlockEntity(pos)) instanceof WindmillBearingBlockEntity) {
            WindmillBearingBlockEntity wbbe = (WindmillBearingBlockEntity)blockEntity;
            wbbe.disassembleForMovement();
        }
        if (AllBlocks.GANTRY_CARRIAGE.has(state)) {
            this.moveGantryPinion(world, pos, frontier, visited, state);
        }
        if (AllBlocks.GANTRY_SHAFT.has(state)) {
            this.moveGantryShaft(world, pos, frontier, visited, state);
        }
        if (AllBlocks.STICKER.has(state) && ((Boolean)state.getValue((Property)StickerBlock.EXTENDED)).booleanValue() && !visited.contains(attached = pos.relative(offset = (Direction)state.getValue((Property)StickerBlock.FACING))) && !BlockMovementChecks.isNotSupportive(world.getBlockState((BlockPos)attached), offset.getOpposite())) {
            frontier.add((BlockPos)attached);
        }
        if ((attached = world.getBlockEntity(pos)) instanceof ChainConveyorBlockEntity) {
            ChainConveyorBlockEntity ccbe = (ChainConveyorBlockEntity)attached;
            ccbe.notifyConnectedToValidate();
        }
        if (state.hasProperty((Property)ChestBlock.TYPE) && state.hasProperty((Property)ChestBlock.FACING) && state.getValue((Property)ChestBlock.TYPE) != ChestType.SINGLE && !visited.contains(attached = pos.relative(offset = ChestBlock.getConnectedDirection((BlockState)state)))) {
            frontier.add((BlockPos)attached);
        }
        if ((attached = state.getBlock()) instanceof AbstractBogeyBlock) {
            AbstractBogeyBlock bogey = (AbstractBogeyBlock)attached;
            for (Direction d : bogey.getStickySurfaces((BlockGetter)world, pos, state)) {
                if (visited.contains(pos.relative(d))) continue;
                frontier.add(pos.relative(d));
            }
        }
        if (AllBlocks.MECHANICAL_BEARING.has(state)) {
            this.moveBearing(pos, frontier, visited, state);
        }
        if (AllBlocks.WINDMILL_BEARING.has(state)) {
            this.moveWindmillBearing(pos, frontier, visited, state);
        }
        if (state.getBlock() instanceof SeatBlock) {
            this.moveSeat(world, pos);
        }
        if (state.getBlock() instanceof PulleyBlock) {
            this.movePulley(world, pos, frontier, visited);
        }
        if (state.getBlock() instanceof MechanicalPistonBlock && !this.moveMechanicalPiston(world, pos, frontier, visited, state)) {
            return false;
        }
        if (MechanicalPistonBlock.isExtensionPole(state)) {
            this.movePistonPole(world, pos, frontier, visited, state);
        }
        if (MechanicalPistonBlock.isPistonHead(state)) {
            this.movePistonHead(world, pos, frontier, visited, state);
        }
        BlockPos posDown = pos.below();
        BlockState stateBelow = world.getBlockState(posDown);
        if (!visited.contains(posDown) && AllBlocks.CART_ASSEMBLER.has(stateBelow)) {
            frontier.add(posDown);
        }
        for (Direction offset2 : Iterate.directions) {
            boolean canStick;
            BlockPos offsetPos = pos.relative(offset2);
            BlockState blockState = world.getBlockState(offsetPos);
            if (this.isAnchoringBlockAt(offsetPos)) continue;
            if (!this.movementAllowed(blockState, world, offsetPos)) {
                if (offset2 != forcedDirection) continue;
                throw AssemblyException.unmovableBlock(pos, state);
            }
            boolean wasVisited = visited.contains(offsetPos);
            boolean faceHasGlue = SuperGlueEntity.isGlued((LevelAccessor)world, pos, offset2, this.glueToRemove);
            boolean blockAttachedTowardsFace = BlockMovementChecks.isBlockAttachedTowards(blockState, world, offsetPos, offset2.getOpposite());
            boolean brittle = BlockMovementChecks.isBrittle(blockState);
            boolean bl = canStick = !brittle && state.canStickTo(blockState) && blockState.canStickTo(state);
            if (canStick) {
                if (state.getPistonPushReaction() == PushReaction.PUSH_ONLY || blockState.getPistonPushReaction() == PushReaction.PUSH_ONLY) {
                    canStick = false;
                }
                if (BlockMovementChecks.isNotSupportive(state, offset2)) {
                    canStick = false;
                }
                if (BlockMovementChecks.isNotSupportive(blockState, offset2.getOpposite())) {
                    canStick = false;
                }
            }
            if (wasVisited || !canStick && !blockAttachedTowardsFace && !faceHasGlue && (offset2 != forcedDirection || BlockMovementChecks.isNotSupportive(state, forcedDirection))) continue;
            frontier.add(offsetPos);
        }
        this.addBlock(world, pos, this.capture(world, pos));
        if (this.blocks.size() <= (Integer)AllConfigs.server().kinetics.maxBlocksMoved.get()) {
            return true;
        }
        throw AssemblyException.structureTooLarge();
    }

    protected void movePistonHead(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited, BlockState state) {
        BlockPos attached;
        Direction direction = (Direction)state.getValue((Property)MechanicalPistonHeadBlock.FACING);
        BlockPos offset = pos.relative(direction.getOpposite());
        if (!visited.contains(offset)) {
            Direction pistonFacing;
            BlockState blockState = world.getBlockState(offset);
            if (MechanicalPistonBlock.isExtensionPole(blockState) && ((Direction)blockState.getValue((Property)PistonExtensionPoleBlock.FACING)).getAxis() == direction.getAxis()) {
                frontier.add(offset);
            }
            if (blockState.getBlock() instanceof MechanicalPistonBlock && (pistonFacing = (Direction)blockState.getValue((Property)MechanicalPistonBlock.FACING)) == direction && blockState.getValue(MechanicalPistonBlock.STATE) == MechanicalPistonBlock.PistonState.EXTENDED) {
                frontier.add(offset);
            }
        }
        if (state.getValue(MechanicalPistonHeadBlock.TYPE) == PistonType.STICKY && !visited.contains(attached = pos.relative(direction))) {
            frontier.add(attached);
        }
    }

    protected void movePistonPole(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited, BlockState state) {
        for (Direction d : Iterate.directionsInAxis((Direction.Axis)((Direction)state.getValue((Property)PistonExtensionPoleBlock.FACING)).getAxis())) {
            Direction pistonFacing;
            BlockPos offset = pos.relative(d);
            if (visited.contains(offset)) continue;
            BlockState blockState = world.getBlockState(offset);
            if (MechanicalPistonBlock.isExtensionPole(blockState) && ((Direction)blockState.getValue((Property)PistonExtensionPoleBlock.FACING)).getAxis() == d.getAxis()) {
                frontier.add(offset);
            }
            if (MechanicalPistonBlock.isPistonHead(blockState) && ((Direction)blockState.getValue((Property)MechanicalPistonHeadBlock.FACING)).getAxis() == d.getAxis()) {
                frontier.add(offset);
            }
            if (!(blockState.getBlock() instanceof MechanicalPistonBlock) || (pistonFacing = (Direction)blockState.getValue((Property)MechanicalPistonBlock.FACING)) != d && (pistonFacing != d.getOpposite() || blockState.getValue(MechanicalPistonBlock.STATE) != MechanicalPistonBlock.PistonState.EXTENDED)) continue;
            frontier.add(offset);
        }
    }

    protected void moveGantryPinion(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited, BlockState state) {
        BlockPos offset = pos.relative((Direction)state.getValue((Property)GantryCarriageBlock.FACING));
        if (!visited.contains(offset)) {
            frontier.add(offset);
        }
        Direction.Axis rotationAxis = ((IRotate)state.getBlock()).getRotationAxis(state);
        for (Direction d : Iterate.directionsInAxis((Direction.Axis)rotationAxis)) {
            offset = pos.relative(d);
            BlockState offsetState = world.getBlockState(offset);
            if (!AllBlocks.GANTRY_SHAFT.has(offsetState) || ((Direction)offsetState.getValue((Property)GantryShaftBlock.FACING)).getAxis() != d.getAxis() || visited.contains(offset)) continue;
            frontier.add(offset);
        }
    }

    protected void moveGantryShaft(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited, BlockState state) {
        for (Direction d : Iterate.directions) {
            BlockPos offset = pos.relative(d);
            if (visited.contains(offset)) continue;
            BlockState offsetState = world.getBlockState(offset);
            Direction facing = (Direction)state.getValue((Property)GantryShaftBlock.FACING);
            if (d.getAxis() == facing.getAxis() && AllBlocks.GANTRY_SHAFT.has(offsetState) && offsetState.getValue((Property)GantryShaftBlock.FACING) == facing) {
                frontier.add(offset);
                continue;
            }
            if (!AllBlocks.GANTRY_CARRIAGE.has(offsetState) || offsetState.getValue((Property)GantryCarriageBlock.FACING) != d) continue;
            frontier.add(offset);
        }
    }

    private void moveWindmillBearing(BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited, BlockState state) {
        Direction facing = (Direction)state.getValue((Property)WindmillBearingBlock.FACING);
        BlockPos offset = pos.relative(facing);
        if (!visited.contains(offset)) {
            frontier.add(offset);
        }
    }

    private void moveBearing(BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited, BlockState state) {
        Direction facing = (Direction)state.getValue((Property)MechanicalBearingBlock.FACING);
        if (!this.canBeStabilized(facing, pos.subtract((Vec3i)this.anchor))) {
            BlockPos offset = pos.relative(facing);
            if (!visited.contains(offset)) {
                frontier.add(offset);
            }
            return;
        }
        this.pendingSubContraptions.add(new BlockFace(pos, facing));
    }

    private void moveBelt(BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited, BlockState state) {
        BlockPos nextPos = BeltBlock.nextSegmentPosition(state, pos, true);
        BlockPos prevPos = BeltBlock.nextSegmentPosition(state, pos, false);
        if (nextPos != null && !visited.contains(nextPos)) {
            frontier.add(nextPos);
        }
        if (prevPos != null && !visited.contains(prevPos)) {
            frontier.add(prevPos);
        }
    }

    private void moveSeat(Level world, BlockPos pos) {
        SeatEntity seat;
        List passengers;
        BlockPos local = this.toLocalPos(pos);
        this.getSeats().add(local);
        List seatsEntities = world.getEntitiesOfClass(SeatEntity.class, new AABB(pos));
        if (!seatsEntities.isEmpty() && !(passengers = (seat = (SeatEntity)((Object)seatsEntities.get(0))).getPassengers()).isEmpty()) {
            this.initialPassengers.put(local, (Entity)passengers.get(0));
        }
    }

    private void movePulley(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited) {
        int limit = (Integer)AllConfigs.server().kinetics.maxRopeLength.get();
        BlockPos ropePos = pos;
        while (limit-- >= 0 && world.isLoaded(ropePos = ropePos.below())) {
            BlockState ropeState = world.getBlockState(ropePos);
            Block block = ropeState.getBlock();
            if (!(block instanceof PulleyBlock.RopeBlock) && !(block instanceof PulleyBlock.MagnetBlock)) {
                if (visited.contains(ropePos)) break;
                frontier.add(ropePos);
                break;
            }
            this.addBlock(world, ropePos, this.capture(world, ropePos));
        }
    }

    private boolean moveMechanicalPiston(Level world, BlockPos pos, Queue<BlockPos> frontier, Set<BlockPos> visited, BlockState state) throws AssemblyException {
        BlockState poleState;
        Direction direction = (Direction)state.getValue((Property)MechanicalPistonBlock.FACING);
        MechanicalPistonBlock.PistonState pistonState = (MechanicalPistonBlock.PistonState)((Object)state.getValue(MechanicalPistonBlock.STATE));
        if (pistonState == MechanicalPistonBlock.PistonState.MOVING) {
            return false;
        }
        BlockPos offset = pos.relative(direction.getOpposite());
        if (!visited.contains(offset) && AllBlocks.PISTON_EXTENSION_POLE.has(poleState = world.getBlockState(offset)) && ((Direction)poleState.getValue((Property)PistonExtensionPoleBlock.FACING)).getAxis() == direction.getAxis()) {
            frontier.add(offset);
        }
        if ((pistonState == MechanicalPistonBlock.PistonState.EXTENDED || MechanicalPistonBlock.isStickyPiston(state)) && !visited.contains(offset = pos.relative(direction))) {
            frontier.add(offset);
        }
        return true;
    }

    private boolean moveChassis(Level world, BlockPos pos, Direction movementDirection, Queue<BlockPos> frontier, Set<BlockPos> visited) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof ChassisBlockEntity)) {
            return false;
        }
        ChassisBlockEntity chassis = (ChassisBlockEntity)be;
        chassis.addAttachedChasses(frontier, visited);
        List<BlockPos> includedBlockPositions = chassis.getIncludedBlockPositions(movementDirection, false);
        if (includedBlockPositions == null) {
            return false;
        }
        for (BlockPos blockPos : includedBlockPositions) {
            if (visited.contains(blockPos)) continue;
            frontier.add(blockPos);
        }
        return true;
    }

    protected Pair<StructureTemplate.StructureBlockInfo, BlockEntity> capture(Level world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        if (AllBlocks.REDSTONE_CONTACT.has(blockstate)) {
            blockstate = (BlockState)blockstate.setValue((Property)RedstoneContactBlock.POWERED, (Comparable)Boolean.valueOf(true));
        }
        if (AllBlocks.POWERED_SHAFT.has(blockstate)) {
            blockstate = BlockHelper.copyProperties(blockstate, AllBlocks.SHAFT.getDefaultState());
        }
        if (blockstate.getBlock() instanceof ControlsBlock && AllTags.AllContraptionTypeTags.OPENS_CONTROLS.matches(this.getType())) {
            blockstate = (BlockState)blockstate.setValue((Property)ControlsBlock.OPEN, (Comparable)Boolean.valueOf(true));
        }
        if (blockstate.hasProperty((Property)SlidingDoorBlock.VISIBLE)) {
            blockstate = (BlockState)blockstate.setValue((Property)SlidingDoorBlock.VISIBLE, (Comparable)Boolean.valueOf(false));
        }
        if (blockstate.getBlock() instanceof ButtonBlock) {
            blockstate = (BlockState)blockstate.setValue((Property)ButtonBlock.POWERED, (Comparable)Boolean.valueOf(false));
            world.scheduleTick(pos, blockstate.getBlock(), -1);
        }
        if (blockstate.getBlock() instanceof PressurePlateBlock) {
            blockstate = (BlockState)blockstate.setValue((Property)PressurePlateBlock.POWERED, (Comparable)Boolean.valueOf(false));
            world.scheduleTick(pos, blockstate.getBlock(), -1);
        }
        CompoundTag compoundnbt = this.getBlockEntityNBT(world, pos);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof PoweredShaftBlockEntity) {
            blockEntity = AllBlockEntityTypes.BRACKETED_KINETIC.create(pos, blockstate);
        }
        if (blockEntity instanceof FactoryPanelBlockEntity) {
            FactoryPanelBlockEntity fpbe = (FactoryPanelBlockEntity)blockEntity;
            fpbe.writeSafe(compoundnbt, (HolderLookup.Provider)world.registryAccess());
        }
        return Pair.of((Object)new StructureTemplate.StructureBlockInfo(pos, blockstate, compoundnbt), (Object)blockEntity);
    }

    protected void addBlock(Level level, BlockPos pos, Pair<StructureTemplate.StructureBlockInfo, BlockEntity> pair) {
        MovingInteractionBehaviour interactionBehaviour;
        BlockState state;
        StructureTemplate.StructureBlockInfo structureBlockInfo;
        StructureTemplate.StructureBlockInfo captured = (StructureTemplate.StructureBlockInfo)pair.getKey();
        BlockPos localPos = pos.subtract((Vec3i)this.anchor);
        if (this.blocks.put(localPos, structureBlockInfo = new StructureTemplate.StructureBlockInfo(localPos, state = captured.state(), captured.nbt())) != null) {
            return;
        }
        this.bounds = this.bounds.minmax(new AABB(localPos));
        BlockEntity be = (BlockEntity)pair.getValue();
        if (be != null) {
            CompoundTag updateTag = be.getUpdateTag((HolderLookup.Provider)level.registryAccess());
            this.updateTags.put(localPos, updateTag);
        }
        this.storage.addBlock(level, state, pos, localPos, be);
        this.captureMultiblock(localPos, structureBlockInfo, be);
        if (MovementBehaviour.REGISTRY.get((StateHolder<Block, ?>)state) != null) {
            this.actors.add((MutablePair<StructureTemplate.StructureBlockInfo, MovementContext>)MutablePair.of((Object)structureBlockInfo, null));
        }
        if ((interactionBehaviour = MovingInteractionBehaviour.REGISTRY.get((StateHolder<Block, ?>)state)) != null) {
            this.interactors.put(localPos, interactionBehaviour);
        }
        if (be instanceof CreativeCrateBlockEntity && ((CreativeCrateBlockEntity)be).getBehaviour(FilteringBehaviour.TYPE).getFilter().isEmpty()) {
            this.hasUniversalCreativeCrate = true;
        }
    }

    protected void captureMultiblock(BlockPos localPos, StructureTemplate.StructureBlockInfo structureBlockInfo, BlockEntity be) {
        if (!(be instanceof IMultiBlockEntityContainer)) {
            return;
        }
        IMultiBlockEntityContainer multiBlockBE = (IMultiBlockEntityContainer)be;
        CompoundTag nbt = structureBlockInfo.nbt();
        BlockPos controllerPos = localPos;
        if (nbt.contains("Controller")) {
            controllerPos = this.toLocalPos(NBTHelper.readBlockPos((CompoundTag)nbt, (String)"Controller"));
        }
        nbt.put("Controller", NbtUtils.writeBlockPos((BlockPos)controllerPos));
        if (this.updateTags.containsKey(localPos)) {
            this.updateTags.get(localPos).put("Controller", NbtUtils.writeBlockPos((BlockPos)controllerPos));
        }
        if (multiBlockBE.isController() && multiBlockBE.getHeight() <= 1 && multiBlockBE.getWidth() <= 1) {
            nbt.put("LastKnownPos", NbtUtils.writeBlockPos((BlockPos)BlockPos.ZERO.below(0x7FFFFFFE)));
            return;
        }
        nbt.remove("LastKnownPos");
        this.capturedMultiblocks.put((Object)controllerPos, (Object)structureBlockInfo);
    }

    @Nullable
    protected CompoundTag getBlockEntityNBT(Level world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        CompoundTag nbt = blockEntity.saveWithFullMetadata((HolderLookup.Provider)world.registryAccess());
        nbt.remove("x");
        nbt.remove("y");
        nbt.remove("z");
        return nbt;
    }

    protected BlockPos toLocalPos(BlockPos globalPos) {
        return globalPos.subtract((Vec3i)this.anchor);
    }

    protected boolean movementAllowed(BlockState state, Level world, BlockPos pos) {
        return BlockMovementChecks.isMovementAllowed(state, world, pos);
    }

    protected boolean isAnchoringBlockAt(BlockPos pos) {
        return pos.equals((Object)this.anchor);
    }

    public void readNBT(Level world, CompoundTag nbt, boolean spawnData) {
        Tag blocks = nbt.get("Blocks");
        boolean usePalettedDeserialization = blocks != null && blocks.getId() == 10 && ((CompoundTag)blocks).contains("Palette");
        this.readBlocksCompound(blocks, world, usePalettedDeserialization);
        this.capturedMultiblocks.clear();
        nbt.getList("CapturedMultiblocks", 10).forEach(c -> {
            CompoundTag tag = (CompoundTag)c;
            if (!tag.contains("Controller", 10) && !tag.contains("Parts", 9)) {
                return;
            }
            BlockPos controllerPos = NBTHelper.readBlockPos((CompoundTag)tag, (String)"Controller");
            tag.getList("Parts", 10).forEach(part -> {
                CompoundTag cPart = (CompoundTag)part;
                BlockPos partPos = cPart.contains("Pos") ? NBTHelper.readBlockPos((CompoundTag)cPart, (String)"Pos") : new BlockPos(cPart.getInt("X"), cPart.getInt("Y"), cPart.getInt("Z"));
                StructureTemplate.StructureBlockInfo partInfo = this.blocks.get(partPos);
                this.capturedMultiblocks.put((Object)controllerPos, (Object)partInfo);
            });
        });
        this.storage.read(nbt, (HolderLookup.Provider)world.registryAccess(), spawnData, this);
        this.actors.clear();
        nbt.getList("Actors", 10).forEach(c -> {
            CompoundTag comp = (CompoundTag)c;
            StructureTemplate.StructureBlockInfo info = this.blocks.get(NBTHelper.readBlockPos((CompoundTag)comp, (String)"Pos"));
            if (info == null) {
                return;
            }
            MovementContext context = MovementContext.readNBT(world, info, comp, this);
            this.getActors().add((MutablePair<StructureTemplate.StructureBlockInfo, MovementContext>)MutablePair.of((Object)info, (Object)context));
        });
        this.disabledActors = NBTHelper.readItemList((ListTag)nbt.getList("DisabledActors", 10), (HolderLookup.Provider)world.registryAccess());
        for (ItemStack stack : this.disabledActors) {
            this.setActorsActive(stack, false);
        }
        this.superglue.clear();
        NBTHelper.iterateCompoundList((ListTag)nbt.getList("Superglue", 10), c -> this.superglue.add(SuperGlueEntity.readBoundingBox(c)));
        this.seats.clear();
        NBTHelper.iterateCompoundList((ListTag)nbt.getList("Seats", 10), c -> this.seats.add(c.contains("Pos") ? NBTHelper.readBlockPos((CompoundTag)c, (String)"Pos") : new BlockPos(c.getInt("X"), c.getInt("Y"), c.getInt("Z"))));
        this.seatMapping.clear();
        NBTHelper.iterateCompoundList((ListTag)nbt.getList("Passengers", 10), c -> this.seatMapping.put(NbtUtils.loadUUID((Tag)NBTHelper.getINBT((CompoundTag)c, (String)"Id")), c.getInt("Seat")));
        this.stabilizedSubContraptions.clear();
        NBTHelper.iterateCompoundList((ListTag)nbt.getList("SubContraptions", 10), c -> this.stabilizedSubContraptions.put(c.getUUID("Id"), BlockFace.fromNBT((CompoundTag)c.getCompound("Location"))));
        this.interactors.clear();
        NBTHelper.iterateCompoundList((ListTag)nbt.getList("Interactors", 10), c -> {
            BlockPos pos = NBTHelper.readBlockPos((CompoundTag)c, (String)"Pos");
            StructureTemplate.StructureBlockInfo structureBlockInfo = this.getBlocks().get(pos);
            if (structureBlockInfo == null) {
                return;
            }
            MovingInteractionBehaviour behaviour = MovingInteractionBehaviour.REGISTRY.get((StateHolder<Block, ?>)structureBlockInfo.state());
            if (behaviour != null) {
                this.interactors.put(pos, behaviour);
            }
        });
        if (nbt.contains("BoundsFront")) {
            this.bounds = NBTHelper.readAABB((ListTag)nbt.getList("BoundsFront", 5));
        }
        this.stalled = nbt.getBoolean("Stalled");
        this.hasUniversalCreativeCrate = nbt.getBoolean("BottomlessSupply");
        this.anchor = NBTHelper.readBlockPos((CompoundTag)nbt, (String)"Anchor");
    }

    public CompoundTag writeNBT(HolderLookup.Provider registries, boolean spawnPacket) {
        CompoundTag nbt = new CompoundTag();
        ResourceLocation typeId = this.getType().holder.key().location();
        nbt.putString("Type", typeId.toString());
        CompoundTag blocksNBT = this.writeBlocksCompound(spawnPacket);
        ListTag multiblocksNBT = new ListTag();
        this.capturedMultiblocks.keySet().forEach(controllerPos -> {
            CompoundTag tag = new CompoundTag();
            tag.put("Controller", NbtUtils.writeBlockPos((BlockPos)controllerPos));
            Collection multiblockParts = this.capturedMultiblocks.get(controllerPos);
            ListTag partsNBT = new ListTag();
            multiblockParts.forEach(info -> {
                CompoundTag c = new CompoundTag();
                c.put("Pos", NbtUtils.writeBlockPos((BlockPos)info.pos()));
                partsNBT.add((Object)c);
            });
            tag.put("Parts", (Tag)partsNBT);
            multiblocksNBT.add((Object)tag);
        });
        ListTag actorsNBT = new ListTag();
        for (MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> actor : this.getActors()) {
            MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get((StateHolder<Block, ?>)((StructureTemplate.StructureBlockInfo)actor.left).state());
            if (behaviour == null) continue;
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.put("Pos", NbtUtils.writeBlockPos((BlockPos)((StructureTemplate.StructureBlockInfo)actor.left).pos()));
            behaviour.writeExtraData((MovementContext)actor.right);
            ((MovementContext)actor.right).writeToNBT(compoundTag);
            actorsNBT.add((Object)compoundTag);
        }
        ListTag disabledActorsNBT = NBTHelper.writeItemList(this.disabledActors, (HolderLookup.Provider)registries);
        ListTag superglueNBT = new ListTag();
        if (!spawnPacket) {
            for (AABB aABB : this.superglue) {
                CompoundTag c = new CompoundTag();
                SuperGlueEntity.writeBoundingBox(c, aABB);
                superglueNBT.add((Object)c);
            }
        }
        this.writeStorage(nbt, registries, spawnPacket);
        ListTag interactorNBT = new ListTag();
        for (BlockPos pos2 : this.interactors.keySet()) {
            CompoundTag c = new CompoundTag();
            c.put("Pos", NbtUtils.writeBlockPos((BlockPos)pos2));
            interactorNBT.add((Object)c);
        }
        nbt.put("Seats", (Tag)NBTHelper.writeCompoundList(this.getSeats(), pos -> {
            CompoundTag c = new CompoundTag();
            c.put("Pos", NbtUtils.writeBlockPos((BlockPos)pos));
            return c;
        }));
        nbt.put("Passengers", (Tag)NBTHelper.writeCompoundList(this.getSeatMapping().entrySet(), e -> {
            CompoundTag tag = new CompoundTag();
            tag.put("Id", (Tag)NbtUtils.createUUID((UUID)((UUID)e.getKey())));
            tag.putInt("Seat", ((Integer)e.getValue()).intValue());
            return tag;
        }));
        nbt.put("SubContraptions", (Tag)NBTHelper.writeCompoundList(this.stabilizedSubContraptions.entrySet(), e -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", (UUID)e.getKey());
            tag.put("Location", (Tag)((BlockFace)e.getValue()).serializeNBT());
            return tag;
        }));
        nbt.put("Blocks", (Tag)blocksNBT);
        nbt.put("Actors", (Tag)actorsNBT);
        nbt.put("CapturedMultiblocks", (Tag)multiblocksNBT);
        nbt.put("DisabledActors", (Tag)disabledActorsNBT);
        nbt.put("Interactors", (Tag)interactorNBT);
        nbt.put("Superglue", (Tag)superglueNBT);
        nbt.put("Anchor", NbtUtils.writeBlockPos((BlockPos)this.anchor));
        nbt.putBoolean("Stalled", this.stalled);
        nbt.putBoolean("BottomlessSupply", this.hasUniversalCreativeCrate);
        if (this.bounds != null) {
            ListTag listTag = NBTHelper.writeAABB((AABB)this.bounds);
            nbt.put("BoundsFront", (Tag)listTag);
        }
        return nbt;
    }

    public void writeStorage(CompoundTag nbt, HolderLookup.Provider registries, boolean spawnPacket) {
        this.storage.write(nbt, registries, spawnPacket);
    }

    private CompoundTag writeBlocksCompound(boolean spawnPacket) {
        CompoundTag compound = new CompoundTag();
        HashMapPalette palette = new HashMapPalette((IdMap)GameData.getBlockStateIDMap(), 16, (i, s) -> {
            throw new IllegalStateException("Palette Map index exceeded maximum");
        });
        ListTag blockList = new ListTag();
        for (StructureTemplate.StructureBlockInfo block : this.blocks.values()) {
            int id = palette.idFor((Object)block.state());
            BlockPos pos = block.pos();
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", pos.asLong());
            c.putInt("State", id);
            CompoundTag updateTag = this.updateTags.get(pos);
            if (spawnPacket) {
                if (updateTag != null) {
                    c.put("Data", (Tag)updateTag);
                } else if (block.nbt() != null) {
                    c.put("Data", (Tag)block.nbt());
                    NBTHelper.putMarker((CompoundTag)c, (String)"Legacy");
                }
            } else {
                if (block.nbt() != null) {
                    c.put("Data", (Tag)block.nbt());
                }
                if (updateTag != null) {
                    c.put("UpdateTag", (Tag)updateTag);
                }
            }
            blockList.add((Object)c);
        }
        ListTag paletteNBT = new ListTag();
        for (int i2 = 0; i2 < palette.getSize(); ++i2) {
            paletteNBT.add((Object)NbtUtils.writeBlockState((BlockState)((BlockState)palette.values.byId(i2))));
        }
        compound.put("Palette", (Tag)paletteNBT);
        compound.put("BlockList", (Tag)blockList);
        return compound;
    }

    private void readBlocksCompound(Tag compound, Level world, boolean usePalettedDeserialization) {
        ListTag blockList;
        this.blocks.clear();
        this.updateTags.clear();
        this.isLegacy.clear();
        HolderLookup holderGetter = world.holderLookup(Registries.BLOCK);
        HashMapPalette palette = null;
        if (usePalettedDeserialization) {
            CompoundTag c = (CompoundTag)compound;
            palette = new HashMapPalette((IdMap)GameData.getBlockStateIDMap(), 16, (i, s) -> {
                throw new IllegalStateException("Palette Map index exceeded maximum");
            });
            ListTag list = c.getList("Palette", 10);
            palette.values.clear();
            for (int i2 = 0; i2 < list.size(); ++i2) {
                palette.values.add((Object)NbtUtils.readBlockState((HolderGetter)holderGetter, (CompoundTag)list.getCompound(i2)));
            }
            blockList = c.getList("BlockList", 10);
        } else {
            blockList = (ListTag)compound;
        }
        for (Tag tag : blockList) {
            CompoundTag c = (CompoundTag)tag;
            StructureTemplate.StructureBlockInfo info = usePalettedDeserialization ? Contraption.readStructureBlockInfo(c, (HashMapPalette<BlockState>)palette) : Contraption.legacyReadStructureBlockInfo(c, (HolderGetter<Block>)holderGetter);
            this.blocks.put(info.pos(), info);
            if (c.contains("UpdateTag", 10)) {
                CompoundTag updateTag = c.getCompound("UpdateTag");
                this.updateTags.put(info.pos(), updateTag);
            }
            this.isLegacy.put((Object)info.pos(), c.contains("Legacy"));
        }
        this.resetClientContraption();
    }

    private static StructureTemplate.StructureBlockInfo readStructureBlockInfo(CompoundTag blockListEntry, HashMapPalette<BlockState> palette) {
        return new StructureTemplate.StructureBlockInfo(BlockPos.of((long)blockListEntry.getLong("Pos")), Objects.requireNonNull((BlockState)palette.valueFor(blockListEntry.getInt("State"))), blockListEntry.contains("Data") ? blockListEntry.getCompound("Data") : null);
    }

    private static StructureTemplate.StructureBlockInfo legacyReadStructureBlockInfo(CompoundTag blockListEntry, HolderGetter<Block> holderGetter) {
        return new StructureTemplate.StructureBlockInfo(NBTHelper.readBlockPos((CompoundTag)blockListEntry, (String)"Pos"), NbtUtils.readBlockState(holderGetter, (CompoundTag)blockListEntry.getCompound("Block")), blockListEntry.contains("Data") ? blockListEntry.getCompound("Data") : null);
    }

    public void removeBlocksFromWorld(Level world, BlockPos offset) {
        this.glueToRemove.forEach(glue -> {
            this.superglue.add(glue.getBoundingBox().move(Vec3.atLowerCornerOf((Vec3i)offset.offset((Vec3i)this.anchor)).scale(-1.0)));
            glue.discard();
        });
        ArrayList<BoundingBox> minimisedGlue = new ArrayList<BoundingBox>();
        for (int i = 0; i < this.superglue.size(); ++i) {
            minimisedGlue.add(null);
        }
        for (boolean brittles : Iterate.trueAndFalse) {
            Iterator<StructureTemplate.StructureBlockInfo> iterator = this.blocks.values().iterator();
            while (iterator.hasNext()) {
                StructureTemplate.StructureBlockInfo block = iterator.next();
                if (brittles != BlockMovementChecks.isBrittle(block.state())) continue;
                for (int i = 0; i < this.superglue.size(); ++i) {
                    AABB aabb = this.superglue.get(i);
                    if (aabb == null || !aabb.contains((double)block.pos().getX() + 0.5, (double)block.pos().getY() + 0.5, (double)block.pos().getZ() + 0.5)) continue;
                    if (minimisedGlue.get(i) == null) {
                        minimisedGlue.set(i, new BoundingBox(block.pos()));
                        continue;
                    }
                    minimisedGlue.set(i, BBHelper.encapsulate((BoundingBox)((BoundingBox)minimisedGlue.get(i)), (BlockPos)block.pos()));
                }
                BlockPos add = block.pos().offset((Vec3i)this.anchor).offset((Vec3i)offset);
                if (this.customBlockRemoval((LevelAccessor)world, add, block.state())) continue;
                BlockState oldState = world.getBlockState(add);
                Block blockIn = oldState.getBlock();
                boolean blockMismatch = block.state().getBlock() != blockIn;
                if (blockMismatch &= !AllBlocks.POWERED_SHAFT.is((Object)blockIn) || !AllBlocks.SHAFT.has(block.state())) {
                    iterator.remove();
                }
                world.removeBlockEntity(add);
                int flags = 122;
                if (blockIn instanceof SimpleWaterloggedBlock && oldState.hasProperty((Property)BlockStateProperties.WATERLOGGED) && ((Boolean)oldState.getValue((Property)BlockStateProperties.WATERLOGGED)).booleanValue()) {
                    world.setBlock(add, Blocks.WATER.defaultBlockState(), flags);
                    continue;
                }
                world.setBlock(add, Blocks.AIR.defaultBlockState(), flags);
            }
        }
        this.superglue.clear();
        Object object = minimisedGlue.iterator();
        while (object.hasNext()) {
            AABB bb;
            BoundingBox box = (BoundingBox)object.next();
            if (box == null || !((bb = new AABB((double)box.minX(), (double)box.minY(), (double)box.minZ(), (double)(box.maxX() + 1), (double)(box.maxY() + 1), (double)(box.maxZ() + 1))).getSize() > 1.01)) continue;
            this.superglue.add(bb);
        }
        for (StructureTemplate.StructureBlockInfo block : this.blocks.values()) {
            BlockPos add = block.pos().offset((Vec3i)this.anchor).offset((Vec3i)offset);
            int flags = 67;
            world.sendBlockUpdated(add, block.state(), Blocks.AIR.defaultBlockState(), flags);
            ServerLevel serverWorld = (ServerLevel)world;
            PoiTypes.forState((BlockState)block.state()).ifPresent(poiType -> world.getServer().execute(() -> {
                serverWorld.getPoiManager().add(add, poiType);
                DebugPackets.sendPoiAddedPacket((ServerLevel)serverWorld, (BlockPos)add);
            }));
            world.markAndNotifyBlock(add, world.getChunkAt(add), block.state(), Blocks.AIR.defaultBlockState(), flags, 512);
            block.state().updateIndirectNeighbourShapes((LevelAccessor)world, add, flags & 0xFFFFFFFE);
        }
    }

    public void addBlocksToWorld(Level world, StructureTransform transform) {
        if (this.disassembled) {
            return;
        }
        this.disassembled = true;
        boolean shouldDropBlocks = (Boolean)AllConfigs.server().kinetics.noDropWhenContraptionReplaceBlocks.get() == false;
        this.translateMultiblockControllers(transform);
        for (boolean nonBrittles : Iterate.trueAndFalse) {
            for (StructureTemplate.StructureBlockInfo block : this.blocks.values()) {
                BlockState blockState;
                BlockState state;
                BlockPos targetPos;
                if (nonBrittles == BlockMovementChecks.isBrittle(block.state()) || this.customBlockPlacement((LevelAccessor)world, targetPos = transform.apply(block.pos()), state = transform.apply(block.state()))) continue;
                if (nonBrittles) {
                    for (Direction face : Iterate.directions) {
                        state = state.updateShape(face, world.getBlockState(targetPos.relative(face)), (LevelAccessor)world, targetPos, targetPos.relative(face));
                    }
                }
                if ((blockState = world.getBlockState(targetPos)).getDestroySpeed((BlockGetter)world, targetPos) == -1.0f || state.getCollisionShape((BlockGetter)world, targetPos).isEmpty() && !blockState.getCollisionShape((BlockGetter)world, targetPos).isEmpty()) {
                    if (targetPos.getY() == world.getMinBuildHeight()) {
                        targetPos = targetPos.above();
                    }
                    world.levelEvent(2001, targetPos, Block.getId((BlockState)state));
                    if (!shouldDropBlocks) continue;
                    Block.dropResources((BlockState)state, (LevelAccessor)world, (BlockPos)targetPos, null);
                    continue;
                }
                if (state.getBlock() instanceof SimpleWaterloggedBlock && state.hasProperty((Property)BlockStateProperties.WATERLOGGED)) {
                    FluidState FluidState2 = world.getFluidState(targetPos);
                    state = (BlockState)state.setValue((Property)BlockStateProperties.WATERLOGGED, (Comparable)Boolean.valueOf(FluidState2.getType() == Fluids.WATER));
                }
                world.destroyBlock(targetPos, shouldDropBlocks);
                if (AllBlocks.SHAFT.has(state)) {
                    state = ShaftBlock.pickCorrectShaftType(state, world, targetPos);
                }
                if (state.hasProperty((Property)SlidingDoorBlock.VISIBLE)) {
                    state = (BlockState)((BlockState)state.setValue((Property)SlidingDoorBlock.VISIBLE, (Comparable)Boolean.valueOf((Boolean)state.getValue((Property)SlidingDoorBlock.OPEN) == false))).setValue((Property)SlidingDoorBlock.POWERED, (Comparable)Boolean.valueOf(false));
                }
                if (state.is(Blocks.SCULK_SHRIEKER)) {
                    state = Blocks.SCULK_SHRIEKER.defaultBlockState();
                }
                world.setBlock(targetPos, state, 67);
                boolean verticalRotation = transform.rotationAxis == null || transform.rotationAxis.isHorizontal();
                boolean bl = verticalRotation = verticalRotation && transform.rotation != Rotation.NONE;
                if (verticalRotation && (state.getBlock() instanceof PulleyBlock.RopeBlock || state.getBlock() instanceof PulleyBlock.MagnetBlock || state.getBlock() instanceof DoorBlock)) {
                    world.destroyBlock(targetPos, shouldDropBlocks);
                }
                BlockEntity blockEntity = world.getBlockEntity(targetPos);
                CompoundTag tag = block.nbt();
                if (state.is(Blocks.SCULK_SENSOR) || state.is(Blocks.SCULK_SHRIEKER)) {
                    tag = null;
                }
                if (blockEntity != null) {
                    tag = NBTProcessors.process((BlockState)state, (BlockEntity)blockEntity, (CompoundTag)tag, (boolean)false);
                }
                if (blockEntity != null && tag != null) {
                    tag.putInt("x", targetPos.getX());
                    tag.putInt("y", targetPos.getY());
                    tag.putInt("z", targetPos.getZ());
                    if (verticalRotation && blockEntity instanceof PulleyBlockEntity) {
                        tag.remove("Offset");
                        tag.remove("InitialOffset");
                    }
                    if (blockEntity instanceof IMultiBlockEntityContainer && (tag.contains("LastKnownPos") || this.capturedMultiblocks.isEmpty())) {
                        tag.put("LastKnownPos", NbtUtils.writeBlockPos((BlockPos)BlockPos.ZERO.below(0x7FFFFFFE)));
                        tag.remove("Controller");
                    }
                    blockEntity.loadWithComponents(tag, (HolderLookup.Provider)world.registryAccess());
                }
                this.storage.unmount(world, block, targetPos, blockEntity);
                if (blockEntity == null) continue;
                transform.apply(blockEntity);
            }
        }
        Object object = this.blocks.values().iterator();
        while (object.hasNext()) {
            StructureTemplate.StructureBlockInfo block = (StructureTemplate.StructureBlockInfo)object.next();
            if (!this.shouldUpdateAfterMovement(block)) continue;
            BlockPos targetPos = transform.apply(block.pos());
            world.markAndNotifyBlock(targetPos, world.getChunkAt(targetPos), block.state(), block.state(), 67, 512);
        }
        for (AABB box : this.superglue) {
            box = new AABB(transform.apply(new Vec3(box.minX, box.minY, box.minZ)), transform.apply(new Vec3(box.maxX, box.maxY, box.maxZ)));
            if (world.isClientSide) continue;
            world.addFreshEntity((Entity)new SuperGlueEntity(world, box));
        }
    }

    protected void translateMultiblockControllers(StructureTransform transform) {
        if (transform.rotationAxis != null && transform.rotationAxis != Direction.Axis.Y && transform.rotation != Rotation.NONE) {
            this.capturedMultiblocks.values().forEach(info -> info.nbt().put("LastKnownPos", NbtUtils.writeBlockPos((BlockPos)BlockPos.ZERO.below(0x7FFFFFFE))));
            return;
        }
        this.capturedMultiblocks.keySet().forEach(controllerPos -> {
            Collection multiblockParts = this.capturedMultiblocks.get(controllerPos);
            Optional optionalBoundingBox = BoundingBox.encapsulatingPositions(multiblockParts.stream().map(info -> transform.apply(info.pos())).toList());
            if (optionalBoundingBox.isEmpty()) {
                return;
            }
            BoundingBox boundingBox = (BoundingBox)optionalBoundingBox.get();
            BlockPos newControllerPos = new BlockPos(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ());
            BlockPos otherPos = transform.unapply(newControllerPos);
            multiblockParts.forEach(info -> info.nbt().put("Controller", NbtUtils.writeBlockPos((BlockPos)newControllerPos)));
            if (controllerPos.equals((Object)otherPos)) {
                return;
            }
            StructureTemplate.StructureBlockInfo prevControllerInfo = this.blocks.get(controllerPos);
            StructureTemplate.StructureBlockInfo newControllerInfo = this.blocks.get(otherPos);
            if (prevControllerInfo == null || newControllerInfo == null) {
                return;
            }
            this.blocks.put(otherPos, new StructureTemplate.StructureBlockInfo(newControllerInfo.pos(), newControllerInfo.state(), prevControllerInfo.nbt()));
            this.blocks.put((BlockPos)controllerPos, new StructureTemplate.StructureBlockInfo(prevControllerInfo.pos(), prevControllerInfo.state(), newControllerInfo.nbt()));
        });
    }

    public void addPassengersToWorld(Level world, StructureTransform transform, List<Entity> seatedEntities) {
        for (Entity seatedEntity : seatedEntities) {
            Integer seatIndex;
            if (this.getSeatMapping().isEmpty() || (seatIndex = this.getSeatMapping().get(seatedEntity.getUUID())) == null) continue;
            BlockPos seatPos = this.getSeats().get(seatIndex);
            if (!(world.getBlockState(seatPos = transform.apply(seatPos)).getBlock() instanceof SeatBlock) || SeatBlock.isSeatOccupied(world, seatPos)) continue;
            SeatBlock.sitDown(world, seatPos, seatedEntity);
        }
    }

    public void startMoving(Level world) {
        this.disabledActors.clear();
        for (MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> pair : this.actors) {
            MovementContext context = new MovementContext(world, (StructureTemplate.StructureBlockInfo)pair.left, this);
            MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get((StateHolder<Block, ?>)((StructureTemplate.StructureBlockInfo)pair.left).state());
            if (behaviour != null) {
                behaviour.startMoving(context);
            }
            pair.setRight((Object)context);
            if (!(behaviour instanceof ContraptionControlsMovement)) continue;
            this.disableActorOnStart(context);
        }
        for (ItemStack stack : this.disabledActors) {
            this.setActorsActive(stack, false);
        }
    }

    protected void disableActorOnStart(MovementContext context) {
        if (!ContraptionControlsMovement.isDisabledInitially(context)) {
            return;
        }
        ItemStack filter = ContraptionControlsMovement.getFilter(context);
        if (filter == null) {
            return;
        }
        if (this.isActorTypeDisabled(filter)) {
            return;
        }
        this.disabledActors.add(filter);
    }

    public boolean isActorTypeDisabled(ItemStack filter) {
        return this.disabledActors.stream().anyMatch(i -> ContraptionControlsMovement.isSameFilter(i, filter));
    }

    public void setActorsActive(ItemStack referenceStack, boolean enable) {
        for (MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> pair : this.actors) {
            ItemStack behaviourStack;
            MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get((StateHolder<Block, ?>)((StructureTemplate.StructureBlockInfo)pair.left).state());
            if (behaviour == null || (behaviourStack = behaviour.canBeDisabledVia((MovementContext)pair.right)) == null || !referenceStack.isEmpty() && !ContraptionControlsMovement.isSameFilter(referenceStack, behaviourStack)) continue;
            boolean bl = ((MovementContext)pair.right).disabled = !enable;
            if (enable) continue;
            behaviour.onDisabledByControls((MovementContext)pair.right);
        }
    }

    public List<ItemStack> getDisabledActors() {
        return this.disabledActors;
    }

    public void stop(Level world) {
        this.forEachActor(world, (behaviour, ctx) -> {
            behaviour.stopMoving((MovementContext)ctx);
            ctx.position = null;
            ctx.motion = Vec3.ZERO;
            ctx.relativeMotion = Vec3.ZERO;
            ctx.rotation = v -> v;
        });
    }

    public void forEachActor(Level world, BiConsumer<MovementBehaviour, MovementContext> callBack) {
        for (MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> pair : this.actors) {
            MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get((StateHolder<Block, ?>)((StructureTemplate.StructureBlockInfo)pair.getLeft()).state());
            if (behaviour == null) continue;
            callBack.accept(behaviour, (MovementContext)pair.getRight());
        }
    }

    protected boolean shouldUpdateAfterMovement(StructureTemplate.StructureBlockInfo info) {
        if (PoiTypes.forState((BlockState)info.state()).isPresent()) {
            return false;
        }
        return !(info.state().getBlock() instanceof SlidingDoorBlock);
    }

    public void expandBoundsAroundAxis(Direction.Axis axis) {
        Set<BlockPos> blocks = this.getBlocks().keySet();
        int radius = (int)Math.ceil(Contraption.getRadius(blocks, axis));
        int maxX = radius + 2;
        int maxY = radius + 2;
        int maxZ = radius + 2;
        int minX = -radius - 1;
        int minY = -radius - 1;
        int minZ = -radius - 1;
        if (axis == Direction.Axis.X) {
            maxX = (int)this.bounds.maxX;
            minX = (int)this.bounds.minX;
        } else if (axis == Direction.Axis.Y) {
            maxY = (int)this.bounds.maxY;
            minY = (int)this.bounds.minY;
        } else if (axis == Direction.Axis.Z) {
            maxZ = (int)this.bounds.maxZ;
            minZ = (int)this.bounds.minZ;
        }
        this.bounds = new AABB((double)minX, (double)minY, (double)minZ, (double)maxX, (double)maxY, (double)maxZ);
    }

    public Map<UUID, Integer> getSeatMapping() {
        return this.seatMapping;
    }

    public BlockPos getSeatOf(UUID entityId) {
        if (!this.getSeatMapping().containsKey(entityId)) {
            return null;
        }
        int seatIndex = this.getSeatMapping().get(entityId);
        if (seatIndex >= this.getSeats().size()) {
            return null;
        }
        return this.getSeats().get(seatIndex);
    }

    public BlockPos getBearingPosOf(UUID subContraptionEntityId) {
        if (this.stabilizedSubContraptions.containsKey(subContraptionEntityId)) {
            return this.stabilizedSubContraptions.get(subContraptionEntityId).getConnectedPos();
        }
        return null;
    }

    public void setSeatMapping(Map<UUID, Integer> seatMapping) {
        this.seatMapping = seatMapping;
    }

    public List<BlockPos> getSeats() {
        return this.seats;
    }

    public Map<BlockPos, StructureTemplate.StructureBlockInfo> getBlocks() {
        return this.blocks;
    }

    public Object2BooleanMap<BlockPos> getIsLegacy() {
        return this.isLegacy;
    }

    public List<MutablePair<StructureTemplate.StructureBlockInfo, MovementContext>> getActors() {
        return this.actors;
    }

    @Nullable
    public MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> getActorAt(BlockPos localPos) {
        for (MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> pair : this.actors) {
            if (!localPos.equals((Object)((StructureTemplate.StructureBlockInfo)pair.left).pos())) continue;
            return pair;
        }
        return null;
    }

    public Map<BlockPos, MovingInteractionBehaviour> getInteractors() {
        return this.interactors;
    }

    public void invalidateColliders() {
        this.simplifiedEntityColliders = Optional.empty();
        this.gatherBBsOffThread();
    }

    private void gatherBBsOffThread() {
        this.getContraptionWorld();
        if (this.simplifiedEntityColliderProvider != null) {
            this.simplifiedEntityColliderProvider.cancel(false);
        }
        this.simplifiedEntityColliderProvider = CompletableFuture.supplyAsync(() -> {
            VoxelShape combinedShape = Shapes.empty();
            for (Map.Entry<BlockPos, StructureTemplate.StructureBlockInfo> entry : this.blocks.entrySet()) {
                StructureTemplate.StructureBlockInfo info = entry.getValue();
                BlockPos localPos = entry.getKey();
                VoxelShape collisionShape = info.state().getCollisionShape((BlockGetter)this.collisionLevel, localPos, CollisionContext.empty());
                if (collisionShape.isEmpty()) continue;
                combinedShape = Shapes.joinUnoptimized((VoxelShape)combinedShape, (VoxelShape)collisionShape.move((double)localPos.getX(), (double)localPos.getY(), (double)localPos.getZ()), (BooleanOp)BooleanOp.OR);
            }
            return combinedShape.optimize().toAabbs();
        }).thenAccept(r -> {
            this.simplifiedEntityColliders = Optional.of(r);
        });
    }

    public static double getRadius(Iterable<? extends Vec3i> blocks, Direction.Axis axis) {
        Direction.Axis axisA;
        Direction.Axis axisB = switch (axis) {
            case Direction.Axis.X -> {
                axisA = Direction.Axis.Y;
                yield Direction.Axis.Z;
            }
            case Direction.Axis.Y -> {
                axisA = Direction.Axis.X;
                yield Direction.Axis.Z;
            }
            case Direction.Axis.Z -> {
                axisA = Direction.Axis.X;
                yield Direction.Axis.Y;
            }
            default -> throw new IllegalStateException("Unexpected value: " + String.valueOf(axis));
        };
        int maxDistSq = 0;
        for (Vec3i vec3i : blocks) {
            int b;
            int a = vec3i.get(axisA);
            int distSq = a * a + (b = vec3i.get(axisB)) * b;
            if (distSq <= maxDistSq) continue;
            maxDistSq = distSq;
        }
        return Math.sqrt(maxDistSq);
    }

    public MountedStorageManager getStorage() {
        return this.storage;
    }

    public boolean isHiddenInPortal(BlockPos localPos) {
        return false;
    }

    public Optional<List<AABB>> getSimplifiedEntityColliders() {
        return this.simplifiedEntityColliders;
    }

    public void tickStorage(AbstractContraptionEntity entity) {
        this.getStorage().tick(entity);
    }

    public boolean containsBlockBreakers() {
        for (MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> pair : this.actors) {
            MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get((StateHolder<Block, ?>)((StructureTemplate.StructureBlockInfo)pair.getLeft()).state());
            if (!(behaviour instanceof BlockBreakingMovementBehaviour) && !(behaviour instanceof HarvesterMovementBehaviour)) continue;
            return true;
        }
        return false;
    }

    public final ClientContraption getOrCreateClientContraptionLazy() {
        ClientContraption out = this.clientContraption.getAcquire();
        if (out == null) {
            this.clientContraption.compareAndExchangeRelease(null, this.createClientContraption());
            out = this.clientContraption.getAcquire();
        }
        return out;
    }

    @Contract(value=" -> new")
    protected ClientContraption createClientContraption() {
        return new ClientContraption(this);
    }

    public void resetClientContraption() {
        ClientContraption maybeNullClientContraption = this.clientContraption.getAcquire();
        if (maybeNullClientContraption != null) {
            maybeNullClientContraption.resetRenderLevel();
        }
    }

    public void invalidateClientContraptionStructure() {
        ClientContraption maybeNullClientContraption = this.clientContraption.getAcquire();
        if (maybeNullClientContraption != null) {
            maybeNullClientContraption.invalidateStructure();
        }
    }

    public void invalidateClientContraptionChildren() {
        ClientContraption maybeNullClientContraption = this.clientContraption.getAcquire();
        if (maybeNullClientContraption != null) {
            maybeNullClientContraption.invalidateChildren();
        }
    }

    @Nullable
    public BlockEntity getBlockEntityClientSide(BlockPos localPos) {
        ClientContraption maybeNullClientContraption = this.clientContraption.getAcquire();
        if (maybeNullClientContraption == null) {
            return null;
        }
        return maybeNullClientContraption.getBlockEntity(localPos);
    }
}
