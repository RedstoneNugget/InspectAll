package de.inspectall.client.mixin;

import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Damit H beim Tippen in der Kreativ-Suche nicht das Panel öffnet. */
@Mixin(CreativeInventoryScreen.class)
public interface CreativeInventoryScreenAccessor {
    @Accessor("searchBox")
    TextFieldWidget inspectall$getSearchBox();
}
