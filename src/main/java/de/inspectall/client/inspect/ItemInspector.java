package de.inspectall.client.inspect;

import de.inspectall.client.data.Category;
import de.inspectall.client.data.InspectData;
import de.inspectall.client.data.Role;
import de.inspectall.client.data.Section;
import de.inspectall.client.export.Exporter;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.Block;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.EnchantableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Sammelt alle Daten zu einem Item-Stack (Client-Thread). Items werden vollständig an den Client übertragen. */
public final class ItemInspector {

    private ItemInspector() {
    }

    /** @param origin z. B. "Haupthand" oder "Inventar-Slot 5" */
    public static InspectData inspect(ItemStack stack, World world, String origin) {
        Ctx ctx = new Ctx(world, world.getRegistryManager(), Ctx.Source.CLIENT_SP, false);
        RegistryOps<NbtElement> ops = ctx.ops();
        Item item = stack.getItem();
        Identifier id = Registries.ITEM.getId(item);

        InspectData data = new InspectData(InspectData.Kind.ITEM);
        data.name = stack.getName();
        data.icon = stack.copy();
        data.registryId = id.toString();
        data.namespace = id.getNamespace();
        data.modName = Nbts.modName(id.getNamespace());
        data.source = "Client (Items sind vollständig)";

        general(data, stack, item, id, origin);
        properties(data, stack, world);
        summaries(data, stack, ops);
        tags(data, stack);
        components(data, stack, ops);
        data.giveCommand = Exporter.giveCommand(stack, ops);
        return data.sortSections();
    }

