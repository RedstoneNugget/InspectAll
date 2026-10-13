package de.inspectall.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.inspectall.client.InspectAllClient;
import de.inspectall.client.data.Category;
import de.inspectall.client.data.Role;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Einstellungen, gespeichert in config/inspectall.json. */
public final class InspectConfig {

    public enum PanelPosition {
        LEFT("Links"), CENTER("Mitte"), RIGHT("Rechts");

        public final String label;

        PanelPosition(String label) {
            this.label = label;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static InspectConfig instance = new InspectConfig();

    // ---- Panel
    public PanelPosition panelPosition = PanelPosition.CENTER;
    public int panelWidthPercent = 72;
    public int backgroundOpacity = 88;

    // ---- Farben (#RRGGBB)
    public String colorBackground = "#101016";
    public String colorAccent = "#FFC21A";
    public String colorText = "#E6E6E6";
    public String colorKey = "#7CC4FF";
    public String colorString = "#98E07E";
    public String colorNumber = "#FFAE5E";
    public String colorBoolean = "#D58CFF";
    public String colorCoord = "#FFE14D";
    public String colorDim = "#9A9AA2";
    public String colorWarn = "#FF7A5C";
    public String colorMarker = "#FFC21A";

    // ---- Kategorien
    public Map<String, Boolean> categories = new LinkedHashMap<>();

    // ---- Sonstiges
    public boolean tooltipHint = true;
    public boolean hudWhenLocked = true;
    /** Mehrspieler mit OP-Rechten: volle NBT über die Vanilla-Abfrage (wie F3+I) holen. */
    public boolean opQuery = true;
    public int lookRange = 64;
    public int maxSnbtLines = 50000;

    public static InspectConfig get() {
        return instance;
    }

    public boolean isVisible(Category category) {
        return categories.getOrDefault(category.name(), true);
    }

    public void setVisible(Category category, boolean visible) {
        categories.put(category.name(), visible);
    }

    public int color(Role role) {
        return switch (role) {
            case TEXT -> parse(colorText, 0xFFE6E6E6);
            case KEY -> parse(colorKey, 0xFF7CC4FF);
            case STRING -> parse(colorString, 0xFF98E07E);
            case NUMBER -> parse(colorNumber, 0xFFFFAE5E);
            case BOOLEAN -> parse(colorBoolean, 0xFFD58CFF);
            case PUNCT, DIM -> parse(colorDim, 0xFF9A9AA2);
            case WARN -> parse(colorWarn, 0xFFFF7A5C);
            case ACCENT -> accent();
            case COORD -> parse(colorCoord, 0xFFFFE14D);
        };
    }

    public int accent() {
        return parse(colorAccent, 0xFFFFC21A);
    }

    public int markerColor() {
        return parse(colorMarker, 0xFFFFC21A);
    }

    public int background() {
        int rgb = parse(colorBackground, 0xFF101016) & 0xFFFFFF;
        int a = Math.round(Math.max(20, Math.min(100, backgroundOpacity)) * 2.55f);
        return (a << 24) | rgb;
    }

    /** "#RRGGBB" oder "#AARRGGBB" → ARGB; bei Fehler der Standardwert. */
    public static int parse(String hex, int fallback) {
        Integer v = tryParse(hex);
        return v != null ? v : fallback;
    }

    public static Integer tryParse(String hex) {
        if (hex == null) {
            return null;
        }
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.toLowerCase(Locale.ROOT).startsWith("0x")) {
            s = s.substring(2);
        }
        try {
            if (s.length() == 6) {
                return 0xFF000000 | Integer.parseUnsignedInt(s, 16);
            }
            if (s.length() == 8) {
                return (int) Long.parseLong(s, 16);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------ Laden/Speichern

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("inspectall.json");
    }

    public static void load() {
        Path path = path();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                InspectConfig loaded = GSON.fromJson(reader, InspectConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | RuntimeException e) {
                InspectAllClient.LOGGER.warn("[InspectAll] Config unlesbar, nutze Standardwerte", e);
                instance = new InspectConfig();
            }
        }
        instance.sanitize();
        save();
    }

    public static void resetToDefaults() {
        instance = new InspectConfig();
        instance.sanitize();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(path().getParent());
            try (Writer writer = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            InspectAllClient.LOGGER.warn("[InspectAll] Config konnte nicht gespeichert werden", e);
        }
    }

    private void sanitize() {
        if (panelPosition == null) {
            panelPosition = PanelPosition.CENTER;
        }
        if (categories == null) {
            categories = new LinkedHashMap<>();
        }
        for (Category c : Category.values()) {
            categories.putIfAbsent(c.name(), true);
        }
        panelWidthPercent = Math.max(30, Math.min(100, panelWidthPercent));
        backgroundOpacity = Math.max(20, Math.min(100, backgroundOpacity));
        lookRange = Math.max(4, Math.min(256, lookRange));
        maxSnbtLines = Math.max(1000, Math.min(500000, maxSnbtLines));
        InspectConfig defaults = new InspectConfig();
        if (tryParse(colorBackground) == null) colorBackground = defaults.colorBackground;
        if (tryParse(colorAccent) == null) colorAccent = defaults.colorAccent;
        if (tryParse(colorText) == null) colorText = defaults.colorText;
        if (tryParse(colorKey) == null) colorKey = defaults.colorKey;
        if (tryParse(colorString) == null) colorString = defaults.colorString;
        if (tryParse(colorNumber) == null) colorNumber = defaults.colorNumber;
        if (tryParse(colorBoolean) == null) colorBoolean = defaults.colorBoolean;
        if (tryParse(colorCoord) == null) colorCoord = defaults.colorCoord;
        if (tryParse(colorDim) == null) colorDim = defaults.colorDim;
        if (tryParse(colorWarn) == null) colorWarn = defaults.colorWarn;
        if (tryParse(colorMarker) == null) colorMarker = defaults.colorMarker;
    }
}
