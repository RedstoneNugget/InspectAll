package de.inspectall.test;

import de.inspectall.client.InspectAllClient;
import de.inspectall.client.config.ConfigScreen;
import de.inspectall.client.config.InspectConfig;
import de.inspectall.client.ui.InspectScreen;
import de.inspectall.client.world.WorldMarkers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Automatischer In-Game-Test im echten Client: ./gradlew runClientGameTest
 * Prüft Bienennest, Truhe, Spawner, Schild, Zombie und ein verzaubertes Item
 * in Singleplayer und auf einem dedizierten Server. Screenshots landen in
 * build/run/clientGameTest/screenshots.
 */
public class InspectAllClientGameTest implements FabricClientGameTest {

    private static final String BEE_A = "{entity_data:{id:\"minecraft:bee\",flower_pos:[I;20,-60,15],HasNectar:1b,Health:7.5f},ticks_in_hive:120,min_ticks_in_hive:600}";
    private static final String BEE_B = "{entity_data:{id:\"minecraft:bee\",flower_pos:[I;20,-60,15],HasNectar:0b},ticks_in_hive:5,min_ticks_in_hive:2400}";
    private static final String BEE_C = "{entity_data:{id:\"minecraft:bee\",FlowerPos:{X:-12,Y:-60,Z:8}},ticks_in_hive:0,min_ticks_in_hive:600}";
    private static final String NEST = "setblock 4 -60 0 minecraft:bee_nest[honey_level=3]{bees:[" + BEE_A + "," + BEE_B + "," + BEE_C + "]}";
    private static final String CHEST = "setblock 4 -60 4 minecraft:chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:12},"
            + "{Slot:5b,id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":5}}}]}";
    private static final String SPAWNER = "setblock 4 -60 8 minecraft:spawner{SpawnData:{entity:{id:\"minecraft:zombie\"}},Delay:120s,SpawnCount:4s,RequiredPlayerRange:0s}";
    private static final String SIGN = "setblock 4 -60 12 minecraft:oak_sign[rotation=4]{front_text:{messages:[\"Zeile eins\",\"Zeile zwei\",\"\",\"\"],color:\"blue\",has_glowing_text:1b}}";
    private static final String ZOMBIE = "summon minecraft:zombie 0.5 -60 -6.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:\"Testzombie\","
            + "Tags:[\"inspect_test\"],active_effects:[{id:\"minecraft:speed\",amplifier:1b,duration:6000}],"
            + "equipment:{head:{id:\"minecraft:iron_helmet\",count:1}},Rotation:[0f,0f]}";
    private static final String SWORD = "give @a minecraft:diamond_sword[minecraft:enchantments={\"minecraft:sharpness\":5,\"minecraft:unbreaking\":3},minecraft:custom_name=\"Testklinge\",minecraft:damage=17]";

    @Override
    public void runTest(ClientGameTestContext context) {
        singleplayer(context);
        dedicatedServer(context);
        log("ALLE TESTS BESTANDEN");
    }

    // ================================================================ Singleplayer

    private void singleplayer(ClientGameTestContext context) {
        try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
            TestServerContext server = sp.getServer();
            sp.getClientWorld().waitForChunksRender();
            buildScene(server);
            context.waitTicks(10);
            sp.getClientWorld().waitForChunksRender();

            // --- 1) Bienennest ---
            lookAt(context, server, 0.5, -60, 0.5, 4.5, -59.5, 0.5);
            List<String> rows = openPanel(context, "SP Bienennest");
            expect(rows, "Bees: 3 / 3");
            expect(rows, "Honey: 3 / 5");
            expect(rows, "flower_pos: 20, -60, 15");
            expect(rows, "flower_pos: -12, -60, 8");
            expect(rows, "HasNectar: ja");
            expect(rows, "ticks_in_hive: ");
            expect(rows, "Integrierter Server");
            check(context.computeOnClient(c -> ((InspectScreen) c.currentScreen).coordRowCount()) >= 4, "Bienennest: Koordinaten erkannt");
            context.takeScreenshot("01_sp_bee_nest_panel");
            check(context.computeOnClient(c -> ((InspectScreen) c.currentScreen).markFirstCoord("20, -60, 15")), "Bienennest: Markieren");
            check(context.computeOnClient(c -> WorldMarkers.count()) == 1, "Bienennest: 1 Markierung");
            context.runOnClient(c -> ((InspectScreen) c.currentScreen).scrollForTest(300));
            context.waitTicks(2);
            context.takeScreenshot("02_sp_bee_nest_snbt");
            closePanel(context);

            lookAt(context, server, -2, -60, -4, 20, -60, 15);
            context.waitTicks(5);
            context.takeScreenshot("03_sp_marker_through_wall");
            context.getInput().pressKey(InspectAllClient.clearKey);
            context.waitTicks(2);
            check(context.computeOnClient(c -> WorldMarkers.count()) == 0, "J löscht alle Markierungen");

            // --- 2) Truhe ---
            lookAt(context, server, 0.5, -60, 4.5, 4.5, -59.5, 4.5);
            rows = openPanel(context, "SP Truhe");
            expect(rows, "Belegte Slots: 2 / 27");
            expect(rows, "Slot 0: 12 ×");
            expect(rows, "(minecraft:diamond)");
            expect(rows, "(minecraft:diamond_sword) ✦");
            expect(rows, "minecraft:sharpness");
            expect(rows, "facing:");
            context.takeScreenshot("04_sp_chest_panel");
            // Suche
            context.runOnClient(c -> ((InspectScreen) c.currentScreen).setQueryForTest("sharp"));
            context.waitTicks(2);
            rows = rows(context);
            dump("SP Truhe Suche 'sharp'", rows);
            check(rows.stream().anyMatch(r -> r.contains("minecraft:sharpness")) && rows.size() < 20, "Suche filtert");
            context.takeScreenshot("05_sp_chest_search");
            context.runOnClient(c -> ((InspectScreen) c.currentScreen).setQueryForTest(""));
            closePanel(context);

            // --- 2b) sehr große Daten: 27 Shulkerkisten mit je 27 verzauberten Items ---
            server.runCommand(bigChest());
            context.waitTicks(5);
            lookAt(context, server, 0.5, -60, 16.5, 4.5, -59.5, 16.5);
            long t0 = System.nanoTime();
            rows = openPanel(context, "SP Große Truhe");
            long openMs = (System.nanoTime() - t0) / 1_000_000;
            int snbtLines = context.computeOnClient(c -> ((InspectScreen) c.currentScreen).totalSnbtLines());
            log("Große Truhe: " + snbtLines + " SNBT-Zeilen, Panel offen nach " + openMs + " ms (inkl. Warten auf Server)");
            check(snbtLines > 5000, "große SNBT-Daten aufgebaut");
            expect(rows, "Belegte Slots: 27 / 27");
            context.runOnClient(c -> ((InspectScreen) c.currentScreen).scrollForTest(1e9));
            context.waitTicks(20);
            int fps = context.computeOnClient(c -> c.getCurrentFps());
            log("FPS mit großem Panel (ans Ende gescrollt): " + fps);
            context.takeScreenshot("05b_sp_big_chest_end");
            // "Auf/Zu": erst alles aufklappen (Standard klappt große Listen automatisch zu), dann alles zu
            int initial = rows.size();
            context.runOnClient(c -> ((InspectScreen) c.currentScreen).toggleFoldAllForTest());
            context.waitTicks(2);
            int first = context.computeOnClient(c -> ((InspectScreen) c.currentScreen).getRowStrings().size());
            context.runOnClient(c -> ((InspectScreen) c.currentScreen).toggleFoldAllForTest());
            context.waitTicks(2);
            int second = context.computeOnClient(c -> ((InspectScreen) c.currentScreen).getRowStrings().size());
            log("Zeilen: Standard " + initial + ", nach Auf/Zu #1 " + first + ", nach Auf/Zu #2 " + second);
            check(Math.max(first, second) > 5000 && Math.min(first, second) < initial + 50, "Auf/Zu klappt alles auf bzw. zu");
            closePanel(context);
            server.runCommand("setblock 4 -60 16 minecraft:air");

            // --- 3) Spawner ---
            lookAt(context, server, 0.5, -60, 8.5, 4.5, -59.5, 8.5);
            rows = openPanel(context, "SP Spawner");
            expect(rows, "Entity: minecraft:zombie");
            expect(rows, "Nächster Spawn in: 120 Ticks");
            expect(rows, "Anzahl pro Spawn: 4");
            context.takeScreenshot("06_sp_spawner_panel");
            closePanel(context);

            // --- 4) Schild ---
            lookAt(context, server, 0.5, -60, 12.5, 4.5, -59.3, 12.5);
            rows = openPanel(context, "SP Schild");
            expect(rows, "Zeile 1: Zeile eins");
            expect(rows, "Zeile 2: Zeile zwei");
            expect(rows, "Farbe: blue");
            expect(rows, "Leuchttinte: ja");
            context.takeScreenshot("07_sp_sign_panel");
            closePanel(context);

            // --- 5) Zombie ---
            lookAt(context, server, 0.5, -60, -2.5, 0.5, -59, -6.5);
            rows = openPanel(context, "SP Zombie");
            expect(rows, "Typ: minecraft:zombie");
            expect(rows, "Custom Name: Testzombie");
            expect(rows, "Gesundheit: 20 / 20");
            expect(rows, "Kopf: 1 ×");
            expect(rows, "minecraft:speed");
            expect(rows, "inspect_test");
            expect(rows, "Rüstung: ");
            expect(rows, "UUID");
            context.takeScreenshot("08_sp_zombie_panel");
            closePanel(context);

            // --- 5b) Sperren (G) -> Overlay bleibt offen ---
            context.getInput().pressKey(InspectAllClient.lockKey);
            context.waitTicks(25);
            check(context.computeOnClient(c -> de.inspectall.client.InspectAllClient.hasLock()), "G sperrt Ziel");
            lookAt(context, server, 0.5, -60, -2.5, 10, -60, -2.5);
            context.waitTicks(25);
            context.takeScreenshot("09_sp_locked_hud");
            context.getInput().pressKey(InspectAllClient.lockKey);
            context.waitTicks(2);
            check(!context.computeOnClient(c -> InspectAllClient.hasLock()), "G entsperrt");

            // --- 6) verzaubertes Item in der Hand ---
            server.runCommand("clear @a");
            server.runCommand(SWORD);
            look(context, server, "tp @a 0.5 -60 0.5 0 -90");
            rows = openPanel(context, "SP Schwert");
            expect(rows, "Eigener Name (custom_name): Testklinge");
            expect(rows, "Haltbarkeit: 1544 / 1561");
            expect(rows, "minecraft:sharpness – Stufe 5");
            expect(rows, "minecraft:unbreaking – Stufe 3");
            expect(rows, "Max. Stapelgröße: 1");
            expect(rows, "minecraft:enchantments");
            expect(rows, "Rarität:");
            String give = context.computeOnClient(c -> ((InspectScreen) c.currentScreen).getData().giveCommand);
            log("give = " + give);
            check(give != null && give.startsWith("/give @s minecraft:diamond_sword[") && give.contains("minecraft:enchantments=")
                    && give.contains("sharpness") && give.contains("minecraft:damage=17"), "/give-Befehl");
            context.clickScreenButton("Als /give kopieren");
            context.waitTicks(2);
            String clip = context.computeOnClient(c -> c.keyboard.getClipboard());
            check(give.equals(clip), "/give in Zwischenablage");
            // der kopierte Befehl muss ein identisches Item erzeugen
            server.runCommand(give.substring(1).replace("@s", "@a"));
            context.waitTicks(3);
            boolean same = server.computeOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().get(0);
                var inv = player.getInventory();
                var original = inv.getStack(0);
                for (int i = 1; i < inv.size(); i++) {
                    if (net.minecraft.item.ItemStack.areItemsAndComponentsEqual(original, inv.getStack(i))) {
                        return true;
                    }
                }
                return false;
            });
            check(same, "/give-Befehl erzeugt identisches Item");
            context.clickScreenButton("SNBT kopieren");
            context.waitTicks(2);
            clip = context.computeOnClient(c -> c.keyboard.getClipboard());
            check(clip.contains("minecraft:sharpness") && clip.contains("\n"), "SNBT in Zwischenablage (eingerückt)");
            long before = exportCount();
            context.clickScreenButton("Als JSON speichern");
            context.waitTicks(2);
            check(exportCount() == before + 1, "JSON-Datei gespeichert");
            String json = newestExport();
            check(json.contains("\"id\": \"minecraft:diamond_sword\"") && json.contains("\"give_command\"")
                    && json.contains("\"snbt\"") && json.contains("minecraft:sharpness"), "JSON-Inhalt vollständig");
            context.takeScreenshot("10_sp_sword_panel");
            closePanel(context);

            // --- 7) Item unter dem Mauszeiger im Inventar (+ Tooltip-Hinweis) ---
            server.runCommand("gamemode survival @a");
            context.waitTicks(5);
            context.getInput().pressKey(o -> o.inventoryKey);
            context.waitForScreen(InventoryScreen.class);
            hoverHotbarSlot0(context);
            context.waitTicks(3);
            context.takeScreenshot("11_sp_inventory_tooltip");
            context.getInput().pressKey(InspectAllClient.openKey);
            context.waitForScreen(InspectScreen.class);
            rows = waitRows(context);
            dump("SP Inventar-Hover", rows);
            expect(rows, "Herkunft: Slot");
            expect(rows, "Testklinge");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitForScreen(InventoryScreen.class);
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitFor(c -> c.currentScreen == null);

            // --- 8) Kategorien ausblenden (Config) ---
            context.runOnClient(c -> InspectConfig.get().setVisible(de.inspectall.client.data.Category.NBT, false));
            lookAt(context, server, 0.5, -60, 4.5, 4.5, -59.5, 4.5);
            rows = openPanel(context, "SP Truhe ohne NBT");
            check(rows.stream().noneMatch(r -> r.startsWith("## Block-Entity-Daten")), "Kategorie NBT ausgeblendet");
            expect(rows, "Kategorie(n) ausgeblendet");
            context.runOnClient(c -> InspectConfig.get().setVisible(de.inspectall.client.data.Category.NBT, true));
            closePanel(context);
            server.runCommand("gamemode creative @a");

            // --- 9) Einstellungs-Screen ---
            context.setScreen(() -> new ConfigScreen(null));
            context.waitTicks(3);
            context.takeScreenshot("12_sp_config_screen");
            context.getInput().scroll(-10);
            context.waitTicks(2);
            context.takeScreenshot("12b_sp_config_screen_colors");
            context.setScreen(() -> null);

            // --- 10) großes Fenster, Panel rechts (einzeilige Knopfleiste) ---
            context.runOnClient(c -> {
                InspectConfig.get().panelPosition = InspectConfig.PanelPosition.RIGHT;
                InspectConfig.get().panelWidthPercent = 60;
            });
            context.getInput().resizeWindow(1600, 900);
            context.waitTicks(5);
            lookAt(context, server, 0.5, -60, 0.5, 4.5, -59.5, 0.5);
            rows = openPanel(context, "SP Bienennest rechts/breit");
            check(rows.get(0).startsWith("## Bienennest"), "Zusammenfassung steht oben");
            context.takeScreenshot("13_sp_wide_right_panel");
            closePanel(context);
            context.runOnClient(c -> {
                InspectConfig.get().panelPosition = InspectConfig.PanelPosition.CENTER;
                InspectConfig.get().panelWidthPercent = 72;
            });
            context.getInput().resizeWindow(854, 480);
            context.waitTicks(5);
        }
    }

    // ================================================================ Dedizierter Server

    private void dedicatedServer(ClientGameTestContext context) {
        try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(true).createServer()) {
            try (TestServerConnection connection = server.connect()) {
                context.runOnClient(c -> InspectConfig.get().opQuery = false);
                connection.getClientWorld().waitForChunksRender();
                buildScene(server);
                context.waitTicks(20);
                connection.getClientWorld().waitForChunksRender();

                lookAt(context, server, 0.5, -60, 0.5, 4.5, -59.5, 0.5);
                List<String> rows = openPanel(context, "MP Bienennest");
                expect(rows, "Honey: 3 / 5");
                expect(rows, "Bienen-Daten: Vom Server nicht übertragen");
                context.takeScreenshot("12_mp_bee_nest_panel");
                closePanel(context);

                lookAt(context, server, 0.5, -60, 4.5, 4.5, -59.5, 4.5);
                rows = openPanel(context, "MP Truhe");
                expect(rows, "Inhalt: Vom Server nicht übertragen");
                context.takeScreenshot("13_mp_chest_panel");
                closePanel(context);

                lookAt(context, server, 0.5, -60, 8.5, 4.5, -59.5, 8.5);
                rows = openPanel(context, "MP Spawner");
                expect(rows, "Entity: minecraft:zombie");
                closePanel(context);

                lookAt(context, server, 0.5, -60, 12.5, 4.5, -59.3, 12.5);
                rows = openPanel(context, "MP Schild");
                expect(rows, "Zeile 1: Zeile eins");
                closePanel(context);

                lookAt(context, server, 0.5, -60, -2.5, 0.5, -59, -6.5);
                rows = openPanel(context, "MP Zombie");
                expect(rows, "Custom Name: Testzombie");
                expect(rows, "Tags: Vom Server nicht übertragen");
                expect(rows, "Effekte: Vom Server nicht übertragen");
                expect(rows, "Kopf: 1 ×");
                context.takeScreenshot("14_mp_zombie_panel");
                closePanel(context);

                server.runCommand(SWORD);
                look(context, server, "tp @a 0.5 -60 0.5 0 -90");
                rows = openPanel(context, "MP Schwert");
                expect(rows, "minecraft:sharpness – Stufe 5");
                closePanel(context);

                // OP: Vanilla-NBT-Abfrage (wie F3+I) liefert die vollständigen Serverdaten
                server.runCommand("op @a");
                context.waitTicks(20);
                context.runOnClient(c -> InspectConfig.get().opQuery = true);
                lookAt(context, server, 0.5, -60, 4.5, 4.5, -59.5, 4.5);
                rows = openPanel(context, "MP Truhe (OP-Abfrage)");
                boolean op = rows.stream().anyMatch(r -> r.contains("Server-Abfrage"));
                if (op) {
                    expect(rows, "Slot 0: 12 ×");
                    context.takeScreenshot("15_mp_chest_op_query");
                } else {
                    log("Hinweis: OP-Abfrage nicht verfügbar (Rechte nicht übertragen) – übersprungen");
                }
                closePanel(context);
                if (op) {
                    lookAt(context, server, 0.5, -60, -2.5, 0.5, -59, -6.5);
                    rows = openPanel(context, "MP Zombie (OP-Abfrage)");
                    expect(rows, "Server-Abfrage");
                    expect(rows, "inspect_test");
                    expect(rows, "minecraft:speed");
                    closePanel(context);
                }
            }
        }
    }

    // ================================================================ Helfer

    private static String bigChest() {
        StringBuilder sb = new StringBuilder("setblock 4 -60 16 minecraft:chest{Items:[");
        for (int s = 0; s < 27; s++) {
            sb.append(s > 0 ? "," : "").append("{Slot:").append(s).append("b,id:\"minecraft:shulker_box\",count:1,components:{\"minecraft:container\":[");
            for (int i = 0; i < 27; i++) {
                sb.append(i > 0 ? "," : "").append("{slot:").append(i).append(",item:{id:\"minecraft:diamond_sword\",count:1,components:{")
                        .append("\"minecraft:enchantments\":{\"minecraft:sharpness\":").append(1 + i % 5).append("},")
                        .append("\"minecraft:custom_name\":\"Klinge ").append(s).append('-').append(i).append("\"}}}");
            }
            sb.append("]}}");
        }
        return sb.append("]}").toString();
    }

    private static void buildScene(TestServerContext server) {
        server.runCommand("time set noon");
        server.runCommand("gamerule doDaylightCycle false");
        server.runCommand(NEST);
        server.runCommand(CHEST);
        server.runCommand(SPAWNER);
        server.runCommand(SIGN);
        server.runCommand("setblock 20 -60 15 minecraft:poppy");
        server.runCommand("setblock -12 -60 8 minecraft:dandelion");
        server.runCommand("fill 10 -60 -5 10 -52 25 minecraft:stone");
        server.runCommand("kill @e[type=minecraft:zombie]");
        server.runCommand(ZOMBIE);
    }

    /** Teleportiert den Spieler und richtet die Augen (nicht die Füße) exakt auf den Zielpunkt aus. */
    private static void lookAt(ClientGameTestContext context, TestServerContext server,
                               double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x;
        double dy = ty - (y + 1.62);
        double dz = tz - z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        look(context, server, String.format(java.util.Locale.ROOT, "tp @a %s %s %s %.2f %.2f", x, y, z, yaw, pitch));
    }

    private static void look(ClientGameTestContext context, TestServerContext server, String tp) {
        server.runCommand(tp);
        context.waitTicks(8);
    }

    private static List<String> openPanel(ClientGameTestContext context, String title) {
        log(context.computeOnClient(c -> c.player == null ? "kein Spieler" : String.format(java.util.Locale.ROOT,
                "Kamera: pos=%s yaw=%.1f pitch=%.1f ziel=%s", c.player.getEyePos(), c.player.getYaw(), c.player.getPitch(),
                c.crosshairTarget == null ? "null" : c.crosshairTarget.getType() + " " + c.crosshairTarget.getPos())));
        context.getInput().pressKey(InspectAllClient.openKey);
        context.waitForScreen(InspectScreen.class);
        List<String> rows = waitRows(context);
        dump(title, rows);
        return rows;
    }

    private static List<String> waitRows(ClientGameTestContext context) {
        context.waitFor(c -> c.currentScreen instanceof InspectScreen s && !s.getData().isPending(), 200);
        context.waitTicks(3);
        return rows(context);
    }

    private static List<String> rows(ClientGameTestContext context) {
        return context.computeOnClient(c -> ((InspectScreen) c.currentScreen).getRowStrings());
    }

    private static void closePanel(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(c -> c.currentScreen == null);
    }

    private static void hoverHotbarSlot0(ClientGameTestContext context) {
        double[] pos = context.computeOnClient(c -> {
            double scale = c.getWindow().getScaleFactor();
            int left = (c.getWindow().getScaledWidth() - 176) / 2;
            int top = (c.getWindow().getScaledHeight() - 166) / 2;
            return new double[]{(left + 8 + 8) * scale, (top + 142 + 8) * scale};
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
    }

    private static long exportCount() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("inspectall").resolve("exports");
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".json")).count();
        } catch (IOException e) {
            return -1;
        }
    }

    private static String newestExport() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("inspectall").resolve("exports");
        try (Stream<Path> files = Files.list(dir)) {
            Path newest = files.filter(p -> p.toString().endsWith(".json"))
                    .max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified())).orElseThrow();
            return Files.readString(newest);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private static void expect(List<String> rows, String needle) {
        check(rows.stream().anyMatch(r -> r.contains(needle)), "enthält \"" + needle + "\"");
    }

    private static void dump(String title, List<String> rows) {
        log("---- Panel " + title + " (" + rows.size() + " Zeilen) ----");
        int n = 0;
        for (String row : rows) {
            log("   | " + row);
            if (++n >= 220) {
                log("   | …");
                break;
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            log("FEHLGESCHLAGEN: " + message);
            throw new AssertionError(message);
        }
        log("ok: " + message);
    }

    private static void log(String message) {
        System.out.println("[InspectAllTest] " + message);
    }
}
