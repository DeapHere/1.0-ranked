package oldschoolranked.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.TitleScreen;
import oldschoolranked.Ranked;
import oldschoolranked.RankedScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * -Dranked.selftest=Name: signs in as Name to -Dranked.server, queues, plays the match it gets (standing still), and
 * logs the spawn so two clients can be compared. -Dranked.forfeitAfter=N forfeits N ticks after the start.
 */
@Mixin(Minecraft.class)
public abstract class SelfTestMixin {
    private static final String NAME = System.getProperty("ranked.selftest");
    private static final int FORFEIT_AFTER = Integer.getInteger("ranked.forfeitAfter", 0);
    private static int t, runTicks, doneTicks, cdTicks;
    private static boolean drawAccepted;
    private static int drawSeen, seedSeen;
    private static boolean queued, logged, forfeited;
    private static int[] oceanRoom;
    private static int privateSeedAt = -1;

    private static int countItems(Minecraft mc, int id) {
        int n = 0;
        for (Object o : mc.world.entities) {
            if (o instanceof net.minecraft.entity.ItemEntity && !((net.minecraft.entity.ItemEntity) o).removed
                    && ((net.minecraft.entity.ItemEntity) o).item.id == id) n += ((net.minecraft.entity.ItemEntity) o).item.size;
        }
        return n;
    }

    private static String kill(Minecraft mc, net.minecraft.entity.mob.MobEntity e, int item) {
        e.setPositionAndAngles(mc.player.x + 2, mc.player.y, mc.player.z, 0, 0);
        mc.world.addEntity(e);
        int before = countItems(mc, item);
        e.takeDamage(net.minecraft.entity.damage.DamageSource.player(mc.player), 1000);
        return String.valueOf(countItems(mc, item) - before);
    }

