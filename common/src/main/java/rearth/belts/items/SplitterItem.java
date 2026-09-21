package rearth.belts.items;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import rearth.belts.blocks.SplitterBlock;

import java.util.List;

/** Places both halves of a splitter, the clicked one on the left looking the way items flow, or neither. */
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
                  || !level.getBlockState(half.pos()).canBeReplaced()
                  || !level.isUnobstructed(half.state(), half.pos(), CollisionContext.empty())) return false;
        }
        return true;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!context.canPlace()) return InteractionResult.FAIL;
        var halves = halves(context);
        if (!fits(context, halves)) return InteractionResult.FAIL;

        var level = context.getLevel();
        for (var half : halves) level.setBlock(half.pos(), half.state(), Block.UPDATE_ALL);

        var player = context.getPlayer();
        var left = halves.getFirst();
        var sound = left.state().getSoundType();
        level.playSound(player, left.pos(), sound.getPlaceSound(), SoundSource.BLOCKS,
          (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, left.pos(), GameEvent.Context.of(player, left.state()));
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }

    public record Half(BlockPos pos, BlockState state) {
    }
}
