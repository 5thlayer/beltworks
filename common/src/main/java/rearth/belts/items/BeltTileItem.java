package rearth.belts.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.ComponentContent;
import rearth.belts.blocks.BeltTileBlock;
import rearth.belts.model.LineScan;
import rearth.belts.model.Stretch;
import rearth.belts.model.TileShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The tile item (PlanetaryFactory #393). A plain click places one tile facing the look; a
 * sneak-click stores a start and the look, and the next plain click lays a {@link Stretch} to the
 * aimed spot. A sneak-use in the air forgets the start.
 */
public class BeltTileItem extends TooltipBlockItem {

    public BeltTileItem(Block block, Properties settings) {
        super(block, settings);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(ComponentContent.BELT_START.get())) {
            if (!level.isClientSide()) {
                clearStart(stack);
                tell(player, Component.translatable("message.belts.stretch_cleared"));
            }
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        var stack = context.getItemInHand();
        if (player != null && player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                stack.set(ComponentContent.BELT_START.get(), aimedTile(context));
                stack.set(ComponentContent.BELT_DIR.get(), context.getHorizontalDirection());
                tell(player, Component.translatable("message.belts.stretch_started"));
            }
            return InteractionResult.SUCCESS;
        }

        var plan = stretch(context);
        if (plan == null) return super.place(context);
        if (level.isClientSide()) return plan.refused() ? InteractionResult.FAIL : InteractionResult.SUCCESS;
        if (plan.refused()) {
            if (player != null) tell(player, plan.refusal().message());
            return InteractionResult.FAIL;
        }
        execute(plan, level, stack, player);
        return InteractionResult.SUCCESS;
    }

    /**
     * What a plain click with a stored start lays, or null when this click is not one: sneaking, or
     * no start stored.
     */
    public @Nullable StretchPlan stretch(BlockPlaceContext context) {
        var stack = context.getItemInHand();
        var start = stack.get(ComponentContent.BELT_START.get());
        var look = stack.get(ComponentContent.BELT_DIR.get());
        var player = context.getPlayer();
        if (start == null || look == null || player != null && player.isShiftKeyDown()) return null;

        var level = context.getLevel();
        var path = Stretch.path(spot(start), BeltTileBlock.travel(look), spot(aimedTile(context)));
        if (path.isEmpty()) {
            var tile = new StretchPlan.Tile(start, getBlock().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, look),
              StretchPlan.Action.PLACE);
            return new StretchPlan(List.of(tile), 0, List.of(), StretchPlan.Refusal.of(StretchPlan.Reason.BEHIND_LOOK));
        }

        var travels = new HashMap<LineScan.Spot, LineScan.Travel>();
        for (var step : path.get()) travels.put(step.spot(), step.travel());

        var tiles = new ArrayList<StretchPlan.Tile>();
        var returned = new ArrayList<ItemStack>();
        var cost = 0;
        StretchPlan.Refusal refusal = null;
        for (var step : path.get()) {
            var pos = pos(step.spot());
            var facing = Direction.getApproximateNearest(step.travel().x(), 0, step.travel().z());
            var shape = TileShape.at(step.spot(), step.travel(), (from, travel) -> travels.containsKey(from)
              ? travels.get(from).equals(travel)
              : BeltTileBlock.feeds(level.getBlockState(pos(from)), travel));
            var state = getBlock().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                          .setValue(BeltTileBlock.CORNER, BeltTileBlock.Shape.of(shape));

            var there = level.getBlockState(pos);
            // Every tier is one Replace Group, as Factorio's belts are; the fork cannot read the pack's groups (ADR-0082).
            if (there.getBlock() instanceof BeltTileBlock) {
                if (refusal == null && !mayBuild(level, player, pos)) refusal = StretchPlan.Refusal.of(StretchPlan.Reason.BLOCKED);
                if (there.is(getBlock())) {
                    // Its shape follows its neighbours once they are down.
                    if (there.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing) continue;
                    tiles.add(new StretchPlan.Tile(pos, state, StretchPlan.Action.TURN));
                } else {
                    tiles.add(new StretchPlan.Tile(pos, state, StretchPlan.Action.REPLACE));
                    cost++;
                    returned.add(new ItemStack(there.getBlock().asItem()));
                }
                continue;
            }
            tiles.add(new StretchPlan.Tile(pos, state, StretchPlan.Action.PLACE));
            cost++;
            if (refusal == null && (!mayBuild(level, player, pos) || !there.canBeReplaced())) refusal = StretchPlan.Refusal.of(StretchPlan.Reason.BLOCKED);
        }
        if (refusal == null && tiles.stream().anyMatch(tile -> tile.action() == StretchPlan.Action.PLACE && !grounded(level, tile.pos()))) {
            refusal = StretchPlan.Refusal.of(StretchPlan.Reason.NO_GROUND);
        }

        var creative = player != null && player.hasInfiniteMaterials();
        if (creative) {
            cost = 0;
            returned.clear();
        }
        if (refusal == null && player != null && !creative) {
            var held = ContainerHelper.clearOrCountMatchingItems(player.getInventory(), this::isThisTile, 0, true);
            if (held < cost) refusal = StretchPlan.Refusal.of(StretchPlan.Reason.NOT_ENOUGH_TILES, cost, held);
            else if (!fits(player, cost, returned)) refusal = StretchPlan.Refusal.of(StretchPlan.Reason.NO_ROOM_TO_RETURN);
        }
        return new StretchPlan(tiles, cost, returned, refusal);
    }

    private void execute(StretchPlan plan, Level level, ItemStack stack, @Nullable Player player) {
        clearStart(stack);
        if (player != null && plan.cost() > 0) {
            ContainerHelper.clearOrCountMatchingItems(player.getInventory(), this::isThisTile, plan.cost(), false);
        }
        for (var tile : plan.tiles()) {
            if (tile.action() == StretchPlan.Action.REPLACE) {
                var carried = level.getBlockEntity(tile.pos(), BlockEntitiesContent.BELT_TILE.get())
                                .map(old -> old.takeCarried()).orElse(List.of());
                level.setBlock(tile.pos(), tile.state(), Block.UPDATE_ALL);
                level.getBlockEntity(tile.pos(), BlockEntitiesContent.BELT_TILE.get()).ifPresent(placed -> placed.carry(carried));
            } else {
                level.setBlock(tile.pos(), tile.state(), Block.UPDATE_ALL);
            }
        }
        if (player != null) {
            for (var item : plan.returned()) {
                var copy = item.copy();
                if (!player.getInventory().add(copy) && !copy.isEmpty()) player.drop(copy, false);
            }
        }

        if (plan.tiles().isEmpty()) return;
        var first = plan.tiles().getFirst();
        var sound = first.state().getSoundType();
        level.playSound(null, first.pos(), sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, first.pos(), GameEvent.Context.of(player, first.state()));
    }

    // Where a tile placed by this click would go, or the tile aimed at, so a stretch can start or end on one.
    private static BlockPos aimedTile(BlockPlaceContext context) {
        var clicked = context.getClickedPos();
        var aimed = context.replacingClickedOnBlock() ? clicked : clicked.relative(context.getClickedFace().getOpposite());
        return context.getLevel().getBlockState(aimed).getBlock() instanceof BeltTileBlock ? aimed : clicked;
    }

    // No entity check: the player laying a belt usually stands on it.
    private static boolean mayBuild(Level level, @Nullable Player player, BlockPos pos) {
        return level.isInWorldBounds(pos) && (player == null || level.mayInteract(player, pos));
    }

    private static boolean grounded(Level level, BlockPos pos) {
        var below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** Whether the inventory holds what the stretch hands back once its charge is taken, as {@code Inventory#add} places it. */
    private boolean fits(Player player, int cost, List<ItemStack> returned) {
        if (returned.isEmpty()) return true;
        var slots = new ArrayList<ItemStack>();
        for (var slot : player.getInventory().getNonEquipmentItems()) slots.add(slot.copy());
        var toTake = cost;
        for (var slot : slots) {
            if (toTake == 0) break;
            if (!isThisTile(slot)) continue;
            var taken = Math.min(toTake, slot.getCount());
            slot.shrink(taken);
            toTake -= taken;
        }
        for (var item : returned) {
            var rest = item.copy();
            for (var slot : slots) {
                if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, rest)) {
                    var moved = Math.max(0, Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount()));
                    slot.grow(moved);
                    rest.shrink(moved);
                }
            }
            for (var i = 0; i < slots.size() && !rest.isEmpty(); i++) {
                if (slots.get(i).isEmpty()) slots.set(i, rest.split(rest.getMaxStackSize()));
            }
            if (!rest.isEmpty()) return false;
        }
        return true;
    }

    private static void tell(Player player, Component message) {
        if (player instanceof ServerPlayer server) server.sendSystemMessage(message, true);
    }

    private boolean isThisTile(ItemStack candidate) {
        return candidate.is(this);
    }

    private static void clearStart(ItemStack stack) {
        stack.remove(ComponentContent.BELT_START.get());
        stack.remove(ComponentContent.BELT_DIR.get());
    }

    private static LineScan.Spot spot(BlockPos pos) {
        return new LineScan.Spot(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos pos(LineScan.Spot spot) {
        return new BlockPos(spot.x(), spot.y(), spot.z());
    }
}
