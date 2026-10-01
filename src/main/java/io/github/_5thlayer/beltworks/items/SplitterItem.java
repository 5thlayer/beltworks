// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package io.github._5thlayer.beltworks.items;

import io.github._5thlayer.groundworks.PlacementPlan;
import io.github._5thlayer.groundworks.PlansPlacement;
import io.github._5thlayer.groundworks.Refusal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import io.github._5thlayer.beltworks.BlockEntitiesContent;
import io.github._5thlayer.beltworks.blocks.BeltTileBlock;
import io.github._5thlayer.beltworks.blocks.SplitterBlock;
import io.github._5thlayer.beltworks.model.TransportLine;

import java.util.ArrayList;
import java.util.List;

/**
 * Places both halves of a splitter, the clicked one on the left looking the way items flow, or
 * neither. The click executes the plan the preview draws (ADR 0006).
 */
public class SplitterItem extends BlockItem implements PlansPlacement {

    /** Groundworks' own words for a Fast Replace refused for room, which a splitter over a tile line keeps to. */
    public static final String NO_ROOM_TO_RETURN = "message.groundworks.fast_replace_no_room_to_return";

    public SplitterItem(Block block, Properties settings) {
        super(block, settings);
    }

    /** The two halves this context would place, left first. */
    public List<Half> halves(BlockPlaceContext context) {
        var facing = context.getHorizontalDirection();
        var left = getBlock().defaultBlockState().setValue(SplitterBlock.FACING, facing)
                     .setValue(SplitterBlock.SIDE, SplitterBlock.Side.LEFT);
        // On the tile it was aimed at, so the preview shows it refused there rather than on the belt.
        var leftPos = onTopOfATile(context) ? context.getClickedPos().below() : context.getClickedPos();
        return List.of(new Half(leftPos, left),
          new Half(SplitterBlock.partner(leftPos, left), left.setValue(SplitterBlock.SIDE, SplitterBlock.Side.RIGHT)));
    }

    /** Whether every half can go down: in the world, where the player may build, on a replaceable block or a tile it replaces, clear of entities. */
    public boolean fits(BlockPlaceContext context, List<Half> halves) {
        var level = context.getLevel();
        var player = context.getPlayer();
        var replaced = replaced(context, halves);
        for (var half : halves) {
            if (!level.isInWorldBounds(half.pos()) || player != null && !level.mayInteract(player, half.pos())
                  || !level.getBlockState(half.pos()).canBeReplaced() && !replaced.contains(half.pos())
                  || !level.isUnobstructed(half.state(), half.pos(), CollisionContext.empty())) return false;
        }
        return true;
    }

    /**
     * The tiles the halves take the places of, as a Fast Replace takes a block's: straight level
     * tiles running the way the splitter faces, and none on a sneak-click, which places beside
     * (#94).
     */
    public static List<BlockPos> replaced(BlockPlaceContext context, List<Half> halves) {
        if (sneaking(context)) return List.of();
        var level = context.getLevel();
        return halves.stream().filter(half -> replacesTile(level.getBlockState(half.pos()), half.state().getValue(SplitterBlock.FACING)))
                 .map(Half::pos).toList();
    }

    /** Whether the click is a sneak-click, which places beside a tile rather than in its place, as a Fast Replace's does. */
    public static boolean sneaking(BlockPlaceContext context) {
        return context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
    }

    // A tile the splitter does not face along is not replaced, so a click on its top lands above it
    // (PlanetaryFactory #394). A sneak-click lands above any tile.
    private static boolean onTopOfATile(BlockPlaceContext context) {
        return !context.replacingClickedOnBlock() && context.getClickedFace() == Direction.UP && !sneaking(context)
                 && context.getLevel().getBlockState(context.getClickedPos().below()).getBlock() instanceof BeltTileBlock;
    }

    // One of the tile's own item for each tile it replaces, which a player with infinite materials is not handed.
    private static List<ItemStack> handedBack(BlockPlaceContext context, List<BlockPos> replaced) {
        return replaced.stream().map(pos -> new ItemStack(context.getLevel().getBlockState(pos).getBlock().asItem())).toList();
    }

