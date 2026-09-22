package rearth.belts.util;

import rearth.belts.blocks.ChuteBlockEntity;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class SplineUtil {

    private SplineUtil() {
    }
    
    public static Vec3 getPositionOnSpline(ChuteBlockEntity.BeltData data, double t) {
        return getPositionOnSpline(data.allPoints(), data.totalLength(), data.segmentLengths(), t);
    }
    
    public static Vec3 getPositionOnSpline(List<Pair<Vec3, Vec3>> allPoints, double totalLength, double[] segmentLengths, double t) {
        t = Math.clamp(t, 0, 1);
        
        var targetLength = totalLength * t;
        var traversedLength = 0d;
        
        // traverse segments, if traversed dist matches segment, get the final point along it
        for (int i = 0; i < allPoints.size() - 1; i++) {
            var segmentLength = segmentLengths[i];
            
            if (targetLength < traversedLength + segmentLength) {
                var from = allPoints.get(i);
                var to = allPoints.get(i + 1);
                var offset = targetLength - traversedLength;
                var delta = offset / segmentLength;
                
                var mappedT = remapProgress(delta);
                
                return getPointOnHermiteSpline(from.getFirst(), from.getSecond().scale(segmentLength * 1.5f), to.getFirst(), to.getSecond().scale(segmentLength * 1.5F), mappedT);
            } else {
                traversedLength += segmentLength;
            }
            
        }
        
        return allPoints.getLast().getFirst();
    }
    
    private static double remapProgress(double x) {
        return 0.4791667 * x + 1.5625 * x * x - 1.041667 * x * x * x;
    }
    
    /**
     * Calculates a point on a cubic Hermite spline.
     *
     * @param pointA   The starting point of the spline (P0).
     * @param tangentA The tangent vector (derivative) at pointA (M0). The curve will start
     *                 moving in this direction with a "velocity" given by its magnitude.
     * @param pointB   The ending point of the spline (P1).
     * @param tangentB The tangent vector (derivative) at pointB (M1). The curve will arrive
     *                 at pointB with this tangent.
     * @param t        The interpolation parameter, ranging from 0.0 (returns pointA) to 1.0 (returns pointB).
     *                 Values outside this range will be clamped.
     * @return A Vec3 representing the point on the Hermite spline at parameter t.
     */
    public static Vec3 getPointOnHermiteSpline(Vec3 pointA, Vec3 tangentA, Vec3 pointB, Vec3 tangentB, double t) {
        // Clamp t to the range [0, 1]
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;
        
        double t2 = t * t;
        double t3 = t2 * t;
        
        // Hermite basis functions
        double h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
        double h10 = t3 - 2.0 * t2 + t;
        double h01 = -2.0 * t3 + 3.0 * t2;
        double h11 = t3 - t2;
        
        // Calculate the point on the spline
        // H(t) = h00(t)*P0 + h10(t)*M0 + h01(t)*P1 + h11(t)*M1
        return new Vec3(
          h00 * pointA.x + h10 * tangentA.x + h01 * pointB.x + h11 * tangentB.x,
          h00 * pointA.y + h10 * tangentA.y + h01 * pointB.y + h11 * tangentB.y,
          h00 * pointA.z + h10 * tangentA.z + h01 * pointB.z + h11 * tangentB.z
        );
    }
    
}
