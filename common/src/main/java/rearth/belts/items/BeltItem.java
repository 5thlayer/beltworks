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
import rearth.belts.ItemContent;
import rearth.belts.model.BeltTier;

import java.util.ArrayList;
import java.util.List;
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
    
    @Override
    public InteractionResult useOn(UseOnContext context) {
        
        var stack = context.getItemInHand();
        
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        
        var targetBlockPos = context.getClickedPos();
        
        var hasStart = stack.has(ComponentContent.BELT_START.get()) && stack.has(ComponentContent.BELT_DIR.get());
        
        var chuteCandidate = context.getLevel().getBlockEntity(targetBlockPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (chuteCandidate.isPresent()) {
            var chuteEntity = chuteCandidate.get();
            if (chuteEntity.isUsed()) {
                context.getPlayer().sendSystemMessage(Component.translatable("message.belts.chute_used"));
                return InteractionResult.FAIL;
            }
            
            if (hasStart) {
                // create end
                var startPos = stack.get(ComponentContent.BELT_START.get());
                var startDir = stack.get(ComponentContent.BELT_DIR.get());
                var midPoints = stack.getOrDefault(ComponentContent.MIDPOINTS.get(), new ArrayList<BlockPos>());
                var endPos = targetBlockPos;
                var endDir = chuteEntity.getOwnFacing();
                
                createBelt(startPos, startDir, midPoints, endPos, endDir, context.getLevel(), context.getItemInHand(), context.getPlayer());
            } else {
                // assign manual start
                var startPos = targetBlockPos;
                var startDir = chuteEntity.getOwnFacing();
                
                stack.set(ComponentContent.BELT_START.get(), startPos);
                stack.set(ComponentContent.BELT_DIR.get(), startDir);
                
                context.getPlayer().sendSystemMessage(Component.translatable("message.belts.started"));
            }
            
            return InteractionResult.SUCCESS;
        }
        
        var supportCandidate = context.getLevel().getBlockState(targetBlockPos);
        if (hasStart && supportCandidate.getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get())) {
            // store midpoint
            var list = new ArrayList<BlockPos>();
            if (stack.has(ComponentContent.MIDPOINTS.get())) {
                list.addAll(stack.get(ComponentContent.MIDPOINTS.get()));
            }
            
            if (list.contains(targetBlockPos)) {
                context.getPlayer().sendSystemMessage(Component.translatable("message.belts.midpoint_duplicate"));
                return InteractionResult.FAIL;
            }
            
            list.add(targetBlockPos);
            stack.set(ComponentContent.MIDPOINTS.get(), list);
            context.getPlayer().sendSystemMessage(Component.translatable("message.belts.midpoint_added"));
            
            return InteractionResult.SUCCESS;
        }
        
        // at this point, no existing midpoint or start is being targeted, so we try to store the potential positions of a new one.
        // this is done by taking the block on the surface of the target. For grounds/walls, the direction is determined by the player. Otherwise facing away from the target.
        var targetDir = context.getClickedFace();
        targetBlockPos = targetBlockPos.relative(context.getClickedFace());
        if (context.getClickedFace().getAxis().equals(Direction.Axis.Y)) {
            targetDir = context.getHorizontalDirection();
        }
        
        var candidateState = context.getLevel().getBlockState(targetBlockPos);
        if (candidateState.canBeReplaced() || candidateState.isAir()) {
            // create either new start or end at this position
            if (hasStart) {
                // create end
                var startPos = stack.get(ComponentContent.BELT_START.get());
                var startDir = stack.get(ComponentContent.BELT_DIR.get());
                var midPoints = stack.getOrDefault(ComponentContent.MIDPOINTS.get(), new ArrayList<BlockPos>());
                var endPos = targetBlockPos;
                var endDir = targetDir;
                
                createBelt(startPos, startDir, midPoints, endPos, endDir, context.getLevel(), context.getItemInHand(), context.getPlayer());
            } else {
                if (!context.getClickedFace().getAxis().equals(Direction.Axis.Y)) {
                    targetDir = targetDir.getOpposite();
                }
                var startPos = targetBlockPos;
                var startDir = targetDir.getOpposite();
                
                stack.set(ComponentContent.BELT_START.get(), startPos);
                stack.set(ComponentContent.BELT_DIR.get(), startDir);
                context.getPlayer().sendSystemMessage(Component.translatable("message.belts.started"));
                
            }
        }
        
        return InteractionResult.SUCCESS;
    }
    
    // creates optional chute entities at start and end
    private void createBelt(BlockPos start, Direction startDir, List<BlockPos> supports, BlockPos end, Direction endDir, Level world, ItemStack stack, Player player) {
        
        stack.remove(ComponentContent.MIDPOINTS.get());
        stack.remove(ComponentContent.BELT_START.get());
        stack.remove(ComponentContent.BELT_DIR.get());
        
        if (!player.isCreative())
            stack.shrink(1);
        
        player.sendSystemMessage(Component.translatable("message.belts.belt_created"));
        
        var createdChutes = 0;
        
        var distStart = start.distToCenterSqr(player.position());
        var distEnd = end.distToCenterSqr(player.position());
        var playfrom = distStart < distEnd ? start : end;
        world.playSound(null, playfrom, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1f, 0.5f);
        
        // optionally create start entity
        var startState = world.getBlockState(start);
        var startCandidate = world.getBlockEntity(start, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (startCandidate.isEmpty() && (startState.canBeReplaced() || startState.isAir())) {
            world.setBlockAndUpdate(start, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, startDir));
            startCandidate = world.getBlockEntity(start, BlockEntitiesContent.CHUTE_BLOCK.get());
            createdChutes++;
        }
        
        // optionally create end entity
        var endState = world.getBlockState(end);
        var endCandidate = world.getBlockEntity(end, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (endCandidate.isEmpty() && (endState.canBeReplaced() || endState.isAir())) {
            world.setBlockAndUpdate(end, BlockContent.CHUTE_BLOCK.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, endDir));
            endCandidate = world.getBlockEntity(end, BlockEntitiesContent.CHUTE_BLOCK.get());
            createdChutes++;
        }
        
        // create belt in block entity
        if (startCandidate.isPresent() && endCandidate.isPresent()) {
            var startEntity = startCandidate.get();
            startEntity.assignFromBeltItem(end, supports, beltTier);
        }
        
        // optionally consume chutes in inventory
        if (createdChutes > 0) {
            var taken = 0;
            for (var playerItem : player.getInventory().getNonEquipmentItems()) {
                if (playerItem.is(ItemContent.CHUTE.get())) {
                    
                    var count = playerItem.getCount();
                    var removed = Math.min(count, createdChutes);
                    
                    playerItem.shrink(removed);
                    
                    taken += removed;
                    if (taken >= createdChutes) break;
                }
            }
        }
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
                tooltip.accept(Component.literal(midPoint.toShortString()));
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
    
    public static List<Pair<BlockPos, Direction>> getStoredMidpoints(ItemStack stack, Level world) {
        var res = new ArrayList<Pair<BlockPos, Direction>>();
        if (stack.has(ComponentContent.MIDPOINTS.get())) {
            stack.get(ComponentContent.MIDPOINTS.get())
              .stream()
              .filter(point -> world.getBlockState(point).getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
              .map(point -> new Pair<>(point, world.getBlockState(point).getValue(HorizontalDirectionalBlock.FACING))).forEachOrdered(res::add);
        }
        
        return res;
    }
}
