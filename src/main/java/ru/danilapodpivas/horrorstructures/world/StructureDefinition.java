package ru.danilapodpivas.horrorstructures.world;

import net.minecraft.core.Vec3i;
import java.util.List;

public record StructureDefinition(String name, Vec3i size, int grassLevel, ZoneEffect effect) {
    public static final List<StructureDefinition> ALL = List.of(
            new StructureDefinition("abandoned_park", new Vec3i(230, 106, 220), 3, ZoneEffect.PARK),
            new StructureDefinition("labyrinth", new Vec3i(111, 24, 111), 0, ZoneEffect.LABYRINTH),
            new StructureDefinition("circus", new Vec3i(104, 87, 122), 0, ZoneEffect.CIRCUS),
            new StructureDefinition("planes", new Vec3i(227, 51, 223), 0, ZoneEffect.NONE),
            // The entrance is on the low side of the saved hillside (around Y=125).
            // Anchoring to the highest grass at Y=139 buried it fourteen blocks deep.
            new StructureDefinition("bunker", new Vec3i(249, 152, 182), 125, ZoneEffect.NONE));

    public static StructureDefinition byName(String name) {
        return ALL.stream().filter(definition -> definition.name.equals(name)).findFirst().orElse(null);
    }

    public enum ZoneEffect {
        NONE(""), PARK("abandoned_park"), LABYRINTH("labyrinth"), CIRCUS("circus");
        private final String networkName;
        ZoneEffect(String networkName) { this.networkName = networkName; }
        public String networkName() { return networkName; }
    }
}
