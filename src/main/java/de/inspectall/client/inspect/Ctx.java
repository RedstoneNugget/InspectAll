package de.inspectall.client.inspect;

import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/** Woher die Daten kommen und womit sie (de)serialisiert werden. */
public record Ctx(World world, RegistryWrapper.WrapperLookup registries, Source source, boolean selfPlayer) {

    public enum Source {
        CLIENT_SP("Client – lade Serverdaten …"),
        CLIENT_MP("Mehrspieler: nur übertragene Daten"),
        SERVER_SP("Integrierter Server (vollständig)"),
        OP_QUERY("Server-Abfrage (OP, wie F3+I)");

        public final String label;

        Source(String label) {
            this.label = label;
        }
    }

    /** Client-Daten auf einem fremden Server – hier fehlen ggf. Daten. */
    public boolean mpClient() {
        return source == Source.CLIENT_MP;
    }

    public boolean serverSide() {
        return source == Source.SERVER_SP;
    }

    public RegistryOps<NbtElement> ops() {
        return registries.getOps(NbtOps.INSTANCE);
    }

    public String dimension() {
        return world.getRegistryKey().getValue().toString();
    }
}
