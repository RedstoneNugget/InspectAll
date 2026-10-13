package de.inspectall.client.config;

import de.inspectall.client.data.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Einstellungen: Panel-Position/-Größe, Farben, sichtbare Kategorien, Tooltip-Hinweis … */
public class ConfigScreen extends GameOptionsScreen {

    private static final int W = 150;
    private static final int H = 20;

    public ConfigScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.literal("InspectAll – Einstellungen"));
    }

    @Override
    protected void addOptions() {
        InspectConfig cfg = InspectConfig.get();

        body.addHeader(Text.literal("Panel"));
        CyclingButtonWidget<InspectConfig.PanelPosition> position = CyclingButtonWidget
                .<InspectConfig.PanelPosition>builder(p -> Text.literal(p.label), cfg.panelPosition)
                .values(InspectConfig.PanelPosition.values())
                .build(0, 0, W, H, Text.literal("Position"), (b, v) -> cfg.panelPosition = v);
        body.addWidgetEntry(position, new IntSlider("Breite", "%", 30, 100, () -> cfg.panelWidthPercent, v -> cfg.panelWidthPercent = v));
        body.addWidgetEntry(
                new IntSlider("Deckkraft", "%", 20, 100, () -> cfg.backgroundOpacity, v -> cfg.backgroundOpacity = v),
                new IntSlider("Reichweite", " Blöcke", 4, 256, () -> cfg.lookRange, v -> cfg.lookRange = v));
        body.addWidgetEntry(
                toggle("Tooltip-Hinweis", cfg.tooltipHint, v -> cfg.tooltipHint = v),
                toggle("Overlay bei Sperre", cfg.hudWhenLocked, v -> cfg.hudWhenLocked = v));
        CyclingButtonWidget<Integer> maxLines = CyclingButtonWidget
                .<Integer>builder(v -> Text.literal(v / 1000 + " k"), cfg.maxSnbtLines)
                .values(5000, 20000, 50000, 100000, 250000)
                .build(0, 0, W, H, Text.literal("Max. SNBT-Zeilen"), (b, v) -> cfg.maxSnbtLines = v);
        body.addWidgetEntry(toggle("OP: Server-NBT abfragen", cfg.opQuery, v -> cfg.opQuery = v), maxLines);

        body.addHeader(Text.literal("Sichtbare Kategorien"));
        List<ClickableWidget> cats = new ArrayList<>();
        for (Category c : Category.values()) {
            cats.add(toggle(c.label, cfg.isVisible(c), v -> cfg.setVisible(c, v)));
        }
        for (int i = 0; i < cats.size(); i += 2) {
            body.addWidgetEntry(cats.get(i), i + 1 < cats.size() ? cats.get(i + 1) : null);
        }

        body.addHeader(Text.literal("Farben (#RRGGBB oder #AARRGGBB)"));
        color("Hintergrund", () -> cfg.colorBackground, v -> cfg.colorBackground = v);
        color("Akzent / Rahmen", () -> cfg.colorAccent, v -> cfg.colorAccent = v);
        color("Text", () -> cfg.colorText, v -> cfg.colorText = v);
        color("SNBT-Schlüssel", () -> cfg.colorKey, v -> cfg.colorKey = v);
        color("Strings", () -> cfg.colorString, v -> cfg.colorString = v);
        color("Zahlen", () -> cfg.colorNumber, v -> cfg.colorNumber = v);
        color("Booleans", () -> cfg.colorBoolean, v -> cfg.colorBoolean = v);
        color("Koordinaten", () -> cfg.colorCoord, v -> cfg.colorCoord = v);
        color("Gedimmt / Klammern", () -> cfg.colorDim, v -> cfg.colorDim = v);
        color("Warnungen", () -> cfg.colorWarn, v -> cfg.colorWarn = v);
        color("Markierungen (Welt)", () -> cfg.colorMarker, v -> cfg.colorMarker = v);

        body.addWidgetEntry(ButtonWidget.builder(Text.literal("Alles zurücksetzen"), b -> {
            InspectConfig.resetToDefaults();
            if (client != null) {
                client.setScreen(new ConfigScreen(parent));
            }
        }).dimensions(0, 0, W, H).build(), null);
    }

    private static CyclingButtonWidget<Boolean> toggle(String label, boolean value, Consumer<Boolean> setter) {
        return CyclingButtonWidget.onOffBuilder(value).build(0, 0, W, H, Text.literal(label), (b, v) -> setter.accept(v));
    }

    private void color(String label, Supplier<String> getter, Consumer<String> setter) {
        TextWidget text = new TextWidget(W, H, Text.literal(label), textRenderer);
        TextFieldWidget field = new TextFieldWidget(textRenderer, W, H, Text.literal(label));
        field.setMaxLength(9);
        field.setText(getter.get());
        Integer initial = InspectConfig.tryParse(getter.get());
        if (initial != null) {
            field.setEditableColor(initial | 0xFF000000);
        }
        field.setChangedListener(s -> {
            Integer parsed = InspectConfig.tryParse(s);
            if (parsed != null) {
                setter.accept(s.trim());
                field.setEditableColor(parsed | 0xFF000000);
            } else {
                field.setEditableColor(0xFFFF5555);
            }
        });
        body.addWidgetEntry(text, field);
    }

    @Override
    public void removed() {
        InspectConfig.save();
        super.removed();
    }

    /** Ganzzahl-Schieberegler. */
    private static final class IntSlider extends SliderWidget {
        private final String label;
        private final String unit;
        private final int min;
        private final int max;
        private final Consumer<Integer> setter;

        IntSlider(String label, String unit, int min, int max, Supplier<Integer> getter, Consumer<Integer> setter) {
            super(0, 0, W, H, Text.empty(), (MathHelper.clamp(getter.get(), min, max) - min) / (double) (max - min));
            this.label = label;
            this.unit = unit;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private int current() {
            return (int) Math.round(min + value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + current() + unit));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}
