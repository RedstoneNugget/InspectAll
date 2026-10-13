package de.inspectall.client.inspect;

import de.inspectall.client.InspectAllClient;
import de.inspectall.client.data.Category;
import de.inspectall.client.data.InspectData;
import de.inspectall.client.data.Role;
import de.inspectall.client.data.Section;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Sammelt alle Daten zu einem Block. Läuft auf dem Client- oder dem Server-Thread (je nach {@link Ctx}). */
public final class BlockInspector {

    private static final Set<String> IDENTIFYING = Set.of("id", "x", "y", "z");

    private BlockInspector() {
    }

    /**
     * @param side      angesehene Seite (für Lichtwerte vor dem Block), darf null sein
     * @param queryNbt  Block-Entity-NBT aus der Vanilla-Serverabfrage (OP) oder null
     */
    public static InspectData inspect(Ctx ctx, BlockPos pos, @Nullable Direction side, @Nullable NbtCompound queryNbt) {
        World world = ctx.world();
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);

        InspectData data = new InspectData(InspectData.Kind.BLOCK);
        data.name = block.getName();
        data.icon = icon(world, pos, state);
        data.registryId = id.toString();
        data.namespace = id.getNamespace();
        data.modName = Nbts.modName(id.getNamespace());
        data.position = pos.toImmutable();
        data.positionText = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        data.source = ctx.source().label;
        data.multiplayer = ctx.mpClient() || ctx.source() == Ctx.Source.OP_QUERY;

        BlockEntity be = world.getBlockEntity(pos);

