package rearth.belts.model;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A belt's curve through its ends and supports, and the bounds each span of it keeps
 * (PlanetaryFactory ADR-0078). A span is the stretch between two consecutive ends or supports.
 * The curve is upstream's: a Hermite span whose end tangents are 1.5 times its measured length.
 */
public final class BeltPath {

    public static final int MAX_REACH = 32;
    public static final double MIN_RADIUS = 1;
    public static final double MAX_SLOPE = Math.tan(Math.toRadians(35));

    private static final double TANGENT_SCALE = 1.5;
    private static final int SAMPLES = 512;

    /**
     * A block a belt ends at or passes through, facing along X or Z. An end faces the way a loader
     * there would: along the belt at its start, back up it at its end. A loader's curve runs through
     * the block to its face on the inventory's side; a support's ends at its centre.
     */
    public record Anchor(int x, int y, int z, int facingX, int facingZ, boolean support) {

        public Anchor(int x, int y, int z, int facingX, int facingZ) {
            this(x, y, z, facingX, facingZ, false);
        }

        public static Anchor support(int x, int y, int z, int facingX, int facingZ) {
            return new Anchor(x, y, z, facingX, facingZ, true);
        }
    }

    /** A point of the curve, with the unit horizontal tangent the curve has there. */
    public record Node(double x, double y, double z, double tangentX, double tangentZ) {
    }

    public enum Bound {
        SPAN_TOO_LONG, TURNS_WHILE_CLIMBING, TOO_STEEP, TURN_TOO_TIGHT;

        public String messageKey() {
            return "message.belts." + name().toLowerCase(Locale.ROOT);
        }
    }

    public record Refusal(Bound bound, int span) {
    }

    private final List<Anchor> anchors;
    private final List<Node> nodes;
    private final double[] spanLengths;
    private final double length;

    private BeltPath(List<Anchor> anchors, List<Node> nodes) {
        this.anchors = anchors;
        this.nodes = nodes;
        spanLengths = new double[nodes.size() - 1];
        var total = 0d;
        for (int i = 0; i < spanLengths.length; i++) {
            spanLengths[i] = measure(nodes.get(i), nodes.get(i + 1));
            total += spanLengths[i];
        }
        length = total;
    }

    /**
     * The path from the end at {@code start} through {@code supports} to the end at {@code end}.
     * With no end, the path stops at its last support.
     */
    public static BeltPath of(Anchor start, List<Anchor> supports, @Nullable Anchor end) {
        var anchors = new ArrayList<Anchor>();
        var nodes = new ArrayList<Node>();
        // A loader's curve leaves from, and arrives at, its face on the belt's side.
        anchors.add(start);
        nodes.add(face(start, start.facingX, start.facingZ));

        // Upstream's rule for which way a support faces along the belt, kept to its float constants.
        var fromX = nodes.getFirst().x + start.facingX * 0.3f;
        var fromZ = nodes.getFirst().z + start.facingZ * 0.3f;
        var fromY = nodes.getFirst().y;
        for (var support : supports) {
            double x = support.x + 0.5, y = support.y + 0.5, z = support.z + 0.5;
            var ahead = distance(fromX, fromY, fromZ, x + support.facingX, y, z + support.facingZ);
            var behind = distance(fromX, fromY, fromZ, x - support.facingX, y, z - support.facingZ);
            var sign = ahead > behind ? 1 : -1;
            anchors.add(support);
            nodes.add(new Node(x, y, z, sign * support.facingX, sign * support.facingZ));
            fromX = x - sign * support.facingX * 0.3f;
            fromY = y;
            fromZ = z - sign * support.facingZ * 0.3f;
        }

        if (end != null) {
            anchors.add(end);
            nodes.add(face(end, -end.facingX, -end.facingZ));
        }
        return new BeltPath(List.copyOf(anchors), List.copyOf(nodes));
    }

    private static Node face(Anchor loader, int tangentX, int tangentZ) {
        if (loader.support) return new Node(loader.x + 0.5, loader.y + 0.5, loader.z + 0.5, tangentX, tangentZ);
        return new Node(loader.x + 0.5 - 0.5 * loader.facingX, loader.y + 0.5, loader.z + 0.5 - 0.5 * loader.facingZ,
          tangentX, tangentZ);
    }

    public List<Node> nodes() {
        return nodes;
    }

    public double[] spanLengths() {
        return spanLengths.clone();
    }

    public double length() {
        return length;
    }

    /** The first bound a span breaks, in span order, or empty when every span keeps them all. */
    public Optional<Refusal> refusal() {
        for (int span = 0; span < spanLengths.length; span++) {
            var bound = broken(span);
            if (bound != null) return Optional.of(new Refusal(bound, span));
        }
        return Optional.empty();
    }

