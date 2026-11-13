package oldschoolranked;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.mob.monster.EndermanEntity;

/**
 * Match-world rules that take luck out of the race without changing vanilla odds much:
 *
 * - Drops: ender pearls and blaze rods come from their own random sequence, seeded from the match seed, so a player's
 *   1st, 2nd, 3rd... enderman (or blaze) drops exactly what their opponent's do. The odds are still vanilla (0 or 1).
 * - Endermen: each death reset at night tops you up to 3 endermen on open ground 24 to 87 blocks around you (any already
 *   there count), so dying over and over can't farm them. Vanilla spawning is untouched on top of that.
 */
public final class Standard {
    public static int endermenPerReset = 3;
    public static int endermenFloor = 0; // no all-night minimum: only the guaranteed ones per death reset, plus whatever vanilla spawns
    private static final int FLOOR_RADIUS = 87;

    /**
     * Match worlds (and the seed finder's) have no caves or ravines in chunks with any desert in them, so digging down
     * in the desert never drops you into a cave. Set while a match world exists.
     */
    public static volatile boolean noDesertCaves;
    private static Random pearls, rods, spots;
    /** The dragon's choices (charge you or fly to a point, and which point), the same sequence for both players. */
    private static Random dragon;
    private static int pendingSpawns, spawnCooldown, floorCheck;
    private static boolean wasDead;

    private Standard() {
    }

    /** New match world. */
    public static void reset(long seed) {
        pearls = new Random(seed ^ 0x5EA4L);
        rods = new Random(seed ^ 0xB1A2EL);
        spots = new Random(seed ^ 0xE4DE2L);
        dragon = new Random(seed ^ 0xD4A60L);
        pendingSpawns = 0;
        spawnCooldown = 0;
        floorCheck = 0;
        wasDead = false;
    }

    /** Pearls from one enderman: 0 to 1 + looting, like vanilla, from the shared sequence. */
    public static int pearls(int vanilla, int looting) {
        return Ranked.inMatchWorld() && pearls != null ? pearls.nextInt(2 + looting) : vanilla;
    }

    /** Rods from one blaze, same idea. */
    public static int rods(int vanilla, int looting) {
        return Ranked.inMatchWorld() && rods != null ? rods.nextInt(2 + looting) : vanilla;
    }

    private static boolean night(Minecraft mc) {
        return mc.world.ambientDarkness >= 8;
    }

    /** Each tick while racing. */
    public static void tick(Minecraft mc) {
        if (spots == null || mc.player == null || mc.world == null || mc.world.dimension.id != 0) return;
        boolean dead = mc.player.getHealth() <= 0;
        if (wasDead && !dead && night(mc)) {
            // a top-up, not a stack: dying again before they've come doesn't add more
            pendingSpawns = endermenPerReset;
            spawnCooldown = 20; // a second after respawning
        }
        wasDead = dead;
        if (dead) return;
        if (!night(mc)) {
            pendingSpawns = 0;
            return;
        }
        if (spawnCooldown > 0) {
            spawnCooldown--;
        } else if (pendingSpawns > 0) {
            // only up to 3 endermen around you (vanilla ones count), so dying over and over can't farm them
            if (nearby(mc) >= endermenPerReset) pendingSpawns = 0;
            else if (spawn(mc)) pendingSpawns--;
            spawnCooldown = 20 + spots.nextInt(60); // trickle in over several seconds, not all at once
        }
        if (++floorCheck >= 100) {
            floorCheck = 0;
            if (nearby(mc) < endermenFloor) spawn(mc);
        }
    }

    private static int nearby(Minecraft mc) {
        int n = 0;
        for (Object o : mc.world.entities) {
            if (!(o instanceof EndermanEntity) || ((EndermanEntity) o).removed) continue;
            EndermanEntity e = (EndermanEntity) o;
            double dx = e.x - mc.player.x, dz = e.z - mc.player.z;
            if (dx * dx + dz * dz <= FLOOR_RADIUS * FLOOR_RADIUS) n++;
        }
        return n;
    }

    /** One enderman on open, dry ground 24 to 87 blocks away, like a natural surface spawn. */
    private static boolean spawn(Minecraft mc) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = spots.nextDouble() * Math.PI * 2;
            // spread evenly over the area, like vanilla's chunk-based spawning (so more far ones than close ones)
            double dist = Math.sqrt(24 * 24 + spots.nextDouble() * (87 * 87 - 24 * 24));
            int x = (int) Math.floor(mc.player.x + Math.cos(angle) * dist);
            int z = (int) Math.floor(mc.player.z + Math.sin(angle) * dist);
            int y = mc.world.getHeight(x, z);
            if (y <= 1 || y > 124) continue;
            Material below = mc.world.getMaterial(x, y - 1, z);
            if (!below.isSolid() || below.isLiquid()) continue;
            if (!mc.world.isAir(x, y, z) || !mc.world.isAir(x, y + 1, z) || !mc.world.isAir(x, y + 2, z)) continue;
            if (mc.world.getRawBrightness(x, y, z) > 7) continue; // dark enough for a vanilla spawn (no torches)
            EndermanEntity e = new EndermanEntity(mc.world);
            e.setPositionAndAngles(x + 0.5, y, z + 0.5, spots.nextFloat() * 360.0F, 0.0F);
            if (!e.canSpawn()) continue; // vanilla's own spawn check: room to stand, no collisions
            mc.world.addEntity(e);
            return true;
        }
        return false;
    }

    /** The dragon's decision Random in a match world, otherwise its own. */
    public static Random dragon(Random own) {
        return Ranked.inMatchWorld() && dragon != null ? dragon : own;
    }
}
