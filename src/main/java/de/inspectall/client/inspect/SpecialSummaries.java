package de.inspectall.client.inspect;

import de.inspectall.client.data.Category;
import de.inspectall.client.data.Coord;
import de.inspectall.client.data.InspectData;
import de.inspectall.client.data.Role;
import de.inspectall.client.data.Section;
import de.inspectall.client.snbt.SnbtTree;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryOps;
import net.minecraft.state.property.Property;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Lesbare Zusammenfassungen für bekannte Block-Entities (oben im Panel);
 * das Roh-SNBT steht immer darunter. Alles wird aus NBT gelesen, damit
 * dieselbe Logik für Client-, Server- und Item-Daten funktioniert.
 */
public final class SpecialSummaries {

    public static final int MAX_BEES = 3;
    public static final int MAX_HONEY = BeehiveBlock.FULL_HONEY_LEVEL;

    private SpecialSummaries() {
    }

    /** Fehlt ein Feld, weil der Server es nicht schickt? (nur bei Client-Daten auf Servern) */
    private record Avail(boolean mpClient, Set<String> notSent, NbtCompound nbt) {
        boolean missing(String key) {
            return mpClient && (notSent.contains(key) || !nbt.contains(key));
        }
    }

    /**
     * @param beId     Block-Entity-Typ, z. B. "minecraft:chest" (oder null)
     * @param nbt      Block-Entity-NBT (bei Mehrspieler bereits auf übertragene Felder gefiltert)
     * @param notSent  Felder, die der Server nicht übertragen hat
     * @param invSize  Inventargröße, falls bekannt
     */
    public static void block(InspectData data, @Nullable BlockState state, @Nullable String beId, @Nullable NbtCompound nbt,
                             Set<String> notSent, @Nullable Integer invSize, boolean mpClient, RegistryOps<NbtElement> ops) {
        if (nbt == null) {
            nbt = new NbtCompound();
        }
        Avail a = new Avail(mpClient, notSent, nbt);
        String id = beId != null ? beId : "";
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;

        if (state != null && state.getBlock() instanceof BeehiveBlock || path.equals("beehive")) {
            Section s = data.section(Category.SUMMARY, "Bienennest / Bienenstock");
            Integer honey = state != null && state.contains(BeehiveBlock.HONEY_LEVEL) ? state.get(BeehiveBlock.HONEY_LEVEL) : null;
            hive(s, honey, a.missing("bees") ? null : Nbts.list(nbt, "bees"), a.missing("bees"));
            return;
        }
        switch (path) {
            case "mob_spawner" -> spawner(data.section(Category.SUMMARY, "Spawner"), a, ops);
            case "trial_spawner" -> trialSpawner(data.section(Category.SUMMARY, "Trial-Spawner"), a);
            case "sign", "hanging_sign" -> sign(data.section(Category.SUMMARY, path.equals("sign") ? "Schild" : "Hängeschild"), a, ops);
            case "command_block" -> commandBlock(data.section(Category.SUMMARY, "Kommandoblock"), state, a, ops);
            case "lectern" -> lectern(data.section(Category.SUMMARY, "Lesepult"), state, a, ops);
            case "brewing_stand" -> brewing(data.section(Category.SUMMARY, "Braustand"), a, ops);
            case "furnace", "blast_furnace", "smoker" -> furnace(data.section(Category.SUMMARY,
                    path.equals("furnace") ? "Ofen" : path.equals("smoker") ? "Räucherofen" : "Schmelzofen"), state, a, ops);
            case "beacon" -> beacon(data.section(Category.SUMMARY, "Leuchtfeuer"), a);
            case "jukebox" -> jukebox(data.section(Category.SUMMARY, "Plattenspieler"), a, ops);
            default -> {
                if (nbt.contains("Items") || invSize != null && invSize > 0 || notSent.contains("Items")) {
                    container(data.section(Category.SUMMARY, "Container"), a, invSize, ops);
                }
            }
        }
    }

    // ------------------------------------------------------------ Bienennest

