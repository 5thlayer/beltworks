package rearth.belts.items;

import rearth.belts.BlockContent;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.ComponentContent;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import rearth.belts.model.BeltCost;
import rearth.belts.model.BeltPath;
import rearth.belts.model.BeltTier;
import rearth.belts.model.LoaderChoice;
import rearth.belts.model.SupportSlots;
import rearth.belts.api.item.ItemApi;
import rearth.belts.blocks.ChuteBlock;
import rearth.belts.blocks.ChuteBlockEntity;
import net.minecraft.world.ContainerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class BeltItem extends Item {

    private final BeltTier beltTier;
    
    public BeltItem(Properties settings, BeltTier beltTier) {
        super(settings);
        this.beltTier = beltTier;
    }
    
    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        
        if (!world.isClientSide() && user.isShiftKeyDown()) {
            var stack = user.getItemInHand(hand);
            stack.remove(ComponentContent.MIDPOINTS.get());
            stack.remove(ComponentContent.BELT_START.get());
            stack.remove(ComponentContent.BELT_DIR.get());
            user.sendSystemMessage(Component.translatable("message.belts.reset"));
        }
        
        return super.use(world, user, hand);
    }
    
    // PlanetaryFactory #366 has the gesture.
    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        if (level.isClientSide() || player == null) return InteractionResult.SUCCESS;

        var stack = context.getItemInHand();
        var hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        var plan = plan(level, stack, player, hit);
        if (plan == null) return InteractionResult.PASS;
        if (plan.refused()) {
            player.sendSystemMessage(plan.refusal().message());
            return InteractionResult.FAIL;
        }
        execute(plan, level, stack, player);
        return InteractionResult.SUCCESS;
    }

    /**
     * What a click with this stack at this aim would do, or null where the aimed block answers the
     * click itself (PlanetaryFactory #372).
     */
    public @Nullable BeltPlan plan(Level world, ItemStack stack, Player player, BlockHitResult hit) {
        var clicked = hit.getBlockPos();
        var face = hit.getDirection();
        var start = stack.get(ComponentContent.BELT_START.get());
        var startDir = stack.get(ComponentContent.BELT_DIR.get());
        var hasStart = start != null && startDir != null;
        var sneaking = player.isShiftKeyDown();

        var chute = world.getBlockEntity(clicked, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (chute.isPresent()) {
            // Sneaking skips the block's own answer, as vanilla does for any held item.
            if (!sneaking && chute.get().getBlockState().getBlock() instanceof ChuteBlock block && block.setsFilter(chute.get(), stack)) {
                return null;
            }
            var refusal = clickRefusal(chute.get(), hasStart, sneaking).map(BeltPlan.Refusal::of).orElse(null);
            if (!hasStart) return startOn(chute.get(), clicked, refusal);
            return belt(world, stack, player, start, startDir, clicked, chute.get().getOwnFacing(), refusal);
        }

        // The block on the surface of the target. From the ground the player's facing gives the
        // direction; from a wall, the direction faces away from it.
        var target = clicked.relative(face);
        var targetDir = face.getAxis() == Direction.Axis.Y ? player.getDirection() : face;
        var blocked = isOpen(world, target) ? null : BeltPlan.Refusal.of("message.belts.blocked");
        if (!hasStart) {
            return startOnGround(world, target, face.getAxis() == Direction.Axis.Y ? targetDir.getOpposite() : face, blocked);
        }
        if (sneaking) return midpoint(world, stack, start, startDir, new PlannedSupport(target, targetDir));
        return belt(world, stack, player, start, startDir, target, targetDir, null);
    }

    /** The message refusing a click on this loader, splitter or support, or empty when the click takes it. */
    private static Optional<String> clickRefusal(ChuteBlockEntity clicked, boolean hasStart, boolean sneaking) {
        if (clicked.isSupport()) {
            var click = sneaking ? SupportSlots.Click.MIDPOINT : hasStart ? SupportSlots.Click.END : SupportSlots.Click.START;
            return SupportSlots.refusal(clicked.supportUse(), click).map(SupportSlots.Refusal::messageKey);
        }
        return (hasStart ? clicked.canEndBelt() : clicked.canStartBelt()) ? Optional.empty() : Optional.of("message.belts.chute_used");
    }

    // A free support's direction is the belt's to set; a support a belt arrives at leaves along it.
    private BeltPlan startOn(ChuteBlockEntity chute, BlockPos clicked, BeltPlan.@Nullable Refusal refusal) {
        var arrow = isFreeSupport(chute) ? null : chute.getOwnFacing();
        var end = new BeltPlan.End(clicked, chute.getOwnFacing(), BeltPlan.Action.USE, null, null, arrow);
        return new BeltPlan(BeltPlan.Click.START, beltTier, List.of(end), List.of(), null, 0, refusal);
    }

    // With no inventory beside it the start becomes a support, whose direction the belt's end decides.
    private BeltPlan startOnGround(Level world, BlockPos pos, Direction stored, BeltPlan.@Nullable Refusal refusal) {
        var loader = loaderFacing(world, pos, stored);
        var end = loader.isPresent()
                ? new BeltPlan.End(pos, stored, BeltPlan.Action.PLACE_LOADER, loaderState(beltTier, loader.get()), beltTier, loader.get())
                : new BeltPlan.End(pos, stored, BeltPlan.Action.PLACE_SUPPORT, supportState(stored), null, null);
        return new BeltPlan(BeltPlan.Click.START, beltTier, List.of(end), List.of(), null, 0, refusal);
    }

    private BeltPlan midpoint(Level world, ItemStack stack, BlockPos start, Direction startDir, PlannedSupport support) {
        var refusal = planRefusal(world, stack, support).map(BeltPlan.Refusal::of).orElse(null);
        var planned = new ArrayList<>(stack.getOrDefault(ComponentContent.MIDPOINTS.get(), List.of()));
        planned.add(support);
        var supports = supportsOf(planned);
        var startEnd = planStart(world, start, startDir, supports, null);
        var path = ChuteBlockEntity.BeltData.path(startEnd.anchor(), supports, null);
        var ends = List.of(startEnd.toEnd(world, start, startEnd.places() == SupportSlots.OpenEnd.LOADER ? beltTier : null, false));
        return new BeltPlan(BeltPlan.Click.MIDPOINT, beltTier, ends, List.copyOf(planned), path, 0, refusal);
    }

    /** The message refusing a sneak-click that plans this mid-belt support, or empty. */
    private static Optional<String> planRefusal(Level world, ItemStack stack, PlannedSupport support) {
        var planned = new ArrayList<>(stack.getOrDefault(ComponentContent.MIDPOINTS.get(), List.of()));
        var start = stack.get(ComponentContent.BELT_START.get());
        if (support.pos().equals(start) || planned.stream().anyMatch(other -> other.pos().equals(support.pos()))) {
            return Optional.of("message.belts.midpoint_duplicate");
        }
        if (!isOpen(world, support.pos())) return Optional.of("message.belts.blocked");
        planned.add(support);
        return plannedPath(world, start, stack.get(ComponentContent.BELT_DIR.get()), supportsOf(planned), null, null)
                 .refusal().map(refusal -> refusal.bound().messageKey());
    }

    // The first refusal met is the one named, so the order is the reason the player reads (PlanetaryFactory #372).
    private BeltPlan belt(Level world, ItemStack stack, Player player, BlockPos start, Direction startDir,
                          BlockPos end, Direction endDir, BeltPlan.@Nullable Refusal clickRefusal) {
        var planned = stack.getOrDefault(ComponentContent.MIDPOINTS.get(), List.<PlannedSupport>of());
        var refusal = clickRefusal;
        if (refusal == null) {
            refusal = world.getBlockEntity(start, BlockEntitiesContent.CHUTE_BLOCK.get())
                        .flatMap(chute -> clickRefusal(chute, false, false)).map(BeltPlan.Refusal::of).orElse(null);
        }
        // A splitter half is free at both ends, and a belt from its front to its own back would
        // hand its end straight to its head (#349).
        if (refusal == null && start.equals(end)) refusal = BeltPlan.Refusal.of("message.belts.chute_used");
        if (refusal == null && (!canEndBelt(world, start) || !canEndBelt(world, end)
              || planned.stream().anyMatch(support -> support.pos().equals(end) || !isOpen(world, support.pos())))) {
            refusal = BeltPlan.Refusal.of("message.belts.blocked");
        }

        var supports = supportsOf(planned);
        var startEnd = planStart(world, start, startDir, supports, end);
        var endEnd = planEnd(world, end, endDir, supports, start);
        var path = start.equals(end) ? null : ChuteBlockEntity.BeltData.path(startEnd.anchor(), supports, endEnd.anchor());
        if (refusal == null && path != null && path.refusal().isPresent()) {
            refusal = BeltPlan.Refusal.of(path.refusal().get().bound().messageKey());
        }

        var cost = player.isCreative() || path == null ? 0 : BeltCost.of(path.length());
        if (refusal == null) {
            var held = ContainerHelper.clearOrCountMatchingItems(player.getInventory(), this::isThisBelt, 0, true);
            if (held < cost) refusal = BeltPlan.Refusal.of("message.belts.not_enough_belts", cost, held);
        }

        var openLoaders = (startEnd.places() == SupportSlots.OpenEnd.LOADER ? 1 : 0) + (endEnd.places() == SupportSlots.OpenEnd.LOADER ? 1 : 0);
        var loaders = LoaderChoice.of(beltTier, openLoaders, player.isCreative() ? Map.of(beltTier, openLoaders) : heldLoaders(player));
        if (refusal == null && loaders.isEmpty()) {
            refusal = BeltPlan.Refusal.of("message.belts.not_enough_loaders", openLoaders, beltTier.number());
        }
        var chosen = new ArrayDeque<>(loaders.orElse(List.of()));
        var ends = List.of(
          startEnd.toEnd(world, start, startEnd.places() == SupportSlots.OpenEnd.LOADER ? tierOf(chosen) : null, false),
          endEnd.toEnd(world, end, endEnd.places() == SupportSlots.OpenEnd.LOADER ? tierOf(chosen) : null, true));
        return new BeltPlan(BeltPlan.Click.BELT, beltTier, ends, List.copyOf(planned), path, cost, refusal);
    }

    // A refused plan still draws its loaders, in the belt's own tier.
    private BeltTier tierOf(ArrayDeque<BeltTier> chosen) {
        return chosen.isEmpty() ? beltTier : chosen.pop();
    }

    private void execute(BeltPlan plan, Level world, ItemStack stack, Player player) {
        switch (plan.click()) {
            case START -> {
                var end = plan.ends().getFirst();
                stack.set(ComponentContent.BELT_START.get(), end.pos());
                stack.set(ComponentContent.BELT_DIR.get(), end.facing());
                player.sendSystemMessage(Component.translatable("message.belts.started"));
            }
            case MIDPOINT -> {
                stack.set(ComponentContent.MIDPOINTS.get(), plan.supports());
                player.sendSystemMessage(Component.translatable("message.belts.midpoint_added"));
            }
            case BELT -> createBelt(plan, world, stack, player);
        }
    }

    private void createBelt(BeltPlan plan, Level world, ItemStack stack, Player player) {
        var start = plan.ends().get(0).pos();
        var end = plan.ends().get(1).pos();

        stack.remove(ComponentContent.MIDPOINTS.get());
        stack.remove(ComponentContent.BELT_START.get());
        stack.remove(ComponentContent.BELT_DIR.get());
        ContainerHelper.clearOrCountMatchingItems(player.getInventory(), this::isThisBelt, plan.beltCost(), false);

        var distStart = start.distToCenterSqr(player.position());
        var distEnd = end.distToCenterSqr(player.position());
        var playfrom = distStart < distEnd ? start : end;
        world.playSound(null, playfrom, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1f, 0.5f);

        for (var placed : plan.ends()) {
            if (placed.state() != null) world.setBlockAndUpdate(placed.pos(), placed.state());
            if (placed.action() == BeltPlan.Action.PLACE_LOADER && !player.isCreative()) {
                var item = BlockContent.loaderFor(placed.loader()).asItem();
                ContainerHelper.clearOrCountMatchingItems(player.getInventory(), candidate -> candidate.is(item), 1, false);
            }
        }
        for (var support : plan.supports()) world.setBlockAndUpdate(support.pos(), supportState(support.facing()));

        world.getBlockEntity(start, BlockEntitiesContent.CHUTE_BLOCK.get())
          .ifPresent(startEntity -> startEntity.assignFromBeltItem(end, plan.supports().stream().map(PlannedSupport::pos).toList(),
            beltTier, plan.beltCost()));

        var next = world.getBlockEntity(end, BlockEntitiesContent.CHUTE_BLOCK.get())
                     .filter(chute -> chute.isSupport() && chute.canStartBelt());
        if (next.isPresent()) {
            stack.set(ComponentContent.BELT_START.get(), end);
            stack.set(ComponentContent.BELT_DIR.get(), next.get().getOwnFacing());
            player.sendSystemMessage(Component.translatable("message.belts.belt_chained"));
        } else {
            player.sendSystemMessage(Component.translatable("message.belts.belt_created"));
        }
    }

    /**
     * The path the belt item would lay from {@code start}, reading a loader, splitter or support
     * already standing at either end, and otherwise the loader or support an open end would get;
     * with no end, the path to the last support (PlanetaryFactory ADR-0078).
     */
    private static BeltPath plannedPath(Level world, BlockPos start, Direction startDir, List<Pair<BlockPos, Direction>> supports,
                                        @Nullable BlockPos end, @Nullable Direction endDir) {
        var endPlan = end == null ? null : planEnd(world, end, endDir, supports, start);
        return ChuteBlockEntity.BeltData.path(planStart(world, start, startDir, supports, end).anchor(), supports,
          endPlan == null ? null : endPlan.anchor());
    }

    /**
     * One end of a planned belt: where its curve meets it, facing as a loader there would, and what
     * the click places there, or whether it turns a free support to face along the belt.
     */
    private record PlannedEnd(BlockPos pos, Direction facing, boolean support, SupportSlots.@Nullable OpenEnd places, boolean turns) {

        BeltPath.Anchor anchor() {
            return ChuteBlockEntity.BeltData.anchor(pos, facing, support);
        }

        // A support faces the way its belts run, which at a belt's end is against a loader's facing.
        BeltPlan.End toEnd(Level world, BlockPos clicked, @Nullable BeltTier loader, boolean atEnd) {
            var flow = atEnd ? facing.getOpposite() : facing;
            if (places == SupportSlots.OpenEnd.LOADER) {
                return new BeltPlan.End(pos, facing, BeltPlan.Action.PLACE_LOADER, loaderState(loader, facing), loader, null);
            }
            if (places == SupportSlots.OpenEnd.SUPPORT) {
                return new BeltPlan.End(pos, flow, BeltPlan.Action.PLACE_SUPPORT, supportState(flow), null, null);
            }
            if (turns) {
                var turned = world.getBlockState(pos).setValue(HorizontalDirectionalBlock.FACING, flow);
                return new BeltPlan.End(pos, flow, BeltPlan.Action.TURN_SUPPORT, turned, null, null);
            }
            return new BeltPlan.End(clicked, facing, BeltPlan.Action.USE, null, null, null);
        }
    }

    private static PlannedEnd planStart(Level world, BlockPos start, Direction startDir, List<Pair<BlockPos, Direction>> supports,
                                        @Nullable BlockPos end) {
        var next = supports.isEmpty() ? end : supports.getFirst().getFirst();
        var flow = next == null ? startDir : heading(next.getX() - start.getX(), next.getZ() - start.getZ(), startDir);
        var chute = world.getBlockEntity(start, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (chute.isPresent()) {
            if (isFreeSupport(chute.get())) return new PlannedEnd(start, flow, true, null, true);
            return new PlannedEnd(chute.get().beltStartPos(), chute.get().getOwnFacing(), chute.get().isSupport(), null, false);
        }
        var loader = loaderFacing(world, start, startDir);
        return switch (SupportSlots.OpenEnd.of(loader.isPresent())) {
            case LOADER -> new PlannedEnd(start, loader.get(), false, SupportSlots.OpenEnd.LOADER, false);
            case SUPPORT -> new PlannedEnd(start, flow, true, SupportSlots.OpenEnd.SUPPORT, false);
        };
    }

    private static PlannedEnd planEnd(Level world, BlockPos end, @Nullable Direction endDir, List<Pair<BlockPos, Direction>> supports,
                                      BlockPos start) {
        var previous = supports.isEmpty() ? start : supports.getLast().getFirst();
        var chute = world.getBlockEntity(end, BlockEntitiesContent.CHUTE_BLOCK.get());
        var fallback = endDir != null ? endDir.getOpposite() : chute.map(ChuteBlockEntity::getOwnFacing).orElse(Direction.NORTH);
        var flow = heading(end.getX() - previous.getX(), end.getZ() - previous.getZ(), fallback);
        if (chute.isPresent()) {
            if (isFreeSupport(chute.get())) return new PlannedEnd(end, flow.getOpposite(), true, null, true);
            return new PlannedEnd(chute.get().beltEndPos(), chute.get().beltEndFacing(), chute.get().isSupport(), null, false);
        }
        var loader = endDir == null ? Optional.<Direction>empty() : loaderFacing(world, end, endDir);
        return switch (SupportSlots.OpenEnd.of(loader.isPresent())) {
            case LOADER -> new PlannedEnd(end, loader.get(), false, SupportSlots.OpenEnd.LOADER, false);
            case SUPPORT -> new PlannedEnd(end, flow.getOpposite(), true, SupportSlots.OpenEnd.SUPPORT, false);
        };
    }

    // Its facing was set by belts no longer there, so the new belt sets it again (#366).
    private static boolean isFreeSupport(ChuteBlockEntity chute) {
        return chute.isSupport() && chute.supportUse().equals(new SupportSlots.Use(false, false, false));
    }

    private static Direction heading(int dx, int dz, Direction fallback) {
        var heading = SupportSlots.Heading.toward(dx, dz, new SupportSlots.Heading(fallback.getStepX(), fallback.getStepZ()));
        return Direction.getApproximateNearest(heading.x(), 0, heading.z());
    }

    /**
     * The facing a loader at this open end takes, with the inventory beyond it: the way the click
     * suggests, else the other way, so a player may face the inventory or the belt. Empty with no
     * inventory either side, where the end becomes a support.
     */
    private static Optional<Direction> loaderFacing(Level world, BlockPos pos, Direction suggested) {
        for (var facing : List.of(suggested, suggested.getOpposite())) {
            if (ItemApi.BLOCK.find(world, pos.relative(facing.getOpposite()), null, null, facing) != null) return Optional.of(facing);
        }
        return Optional.empty();
    }

    private static BlockState supportState(Direction facing) {
        return BlockContent.CONVEYOR_SUPPORT_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    private static BlockState loaderState(BeltTier tier, Direction facing) {
        return BlockContent.loaderFor(tier).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    private static Map<BeltTier, Integer> heldLoaders(Player player) {
        var held = new EnumMap<BeltTier, Integer>(BeltTier.class);
        for (var tier : BeltTier.values()) {
            var item = BlockContent.loaderFor(tier).asItem();
            held.put(tier, ContainerHelper.clearOrCountMatchingItems(player.getInventory(), stack -> stack.is(item), 0, true));
        }
        return held;
    }
    
    private boolean isThisBelt(ItemStack candidate) {
        return candidate.is(this);
    }
    
    /** Whether a loader or support stands here or one could be placed. */
    private static boolean canEndBelt(Level world, BlockPos pos) {
        return world.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get()).isPresent() || isOpen(world, pos);
    }

    private static boolean isOpen(Level world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return state.canBeReplaced() || state.isAir();
    }
    
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag type) {
        
        if (stack.has(ComponentContent.BELT_START.get())) {
            var targetPos = stack.get(ComponentContent.BELT_START.get());
            tooltip.accept(Component.literal(targetPos.toShortString()));
        }
        
        if (stack.has(ComponentContent.MIDPOINTS.get())) {
            tooltip.accept(Component.literal("Midpoints: "));
            for (var midPoint : stack.get(ComponentContent.MIDPOINTS.get())) {
                tooltip.accept(Component.literal(midPoint.pos().toShortString()));
            }
        }
        
        var showExtra = Minecraft.getInstance().hasControlDown();
        if (showExtra) {
            for (int i = 0; i < 4; i++) {
                tooltip.accept(Component.translatable(getDescriptionId() + ".tooltip." + i).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.accept(Component.translatable("message.belts.show_extra").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        
        super.appendHoverText(stack, context, display, tooltip, type);
    }
    
    private static List<Pair<BlockPos, Direction>> supportsOf(List<PlannedSupport> planned) {
        return planned.stream().map(support -> Pair.of(support.pos(), support.facing())).toList();
    }
}
