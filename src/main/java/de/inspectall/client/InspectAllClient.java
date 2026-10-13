package de.inspectall.client;

import de.inspectall.client.config.InspectConfig;
import de.inspectall.client.data.Category;
import de.inspectall.client.data.InspectData;
import de.inspectall.client.inspect.Inspector;
import de.inspectall.client.mixin.CreativeInventoryScreenAccessor;
import de.inspectall.client.target.Target;
import de.inspectall.client.target.TargetFinder;
import de.inspectall.client.ui.InspectScreen;
import de.inspectall.client.ui.LockedHud;
import de.inspectall.client.world.WorldMarkers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InspectAllClient implements ClientModInitializer {

    public static final String MOD_ID = "inspectall";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int LOCK_REFRESH_TICKS = 20;

    public static KeyBinding openKey;
    public static KeyBinding lockKey;
    public static KeyBinding clearKey;

    @Nullable
    private static Target lockedTarget;
    @Nullable
    private static InspectData lockedData;
    private static int lockRefresh;

    @Override
    public void onInitializeClient() {
        InspectConfig.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "general"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.inspectall.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, category));
        lockKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.inspectall.lock", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, category));
        clearKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.inspectall.clear_markers", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                open(client, null);
            }
            while (lockKey.wasPressed()) {
                toggleLockFromWorld(client, null);
            }
            while (clearKey.wasPressed()) {
                int n = WorldMarkers.clear();
                actionbar(client, n + " Markierung(en) gelöscht");
            }
            tickLock(client);
        });

        // In Inventar-/Container-Screens: Taste wirkt auf das Item unter dem Mauszeiger
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof HandledScreen<?>) {
                ScreenKeyboardEvents.afterKeyPress(screen).register((s, key) -> {
                    if (isTyping(s)) {
                        return;
                    }
                    if (openKey.matchesKey(key)) {
                        open(client, s);
                    } else if (lockKey.matchesKey(key)) {
                        toggleLockFromWorld(client, s);
                    }
                });
            }
        });

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (InspectConfig.get().tooltipHint && openKey != null && !openKey.isUnbound()) {
                lines.add(Text.empty()
                        .append(openKey.getBoundKeyLocalizedText().copy().formatted(Formatting.DARK_GRAY))
                        .append(Text.literal(": alle Daten").formatted(Formatting.DARK_GRAY)));
            }
        });

        WorldRenderEvents.END_EXTRACTION.register(WorldMarkers::render);
        HudElementRegistry.addLast(Identifier.of(MOD_ID, "locked_target"), new LockedHud());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            WorldMarkers.clear();
            lockedTarget = null;
            lockedData = null;
        }));

        LOGGER.info("[InspectAll] geladen – H: Info-Panel, G: Ziel sperren, J: Markierungen löschen");
    }

    // ------------------------------------------------------------ Panel

    public static void open(MinecraftClient client, @Nullable Screen screen) {
        Target target = null;
        try {
            target = screen == null && lockedTarget != null ? lockedTarget : TargetFinder.find(client, screen);
        } catch (RuntimeException e) {
            LOGGER.warn("[InspectAll] Ziel konnte nicht ermittelt werden", e);
        }
        if (target == null) {
            actionbar(client, screen != null ? "Kein Item unter dem Mauszeiger."
                    : "Kein Ziel: Block oder Entity ansehen bzw. ein Item in die Hand nehmen.");
            return;
        }
        InspectData data = inspectSafely(client, target);
        if (data == null) {
            actionbar(client, "Ziel ist nicht mehr verfügbar (entladen oder entfernt).");
            return;
        }
        client.setScreen(new InspectScreen(screen, target, data));
    }

    /** Wie {@link Inspector#inspect}, aber stürzt nie ab: Fehler landen als Hinweis im Panel. */
    @Nullable
    public static InspectData inspectSafely(MinecraftClient client, Target target) {
        try {
            return Inspector.inspect(client, target);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("[InspectAll] Daten konnten nicht vollständig gelesen werden", e);
            InspectData data = new InspectData(target instanceof Target.ItemTarget ? InspectData.Kind.ITEM
                    : target instanceof Target.EntityTarget ? InspectData.Kind.ENTITY : InspectData.Kind.BLOCK);
            data.name = Text.literal("Fehler beim Lesen");
            data.icon = target instanceof Target.ItemTarget item ? item.stack() : new ItemStack(Items.BARRIER);
            data.section(Category.GENERAL, "Fehler")
                    .warn("Die Daten konnten nicht gelesen werden: " + e)
                    .dim("Details stehen im Log (logs/latest.log).");
            return data;
        }
    }

    // ------------------------------------------------------------ Sperre

    public static boolean hasLock() {
        return lockedTarget != null;
    }

    @Nullable
    public static InspectData lockedData() {
        return lockedData;
    }

    public static boolean isLocked(Target target) {
        return lockedTarget != null && sameTarget(lockedTarget, target);
    }

    /** Sperren/Entsperren aus dem Panel. Rückgabe: true = jetzt gesperrt. */
    public static boolean toggleLock(Target target, InspectData data) {
        if (isLocked(target)) {
            lockedTarget = null;
            lockedData = null;
            return false;
        }
        lockedTarget = target;
        lockedData = data;
        lockRefresh = LOCK_REFRESH_TICKS;
        return true;
    }

    public static void updateLockedData(InspectData data) {
        lockedData = data;
    }

    private static void toggleLockFromWorld(MinecraftClient client, @Nullable Screen screen) {
        if (lockedTarget != null && screen == null) {
            lockedTarget = null;
            lockedData = null;
            actionbar(client, "Ziel entsperrt");
            return;
        }
        Target target = TargetFinder.find(client, screen);
        if (target == null) {
            actionbar(client, "Kein Ziel zum Sperren.");
            return;
        }
        InspectData data = inspectSafely(client, target);
        if (data == null) {
            actionbar(client, "Ziel ist nicht verfügbar.");
            return;
        }
        lockedTarget = target;
        lockedData = data;
        lockRefresh = LOCK_REFRESH_TICKS;
        actionbar(client, "Gesperrt: " + data.name.getString() + " – "
                + openKey.getBoundKeyLocalizedText().getString() + " öffnet das Panel, "
                + lockKey.getBoundKeyLocalizedText().getString() + " entsperrt");
    }

    /** Hält die Overlay-Daten des gesperrten Ziels aktuell (ca. 1× pro Sekunde). */
    private static void tickLock(MinecraftClient client) {
        if (lockedTarget == null || client.world == null) {
            return;
        }
        if (lockedData != null && lockedData.pending != null && lockedData.pending.isDone()) {
            InspectData result = null;
            try {
                result = lockedData.pending.getNow(null);
            } catch (RuntimeException ignored) {
            }
            lockedData.pending = null;
            if (result != null) {
                if (result.iconEntity == null) {
                    result.iconEntity = lockedData.iconEntity;
                }
                lockedData = result;
            }
        }
        if (client.currentScreen instanceof InspectScreen || !InspectConfig.get().hudWhenLocked) {
            return;
        }
        if (--lockRefresh > 0) {
            return;
        }
        lockRefresh = LOCK_REFRESH_TICKS;
        if (lockedData != null && lockedData.isPending()) {
            return;
        }
        if (lockedTarget instanceof Target.ItemTarget) {
            return; // Item-Kopie ändert sich nicht
        }
        InspectData fresh = inspectSafely(client, lockedTarget);
        if (fresh != null) {
            if (fresh.iconEntity == null && lockedData != null) {
                fresh.iconEntity = lockedData.iconEntity;
            }
            lockedData = fresh;
        } else {
            lockedData = null; // Ziel weg – Overlay zeigt "nicht verfügbar"
        }
    }

    private static boolean sameTarget(Target a, Target b) {
        if (a instanceof Target.BlockTarget x && b instanceof Target.BlockTarget y) {
            return x.pos().equals(y.pos()) && x.dimension().equals(y.dimension());
        }
        if (a instanceof Target.EntityTarget x && b instanceof Target.EntityTarget y) {
            return x.uuid().equals(y.uuid());
        }
        if (a instanceof Target.ItemTarget x && b instanceof Target.ItemTarget y) {
            return x == y || x.origin().equals(y.origin()) && ItemStack.areEqual(x.stack(), y.stack());
        }
        return false;
    }

    // ------------------------------------------------------------ Helfer

    private static boolean isTyping(Screen screen) {
        // fokussierte Elemente bis ins innerste Widget verfolgen (z. B. Rezeptbuch-Suche)
        Element focused = screen.getFocused();
        for (int depth = 0; focused != null && depth < 8; depth++) {
            if (focused instanceof TextFieldWidget field) {
                return field.isFocused();
            }
            focused = focused instanceof ParentElement parent ? parent.getFocused() : null;
        }
        if (screen instanceof CreativeInventoryScreen creative) {
            TextFieldWidget search = ((CreativeInventoryScreenAccessor) creative).inspectall$getSearchBox();
            return search != null && search.isFocused() && search.isVisible();
        }
        return false;
    }

    public static void actionbar(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message).formatted(Formatting.GOLD), true);
        }
    }
}
