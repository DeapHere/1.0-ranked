package oldschoolranked.finder;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SurvivalInteractionManager;
import net.minecraft.world.WorldSettings;
import oldschoolranked.Ranked;
import oldschoolranked.seeds.FinderSettings;
import oldschoolranked.seeds.FinderStatus;
import oldschoolranked.seeds.ParallelFinder;
import oldschoolranked.seeds.SeedCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Seed finder mod: makes 1.0 worlds from random seeds and keeps the ones with a good spawn in ranked-seeds.txt (in the
 * instance folder), one line per seed: the seed, then what was found. Install it in a spare instance only.
 *
 * A good spawn: plains with desert close by, a fortress within 8 chunks of spawn / 8 in the nether, little water, a
 * surface lava pool, trees, sheep and food animals (pigs, cows, chickens). Everything checked comes from world
 * generation, so it's the same for every player on that seed. The rules are in SeedCheck.
 *
 * With Queue Wall installed, worlds are checked on several threads at once (ParallelFinder). Without it, one at a
 * time on the game thread.
 */
@Mixin(Minecraft.class)
public abstract class SeedFinderMixin {
    // the standalone finder mod is always on; output goes next to the game's saves
    private static final String OUT = System.getProperty("ranked.finder",
            new java.io.File(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toFile(), "ranked-seeds.txt").getAbsolutePath());
    private static final boolean PARALLEL = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("queuewall")
            && !Boolean.getBoolean("ranked.finderSerial");
    private static int fT, tried, found, waitTicks;

    private static long seed;
    private static final Random RANDOM = new Random();

    // terrain is only generated where the checks read it, so failing seeds cost almost nothing
    @Inject(method = "prepareWorld", at = @At("HEAD"), cancellable = true)
    private void ranked$skipPrepare(String message, CallbackInfo ci) {
        if (OUT != null && !PARALLEL) ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ranked$find(CallbackInfo ci) {
        if (OUT == null) return;
        Minecraft mc = (Minecraft) (Object) this;
        if (++fT < 40) return;
        if (fT == 40 && System.getProperty("ranked.strongholdScan") != null) {
            try { oldschoolranked.seeds.StrongholdScan.run(System.getProperty("ranked.strongholdScan")); } catch (Exception e) { e.printStackTrace(); }
            System.exit(0);
            return;
        }
        if (fT == 40) {
            FinderSettings.load();
            // build worlds the way match worlds are built (no caves in desert chunks)
            oldschoolranked.Standard.noDesertCaves = true;
            if (PARALLEL) ParallelFinder.start(mc, OUT);
        }
        if (PARALLEL) {
            if (fT % 20 == 0) org.lwjgl.opengl.Display.setTitle(FinderStatus.status);
            return;
        }
        if (mc.world == null) {
            String fixed = System.getProperty("ranked.finderSeeds", "").trim();
            if (!fixed.isEmpty()) {
                // testing: check these seeds, in order, then stop
                String[] list = fixed.split(",");
                if (tried >= list.length) return;
                seed = Long.parseLong(list[tried++].trim());
            } else {
                do {
                    seed = RANDOM.nextLong();
                    tried++;
                } while (!SeedCheck.worthMaking(seed));
            }
            FinderStatus.status = "Seed finder: " + tried + " checked, " + found + " good";
            org.lwjgl.opengl.Display.setTitle(FinderStatus.status);

            mc.getWorldStorageSource().delete("seed-finder");
            mc.interactionManager = new SurvivalInteractionManager(mc);
            Ranked.resetBiomeArrayPool();
            System.out.println("[finder] building world for " + seed + " (" + tried + " checked)");
            mc.startGame("seed-finder", "Seed finder", new WorldSettings(seed, 0, true, false));
            mc.openScreen(null);
            waitTicks = 0;
            return;
        }
        if (++waitTicks < 3) return;
        int sx = (int) Math.floor(mc.player.x), sz = (int) Math.floor(mc.player.z);
        String verdict = SeedCheck.check(mc.world, sx, sz);
        if (verdict.startsWith("OK")) verdict = verifyFortress(mc, verdict, sx, sz);
        if (verdict.startsWith("OK")) {
            found++;
            try (PrintWriter w = new PrintWriter(new FileWriter(OUT, true))) {
                w.println(seed + "  # " + verdict.substring(3));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("[finder] " + tried + " tried, " + found + " found: " + seed + " " + verdict);
        FinderStatus.status = "Seed finder: " + tried + " checked, " + found + " good";
        if (verdict.startsWith("OK")) FinderStatus.lastGood = seed + "  " + verdict.substring(3);
        org.lwjgl.opengl.Display.setTitle(FinderStatus.status);
        mc.setWorld(null);
        mc.openScreen(new net.minecraft.client.gui.screen.TitleScreen());
    }

    /** Checks the predicted fortress against the real game: nether bricks must be in the predicted start chunk. */
    private static String verifyFortress(Minecraft mc, String verdict, int sx, int sz) {
        double[] f = SeedCheck.fortress(mc.world.getSeed(), (sx >> 3) >> 4, (sz >> 3) >> 4);
        int cx = (int) f[1], cz = (int) f[2];
        mc.changeDimension(-1);
        for (int x = cx * 16; x < cx * 16 + 16; x++) {
            for (int z = cz * 16; z < cz * 16 + 16; z++) {
                for (int y = 30; y < 100; y++) {
                    if (mc.world.getBlock(x, y, z) == 112) return verdict;
                }
            }
        }
        return "no: fortress not where predicted (" + cx + ", " + cz + ")";
    }
}
