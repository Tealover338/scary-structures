package ru.danilapodpivas.horrorstructures.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StructureWorldData extends SavedData {
    private static final String FILE_NAME = "horror_structures_locations";
    private final Map<String, Placement> placements = new LinkedHashMap<>();
    private int version = 6;

    static StructureWorldData peek(ServerLevel level) {
        return level.getDataStorage().get(StructureWorldData::load, FILE_NAME);
    }

    public int version() { return version; }

    public static StructureWorldData get(ServerLevel level) {
        StructureWorldData data = level.getDataStorage().computeIfAbsent(StructureWorldData::load, StructureWorldData::new, FILE_NAME);
        data.createLayoutIfNeeded(level);
        return data;
    }

    public Placement getPlacement(String name) { return placements.get(name); }
    public Iterable<Map.Entry<String, Placement>> entries() { return placements.entrySet(); }

    private void createLayoutIfNeeded(ServerLevel level) {
        if (!placements.isEmpty()) return;
        placements.putAll(WorldgenLayout.get(level));
        setDirty();
    }

    public void markGenerated(String name, int originY, boolean generated) {
        Placement current = placements.get(name);
        if (current != null) {
            placements.put(name, new Placement(current.centerX, current.centerZ, originY, generated));
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", version);
        ListTag list = new ListTag();
        placements.forEach((name, placement) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("name", name);
            entry.putInt("center_x", placement.centerX);
            entry.putInt("center_z", placement.centerZ);
            entry.putInt("origin_y", placement.originY);
            entry.putBoolean("generated", placement.generated);
            list.add(entry);
        });
        tag.put("placements", list);
        return tag;
    }

    private static StructureWorldData load(CompoundTag tag) {
        StructureWorldData data = new StructureWorldData();
        data.version = tag.getInt("version");
        ListTag list = tag.getList("placements", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.placements.put(entry.getString("name"), new Placement(entry.getInt("center_x"), entry.getInt("center_z"), entry.getInt("origin_y"), entry.getBoolean("generated")));
        }
        return data;
    }

    public record Placement(int centerX, int centerZ, int originY, boolean generated) {
        public BlockPos locatePos() { return new BlockPos(centerX, originY == Integer.MIN_VALUE ? 80 : originY, centerZ); }
        public BlockPos origin(StructureDefinition definition) {
            return new BlockPos(centerX - definition.size().getX() / 2, originY, centerZ - definition.size().getZ() / 2);
        }
    }

}