        general(data, ctx, pos, state, block, id, be);
        blockState(data, state);
        properties(data, world, pos, state, block);
        environment(data, world, pos, state, side, ctx);
        tags(data, state);
        blockEntity(data, ctx, state, be, queryNbt);
        return data.sortSections();
    }

    private static ItemStack icon(World world, BlockPos pos, BlockState state) {
        try {
            ItemStack pick = state.getPickStack(world, pos, false);
            if (pick != null && !pick.isEmpty()) {
                return pick;
            }
        } catch (RuntimeException ignored) {
        }
        ItemStack item = new ItemStack(state.getBlock().asItem());
        if (!item.isEmpty()) {
            return item;
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            ItemStack bucket = new ItemStack(fluid.getFluid().getBucketItem());
            if (!bucket.isEmpty()) {
                return bucket;
            }
        }
        return new ItemStack(Items.BARRIER);
    }

    // ------------------------------------------------------------ Abschnitte

    private static void general(InspectData data, Ctx ctx, BlockPos pos, BlockState state, Block block, Identifier id, @Nullable BlockEntity be) {
        Section s = data.section(Category.GENERAL, "Allgemein");
        s.kv("Name", block.getName().getString(), Role.STRING);
        s.kv("Registry-ID", id.toString(), Role.STRING);
        s.kv("Namespace / Mod", id.getNamespace() + " – " + data.modName, Role.TEXT);
        s.kv("Übersetzungsschlüssel", block.getTranslationKey(), Role.DIM);
        s.coord("Position", pos, ctx.dimension(), Registries.BLOCK.getId(block).getPath());
        s.kv("Dimension", ctx.dimension(), Role.STRING);
        Identifier item = Registries.ITEM.getId(block.asItem());
        s.kv("Block-Item", block.asItem() == Items.AIR ? "keins" : item.toString(), Role.STRING);
        s.kv("Block-Entity", be == null ? (state.hasBlockEntity() ? "ja (nicht geladen)" : "keine")
                : String.valueOf(BlockEntityTypeId.of(be)), Role.STRING);
        s.kv("Datenquelle", ctx.source().label, ctx.mpClient() ? Role.WARN : Role.DIM);
    }

    private static void blockState(InspectData data, BlockState state) {
        Section s = data.section(Category.BLOCKSTATE, "Blockstate");
        s.kv("Blockstate", stateString(state), Role.STRING);
        if (state.getProperties().isEmpty()) {
            s.dim("Keine Properties.");
            return;
        }
        List<Property<?>> props = new ArrayList<>(state.getProperties());
        props.sort((a, b) -> a.getName().compareTo(b.getName()));
        for (Property<?> p : props) {
            s.kv(p.getName(), SpecialSummaries.valueName(state, p) + "    (" + possible(p) + ")", roleFor(state, p));
        }
    }

    public static String stateString(BlockState state) {
        StringBuilder sb = new StringBuilder(Registries.BLOCK.getId(state.getBlock()).toString());
        if (!state.getProperties().isEmpty()) {
            List<Property<?>> props = new ArrayList<>(state.getProperties());
            props.sort((a, b) -> a.getName().compareTo(b.getName()));
            sb.append('[');
            for (int i = 0; i < props.size(); i++) {
                Property<?> p = props.get(i);
                sb.append(i > 0 ? "," : "").append(p.getName()).append('=').append(SpecialSummaries.valueName(state, p));
            }
            sb.append(']');
        }
        return sb.toString();
    }

    private static <T extends Comparable<T>> String possible(Property<T> p) {
        return p.getValues().stream().map(p::name).collect(Collectors.joining(", "));
    }

    private static Role roleFor(BlockState state, Property<?> p) {
        Object v = state.get(p);
        if (v instanceof Boolean) {
            return Role.BOOLEAN;
        }
        if (v instanceof Integer) {
            return Role.NUMBER;
        }
        return Role.STRING;
    }

    private static void properties(InspectData data, World world, BlockPos pos, BlockState state, Block block) {
        Section s = data.section(Category.BLOCK_PROPERTIES, "Block-Eigenschaften");
        float hardness = safe(() -> state.getHardness(world, pos), block.getHardness());
        s.kv("Härte", hardness < 0 ? "unzerstörbar (" + Nbts.fmt(hardness) + ")" : Nbts.fmt(hardness), Role.NUMBER);
        s.kv("Explosionswiderstand", Nbts.fmt(block.getBlastResistance()));
        s.kv("Lichtstärke (Leuchtkraft)", state.getLuminance());
        s.kv("Rutschigkeit", Nbts.fmt(block.getSlipperiness()));
        s.kv("Geschwindigkeitsfaktor", Nbts.fmt(block.getVelocityMultiplier()));
        s.kv("Sprungfaktor", Nbts.fmt(block.getJumpVelocityMultiplier()));
        Optional<RegistryKey<net.minecraft.loot.LootTable>> loot = safeOpt(block);
        s.kv("Loot-Table", loot.map(k -> k.getValue().toString()).orElse("keine"), loot.isPresent() ? Role.STRING : Role.DIM);
        s.kv("Werkzeug erforderlich", Section.yesNo(state.isToolRequired()), Role.BOOLEAN);
        s.kv("Kolbenverhalten", state.getPistonBehavior().name().toLowerCase(Locale.ROOT), Role.STRING);
        s.kv("Ersetzbar", Section.yesNo(state.isReplaceable()), Role.BOOLEAN);
        s.kv("Vollblock (solid block)", Section.yesNo(safe(() -> state.isSolidBlock(world, pos), false)), Role.BOOLEAN);
        s.kv("Opak / volle Würfelform", Section.yesNo(state.isOpaque()) + " / " + Section.yesNo(safe(() -> state.isFullCube(world, pos), false)), Role.BOOLEAN);
        s.kv("Brennbar", Section.yesNo(state.isBurnable()), Role.BOOLEAN);
        s.kv("Zufällige Ticks", Section.yesNo(state.hasRandomTicks()), Role.BOOLEAN);
        s.kv("Lichtdurchlässigkeit (Opacity)", state.getOpacity());
        s.kv("Instrument (Notenblock)", state.getInstrument().asString(), Role.STRING);
        s.kv("Kartenfarbe (ID)", safe(() -> state.getMapColor(world, pos).id, -1));
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            s.kv("Flüssigkeit", Registries.FLUID.getId(fluid.getFluid()) + " (Level " + fluid.getLevel()
                    + (fluid.isStill() ? ", Quelle" : ", fließend") + ")", Role.STRING);
        }
    }

    private static Optional<RegistryKey<net.minecraft.loot.LootTable>> safeOpt(Block block) {
        try {
            return block.getLootTableKey();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static void environment(InspectData data, World world, BlockPos pos, BlockState state, @Nullable Direction side, Ctx ctx) {
        Section s = data.section(Category.ENVIRONMENT, "Umgebung");
        try {
            RegistryEntry<Biome> biome = world.getBiome(pos);
            s.kv("Biom", biome.getKey().map(k -> k.getValue().toString()).orElse("?"), Role.STRING);
        } catch (RuntimeException e) {
            s.kv("Biom", "?", Role.DIM);
        }
        s.kv("Dimension", ctx.dimension(), Role.STRING);
        s.kv("Licht am Block (Himmel / Block)", world.getLightLevel(LightType.SKY, pos) + " / " + world.getLightLevel(LightType.BLOCK, pos), Role.NUMBER);
        if (side != null) {
            BlockPos front = pos.offset(side);
            s.kv("Licht davor (" + side.asString() + ", Himmel / Block)", world.getLightLevel(LightType.SKY, front) + " / "
                    + world.getLightLevel(LightType.BLOCK, front), Role.NUMBER);
        }
        s.kv("Redstone empfangen", safe(() -> world.getReceivedRedstonePower(pos), 0));
        s.kv("Redstone stark empfangen", safe(() -> world.getReceivedStrongRedstonePower(pos), 0));
        if (state.emitsRedstonePower()) {
            StringBuilder sb = new StringBuilder();
            int max = 0;
            for (Direction d : Direction.values()) {
                int weak = safe(() -> state.getWeakRedstonePower(world, pos, d), 0);
                int strong = safe(() -> state.getStrongRedstonePower(world, pos, d), 0);
                max = Math.max(max, weak);
                if (weak > 0 || strong > 0) {
                    sb.append(sb.isEmpty() ? "" : ", ").append(d.asString()).append('=').append(weak);
                    if (strong > 0) {
                        sb.append(" (stark ").append(strong).append(')');
                    }
                }
            }
            s.kv("Redstone abgegeben (max)", max);
            if (!sb.isEmpty()) {
                s.kv("  pro Richtung", sb.toString(), Role.NUMBER);
            }
        } else {
            s.kv("Gibt Redstone ab", "nein", Role.BOOLEAN);
        }
        if (state.hasComparatorOutput()) {
            Direction d = side != null ? side : Direction.NORTH;
            int cmp = safe(() -> state.getComparatorOutput(world, pos, d), 0);
            s.kv("Komparator-Signal", cmp + (ctx.mpClient() ? " (Client-Berechnung – Inhalte evtl. nicht übertragen)" : ""),
                    ctx.mpClient() ? Role.WARN : Role.NUMBER);
        }
        s.kv("Weltzeit / Tageszeit", world.getTime() + " / " + (world.getTimeOfDay() % 24000L), Role.NUMBER);
    }

    private static void tags(InspectData data, BlockState state) {
        List<String> tags = state.streamTags().map(TagKey::id).map(Identifier::toString).sorted().toList();
        Section s = data.section(Category.TAGS, "Block-Tags (" + tags.size() + ")");
        s.collapsedByDefault = tags.size() > 6;
        if (tags.isEmpty()) {
            s.dim("Keine Tags.");
        }
        for (String tag : tags) {
            s.kv("#" + tag, "", Role.DIM);
        }
    }

    // ------------------------------------------------------------ Block-Entity

    private static void blockEntity(InspectData data, Ctx ctx, BlockState state, @Nullable BlockEntity be, @Nullable NbtCompound queryNbt) {
        if (be == null && queryNbt == null) {
            if (state.hasBlockEntity()) {
                data.section(Category.NBT, "Block-Entity-Daten").warn("Block-Entity ist auf dem Client nicht geladen.");
            }
            SpecialSummaries.block(data, state, null, null, Set.of(), null, false, ctx.ops());
            return;
        }
        RegistryOps<NbtElement> ops = ctx.ops();
        NbtCompound nbt = null;
        Set<String> notSent = new LinkedHashSet<>();
        String beId = be != null ? BlockEntityTypeId.of(be) : null;
        try {
            if (queryNbt != null) {
                nbt = queryNbt.copy();
                if (be != null) {
                    // Die Vanilla-Abfrage liefert createNbt() ohne id/x/y/z – ergänzen
                    NbtCompound ident = be.createNbtWithIdentifyingData(ctx.registries());
                    for (String key : IDENTIFYING) {
                        if (!nbt.contains(key) && ident.contains(key)) {
                            nbt.put(key, ident.get(key));
                        }
                    }
                }
            } else {
                nbt = be.createNbtWithIdentifyingData(ctx.registries());
                if (ctx.mpClient()) {
                    nbt = filterTransmitted(be, nbt, ctx, notSent);
                }
            }
        } catch (RuntimeException e) {
            InspectAllClient.LOGGER.debug("Block-Entity-NBT nicht lesbar", e);
        }
        if (beId == null && nbt != null) {
            beId = Nbts.str(nbt, "id", null);
        }

        Integer invSize = be instanceof Inventory inv ? safe(inv::size, null) : null;
        SpecialSummaries.block(data, state, beId, nbt, notSent, invSize, ctx.mpClient(), ops);

        Section s = data.section(Category.NBT, "Block-Entity-Daten (SNBT)");
        s.kv("Typ", beId == null ? "?" : beId, Role.STRING);
        if (ctx.mpClient()) {
            if (notSent.isEmpty()) {
                s.dim("Mehrspieler: Der Server überträgt für diesen Block-Entity-Typ alle angezeigten Felder.");
            } else {
                s.kv("Vom Server nicht übertragen", String.join(", ", notSent), Role.WARN);
                s.dim("Diese Felder existieren auf dem Server, der Client kennt nur Standardwerte – sie sind unten ausgeblendet.");
            }
        }
        if (nbt != null) {
            s.snbt(nbt);
            data.rawNbt = nbt;
        } else {
            s.warn("Block-Entity-Daten nicht lesbar.");
        }
    }

    /**
     * Mehrspieler: Welche Felder hat der Server wirklich geschickt?
     * Vanilla überträgt nur, was {@code toInitialChunkDataNbt} liefert (bzw.
     * Update-Pakete mit demselben Inhalt). Felder, die dort fehlen UND auf dem
     * Client noch den Standardwert eines frischen Block-Entities haben, kennt
     * der Client nicht – sie werden entfernt und als "nicht übertragen" gemeldet.
     */
    private static NbtCompound filterTransmitted(BlockEntity be, NbtCompound full, Ctx ctx, Set<String> notSent) {
        Set<String> sent = Set.of();
        try {
            sent = be.toInitialChunkDataNbt(ctx.registries()).getKeys();
        } catch (RuntimeException ignored) {
        }
        NbtCompound defaults = null;
        try {
            BlockEntity fresh = be.getType().instantiate(be.getPos(), be.getCachedState());
            if (fresh != null) {
                defaults = fresh.createNbtWithIdentifyingData(ctx.registries());
            }
        } catch (RuntimeException ignored) {
        }
        NbtCompound out = full.copy();
        for (String key : full.getKeys()) {
            if (IDENTIFYING.contains(key) || key.equals("components") || sent.contains(key)) {
                continue;
            }
            if (defaults != null && !Objects.equals(defaults.get(key), full.get(key))) {
                continue; // weicht vom Standard ab -> muss vom Server stammen
            }
            notSent.add(key);
            out.remove(key);
        }
        return out;
    }

    // ------------------------------------------------------------ Helfer

    interface ThrowingSupplier<T> {
        T get();
    }

    static <T> T safe(ThrowingSupplier<T> supplier, T fallback) {
        try {
            T v = supplier.get();
            return v != null ? v : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Registry-ID eines Block-Entity-Typs. */
    static final class BlockEntityTypeId {
        static String of(BlockEntity be) {
            Identifier id = Registries.BLOCK_ENTITY_TYPE.getId(be.getType());
            return id != null ? id.toString() : "?";
        }
    }
}