    /** A straight tile running the way the half faces, which the half takes the place of (PlanetaryFactory #394). */
    public static boolean replacesTile(BlockState present, Direction facing) {
        return present.getBlock() instanceof BeltTileBlock
                 && present.getValue(BeltTileBlock.CORNER) == BeltTileBlock.Shape.STRAIGHT
                 && present.getValue(BeltTileBlock.PITCH) == BeltTileBlock.PitchState.LEVEL
                 && present.getValue(BeltTileBlock.FACING) == facing;
    }

    /** What a click in this context would do, or null where it would do nothing. */
    public @Nullable Plan splitterPlan(BlockPlaceContext context) {
        if (!context.canPlace()) return null;
        var halves = halves(context);
        var replaced = replaced(context, halves);
        // Refused before anything changes when what it hands back would not all fit.
        Refusal refusal = !fits(context, halves) ? BeltRefusal.BLOCKED
          : !HandBack.fits(context.getPlayer(), context.getHand(), handedBack(context, replaced)) ? Refusal.FastReplace.NO_ROOM_TO_RETURN : null;
        return new Plan(halves, replaced, refusal);
    }

    /** What the Groundworks library draws for a click here: {@link #splitterPlan}'s halves, refused whole (ADR 0010). */
    @Override
    public @Nullable PlacementPlan plan(BlockPlaceContext context) {
        var plan = splitterPlan(context);
        if (plan == null) return null;
        var blocks = plan.halves().stream().map(half -> new PlacementPlan.Placed(half.pos(), half.state())).toList();
        return new PlacementPlan(blocks, plan.replaced(), plan.refusal());
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        var plan = splitterPlan(context);
        if (plan == null) return InteractionResult.FAIL;
        var level = context.getLevel();
        var player = context.getPlayer();
        if (plan.refused()) {
            if (!level.isClientSide() && player instanceof ServerPlayer server) {
                var key = plan.refusal() instanceof BeltRefusal belt ? belt.messageKey() : NO_ROOM_TO_RETURN;
                server.sendSystemMessage(Component.translatable(key), true);
            }
            return InteractionResult.FAIL;
        }

        var back = handedBack(context, plan.replaced());
        var carried = new ArrayList<List<TransportLine.Share<ItemStack>>>();
        for (var half : plan.halves()) {
            var tile = plan.replaced().contains(half.pos()) ? level.getBlockEntity(half.pos(), BlockEntitiesContent.BELT_TILE.get()).orElse(null) : null;
            carried.add(tile == null ? List.of() : tile.takeCarried());
            level.setBlock(half.pos(), half.state(), Block.UPDATE_ALL);
        }
        if (!level.isClientSide()) {
            for (var at = 0; at < plan.halves().size(); at++) {
                var shares = carried.get(at);
                if (shares.isEmpty()) continue;
                level.getBlockEntity(plan.halves().get(at).pos(), BlockEntitiesContent.BELT_END.get())
                  .ifPresent(half -> half.carry(shares, player));
            }
        }
        var left = plan.halves().getFirst();
        var sound = left.state().getSoundType();
        level.playSound(player, left.pos(), sound.getPlaceSound(), SoundSource.BLOCKS,
          (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, left.pos(), GameEvent.Context.of(player, left.state()));
        context.getItemInHand().consume(1, player);
        if (!level.isClientSide()) HandBack.give(player, context.getHand(), back);
        return InteractionResult.SUCCESS;
    }

    /**
     * @param replaced the tiles the halves take the places of
     * @param refusal why neither half goes down: a half's block is not free, or what it hands back would not fit
     */
    public record Plan(List<Half> halves, List<BlockPos> replaced, @Nullable Refusal refusal) {

        public boolean refused() {
            return refusal != null;
        }
    }

    public record Half(BlockPos pos, BlockState state) {
    }
}