    /** Bienennest/-stock: gemeinsam für Block (NBT "bees") und Item (Komponente minecraft:bees). */
    public static void hive(Section s, @Nullable Integer honey, @Nullable NbtList bees, boolean beesNotSent) {
        s.kv("Bees", (bees == null ? "?" : String.valueOf(bees.size())) + " / " + MAX_BEES, Role.NUMBER);
        s.kv("Honey", (honey == null ? "?" : String.valueOf(honey)) + " / " + MAX_HONEY, Role.NUMBER);
        if (beesNotSent || bees == null) {
            s.kv("Bienen-Daten", Section.NOT_SENT, Role.WARN);
            s.dim("Vanilla-Server schicken die Bienen eines platzierten Nests nicht an den Client.");
            s.dim("Als Item (abgebaut mit Behutsamkeit) sind die Daten vollständig sichtbar.");
            return;
        }
        if (bees.isEmpty()) {
            s.dim("Keine Bienen enthalten.");
        }
        for (int i = 0; i < bees.size(); i++) {
            if (!(bees.get(i) instanceof NbtCompound bee)) {
                continue;
            }
            NbtCompound entity = bee.getCompoundOrEmpty("entity_data");
            String type = Nbts.str(entity, "id", "minecraft:bee");
            s.sub("Biene " + (i + 1) + (type.equals("minecraft:bee") ? "" : " (" + type + ")"));
            Coord flower = coordOf(entity, "flower_pos", "FlowerPos");
            if (flower != null) {
                s.coord("flower_pos", flower.pos(), null, "flower_pos (Biene " + (i + 1) + ")");
            } else {
                s.kv("flower_pos", "keine gemerkte Blume", Role.DIM);
            }
            Boolean nectar = Nbts.bool(entity, "HasNectar");
            s.kv("HasNectar", nectar == null ? "nein" : Section.yesNo(nectar), Role.BOOLEAN);
            s.kv("ticks_in_hive", Nbts.num(bee, "ticks_in_hive", "TicksInHive"));
            s.kv("min_ticks_in_hive", Nbts.num(bee, "min_ticks_in_hive", "MinOccupationTicks"));
            Double health = Nbts.dbl(entity, "Health");
            if (health != null) {
                s.kv("Health", Nbts.fmt(health));
            }
            Coord hive = coordOf(entity, "hive_pos", "HivePos");
            if (hive != null) {
                s.coord("hive_pos", hive.pos(), null, "hive_pos (Biene " + (i + 1) + ")");
            }
        }
    }

