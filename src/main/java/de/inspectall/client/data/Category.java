package de.inspectall.client.data;

/** Ein-/ausblendbare Kategorien im Info-Panel (siehe Einstellungen). */
public enum Category {
    GENERAL("Allgemein"),
    SUMMARY("Zusammenfassung"),
    BLOCKSTATE("Blockstate"),
    BLOCK_PROPERTIES("Block-Eigenschaften"),
    ENVIRONMENT("Umgebung"),
    ITEM_PROPERTIES("Item-Eigenschaften"),
    ENTITY_STATS("Entity-Werte"),
    EQUIPMENT("Ausrüstung"),
    EFFECTS("Effekte"),
    ATTRIBUTES("Attribute"),
    TAGS("Tags"),
    COMPONENTS("Komponenten"),
    NBT("Block-/Entity-SNBT");

    public final String label;

    Category(String label) {
        this.label = label;
    }
}
