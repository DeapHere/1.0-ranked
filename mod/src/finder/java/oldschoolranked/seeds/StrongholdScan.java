package oldschoolranked.seeds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/** Testing: where 1.0 puts the three strongholds for each seed, with and without the no-mountains rule. */
public final class StrongholdScan {
    private StrongholdScan() {
    }

    static String[] place(FastBiomes b, long seed, List<Biome> valid) {
        Random r = new Random(seed);
        double a = r.nextDouble() * Math.PI * 2;
        String[] out = new String[3];
        for (int i = 0; i < 3; i++) {
            double d = (1.25 + r.nextDouble()) * 32;
            int x = (int) Math.round(Math.cos(a) * d), z = (int) Math.round(Math.sin(a) * d);
            BlockPos p = b.findBiome((x << 4) + 8, (z << 4) + 8, 112, new ArrayList<>(valid), r);
            boolean ok = p != null;
            if (ok) { x = p.x >> 4; z = p.z >> 4; }
            Biome at = b.getBiome((x << 4) + 8, (z << 4) + 8);
            out[i] = (x << 4) + 8 + "," + ((z << 4) + 8) + ":" + at.name + (ok ? "" : "(INVALID)");
            a += Math.PI * 2 / 3;
        }
        return out;
    }

    public static void run(String file) throws Exception {
        List<Biome> vanilla = Arrays.asList(Biome.DESERT, Biome.FOREST, Biome.EXTREME_HILLS, Biome.SWAMPLAND, Biome.TAIGA, Biome.ICE_PLAINS, Biome.ICE_MOUNTAINS);
        List<Biome> ours = Arrays.asList(Biome.DESERT, Biome.FOREST, Biome.SWAMPLAND, Biome.TAIGA, Biome.ICE_PLAINS);
        int seeds = 0, vMount = 0, oMount = 0, vOcean = 0, oOcean = 0;
        for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(file))) {
            line = line.trim();
            if (line.isEmpty()) continue;
            long seed = Long.parseLong(line.split("\\s")[0]);

            FastBiomes b = new FastBiomes(seed);
            String[] v = place(b, seed, vanilla);
            int[][] q = oldschoolranked.Strongholds.place(b, new Random(), seed, 3);
            String[] o = new String[3];
            for (int i = 0; i < 3; i++) o[i] = (q[i][0] * 16 + 8) + "," + (q[i][1] * 16 + 8) + ":" + b.getBiome(q[i][0] * 16 + 8, q[i][1] * 16 + 8).name + (oldschoolranked.Strongholds.nearMountains(b, q[i][0], q[i][1]) ? "(nearHills)" : "");
            seeds++;
            for (String s : v) { if (s.contains("Hills") || s.contains("Mountains")) vMount++; if (s.contains("Ocean")) vOcean++; }
            for (String s : o) { if (s.contains("Hills") || s.contains("Mountains")) oMount++; if (s.contains("Ocean")) oOcean++; }
            System.out.println("[scan] " + seed + " vanilla " + String.join(" ", v) + " | ours " + String.join(" ", o));
        }
        System.out.println("[scan] " + seeds + " seeds: mountain strongholds vanilla " + vMount + " ours " + oMount + ", ocean vanilla " + vOcean + " ours " + oOcean);
    }
}
