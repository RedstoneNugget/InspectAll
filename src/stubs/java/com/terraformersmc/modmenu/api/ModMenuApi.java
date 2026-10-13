package com.terraformersmc.modmenu.api;

/** Compile-Stub der Mod-Menu-API (wird nicht ausgeliefert). */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> null;
    }
}
