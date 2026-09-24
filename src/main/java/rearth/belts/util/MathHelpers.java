// SPDX-FileCopyrightText: Rearth
// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: CC-BY-4.0 AND MIT

package rearth.belts.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import static net.minecraft.core.Direction.*;

public class MathHelpers {
    
    public static Vec3 lerp(Vec3 a, Vec3 b, float f) {
        return new Vec3(lerp(a.x, b.x, f), lerp(a.y, b.y, f), lerp(a.z, b.z, f));
    }
    
    public static double lerp(double a, double b, double f) {
        return a + f * (b - a);
    }
    
    public static VoxelShape rotateVoxelShape(VoxelShape shape, Direction facing, AttachFace face) {
        
        if (shape.isEmpty()) return shape;
        
        var minX = shape.min(Axis.X);
        var maxX = shape.max(Axis.X);
        var minY = shape.min(Axis.Y);
        var maxY = shape.max(Axis.Y);
        var minZ = shape.min(Axis.Z);
        var maxZ = shape.max(Axis.Z);
        
        if (facing == NORTH) {
            if (face == AttachFace.FLOOR) return shape;
            if (face == AttachFace.WALL)
                return Shapes.box(1 - maxX, 1 - maxZ, 1 - maxY, 1 - minX, 1 - minZ, 1 - minY);
            if (face == AttachFace.CEILING)
                return Shapes.box(minX, 1 - maxY, 1 - maxZ, maxX, 1 - minY, 1 - minZ);
        }
        
        if (facing == SOUTH) {
            if (face == AttachFace.FLOOR)
                return Shapes.box(1 - maxX, minY, 1 - maxZ, 1 - minX, maxY, 1 - minZ);
            if (face == AttachFace.WALL)
                return Shapes.box(minX, 1 - maxZ, minY, maxX, 1 - minZ, maxY);
            if (face == AttachFace.CEILING)
                return Shapes.box(1 - maxX, 1 - maxY, minZ, 1 - minX, 1 - minY, maxZ);
            
        }
        
        if (facing == EAST) {
            if (face == AttachFace.FLOOR)
                return Shapes.box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX);
            if (face == AttachFace.WALL)
                return Shapes.box(minY, 1 - maxZ, 1 - maxX, maxY, 1 - minZ, 1 - minX);
            if (face == AttachFace.CEILING)
                return Shapes.box(minZ, 1 - maxY, minX, maxZ, 1 - minY, maxX);
        }
        
        if (facing == WEST) {
            if (face == AttachFace.FLOOR)
                return Shapes.box(minZ, minY, 1 - maxX, maxZ, maxY, 1 - minX);
            if (face == AttachFace.WALL)
                return Shapes.box(1 - maxY, 1 - maxZ, minX, 1 - minY, 1 - minZ, maxX);
            if (face == AttachFace.CEILING)
                return Shapes.box(1 - maxZ, 1 - maxY, 1 - maxX, 1 - minZ, 1 - minY, 1 - minX);
        }
        
        if (facing == UP) {
            return Shapes.box(minX, 1 - maxZ, minY, maxX, 1 - minZ, maxY);
        }
        
        if (facing == DOWN) {
            return Shapes.box(minX, minZ, minY, maxX, maxZ, maxY);
        }
        
        return shape;
    }
    
}
