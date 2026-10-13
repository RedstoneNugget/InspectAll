package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screen.Screen;

/** Compile-Stub der Mod-Menu-API (wird nicht ausgeliefert). */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
    S create(Screen parent);
}
