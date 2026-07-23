package rearth.belts.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rearth.belts.collision.BeltCollisionRegistry;

import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
public abstract class EntityCollisionMixin {

    @Inject(method = "collectColliders", at = @At("RETURN"), cancellable = true)
    private static void belts$addBeltCollisions(Entity source, Level level, List<VoxelShape> entityColliders,
                                                 AABB boundingBox,
                                                 CallbackInfoReturnable<List<VoxelShape>> callback) {
        var beltCollisions = BeltCollisionRegistry.getCollisionShapes(level, boundingBox);
        if (beltCollisions.isEmpty()) return;

        var colliders = new ArrayList<>(callback.getReturnValue());
        colliders.addAll(beltCollisions);
        callback.setReturnValue(colliders);
    }
}
