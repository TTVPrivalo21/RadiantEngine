package dev.privalo.radiantengine.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Distributes several spawn points inside a radius. Points follow a golden-angle (sunflower) spiral, so they are evenly
 * spread, never coincide and the same input always gives the same layout. Optionally each point is moved to the
 * nearest safe ground (solid block below, two free blocks for the body, no lava) close to the center height.
 */
public final class SpawnPattern {

    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private SpawnPattern() {
    }

    /**
     * @param center     center of the pattern (its yaw/pitch are copied to every point)
     * @param count      number of points (at least 1)
     * @param radius     radius in blocks
     * @param minSpacing minimum distance between neighbours; the radius grows when it is too small for the count
     * @param safeGround move every point to safe ground near the center height
     * @return {@code count} locations; with {@code count == 1} the exact center (no ground adjustment)
     */
    public static List<Location> spread(Location center, int count, double radius, double minSpacing, boolean safeGround) {
        int n = Math.max(1, count);
        List<Location> out = new ArrayList<>(n);
        if (n == 1) {
            // a single instance keeps the exact requested location (same behaviour as before)
            out.add(center.clone());
            return out;
        }
        // In a sunflower spiral the closest neighbours are ~ 0.85 * radius * sqrt(pi / n) apart.
        double needed = minSpacing <= 0 ? 0 : minSpacing / (0.85 * Math.sqrt(Math.PI / n));
        double r = Math.max(radius, needed);
        for (int i = 0; i < n; i++) {
            double distance = r * Math.sqrt((i + 0.5) / n);
            double angle = i * GOLDEN_ANGLE;
            Location point = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            out.add(safeGround ? ground(point) : point);
        }
        return out;
    }

    /** Nearest safe standing spot in the column of {@code loc} (searching up to 4 blocks up and 8 down). */
    public static Location ground(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return loc;
        }
        int baseY = loc.getBlockY();
        int min = Math.max(world.getMinHeight() + 1, baseY - 8);
        int max = Math.min(world.getMaxHeight() - 2, baseY + 4);
        for (int offset = 0; offset <= 8; offset++) {
            for (int sign : new int[]{-1, 1}) {
                if (offset == 0 && sign == 1) {
                    continue;
                }
                int y = baseY + offset * sign;
                if (y < min || y > max) {
                    continue;
                }
                if (safe(world, loc.getBlockX(), y, loc.getBlockZ())) {
                    Location result = loc.clone();
                    result.setY(y);
                    return result;
                }
            }
        }
        return loc;
    }

    private static boolean safe(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block below = world.getBlockAt(x, y - 1, z);
        if (!feet.isPassable() || !head.isPassable() || below.isPassable()) {
            return false;
        }
        return !isHazard(feet.getType()) && !isHazard(head.getType()) && !isHazard(below.getType());
    }

    private static boolean isHazard(Material type) {
        return type == Material.LAVA || type == Material.FIRE || type == Material.SOUL_FIRE
                || type == Material.MAGMA_BLOCK || type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE
                || type == Material.POWDER_SNOW || type == Material.CACTUS || type == Material.SWEET_BERRY_BUSH;
    }
}