    private @Nullable Bound broken(int span) {
        var from = anchors.get(span);
        var to = anchors.get(span + 1);
        if (Math.max(Math.abs(to.x - from.x), Math.abs(to.z - from.z)) > MAX_REACH) return Bound.SPAN_TOO_LONG;
        if (from.y != to.y && !climbsStraight(nodes.get(span), nodes.get(span + 1))) return Bound.TURNS_WHILE_CLIMBING;
        if (steepestSlope(span) > MAX_SLOPE) return Bound.TOO_STEEP;
        if (tightestRadius(span) < MIN_RADIUS) return Bound.TURN_TOO_TIGHT;
        return null;
    }

    private static boolean climbsStraight(Node from, Node to) {
        if (from.tangentX != to.tangentX || from.tangentZ != to.tangentZ) return false;
        double dx = to.x - from.x, dz = to.z - from.z;
        return dx * from.tangentZ - dz * from.tangentX == 0 && dx * from.tangentX + dz * from.tangentZ > 0;
    }

    /** Rise over horizontal run at the span's steepest point; infinite where the curve stands or doubles back. */
    public double steepestSlope(int span) {
        var steepest = 0d;
        for (int i = 0; i <= SAMPLES; i++) {
            var velocity = derivative(span, (double) i / SAMPLES, 1);
            var run = Math.hypot(velocity[0], velocity[2]);
            if (velocity[1] == 0) continue;
            if (run == 0 || forward(span, velocity) <= 0) return Double.POSITIVE_INFINITY;
            steepest = Math.max(steepest, Math.abs(velocity[1]) / run);
        }
        return steepest;
    }

    /** The smallest radius of curvature along the span, in blocks. */
    public double tightestRadius(int span) {
        var tightest = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= SAMPLES; i++) {
            var t = (double) i / SAMPLES;
            var v = derivative(span, t, 1);
            var a = derivative(span, t, 2);
            var speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            if (speed == 0) return 0;
            var cx = v[1] * a[2] - v[2] * a[1];
            var cy = v[2] * a[0] - v[0] * a[2];
            var cz = v[0] * a[1] - v[1] * a[0];
            var bend = Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (bend > 0) tightest = Math.min(tightest, speed * speed * speed / bend);
        }
        return tightest;
    }

    private double forward(int span, double[] velocity) {
        var from = nodes.get(span);
        return velocity[0] * from.tangentX + velocity[2] * from.tangentZ;
    }

    private double[] derivative(int span, double t, int order) {
        var from = nodes.get(span);
        var to = nodes.get(span + 1);
        var scale = spanLengths[span] * TANGENT_SCALE;
        double p0, m0, p1, m1;
        if (order == 1) {
            p0 = 6 * t * t - 6 * t;
            m0 = 3 * t * t - 4 * t + 1;
            p1 = -6 * t * t + 6 * t;
            m1 = 3 * t * t - 2 * t;
        } else {
            p0 = 12 * t - 6;
            m0 = 6 * t - 4;
            p1 = -12 * t + 6;
            m1 = 6 * t - 2;
        }
        return new double[] {
          p0 * from.x + m0 * from.tangentX * scale + p1 * to.x + m1 * to.tangentX * scale,
          p0 * from.y + p1 * to.y,
          p0 * from.z + m0 * from.tangentZ * scale + p1 * to.z + m1 * to.tangentZ * scale
        };
    }

    // Upstream's approximation, which the belt's cost, capacity and drawn curve all read.
    private static double measure(Node from, Node to) {
        var approx = distance(from.x, from.y, from.z, to.x, to.y, to.z);
        var dtx = from.tangentX - to.tangentX;
        var dtz = from.tangentZ - to.tangentZ;
        if (dtx * dtx + dtz * dtz < 0.1) approx += 1;
        var a = hermite(from, to, approx, 0.33f);
        var b = hermite(from, to, approx, 0.66f);
        return distance(from.x, from.y, from.z, a[0], a[1], a[2])
                 + distance(a[0], a[1], a[2], b[0], b[1], b[2])
                 + distance(b[0], b[1], b[2], to.x, to.y, to.z);
    }

    private static double[] hermite(Node from, Node to, double scale, double t) {
        double t2 = t * t, t3 = t2 * t;
        double h00 = 2 * t3 - 3 * t2 + 1, h10 = t3 - 2 * t2 + t, h01 = -2 * t3 + 3 * t2, h11 = t3 - t2;
        return new double[] {
          h00 * from.x + h10 * from.tangentX * scale + h01 * to.x + h11 * to.tangentX * scale,
          h00 * from.y + h01 * to.y,
          h00 * from.z + h10 * from.tangentZ * scale + h01 * to.z + h11 * to.tangentZ * scale
        };
    }

    private static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
