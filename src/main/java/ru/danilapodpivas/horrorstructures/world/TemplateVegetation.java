package ru.danilapodpivas.horrorstructures.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import ru.danilapodpivas.horrorstructures.HorrorStructures;

import java.io.InputStream;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Compact occupancy masks for each saved 16x16 slice. */
final class TemplateVegetation {
    private static final Map<String, Masks> CACHE = new ConcurrentHashMap<>();

    static Masks slice(ServerLevel level, StructureDefinition definition, int x, int z) {
        if (x < 0 || z < 0 || x >= definition.size().getX() || z >= definition.size().getZ()) return null;
        String key = definition.name() + "/" + (x >> 4) + "_" + (z >> 4);
        return CACHE.computeIfAbsent(key, ignored -> load(level, key));
    }

    static boolean contains(ServerLevel level, StructureDefinition definition, int localX, int localY, int localZ) {
        if (localX < 0 || localZ < 0 || localY < 0 || localX >= definition.size().getX()
                || localZ >= definition.size().getZ() || localY >= definition.size().getY()) return false;
        int partX = localX >> 4;
        int partZ = localZ >> 4;
        String key = definition.name() + "/" + partX + "_" + partZ;
        Masks masks = CACHE.computeIfAbsent(key, ignored -> load(level, key));
        return masks.vegetation.get((localY << 8) | ((localZ & 15) << 4) | (localX & 15));
    }

    static boolean containsSolid(ServerLevel level, StructureDefinition definition, int x, int y, int z) {
        return mask(level, definition, x, y, z, false);
    }

    static boolean containsAir(ServerLevel level, StructureDefinition definition, int x, int y, int z) {
        return mask(level, definition, x, y, z, true);
    }

    private static boolean mask(ServerLevel level, StructureDefinition definition, int x, int y, int z, boolean air) {
        if (x < 0 || z < 0 || y < 0 || x >= definition.size().getX()
                || z >= definition.size().getZ() || y >= definition.size().getY()) return false;
        String key = definition.name() + "/" + (x >> 4) + "_" + (z >> 4);
        Masks masks = CACHE.computeIfAbsent(key, ignored -> load(level, key));
        int index = (y << 8) | ((z & 15) << 4) | (x & 15);
        return (air ? masks.air : masks.solid).get(index);
    }

    private static Masks load(ServerLevel level, String key) {
        ResourceLocation binary = ResourceLocation.fromNamespaceAndPath(HorrorStructures.MOD_ID, "occupancy/" + key + ".bin");
        var compiled = level.getServer().getResourceManager().getResource(binary);
        if (compiled.isPresent()) {
            try (var input = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(compiled.get().open()))) {
                int length = input.readInt();
                if (length <= 0 || length > 32768) throw new java.io.IOException("Invalid occupancy length");
                byte[] vegetation = new byte[length], solid = new byte[length], air = new byte[length];
                input.readFully(vegetation); input.readFully(solid); input.readFully(air);
                return new Masks(BitSet.valueOf(vegetation), BitSet.valueOf(solid), BitSet.valueOf(air));
            } catch (java.io.IOException e) {
                HorrorStructures.LOGGER.error("Invalid compiled occupancy for {}; reading template", key, e);
            }
        }
        BitSet vegetation = new BitSet();
        BitSet solid = new BitSet();
        BitSet air = new BitSet();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HorrorStructures.MOD_ID, "worldgen_parts/" + key + ".nbt");
        try {
            Resource resource = level.getServer().getResourceManager().getResourceOrThrow(id);
            try (InputStream stream = resource.open()) {
                CompoundTag tag = NbtIo.readCompressed(stream);
                ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
                boolean[] tree = new boolean[palette.size()];
                boolean[] empty = new boolean[palette.size()];
                for (int i = 0; i < tree.length; i++) {
                    String name = palette.getCompound(i).getString("Name");
                    tree[i] = name.endsWith("_log") || name.endsWith("_wood")
                            || name.endsWith("_leaves") || name.equals("minecraft:vine");
                    empty[i] = name.equals("minecraft:air") || name.equals("minecraft:cave_air")
                            || name.equals("minecraft:void_air");
                }
                ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
                for (int i = 0; i < blocks.size(); i++) {
                    CompoundTag block = blocks.getCompound(i);
                    int state = block.getInt("state");
                    ListTag pos = block.getList("pos", Tag.TAG_INT);
                    int index = (pos.getInt(1) << 8) | (pos.getInt(2) << 4) | pos.getInt(0);
                    if (tree[state]) vegetation.set(index);
                    if (empty[state]) air.set(index);
                    else solid.set(index);
                }
            }
        } catch (Exception exception) {
            HorrorStructures.LOGGER.error("Could not read vegetation mask for {}", key, exception);
        }
        return new Masks(vegetation, solid, air);
    }

    record Masks(BitSet vegetation, BitSet solid, BitSet air) {}
    private TemplateVegetation() {}
}
