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
    private static int t, runTicks, doneTicks;
    private static boolean drawAccepted;
    private static int drawSeen, seedSeen;
    private static boolean queued, logged, forfeited;

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
        if (Ranked.state == Ranked.State.RUNNING) {
            runTicks++;
            if (!logged && runTicks == 40) {
                logged = true;
                System.out.println("[selftest] running: seed " + mc.world.getSeed() + " spawn " + (int) Math.floor(mc.player.x) + " "
                        + (int) Math.floor(mc.player.y) + " " + (int) Math.floor(mc.player.z) + " worldTime " + mc.world.getTime() + " igt " + Ranked.ticks);
            }
            if (Boolean.getBoolean("ranked.droptest")) dropTest(mc);
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
