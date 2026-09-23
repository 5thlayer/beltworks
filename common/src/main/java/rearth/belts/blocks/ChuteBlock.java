package rearth.belts.blocks;

import com.mojang.serialization.MapCodec;
import dev.architectury.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.model.BeltTier;
import rearth.belts.util.MathHelpers;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class ChuteBlock extends HorizontalDirectionalBlock implements EntityBlock {
    
    public static final EnumProperty<Floor> FLOOR = EnumProperty.create("floor", Floor.class);

    private static final Map<Direction, VoxelShape> SHAPES = new HashMap<>();
    
    private VoxelShape createShapeForDirection(Direction direction) {
        return Shapes.or(
          MathHelpers.rotateVoxelShape(Shapes.box(2 / 16f, 4 / 16f, 14 / 16f, 14 / 16f, 1f, 1f), direction, AttachFace.FLOOR),
          MathHelpers.rotateVoxelShape(Shapes.box(3 / 16f, 5 / 16f, 16 / 16f, 13 / 16f, 15 / 16f, 18 / 16f), direction, AttachFace.FLOOR)
        ).optimize();
    }
    
    private final BeltTier tier;

    public ChuteBlock(BlockBehaviour.Properties settings, BeltTier tier) {
        super(settings);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                               .setValue(FLOOR, Floor.NONE));
    }
    
    /** The tier whose items a second this loader moves, whatever belt it is on (ADR-0076). */
    public BeltTier tier() {
        return tier;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        
        var candidate = world.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (candidate.isPresent()) {
            var entity = candidate.get();
            // An empty hand is a reset, and only the main hand's reaches useWithoutItem; an empty
            // off hand tried after the main hand set a filter must not undo it.
            if (!setsFilter(entity, stack)) return super.useItemOn(stack, state, world, pos, player, hand, hit);
            if (!world.isClientSide())
                entity.assignFilterItem(stack, player);
            return InteractionResult.SUCCESS;
        }
        
        return super.useItemOn(stack, state, world, pos, player, hand, hit);
    }
    
    /** Whether a click holding this stack sets the filter, answered before the held item is asked (PlanetaryFactory #372). */
    public boolean setsFilter(ChuteBlockEntity entity, ItemStack stack) {
        return entity.isUsed() && !stack.isEmpty();
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        
        var candidate = world.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (candidate.isPresent()) {
            var entity = candidate.get();
            if (!entity.isUsed()) return super.useWithoutItem(state, world, pos, player, hit);
            if (!world.isClientSide())
                entity.resetFilterItem(player);
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, world, pos, player, hit);
    }
    
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        var dir = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return SHAPES.computeIfAbsent(dir, this::createShapeForDirection);
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, FLOOR);
    }
    
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        
        var targetFacing = ctx.getClickedFace();
        if (targetFacing.getAxis().isVertical())
            targetFacing = ctx.getHorizontalDirection().getOpposite();
        
        var ahead = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(targetFacing));
        return Objects.requireNonNull(super.getStateForPlacement(ctx))
                 .setValue(BlockStateProperties.HORIZONTAL_FACING, targetFacing)
                 .setValue(FLOOR, floorFor(targetFacing, ahead));
    }
    
    @Override
    protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (direction != facing) return state;
        return state.setValue(FLOOR, floorFor(facing, neighborState));
    }
    
    public static Floor floorFor(Direction facing, BlockState ahead) {
        if (!(ahead.getBlock() instanceof BeltTileBlock)) return Floor.NONE;
        var runs = ahead.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (runs == facing) return Floor.LOADING;
        if (runs == facing.getOpposite()) return Floor.UNLOADING;
        return Floor.NONE;
    }
    
    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(settings -> new ChuteBlock(settings, tier));
    }
    
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChuteBlockEntity(pos, state);
    }
    
    @Override
    protected boolean triggerEvent(BlockState state, Level world, BlockPos pos, int type, int data) {
        super.triggerEvent(state, world, pos, type, data);
        var blockEntity = world.getBlockEntity(pos);
        return blockEntity != null && blockEntity.triggerEvent(type, data);
    }
    
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return ((world1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof ChuteBlockEntity chuteBlockEntity)
                chuteBlockEntity.tick(world1, pos, state1, chuteBlockEntity);
        });
    }
    
    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        
        if (world.isClientSide()) return super.playerWillDestroy(world, pos, state, player);
        
        world.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get()).ifPresent(chute -> chute.releaseBelts(player));
        
        return super.playerWillDestroy(world, pos, state, player);
    }

    /**
     * The belt floor a loader draws across its own block to meet the tile in front of it, scrolling
     * the way items move (PlanetaryFactory #399).
     */
    public enum Floor implements StringRepresentable {
        NONE, LOADING, UNLOADING;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
