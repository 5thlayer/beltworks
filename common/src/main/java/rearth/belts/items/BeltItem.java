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
import rearth.belts.model.BeltCost;
import rearth.belts.model.BeltPath;
import rearth.belts.model.BeltTier;
import rearth.belts.model.LoaderChoice;
import rearth.belts.model.SupportSlots;
import rearth.belts.api.item.ItemApi;
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
        
        var stack = context.getItemInHand();
        var level = context.getLevel();
        var player = context.getPlayer();
        
        if (level.isClientSide() || player == null) return InteractionResult.SUCCESS;
        
        var targetBlockPos = context.getClickedPos();
        var hasStart = stack.has(ComponentContent.BELT_START.get()) && stack.has(ComponentContent.BELT_DIR.get());
        
        var chuteCandidate = level.getBlockEntity(targetBlockPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (chuteCandidate.isPresent()) {
            var chuteEntity = chuteCandidate.get();
            var refusal = clickRefusal(chuteEntity, hasStart, player.isShiftKeyDown());
            if (refusal.isPresent()) {
                player.sendSystemMessage(Component.translatable(refusal.get()));
                return InteractionResult.FAIL;
            }
            
            if (hasStart) {
                createBelt(stack.get(ComponentContent.BELT_START.get()), stack.get(ComponentContent.BELT_DIR.get()),
                  targetBlockPos, chuteEntity.getOwnFacing(), level, stack, player);
            } else {
                stack.set(ComponentContent.BELT_START.get(), targetBlockPos);
                stack.set(ComponentContent.BELT_DIR.get(), chuteEntity.getOwnFacing());
                player.sendSystemMessage(Component.translatable("message.belts.started"));
            }
            
            return InteractionResult.SUCCESS;
        }
        
        // The block on the surface of the target. From the ground the player's facing gives the
        // direction; from a wall, the direction faces away from it.
        var targetDir = context.getClickedFace();
        targetBlockPos = targetBlockPos.relative(context.getClickedFace());
        if (context.getClickedFace().getAxis().equals(Direction.Axis.Y)) {
            targetDir = context.getHorizontalDirection();
        }
        
        if (!isOpen(level, targetBlockPos)) return InteractionResult.SUCCESS;
        
        if (!hasStart) {
            if (!context.getClickedFace().getAxis().equals(Direction.Axis.Y)) {
                targetDir = targetDir.getOpposite();
            }
            stack.set(ComponentContent.BELT_START.get(), targetBlockPos);
            stack.set(ComponentContent.BELT_DIR.get(), targetDir.getOpposite());
            player.sendSystemMessage(Component.translatable("message.belts.started"));
        } else if (player.isShiftKeyDown()) {
            planSupport(stack, new PlannedSupport(targetBlockPos, targetDir), level, player);
        } else {
            createBelt(stack.get(ComponentContent.BELT_START.get()), stack.get(ComponentContent.BELT_DIR.get()),
              targetBlockPos, targetDir, level, stack, player);
        }
        
        return InteractionResult.SUCCESS;
    }
    
    /** The message refusing a click on this loader, splitter or support, or empty when the click takes it. */
    public static Optional<String> clickRefusal(ChuteBlockEntity clicked, boolean hasStart, boolean sneaking) {
        if (clicked.isSupport()) {
            var click = sneaking ? SupportSlots.Click.MIDPOINT : hasStart ? SupportSlots.Click.END : SupportSlots.Click.START;
            return SupportSlots.refusal(clicked.supportUse(), click).map(SupportSlots.Refusal::messageKey);
        }
        return (hasStart ? clicked.canEndBelt() : clicked.canStartBelt()) ? Optional.empty() : Optional.of("message.belts.chute_used");
    }
    
    private static void planSupport(ItemStack stack, PlannedSupport support, Level world, Player player) {
        var refusal = planRefusal(world, stack, support);
        if (refusal.isPresent()) {
            player.sendSystemMessage(Component.translatable(refusal.get()));
            return;
        }
        var planned = new ArrayList<>(stack.getOrDefault(ComponentContent.MIDPOINTS.get(), List.of()));
        planned.add(support);
        stack.set(ComponentContent.MIDPOINTS.get(), planned);
        player.sendSystemMessage(Component.translatable("message.belts.midpoint_added"));
    }

    /** The message refusing a sneak-click that plans this mid-belt support, or empty; the preview asks it too. */
    public static Optional<String> planRefusal(Level world, ItemStack stack, PlannedSupport support) {
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
    
    private void createBelt(BlockPos start, Direction startDir, BlockPos end, Direction endDir, Level world, ItemStack stack, Player player) {
        
        var startChute = world.getBlockEntity(start, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (startChute.isPresent()) {
            var refusal = clickRefusal(startChute.get(), false, false);
            if (refusal.isPresent()) {
                player.sendSystemMessage(Component.translatable(refusal.get()));
                return;
            }
        }
        
        // A splitter half is free at both ends, and a belt from its front to its own back would
        // hand its end straight to its head (#349).
        if (start.equals(end)) {
            player.sendSystemMessage(Component.translatable("message.belts.chute_used"));
            return;
        }

        var planned = stack.getOrDefault(ComponentContent.MIDPOINTS.get(), List.<PlannedSupport>of());
        if (!canEndBelt(world, start) || !canEndBelt(world, end)
              || planned.stream().anyMatch(support -> support.pos().equals(end) || !isOpen(world, support.pos()))) {
            player.sendSystemMessage(Component.translatable("message.belts.blocked"));
            return;
        }
        
        var endChute = world.getBlockEntity(end, BlockEntitiesContent.CHUTE_BLOCK.get());
        var path = plannedPath(world, start, startDir, supportsOf(planned), end, endDir);
        var refusal = path.refusal();
        if (refusal.isPresent()) {
            player.sendSystemMessage(Component.translatable(refusal.get().bound().messageKey()));
            return;
        }
        var cost = player.isCreative() ? 0 : BeltCost.of(path.length());
        var held = ContainerHelper.clearOrCountMatchingItems(player.getInventory(), this::isThisBelt, 0, true);
        if (held < cost) {
            player.sendSystemMessage(Component.translatable("message.belts.not_enough_belts", cost, held));
            return;
        }

        var startEnd = startChute.isPresent() ? null : openEnd(world, start, startDir);
        var endEnd = endChute.isPresent() ? null : openEnd(world, end, endDir);
        var openLoaders = (startEnd == SupportSlots.OpenEnd.LOADER ? 1 : 0) + (endEnd == SupportSlots.OpenEnd.LOADER ? 1 : 0);
        var loaders = LoaderChoice.of(beltTier, openLoaders, player.isCreative() ? Map.of(beltTier, openLoaders) : heldLoaders(player));
        if (loaders.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.belts.not_enough_loaders", openLoaders, beltTier.number()));
            return;
        }
        var chosen = new ArrayDeque<>(loaders.get());
        
        stack.remove(ComponentContent.MIDPOINTS.get());
        stack.remove(ComponentContent.BELT_START.get());
        stack.remove(ComponentContent.BELT_DIR.get());
        ContainerHelper.clearOrCountMatchingItems(player.getInventory(), this::isThisBelt, cost, false);
        
        var distStart = start.distToCenterSqr(player.position());
        var distEnd = end.distToCenterSqr(player.position());
        var playfrom = distStart < distEnd ? start : end;
        world.playSound(null, playfrom, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1f, 0.5f);
        
        // A support faces the way its belts run: along the belt at its start and at its end.
        if (startEnd == SupportSlots.OpenEnd.LOADER) placeLoader(world, start, startDir, chosen.pop(), player);
        if (startEnd == SupportSlots.OpenEnd.SUPPORT) placeSupport(world, start, startDir);
        if (endEnd == SupportSlots.OpenEnd.LOADER) placeLoader(world, end, endDir, chosen.pop(), player);
        if (endEnd == SupportSlots.OpenEnd.SUPPORT) placeSupport(world, end, endDir.getOpposite());
        for (var support : planned) placeSupport(world, support.pos(), support.facing());
        
        world.getBlockEntity(start, BlockEntitiesContent.CHUTE_BLOCK.get())
          .ifPresent(startEntity -> startEntity.assignFromBeltItem(end, planned.stream().map(PlannedSupport::pos).toList(), beltTier, cost));
        
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
     * with no end, the path to the last support. The click and the preview both ask this
     * (PlanetaryFactory ADR-0078).
     */
    public static BeltPath plannedPath(Level world, BlockPos start, Direction startDir, List<Pair<BlockPos, Direction>> supports,
                                       @Nullable BlockPos end, @Nullable Direction endDir) {
        var startChute = world.getBlockEntity(start, BlockEntitiesContent.CHUTE_BLOCK.get());
        var startAnchor = startChute
          .map(chute -> ChuteBlockEntity.BeltData.anchor(chute.beltStartPos(), chute.getOwnFacing(), chute.isSupport()))
          .orElseGet(() -> ChuteBlockEntity.BeltData.anchor(start, startDir, openEnd(world, start, startDir) == SupportSlots.OpenEnd.SUPPORT));
        var endChute = end == null ? Optional.<ChuteBlockEntity>empty() : world.getBlockEntity(end, BlockEntitiesContent.CHUTE_BLOCK.get());
        BeltPath.Anchor endAnchor = null;
        if (endChute.isPresent()) {
            var chute = endChute.get();
            endAnchor = ChuteBlockEntity.BeltData.anchor(chute.beltEndPos(), chute.beltEndFacing(), chute.isSupport());
        } else if (end != null && endDir != null) {
            endAnchor = ChuteBlockEntity.BeltData.anchor(end, endDir, openEnd(world, end, endDir) == SupportSlots.OpenEnd.SUPPORT);
        }
        return ChuteBlockEntity.BeltData.path(startAnchor, supports, endAnchor);
    }
    
    /** What an open end facing the way a loader there would gets: a loader when an inventory is beyond it. */
    public static SupportSlots.OpenEnd openEnd(Level world, BlockPos pos, Direction loaderFacing) {
        return SupportSlots.OpenEnd.of(ItemApi.BLOCK.find(world, pos.relative(loaderFacing.getOpposite()), null, null, loaderFacing) != null);
    }

    private static void placeSupport(Level world, BlockPos pos, Direction facing) {
        world.setBlockAndUpdate(pos, BlockContent.CONVEYOR_SUPPORT_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
    }

    private static void placeLoader(Level world, BlockPos pos, Direction facing, BeltTier tier, Player player) {
        world.setBlockAndUpdate(pos, BlockContent.loaderFor(tier).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
        if (!player.isCreative()) {
            var item = BlockContent.loaderFor(tier).asItem();
            ContainerHelper.clearOrCountMatchingItems(player.getInventory(), stack -> stack.is(item), 1, false);
        }
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
    
    /** The mid-belt supports the belt item has planned, as the path reads them. */
    public static List<Pair<BlockPos, Direction>> getStoredMidpoints(ItemStack stack) {
        return supportsOf(stack.getOrDefault(ComponentContent.MIDPOINTS.get(), List.of()));
    }

    private static List<Pair<BlockPos, Direction>> supportsOf(List<PlannedSupport> planned) {
        return planned.stream().map(support -> Pair.of(support.pos(), support.facing())).toList();
    }
}