    @Nullable
    static Coord coordOf(NbtCompound c, String... keys) {
        for (String k : keys) {
            NbtElement el = c.get(k);
            if (el != null) {
                Coord coord = SnbtTree.detectCoord(k, el, null);
                if (coord != null) {
                    return coord;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ Spawner

    private static void spawner(Section s, Avail a, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        if (a.missing("SpawnData") && a.missing("Delay")) {
            s.kv("Spawner-Daten", Section.NOT_SENT, Role.WARN);
            return;
        }
        NbtCompound spawnData = n.getCompoundOrEmpty("SpawnData");
        String entity = Nbts.str(spawnData.getCompoundOrEmpty("entity"), "id", "");
        s.kv("Entity", entity.isEmpty() ? "keine (leerer Spawner)" : entity, entity.isEmpty() ? Role.DIM : Role.STRING);
        Integer delay = Nbts.num(n, "Delay");
        s.kv("Nächster Spawn in", delay == null ? "–" : Nbts.ticks(delay));
        s.kv("Min. Verzögerung", ticksOrDash(Nbts.num(n, "MinSpawnDelay")));
        s.kv("Max. Verzögerung", ticksOrDash(Nbts.num(n, "MaxSpawnDelay")));
        s.kv("Anzahl pro Spawn", Nbts.num(n, "SpawnCount"));
        s.kv("Max. Entities in der Nähe", Nbts.num(n, "MaxNearbyEntities"));
        s.kv("Spieler-Reichweite", blocks(Nbts.num(n, "RequiredPlayerRange")));
        s.kv("Spawn-Radius", blocks(Nbts.num(n, "SpawnRange")));
        if (spawnData.contains("custom_spawn_rules")) {
            s.kv("Eigene Spawn-Regeln", spawnData.get("custom_spawn_rules").toString(), Role.TEXT);
        }
        if (spawnData.contains("equipment")) {
            s.kv("Ausrüstung (Loot-Table)", spawnData.get("equipment").toString(), Role.TEXT);
        }
        if (a.missing("SpawnPotentials")) {
            s.notSent("Spawn-Potenziale");
        } else {
            NbtList potentials = Nbts.list(n, "SpawnPotentials");
            s.kv("Spawn-Potenziale", potentials.size());
            for (NbtElement e : potentials) {
                if (e instanceof NbtCompound p) {
                    String id = Nbts.str(p.getCompoundOrEmpty("data").getCompoundOrEmpty("entity"), "id", "?");
                    s.kv("  • " + id, "Gewicht " + Nbts.num(p, "weight"), Role.NUMBER);
                }
            }
        }
    }

    private static void trialSpawner(Section s, Avail a) {
        NbtCompound n = a.nbt;
        s.kv("Spieler-Reichweite", blocks(Nbts.num(n, "required_player_range")));
        s.kv("Spawn-Radius", blocks(Nbts.num(n, "spawn_range")));
        s.kv("Abklingzeit", ticksOrDash(Nbts.num(n, "target_cooldown_length")));
        s.kv("Normale Konfiguration", Nbts.str(n, "normal_config", "–"));
        s.kv("Ominöse Konfiguration", Nbts.str(n, "ominous_config", "–"));
        if (a.missing("registered_players")) {
            s.notSent("Registrierte Spieler");
        } else {
            s.kv("Registrierte Spieler", Nbts.list(n, "registered_players").size());
            s.kv("Aktive Mobs", Nbts.list(n, "current_mobs").size());
        }
    }

    // ------------------------------------------------------------ Container

    private static void container(Section s, Avail a, @Nullable Integer size, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        if (n.contains("LootTable")) {
            s.kv("Loot-Table (noch nicht generiert)", Nbts.str(n, "LootTable", "?"), Role.STRING);
            if (n.contains("LootTableSeed")) {
                s.kv("Loot-Seed", String.valueOf(n.get("LootTableSeed")), Role.NUMBER);
            }
        }
        if (a.missing("Items")) {
            s.kv("Inhalt", Section.NOT_SENT, Role.WARN);
            s.dim("Vanilla-Server schicken Container-Inhalte erst, wenn du den Container öffnest.");
            s.dim("Tipp: Container öffnen und mit H das Item unter dem Mauszeiger prüfen.");
        } else {
            items(s, Nbts.list(n, "Items"), size, null, ops);
        }
        if (n.contains("lock")) {
            s.kv("Schloss", String.valueOf(n.get("lock")), Role.TEXT);
        }
        if (n.contains("CustomName")) {
            s.kv("Eigener Name", Nbts.text(n.get("CustomName"), ops), Role.STRING);
        }
    }

    /** Listet ein NBT-Inventar ({Slot, id, count, components}) auf. */
    static void items(Section s, NbtList items, @Nullable Integer size, @Nullable String[] slotNames, RegistryOps<NbtElement> ops) {
        int filled = 0;
        long total = 0;
        for (NbtElement e : items) {
            ItemStack stack = Nbts.decodeStack(e, ops);
            if (!stack.isEmpty()) {
                filled++;
                total += stack.getCount();
            }
        }
        s.kv("Belegte Slots", filled + (size != null && size > 0 ? " / " + size : ""), Role.NUMBER);
        s.kv("Items gesamt", total);
        if (filled == 0) {
            s.dim("leer");
        }
        for (NbtElement e : items) {
            if (!(e instanceof NbtCompound c)) {
                continue;
            }
            ItemStack stack = Nbts.decodeStack(c, ops);
            if (stack.isEmpty()) {
                continue;
            }
            Integer slot = Nbts.num(c, "Slot", "slot");
            String name = slot == null ? "Slot ?" : slotNames != null && slot >= 0 && slot < slotNames.length
                    ? slotNames[slot] : "Slot " + slot;
            s.kv(name, Nbts.stackLabel(stack), Role.TEXT);
        }
    }

    // ------------------------------------------------------------ Schild

    private static void sign(Section s, Avail a, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        if (a.missing("front_text") && a.missing("back_text")) {
            s.kv("Text", Section.NOT_SENT, Role.WARN);
            return;
        }
        side(s, "Vorderseite", n.getCompoundOrEmpty("front_text"), ops);
        side(s, "Rückseite", n.getCompoundOrEmpty("back_text"), ops);
        Boolean waxed = Nbts.bool(n, "is_waxed");
        s.kv("Gewachst", waxed == null ? "nein" : Section.yesNo(waxed), Role.BOOLEAN);
    }

    private static void side(Section s, String title, NbtCompound text, RegistryOps<NbtElement> ops) {
        s.sub(title);
        NbtList messages = Nbts.list(text, "messages");
        for (int i = 0; i < Math.max(4, messages.size()); i++) {
            String line = i < messages.size() ? Nbts.text(messages.get(i), ops) : "";
            s.kv("Zeile " + (i + 1), line.isEmpty() ? "(leer)" : line, line.isEmpty() ? Role.DIM : Role.STRING);
        }
        s.kv("Farbe", Nbts.str(text, "color", "black"), Role.STRING);
        Boolean glow = Nbts.bool(text, "has_glowing_text");
        s.kv("Leuchttinte", glow == null ? "nein" : Section.yesNo(glow), Role.BOOLEAN);
    }

    // ------------------------------------------------------------ Kommandoblock

    private static void commandBlock(Section s, @Nullable BlockState state, Avail a, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        if (state != null) {
            String block = state.getBlock().getTranslationKey();
            s.kv("Typ", block.contains("repeating") ? "Wiederholen" : block.contains("chain") ? "Kette" : "Impuls", Role.TEXT);
            Boolean conditional = propertyBool(state, "conditional");
            if (conditional != null) {
                s.kv("Bedingt", Section.yesNo(conditional), Role.BOOLEAN);
            }
            String facing = propertyString(state, "facing");
            if (facing != null) {
                s.kv("Richtung", facing, Role.STRING);
            }
        }
        if (a.missing("Command")) {
            s.notSent("Befehl");
            s.dim("Vanilla-Server schicken Befehle erst, wenn ein OP den Kommandoblock öffnet.");
        } else {
            String cmd = Nbts.str(n, "Command", "");
            s.kv("Befehl", cmd.isEmpty() ? "(leer)" : cmd, cmd.isEmpty() ? Role.DIM : Role.STRING);
        }
        boolKv(s, "Immer aktiv (auto)", a, "auto");
        boolKv(s, "Mit Strom (powered)", a, "powered");
        boolKv(s, "Bedingung erfüllt", a, "conditionMet");
        boolKv(s, "Ausgabe verfolgen", a, "TrackOutput");
        if (a.missing("SuccessCount")) {
            s.notSent("Erfolgszähler");
        } else {
            s.kv("Erfolgszähler", Nbts.num(n, "SuccessCount"));
        }
        if (n.contains("LastOutput")) {
            s.kv("Letzte Ausgabe", Nbts.text(n.get("LastOutput"), ops), Role.STRING);
        }
        if (n.contains("LastExecution")) {
            s.kv("Letzte Ausführung (Weltzeit)", String.valueOf(n.get("LastExecution")), Role.NUMBER);
        }
        if (n.contains("CustomName")) {
            s.kv("Name", Nbts.text(n.get("CustomName"), ops), Role.STRING);
        }
    }

    private static void boolKv(Section s, String label, Avail a, String key) {
        if (a.missing(key)) {
            s.notSent(label);
        } else {
            Boolean b = Nbts.bool(a.nbt, key);
            s.kv(label, b == null ? "nein" : Section.yesNo(b), Role.BOOLEAN);
        }
    }

    // ------------------------------------------------------------ Lesepult

    private static void lectern(Section s, @Nullable BlockState state, Avail a, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        Boolean hasBook = state != null ? propertyBool(state, "has_book") : null;
        if (hasBook != null) {
            s.kv("Buch eingelegt", Section.yesNo(hasBook), Role.BOOLEAN);
        }
        Boolean powered = state != null ? propertyBool(state, "powered") : null;
        if (powered != null) {
            s.kv("Redstone-Impuls", Section.yesNo(powered), Role.BOOLEAN);
        }
        if (a.missing("Book")) {
            if (hasBook == null || hasBook) {
                s.notSent("Buch");
            }
            return;
        }
        ItemStack book = Nbts.decodeStack(n.get("Book"), ops);
        if (book.isEmpty()) {
            s.dim("Kein Buch.");
            return;
        }
        Integer page = Nbts.num(n, "Page");
        bookInfo(s, book, page);
    }

    static void bookInfo(Section s, ItemStack book, @Nullable Integer page) {
        s.kv("Buch", Nbts.stackLabel(book), Role.TEXT);
        WrittenBookContentComponent written = book.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        WritableBookContentComponent writable = book.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
        int pages = 0;
        String first = "";
        if (written != null) {
            s.kv("Titel", written.title().raw(), Role.STRING);
            s.kv("Autor", written.author(), Role.STRING);
            s.kv("Generation", generationName(written.generation()), Role.TEXT);
            pages = written.pages().size();
            if (pages > 0) {
                first = written.pages().get(0).raw().getString();
            }
        } else if (writable != null) {
            pages = writable.pages().size();
            if (pages > 0) {
                first = writable.pages().get(0).raw();
            }
        }
        s.kv("Seiten", pages);
        if (page != null) {
            s.kv("Aufgeschlagen", "Seite " + (page + 1) + (pages > 0 ? " / " + pages : ""), Role.NUMBER);
        }
        if (!first.isEmpty()) {
            String preview = first.replace('\n', ' ');
            s.kv("Seite 1", preview.length() > 160 ? preview.substring(0, 160) + " …" : preview, Role.STRING);
        }
    }

    private static String generationName(int generation) {
        return switch (generation) {
            case 0 -> "Original";
            case 1 -> "Kopie des Originals";
            case 2 -> "Kopie einer Kopie";
            default -> "Zerfleddert (" + generation + ")";
        };
    }

    // ------------------------------------------------------------ Braustand

    private static final String[] BREWING_SLOTS = {"Flasche links", "Flasche Mitte", "Flasche rechts", "Zutat", "Brennstoff"};

    private static void brewing(Section s, Avail a, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        if (a.missing("BrewTime")) {
            s.notSent("Braufortschritt");
        } else {
            int brew = Nbts.num(n, "BrewTime") == null ? 0 : Nbts.num(n, "BrewTime");
            s.kv("Braufortschritt", brew <= 0 ? "braut nicht" : "noch " + Nbts.ticks(brew) + " (" + Nbts.pct(400 - brew, 400) + ")",
                    brew <= 0 ? Role.DIM : Role.NUMBER);
        }
        if (a.missing("Fuel")) {
            s.notSent("Brennstoff-Ladung");
        } else {
            s.kv("Brennstoff-Ladung", (Nbts.num(n, "Fuel") == null ? 0 : Nbts.num(n, "Fuel")) + " / 20", Role.NUMBER);
        }
        if (a.missing("Items")) {
            s.kv("Inhalt", Section.NOT_SENT, Role.WARN);
        } else {
            items(s, Nbts.list(n, "Items"), 5, BREWING_SLOTS, ops);
        }
    }

    // ------------------------------------------------------------ Ofen

    private static final String[] FURNACE_SLOTS = {"Zutat", "Brennstoff", "Ergebnis"};

    private static void furnace(Section s, @Nullable BlockState state, Avail a, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        Boolean lit = state != null ? propertyBool(state, "lit") : null;
        if (lit != null) {
            s.kv("Brennt", Section.yesNo(lit), Role.BOOLEAN);
        }
        Integer spent = Nbts.num(n, "cooking_time_spent", "CookTime");
        Integer total = Nbts.num(n, "cooking_total_time", "CookTimeTotal");
        Integer litLeft = Nbts.num(n, "lit_time_remaining", "BurnTime");
        Integer litTotal = Nbts.num(n, "lit_total_time");
        if (a.missing("cooking_time_spent") && a.missing("CookTime")) {
            s.notSent("Fortschritt");
        } else {
            s.kv("Fortschritt", (spent == null ? 0 : spent) + " / " + (total == null ? "?" : total) + " Ticks ("
                    + Nbts.pct(spent == null ? 0 : spent, total == null ? 0 : total) + ")", Role.NUMBER);
        }
        if (a.missing("lit_time_remaining") && a.missing("BurnTime")) {
            s.notSent("Restbrennzeit");
        } else {
            s.kv("Restbrennzeit", (litLeft == null ? 0 : litLeft) + (litTotal != null ? " / " + litTotal : "") + " Ticks", Role.NUMBER);
        }
        if (a.missing("Items")) {
            s.kv("Inhalt", Section.NOT_SENT, Role.WARN);
        } else {
            items(s, Nbts.list(n, "Items"), 3, FURNACE_SLOTS, ops);
        }
        if (a.missing("RecipesUsed")) {
            s.notSent("Benutzte Rezepte (für XP)");
        } else {
            NbtCompound used = n.getCompoundOrEmpty("RecipesUsed");
            int count = 0;
            for (String key : used.getKeys()) {
                Integer v = Nbts.num(used, key);
                count += v == null ? 0 : v;
            }
            s.kv("Geschmolzen seit Leerung (XP ausstehend)", count + " × in " + used.getSize() + " Rezept(en)", Role.NUMBER);
        }
    }

    // ------------------------------------------------------------ weitere

    private static void beacon(Section s, Avail a) {
        NbtCompound n = a.nbt;
        s.kv("Stufe (Pyramide)", a.missing("Levels") ? Section.NOT_SENT : String.valueOf(Nbts.num(n, "Levels")),
                a.missing("Levels") ? Role.WARN : Role.NUMBER);
        s.kv("Primärer Effekt", Nbts.str(n, "primary_effect", "keiner"), Role.STRING);
        s.kv("Sekundärer Effekt", Nbts.str(n, "secondary_effect", "keiner"), Role.STRING);
    }

    private static void jukebox(Section s, Avail a, RegistryOps<NbtElement> ops) {
        if (a.missing("RecordItem")) {
            s.notSent("Schallplatte");
            return;
        }
        ItemStack disc = Nbts.decodeStack(a.nbt.get("RecordItem"), ops);
        s.kv("Schallplatte", disc.isEmpty() ? "keine" : Nbts.stackLabel(disc), Role.TEXT);
        Integer ticks = Nbts.num(a.nbt, "ticks_since_song_started");
        if (ticks != null) {
            s.kv("Spielt seit", Nbts.ticks(ticks));
        }
    }

    // ------------------------------------------------------------ Blockstate-Helfer

    @Nullable
    static Boolean propertyBool(BlockState state, String name) {
        String v = propertyString(state, name);
        return v == null ? null : Boolean.parseBoolean(v);
    }

    @Nullable
    static String propertyString(BlockState state, String name) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals(name)) {
                return valueName(state, p);
            }
        }
        return null;
    }

    static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.name(state.get(property));
    }

    private static String ticksOrDash(@Nullable Integer ticks) {
        return ticks == null ? "–" : Nbts.ticks(ticks);
    }

    private static String blocks(@Nullable Integer n) {
        return n == null ? "–" : n + " Blöcke";
    }
}
