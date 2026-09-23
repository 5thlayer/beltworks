package rearth.belts.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.blocks.BeltTileBlock;
import rearth.belts.blocks.SplitterBlock;
import rearth.belts.collision.BeltCollisionRegistry;
import rearth.belts.model.BeltPath;
import rearth.belts.model.TransportLine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Places both halves of a splitter, the clicked one on the left looking the way items flow, or
 * neither. A half placed across a running belt cuts it in two (PlanetaryFactory #361). The click
 * executes the plan the preview draws (ADR-0069).
 */
public class SplitterItem extends BlockItem {

    public SplitterItem(Block block, Properties settings) {
        super(block, settings);
    }

    /** The two halves this context would place, left first. */
    public List<Half> halves(BlockPlaceContext context) {
        var facing = context.getHorizontalDirection();
        var left = getBlock().defaultBlockState().setValue(SplitterBlock.FACING, facing)
                     .setValue(SplitterBlock.SIDE, SplitterBlock.Side.LEFT);
        var leftPos = context.getClickedPos();
        return List.of(new Half(leftPos, left),
          new Half(SplitterBlock.partner(leftPos, left), left.setValue(SplitterBlock.SIDE, SplitterBlock.Side.RIGHT)));
    }

    /** Whether every half can go down: in the world, where the player may build, on a replaceable block, clear of entities. */
    public boolean fits(BlockPlaceContext context, List<Half> halves) {
        var level = context.getLevel();
        var player = context.getPlayer();
        for (var half : halves) {
            if (!level.isInWorldBounds(half.pos()) || player != null && !level.mayInteract(player, half.pos())
                  || !level.getBlockState(half.pos()).canBeReplaced() && !replacesTile(level.getBlockState(half.pos()), half.state().getValue(SplitterBlock.FACING))
                  || !level.isUnobstructed(half.state(), half.pos(), CollisionContext.empty())) return false;
        }
        return true;
    }

    // A tile the splitter does not face along is not replaced, so a click on its top lands above
    // it; a splitter sitting on a belt is no placement (PlanetaryFactory #394).
    private static boolean onTopOfATile(BlockPlaceContext context) {
        return !context.replacingClickedOnBlock() && context.getClickedFace() == Direction.UP
                 && context.getLevel().getBlockState(context.getClickedPos().below()).getBlock() instanceof BeltTileBlock;
    }

    /** A straight tile running the way the half faces, which the half takes the place of (PlanetaryFactory #394). */
    public static boolean replacesTile(BlockState present, Direction facing) {
        return present.getBlock() instanceof BeltTileBlock
                 && present.getValue(BeltTileBlock.CORNER) == BeltTileBlock.Shape.STRAIGHT
                 && present.getValue(BeltTileBlock.FACING) == facing;
    }

    /**
     * What a click in this context would do, or null where it would do nothing: both halves, and
     * each belt a half cuts, or the reason it places neither.
     */
    public @Nullable Plan plan(BlockPlaceContext context) {
        if (!context.canPlace()) return null;
        var halves = halves(context);
        if (!fits(context, halves) || onTopOfATile(context)) return new Plan(halves, List.of(), true, null);

        var level = context.getLevel();
        var cuts = new ArrayList<Cut>();
        var sources = new HashSet<BlockPos>();
        for (var half : halves) {
            var facing = half.state().getValue(SplitterBlock.FACING);
            for (var source : BeltCollisionRegistry.beltSourcesAt(level, half.pos())) {
                var path = level.getBlockEntity(source, BlockEntitiesContent.CHUTE_BLOCK.get()).map(chute -> chute.path()).orElse(null);
                if (path == null) continue;
                var crossing = path.crossing(half.pos().getX(), half.pos().getY(), half.pos().getZ(), facing.getStepX(), facing.getStepZ());
                if (crossing.isEmpty()) continue;
                if (crossing.get() instanceof BeltPath.Crossing.Refused refused) return new Plan(halves, List.of(), false, refused.reason());
                // One belt through both halves crosses the splitter rather than running into it.
                if (!sources.add(source)) return new Plan(halves, List.of(), false, BeltPath.CrossingRefusal.NOT_CUT);
                cuts.add(new Cut(half.pos(), source, (BeltPath.Crossing.Cut) crossing.get()));
            }
        }
        return new Plan(halves, cuts, false, null);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        var plan = plan(context);
        if (plan == null) return InteractionResult.FAIL;
        var level = context.getLevel();
        var player = context.getPlayer();
        if (plan.refused()) {
            if (plan.crossing() != null && player != null && !level.isClientSide()) {
                player.sendSystemMessage(Component.translatable(plan.crossing().messageKey()));
            }
            return InteractionResult.FAIL;
        }

        var carried = new ArrayList<List<TransportLine.Share<ItemStack>>>();
        for (var half : plan.halves()) {
            var tile = level.getBlockEntity(half.pos(), BlockEntitiesContent.BELT_TILE.get()).orElse(null);
            carried.add(tile == null ? List.of() : tile.takeCarried());
            if (tile != null && !level.isClientSide() && player != null && !player.hasInfiniteMaterials()) {
                player.getInventory().placeItemBackInInventory(new ItemStack(tile.getBlockState().getBlock().asItem()));
            }
            level.setBlock(half.pos(), half.state(), Block.UPDATE_ALL);
        }
        if (!level.isClientSide()) {
            for (var at = 0; at < plan.halves().size(); at++) {
                var shares = carried.get(at);
                if (shares.isEmpty()) continue;
                level.getBlockEntity(plan.halves().get(at).pos(), BlockEntitiesContent.CHUTE_BLOCK.get())
                  .ifPresent(half -> half.carry(shares, player));
            }
        }
        if (!level.isClientSide()) {
            for (var cut : plan.cuts()) {
                var half = level.getBlockEntity(cut.half(), BlockEntitiesContent.CHUTE_BLOCK.get());
                level.getBlockEntity(cut.source(), BlockEntitiesContent.CHUTE_BLOCK.get())
                  .ifPresent(source -> half.ifPresent(splitterHalf -> source.cut(splitterHalf, cut.crossing(), player)));
            }
        }

        var left = plan.halves().getFirst();
        var sound = left.state().getSoundType();
        level.playSound(player, left.pos(), sound.getPlaceSound(), SoundSource.BLOCKS,
          (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, left.pos(), GameEvent.Context.of(player, left.state()));
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }

    /**
     * @param blocked  whether a half's block is not free, so neither goes down
     * @param crossing why a belt through a half is not cut, so neither goes down
     */
    public record Plan(List<Half> halves, List<Cut> cuts, boolean blocked, BeltPath.@Nullable CrossingRefusal crossing) {

        public boolean refused() {
            return blocked || crossing != null;
        }
    }

    /** A belt, named by the block it starts at, that the half at {@code half} cuts. */
    public record Cut(BlockPos half, BlockPos source, BeltPath.Crossing.Cut crossing) {
    }

    public record Half(BlockPos pos, BlockState state) {
    }
}