    private static void general(InspectData data, ItemStack stack, Item item, Identifier id, String origin) {
        Section s = data.section(Category.GENERAL, "Allgemein");
        s.kv("Name", stack.getName().getString(), Role.STRING);
        Text custom = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (custom != null) {
            s.kv("Eigener Name (custom_name)", custom.getString(), Role.STRING);
            s.kv("Item-Name", stack.getItemName().getString(), Role.STRING);
        }
        s.kv("Registry-ID", id.toString(), Role.STRING);
        s.kv("Namespace / Mod", id.getNamespace() + " – " + data.modName, Role.TEXT);
        s.kv("Übersetzungsschlüssel", item.getTranslationKey(), Role.DIM);
        s.kv("Anzahl", stack.getCount());
        s.kv("Herkunft", origin, Role.TEXT);
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            s.kv("Platziert Block", Registries.BLOCK.getId(block).toString(), Role.STRING);
        }
    }

    private static void properties(InspectData data, ItemStack stack, World world) {
        Section s = data.section(Category.ITEM_PROPERTIES, "Item-Eigenschaften");
        if (stack.isDamageable()) {
            int max = stack.getMaxDamage();
            int dmg = stack.getDamage();
            s.kv("Haltbarkeit", (max - dmg) + " / " + max + "  (Schaden " + dmg + ", " + Nbts.pct(max - dmg, max) + ")", Role.NUMBER);
        } else {
            s.kv("Haltbarkeit", stack.contains(DataComponentTypes.UNBREAKABLE) ? "unzerbrechlich" : "– (nicht beschädigbar)", Role.DIM);
        }
        if (stack.contains(DataComponentTypes.UNBREAKABLE) && stack.isDamageable()) {
            s.kv("Unzerbrechlich", "ja", Role.BOOLEAN);
        }
        s.kv("Max. Stapelgröße", stack.getMaxCount());
        s.kv("Rarität", stack.getRarity().asString(), Role.STRING);

        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (food != null) {
            s.kv("Nahrung", food.nutrition() + " Hungerpunkte, Sättigung " + Nbts.fmt(food.saturation())
                    + (food.canAlwaysEat() ? ", immer essbar" : ""), Role.NUMBER);
        } else {
            s.kv("Nahrung", "– (nicht essbar)", Role.DIM);
        }
        ConsumableComponent consumable = stack.get(DataComponentTypes.CONSUMABLE);
        if (consumable != null) {
            s.kv("Verbrauchsdauer", Nbts.fmt(consumable.consumeSeconds()) + " s (" + consumable.useAction().asString() + ")", Role.NUMBER);
            if (!consumable.onConsumeEffects().isEmpty()) {
                s.kv("Effekte beim Verbrauch", consumable.onConsumeEffects().size());
            }
        }
        int fuel = BlockInspector.safe(() -> world.getFuelRegistry().getFuelTicks(stack), 0);
        s.kv("Brennzeit", fuel > 0 ? Nbts.ticks(fuel) + " – schmilzt " + Nbts.fmt(fuel / 200.0) + " Items" : "– (kein Brennstoff)",
                fuel > 0 ? Role.NUMBER : Role.DIM);

        EnchantableComponent enchantable = stack.get(DataComponentTypes.ENCHANTABLE);
        if (enchantable != null) {
            s.kv("Verzauberbarkeit", enchantable.value());
        }
        enchantments(s, "Verzauberungen", stack.getEnchantments());
        ItemEnchantmentsComponent stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) {
            enchantments(s, "Gespeicherte Verzauberungen", stored);
        }
        AttributeModifiersComponent attributes = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attributes != null && !attributes.modifiers().isEmpty()) {
            s.sub("Attribut-Modifikatoren");
            for (AttributeModifiersComponent.Entry e : attributes.modifiers()) {
                String attr = e.attribute().getKey().map(k -> k.getValue().toString()).orElse("?");
                s.kv(attr, Nbts.fmt(e.modifier().value()) + " (" + e.modifier().operation().asString() + ", Slot "
                        + e.slot().asString() + ", " + e.modifier().id() + ")", Role.NUMBER);
            }
        }
        ToolComponent tool = stack.get(DataComponentTypes.TOOL);
        if (tool != null) {
            s.kv("Werkzeug", "Grundgeschwindigkeit " + Nbts.fmt(tool.defaultMiningSpeed()) + ", " + tool.rules().size()
                    + " Regel(n), " + tool.damagePerBlock() + " Schaden/Block", Role.NUMBER);
        }
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            s.sub("Beschreibung (lore)");
            for (Text line : lore.lines()) {
                s.kv("  ", line.getString(), Role.STRING);
            }
        }
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        if (model != null) {
            s.kv("Item-Modell", model.toString(), Role.STRING);
        }
    }

    private static void enchantments(Section s, String title, @Nullable ItemEnchantmentsComponent enchants) {
        if (enchants == null || enchants.isEmpty()) {
            return;
        }
        s.sub(title + " (" + enchants.getSize() + ")");
        for (var entry : enchants.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> ench = entry.getKey();
            int level = entry.getIntValue();
            String eid = ench.getKey().map(k -> k.getValue().toString()).orElse("?");
            s.kv(Enchantment.getName(ench, level).getString(), eid + " – Stufe " + level, Role.NUMBER);
        }
    }

    // ------------------------------------------------------------ Sonderformate

    private static void summaries(InspectData data, ItemStack stack, RegistryOps<NbtElement> ops) {
        Item item = stack.getItem();
        // Bienennest / Bienenstock als Item
        if (item instanceof BlockItem bi && bi.getBlock() instanceof BeehiveBlock) {
            Section s = data.section(Category.SUMMARY, "Bienennest / Bienenstock");
            Integer honey = 0;
            BlockStateComponent bs = stack.get(DataComponentTypes.BLOCK_STATE);
            if (bs != null && !bs.isEmpty()) {
                try {
                    Integer v = bs.getValue(BeehiveBlock.HONEY_LEVEL);
                    honey = v != null ? v : 0;
                } catch (RuntimeException e) {
                    honey = null;
                }
            }
            NbtElement bees = Nbts.encodeComponent(DataComponentTypes.BEES, stack.get(DataComponentTypes.BEES), ops);
            SpecialSummaries.hive(s, honey, bees instanceof NbtList l ? l : new NbtList(), false);
        }

        // Container-Items (Shulkerkisten u. a.)
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            Section s = data.section(Category.SUMMARY, "Container-Inhalt (minecraft:container)");
            List<ItemStack> stacks = container.stream().toList();
            int filled = 0;
            for (ItemStack st : stacks) {
                if (!st.isEmpty()) {
                    filled++;
                }
            }
            s.kv("Belegte Slots", filled + " / " + stacks.size(), Role.NUMBER);
            for (int i = 0; i < stacks.size(); i++) {
                if (!stacks.get(i).isEmpty()) {
                    s.kv("Slot " + i, Nbts.stackLabel(stacks.get(i)), Role.TEXT);
                }
            }
            if (filled == 0) {
                s.dim("leer");
            }
        }
        BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            Section s = data.section(Category.SUMMARY, "Bündel-Inhalt");
            s.kv("Belegung", bundle.getOccupancy().toString(), Role.NUMBER);
            int i = 0;
            for (ItemStack st : bundle.iterate()) {
                s.kv("#" + (++i), Nbts.stackLabel(st), Role.TEXT);
            }
        }

        // Block-Entity-Daten im Item (Spawner, Schild, Truhe mit NBT …)
        TypedEntityData<?> beData = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (beData != null) {
            String beId = beData.getType() instanceof net.minecraft.block.entity.BlockEntityType<?> t
                    ? String.valueOf(Registries.BLOCK_ENTITY_TYPE.getId(t)) : null;
            NbtCompound nbt = beData.copyNbtWithoutId();
            InspectData tmp = new InspectData(InspectData.Kind.BLOCK);
            SpecialSummaries.block(tmp, null, beId, nbt, Set.of(), null, false, ops);
            for (Section sec : tmp.sections) {
                Section copy = data.section(Category.SUMMARY, sec.title + " (block_entity_data)");
                copy.entries.addAll(sec.entries);
            }
        }

        // Bücher
        if (stack.contains(DataComponentTypes.WRITTEN_BOOK_CONTENT) || stack.contains(DataComponentTypes.WRITABLE_BOOK_CONTENT)) {
            SpecialSummaries.bookInfo(data.section(Category.SUMMARY, "Buch"), stack, null);
        }

        PotionContentsComponent potion = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potion != null) {
            Section s = data.section(Category.SUMMARY, "Trank-Inhalt");
            s.kv("Trank", potion.potion().flatMap(RegistryEntry::getKey).map(k -> k.getValue().toString()).orElse("keiner"), Role.STRING);
            for (StatusEffectInstance effect : potion.getEffects()) {
                s.kv(EntityInspector.effectName(effect), EntityInspector.effectValue(effect), Role.NUMBER);
            }
            potion.customColor().ifPresent(c -> s.kv("Eigene Farbe", String.format("#%06X", c & 0xFFFFFF), Role.STRING));
        }

        LodestoneTrackerComponent lodestone = stack.get(DataComponentTypes.LODESTONE_TRACKER);
        if (lodestone != null) {
            Section s = data.section(Category.SUMMARY, "Leitstein-Kompass");
            if (lodestone.target().isPresent()) {
                var target = lodestone.target().get();
                s.coord("Ziel", target.pos(), target.dimension().getValue().toString(), "Leitstein");
                s.kv("Dimension", target.dimension().getValue().toString(), Role.STRING);
            } else {
                s.kv("Ziel", "keins", Role.DIM);
            }
            s.kv("Verfolgt Leitstein", Section.yesNo(lodestone.tracked()), Role.BOOLEAN);
        }
        MapIdComponent map = stack.get(DataComponentTypes.MAP_ID);
        if (map != null) {
            data.section(Category.SUMMARY, "Karte").kv("Karten-ID", map.id());
        }
        TypedEntityData<?> entityData = stack.get(DataComponentTypes.ENTITY_DATA);
        if (entityData != null) {
            Section s = data.section(Category.SUMMARY, "Entity-Daten im Item (entity_data)");
            s.kv("Entity-Typ", String.valueOf(entityData.getType() instanceof net.minecraft.entity.EntityType<?> t
                    ? Registries.ENTITY_TYPE.getId(t) : entityData.getType()), Role.STRING);
            s.kv("Felder", entityData.copyNbtWithoutId().getSize());
        }
    }

    private static void tags(InspectData data, ItemStack stack) {
        List<String> tags = stack.streamTags().map(TagKey::id).map(Identifier::toString).sorted().toList();
        Section s = data.section(Category.TAGS, "Item-Tags (" + tags.size() + ")");
        s.collapsedByDefault = tags.size() > 6;
        if (tags.isEmpty()) {
            s.dim("Keine Tags.");
        }
        for (String tag : tags) {
            s.kv("#" + tag, "", Role.DIM);
        }
    }

    // ------------------------------------------------------------ Komponenten

    private static void components(InspectData data, ItemStack stack, RegistryOps<NbtElement> ops) {
        // 1) Stack-SNBT (id, count, geänderte Komponenten) – genau das, was gespeichert/übertragen wird
        NbtElement stackNbt = Nbts.encodeStack(stack, ops);
        Section changed = data.section(Category.COMPONENTS, "Item-SNBT (gespeichert, mit geänderten Komponenten)");
        int changes = stack.getComponentChanges().size();
        changed.kv("Geänderte Komponenten", changes);
        if (stackNbt != null) {
            changed.snbt(stackNbt);
            data.rawNbt = stackNbt;
        } else {
            changed.warn("Item-Stack ließ sich nicht kodieren.");
        }

        // 2) Alle Komponenten inkl. Standardwerte
        Section all = data.section(Category.COMPONENTS, "Alle Datenkomponenten (inkl. Standardwerte)");
        Map<String, NbtElement> sorted = new TreeMap<>();
        List<String> transientTypes = new ArrayList<>();
        for (Component<?> c : stack.getComponents()) {
            ComponentType<?> type = c.type();
            String cid = Nbts.componentId(type);
            NbtElement value = Nbts.encodeComponent(type, c.value(), ops);
            if (value != null) {
                sorted.put(cid, value);
            } else if (c.value() instanceof Unit) {
                sorted.put(cid, new NbtCompound());
            } else {
                transientTypes.add(cid);
            }
        }
        NbtCompound allNbt = new NbtCompound();
        for (Map.Entry<String, NbtElement> e : sorted.entrySet()) {
            allNbt.put(e.getKey(), e.getValue());
        }
        List<String> changedIds = new ArrayList<>();
        for (var e : stack.getComponentChanges().entrySet()) {
            changedIds.add((e.getValue().isPresent() ? "" : "!") + Nbts.componentId(e.getKey()));
        }
        all.kv("Anzahl", sorted.size());
        if (!changedIds.isEmpty()) {
            all.kv("Davon geändert / entfernt", String.join(", ", changedIds), Role.STRING);
        }
        if (!transientTypes.isEmpty()) {
            all.kv("Nicht serialisierbar (nur Laufzeit)", String.join(", ", transientTypes), Role.DIM);
        }
        all.snbt(allNbt);
    }
}