    private static void dropTest(Minecraft mc) {
        if (runTicks == 60) {
            StringBuilder pearls = new StringBuilder(), rods = new StringBuilder(), feathers = new StringBuilder();
            for (int i = 0; i < 16; i++) pearls.append(kill(mc, new net.minecraft.entity.mob.monster.EndermanEntity(mc.world), 368));
            for (int i = 0; i < 16; i++) rods.append(kill(mc, new net.minecraft.entity.mob.monster.BlazeEntity(mc.world), 369));
            for (int i = 0; i < 30; i++) feathers.append(kill(mc, new net.minecraft.entity.mob.animal.ChickenEntity(mc.world), 288));
            StringBuilder arrows = new StringBuilder(), pork = new StringBuilder();
            for (int i = 0; i < 30; i++) arrows.append(kill(mc, new net.minecraft.entity.mob.monster.SkeletonEntity(mc.world), 262));
            for (int i = 0; i < 30; i++) pork.append(kill(mc, new net.minecraft.entity.mob.animal.PigEntity(mc.world), 319));
            StringBuilder string = new StringBuilder();
            for (int i = 0; i < 30; i++) string.append(kill(mc, new net.minecraft.entity.mob.monster.SpiderEntity(mc.world), 287));
            System.out.println("[selftest] pearls " + pearls + " rods " + rods + " feathers " + feathers + " arrows " + arrows + " pork " + pork + " string " + string);
            StringBuilder dragon = new StringBuilder();
            net.minecraft.entity.mob.monster.boss.EnderDragonEntity d = new net.minecraft.entity.mob.monster.boss.EnderDragonEntity(mc.world);
            for (int i = 0; i < 12; i++) {
                ((DragonAccess) d).ranked$chooseTarget();
                net.minecraft.entity.Entity tg = ((DragonAccess) d).ranked$target();
                dragon.append(tg != null ? "charge " : "(" + (int) d.targetX + "," + (int) d.targetZ + ") ");
            }
            System.out.println("[selftest] dragon " + dragon);
            mc.world.setTime(14500);
        }
        if (runTicks == 100) mc.options.debugEnabled = true; // F3 must be forced off in a match
        if (runTicks == 110) System.out.println("[selftest] F3 after pressing: " + mc.options.debugEnabled + ", hud " + oldschoolranked.Ranked.hud());
        if (runTicks == 700) {
            int n = 0;
            for (Object o : mc.world.entities) {
                if (o instanceof net.minecraft.entity.mob.monster.EndermanEntity && !((net.minecraft.entity.Entity) o).removed) {
                    net.minecraft.entity.Entity e = (net.minecraft.entity.Entity) o;
                    double d = Math.sqrt((e.x - mc.player.x) * (e.x - mc.player.x) + (e.z - mc.player.z) * (e.z - mc.player.z));
                    if (d <= 87) n++;
                }
            }
            System.out.println("[selftest] night: darkness " + mc.world.ambientDarkness + ", endermen within 87: " + n);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ranked$selfTest(CallbackInfo ci) {
        if (NAME == null) return;
        Minecraft mc = (Minecraft) (Object) this;
        t++;
        if (t == 40) {
            mc.session.username = NAME;
            Ranked.init();
            Ranked.cfg.server = System.getProperty("ranked.server");
            mc.openScreen(new RankedScreen(new TitleScreen()));
            System.out.println("[selftest] signing in as " + NAME);
        }
        if (!queued && Ranked.signedIn()) {
            queued = true;
            System.out.println("[selftest] signed in, elo " + Ranked.elo + ", blocked " + Ranked.blocked + ", mods " + net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("endpractice"));
            if (Boolean.getBoolean("ranked.lobbytest")) {
                // stay in the lobby
            } else if (Boolean.getBoolean("ranked.lbtest")) {
                mc.openScreen(new oldschoolranked.LeaderboardScreen(mc.screen));
            } else if (System.getProperty("ranked.privateSeed") != null) {
                mc.openScreen(new oldschoolranked.PrivateRoomScreen(mc.screen));
                privateSeedAt = t + 80;
            } else if (Boolean.getBoolean("ranked.private")) {
                Ranked.privateRoom();
            } else if (!Ranked.blocked) {
                Ranked.queue();
            } else if (NAME.endsWith("A")) {
                Ranked.queue(); // should be refused
                Ranked.createRoom();
            }
        }
        java.io.File codeFile = new java.io.File(System.getProperty("ranked.codeFile", "/tmp/ranked-room-code"));
        if (Ranked.blocked && NAME.endsWith("A") && Ranked.roomCode != null && !codeFile.exists()) {
            try { java.nio.file.Files.write(codeFile.toPath(), Ranked.roomCode.getBytes()); } catch (Exception e) { throw new RuntimeException(e); }
            System.out.println("[selftest] room " + Ranked.roomCode + ", queue error: " + Ranked.lastError);
        }
        if (Ranked.blocked && NAME.endsWith("B") && Ranked.state == Ranked.State.IDLE && codeFile.exists() && t % 40 == 0) {
            try {
                String code = new String(java.nio.file.Files.readAllBytes(codeFile.toPath())).trim();
                System.out.println("[selftest] joining room " + code);
                Ranked.joinRoom(code);
                codeFile.delete();
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        if (t == 30 && mc.screen != null) {
            StringBuilder sb = new StringBuilder();
            for (Object b : ((oldschoolranked.selftest.ScreenAccess) mc.screen).ranked$buttons()) sb.append(((net.minecraft.client.gui.widget.ButtonWidget) b).id).append(' ');
            System.out.println("[selftest] title buttons: " + sb);
        }
        if (t == privateSeedAt - 40 && mc.screen instanceof oldschoolranked.PrivateRoomScreen) ((oldschoolranked.selftest.ScreenAccess) mc.screen).ranked$buttons();
        if (t == privateSeedAt) Ranked.privateRoom(System.getProperty("ranked.privateSeed"));
        if (System.getProperty("ranked.normalcmds") != null) {
            if (t == 60) mc.startGame("selftest-normal", "Normal", new net.minecraft.world.WorldSettings(1L, 0, true, false));
            String[] cmds = System.getProperty("ranked.normalcmds").replace("_", " ").split(";");
            int i = (t - 120) / 40;
            if (t >= 120 && (t - 120) % 40 == 0 && i < cmds.length && mc.player != null) mc.player.sendChat(cmds[i]);
        }
        if (Boolean.getBoolean("ranked.difftest") && t == 5) mc.options.difficulty = 0; // start on Peaceful
        if (Boolean.getBoolean("ranked.lbtest") && t == 120) mc.openScreen(new oldschoolranked.LeaderboardScreen(mc.screen, Integer.getInteger("ranked.lbview", 1)));
        if (Boolean.getBoolean("ranked.chattest") && Ranked.state == Ranked.State.COUNTDOWN) {
            cdTicks++;
            if (NAME.endsWith("A") && cdTicks == 10) Ranked.sendChat("gl hf");
            if (NAME.endsWith("B") && cdTicks == 30) Ranked.sendChat("you too, \u00a7cno colours");
            if (cdTicks == 60) System.out.println("[selftest] load-in screen " + mc.screen + ", chat " + Ranked.matchChat);
        }
        if (Ranked.state == Ranked.State.RUNNING) {
            runTicks++;
            if (Boolean.getBoolean("ranked.setuptest")) {
                int[] l = oldschoolranked.MatchSetup.lastLava, c = oldschoolranked.MatchSetup.lastChickens;
                if (runTicks == 20) {
                    int chickens = 0;
                    for (Object e : mc.world.entities) if (e instanceof net.minecraft.entity.mob.animal.ChickenEntity && c != null && Math.abs(((net.minecraft.entity.Entity) e).x - c[0]) < 8 && Math.abs(((net.minecraft.entity.Entity) e).z - c[1]) < 8) chickens++;
                    System.out.println("[selftest] setup: spawn " + (int) mc.player.x + " " + (int) mc.player.z + ", lava " + (l == null ? "none" : l[0] + " " + l[1]) + ", chickens near spot " + chickens);
                }
                if (runTicks == 30 && l != null) {
                    mc.player.setPositionAndAngles(l[0] + 0.5, mc.world.getHeight(l[0], l[1]) + 14, l[1] - 10 + 0.5, 0, 50);
                }
            }
            if (Boolean.getBoolean("ranked.setuptest")) {
                int[] gv = oldschoolranked.MatchSetup.lastGravelAt;
                if (runTicks == 60 && gv != null) {
                    // stand in the cave next to the first gravel block and look at it
                    int best = -1, bx = 0, bz = 0;
                    for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                        if ((dx != 0 || dz != 0) && mc.world.getBlock(gv[0] + dx, gv[1] + 1, gv[2] + dz) == 0 && mc.world.getBlock(gv[0] + dx, gv[1] + 2, gv[2] + dz) == 0) {
                            int d = dx * dx + dz * dz;
                            if (d >= 4 && (best < 0 || d < best)) { best = d; bx = gv[0] + dx; bz = gv[2] + dz; }
                        }
                    }
                    float yaw = (float) (Math.toDegrees(Math.atan2(gv[2] - bz, gv[0] - bx)) - 90);
                    mc.player.setPositionAndAngles(bx + 0.5, gv[1] + 1 + 1.62, bz + 0.5, yaw, 45);
                    System.out.println("[selftest] at gravel " + java.util.Arrays.toString(gv) + " from " + bx + " " + bz);
                }
                if (runTicks == 90) mc.openScreen(new oldschoolranked.ConfirmLeaveScreen(new net.minecraft.client.gui.screen.GameMenuScreen(), () -> System.out.println("[selftest] leave confirmed")));
                if (runTicks == 30) try {
                    java.lang.reflect.Field f = net.minecraft.entity.EnderEyeEntity.class.getDeclaredField("dropsItem");
                    f.setAccessible(true);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 12; i++) {
                        net.minecraft.entity.EnderEyeEntity e = new net.minecraft.entity.EnderEyeEntity(mc.world, mc.player.x, mc.player.y, mc.player.z);
                        e.setTarget(1000, 30, 1000);
                        sb.append(f.getBoolean(e) ? 'D' : 'X');
                    }
                    System.out.println("[selftest] eyes " + sb);
                } catch (Exception ex) { System.out.println("[selftest] eyes failed " + ex); }
                if (runTicks == 25) System.out.println("[selftest] gravel " + oldschoolranked.MatchSetup.lastGravel + " cave " + java.util.Arrays.toString(oldschoolranked.MatchSetup.lastCave));
            }
            if (Boolean.getBoolean("ranked.tkey")) {
                if (runTicks == 40) net.minecraft.client.options.KeyBinding.click(mc.options.chatKey.keyCode);
                if (runTicks == 50) System.out.println("[selftest] after T: screen " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
            }
            if (System.getProperty("ranked.devcmds") != null) {
                String[] cmds = System.getProperty("ranked.devcmds").replace("_", " ").split(";");
                int i = (runTicks - 30) / 20;
                if (runTicks >= 30 && (runTicks - 30) % 20 == 0 && i < cmds.length) mc.player.sendChat(cmds[i]);
            }
            if (System.getProperty("ranked.oceanAt") != null) {
                String[] at = System.getProperty("ranked.oceanAt").split(",");
                int ox = Integer.parseInt(at[0]), oz = Integer.parseInt(at[1]);
                if (runTicks == 30) {
                    net.minecraft.util.math.BlockPos e = mc.world.findNearestStructure("Stronghold", ox, 64, oz);
                    System.out.println("[selftest] eye near " + ox + " " + oz + ": " + (e == null ? "null" : e.x + " " + e.z));
                    for (int cx = (ox >> 4) - 8; cx <= (ox >> 4) + 8; cx++) for (int cz = (oz >> 4) - 8; cz <= (oz >> 4) + 8; cz++) mc.world.getChunkAt(cx, cz);
                    int fx = 0, fy = 0, fz = 0, n = 0;
                    for (int x = ox - 120; x <= ox + 120; x++) for (int z = oz - 120; z <= oz + 120; z++) for (int y = 1; y < 64; y++) {
                        if (mc.world.getBlock(x, y, z) == 120) { fx += x; fy += y; fz += z; n++; }
                    }
                    if (n > 0) {
                        fx /= n; fy /= n; fz /= n;
                        StringBuilder col = new StringBuilder();
                        for (int y = fy - 2; y < 70; y++) col.append(mc.world.getBlock(fx, y, fz)).append(' ');
                        System.out.println("[selftest] portal room at " + fx + " " + fy + " " + fz + " (" + n + " frames), biome " + mc.world.getBiomeSource().getBiome(fx, fz).name + ", column up: " + col);
                        oceanRoom = new int[] {fx, fy, fz};
                    } else System.out.println("[selftest] no portal room found near " + ox + " " + oz);
                }
                if (runTicks == 40 && oceanRoom != null) mc.player.setPositionAndAngles(oceanRoom[0] + 0.5, 46, oceanRoom[2] - 9 + 0.5, 0, 35);
            }
            if (Boolean.getBoolean("ranked.difftest")) {
                if (runTicks == 30) System.out.println("[selftest] difficulty at start: option " + mc.options.difficulty + ", world " + mc.world.difficulty);
                if (runTicks == 40) { mc.options.difficulty = 2; mc.options.set(net.minecraft.client.options.GameOptions.Option.DIFFICULTY, 1); System.out.println("[selftest] normal -> " + mc.options.difficulty); }
                if (runTicks == 50) { mc.options.set(net.minecraft.client.options.GameOptions.Option.DIFFICULTY, 1); System.out.println("[selftest] hard -> " + mc.options.difficulty); }
                if (runTicks == 60) System.out.println("[selftest] world difficulty now " + mc.world.difficulty);
            }
            if (Boolean.getBoolean("ranked.weathertest")) {
                if (runTicks == 30) { mc.world.getData().setRaining(true); mc.world.getData().setRainTime(100000); mc.world.getData().setThundering(true); }
                if (runTicks == 60) System.out.println("[selftest] weather: raining " + mc.world.isRaining() + ", thundering " + mc.world.isThundering() + ", data raining " + mc.world.getData().isRaining());
            }
            if (Boolean.getBoolean("ranked.chattest")) {
                if (NAME.endsWith("B") && runTicks == 50) mc.player.sendChat("typed in game");
                if (runTicks == 90) System.out.println("[selftest] chat in game " + Ranked.matchChat);
            }
            if (!logged && runTicks == 40) {
                logged = true;
                System.out.println("[selftest] running: seed " + mc.world.getSeed() + " spawn " + (int) Math.floor(mc.player.x) + " "
                        + (int) Math.floor(mc.player.y) + " " + (int) Math.floor(mc.player.z) + " worldTime " + mc.world.getTime() + " igt " + Ranked.ticks);
            }
            if (Boolean.getBoolean("ranked.droptest")) dropTest(mc);
            if (Boolean.getBoolean("ranked.eyetest") && runTicks == 60) {
                int px = (int) mc.player.x, pz = (int) mc.player.z;
                net.minecraft.util.math.BlockPos p0 = mc.world.findNearestStructure("Stronghold", px, 64, pz);
                System.out.println("[selftest] eye from spawn " + px + " " + pz + ": " + p0.x + " " + p0.z);
                // generate the area around another of the three (120 degrees round), then ask again from spawn
                double a = Math.atan2(p0.z, p0.x) + Math.PI * 2 / 3, r = Math.sqrt((double) p0.x * p0.x + (double) p0.z * p0.z);
                int qx = (int) (Math.cos(a) * r), qz = (int) (Math.sin(a) * r);
                net.minecraft.util.math.BlockPos far = mc.world.findNearestStructure("Stronghold", qx, 64, qz);
                System.out.println("[selftest] eye from far point " + qx + " " + qz + ": " + far.x + " " + far.z);
                for (int cx = (far.x >> 4) - 2; cx <= (far.x >> 4) + 2; cx++) for (int cz = (far.z >> 4) - 2; cz <= (far.z >> 4) + 2; cz++) mc.world.getChunkAt(cx, cz);
                net.minecraft.util.math.BlockPos p1 = mc.world.findNearestStructure("Stronghold", px, 64, pz);
                System.out.println("[selftest] eye from spawn again: " + p1.x + " " + p1.z + (Math.abs(p1.x - p0.x) < 128 && Math.abs(p1.z - p0.z) < 128 ? " (closest, good)" : " (WRONG: the far one)"));
            }
            if (Boolean.getBoolean("ranked.seedtest")) {
                if (NAME.endsWith("A") && runTicks == 60) {
                    System.out.println("[selftest] asking for a new seed, match " + Ranked.matchId + " seed " + mc.world.getSeed());
                    Ranked.newSeed();
                }
                if (NAME.endsWith("B") && Ranked.seedPending() && seedSeen == 0) {
                    seedSeen = runTicks;
                    System.out.println("[selftest] new seed asked, hud " + Ranked.hud());
                    mc.openScreen(new net.minecraft.client.gui.screen.GameMenuScreen());
                }
                if (NAME.endsWith("B") && seedSeen > 0 && runTicks == seedSeen + 60) {
                    System.out.println("[selftest] accepting new seed");
                    Ranked.newSeed();
                    mc.openScreen(null);
                }
                if (runTicks == 400) System.out.println("[selftest] later: match " + Ranked.matchId + " state " + Ranked.state + " seed " + mc.world.getSeed() + " timer " + Ranked.time(Ranked.elapsed()));
            }
            if (Boolean.getBoolean("ranked.drawtest")) {
                if (NAME.endsWith("A") && runTicks == 60) {
                    System.out.println("[selftest] resetting world, timer " + Ranked.time(Ranked.elapsed()));
                    Ranked.resetWorld();
                }
                if (NAME.endsWith("A") && runTicks == 100) System.out.println("[selftest] after reset: world " + (mc.world != null) + ", state " + Ranked.state + ", timer " + Ranked.time(Ranked.elapsed()) + ", spawn " + (int) mc.player.x + " " + (int) mc.player.z);
                if (NAME.endsWith("A") && runTicks == 140) {
                    System.out.println("[selftest] offering draw");
                    Ranked.draw();
                }
                if (NAME.endsWith("B") && Ranked.drawPending() && drawSeen == 0) {
                    drawSeen = runTicks;
                    System.out.println("[selftest] draw offered to me, hud " + Ranked.hud());
                    mc.openScreen(new net.minecraft.client.gui.screen.GameMenuScreen());
                }
                if (NAME.endsWith("B") && drawSeen > 0 && runTicks == drawSeen + 60 && !drawAccepted) {
                    drawAccepted = true;
                    System.out.println("[selftest] accepting");
                    Ranked.draw();
                    mc.openScreen(null);
                }
            }
            if (FORFEIT_AFTER > 0 && runTicks == FORFEIT_AFTER && !forfeited) {
                forfeited = true;
                System.out.println("[selftest] forfeiting");
                Ranked.forfeit();
            }
        }
        if (Ranked.state == Ranked.State.DONE || Ranked.state == Ranked.State.LEAVING) {
            if (++doneTicks == 60) {
                System.out.println("[selftest] result: " + Ranked.resultLines + " state " + Ranked.state);
                System.out.println("[selftest] done");
            }
            if (doneTicks == 140 && mc.screen instanceof oldschoolranked.ResultScreen) {
                ((oldschoolranked.ResultScreen) mc.screen).backToLobby();
                System.out.println("[selftest] back to lobby: world " + (mc.world != null) + ", screen " + (mc.screen == null ? null : mc.screen.getClass().getSimpleName()) + ", state " + Ranked.state);
            }
        }
    }
}
