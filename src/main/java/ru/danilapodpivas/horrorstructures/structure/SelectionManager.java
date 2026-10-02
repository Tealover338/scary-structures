package ru.danilapodpivas.horrorstructures.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SelectionManager {
    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();
    public static void setFirst(ServerPlayer player, BlockPos pos) {
        Selection old = SELECTIONS.get(player.getUUID());
        SELECTIONS.put(player.getUUID(), new Selection(pos.immutable(), old == null ? null : old.second()));
        player.displayClientMessage(Component.translatable("message.horror_structures.first_position", pos.getX(), pos.getY(), pos.getZ()), true);
    }
    public static void setSecond(ServerPlayer player, BlockPos pos) {
        Selection old = SELECTIONS.get(player.getUUID());
        SELECTIONS.put(player.getUUID(), new Selection(old == null ? null : old.first(), pos.immutable()));
        player.displayClientMessage(Component.translatable("message.horror_structures.second_position", pos.getX(), pos.getY(), pos.getZ()), true);
    }
    public static Selection get(ServerPlayer player) { return SELECTIONS.get(player.getUUID()); }
    public record Selection(BlockPos first, BlockPos second) {
        public boolean isComplete() { return first != null && second != null; }
        public BlockPos minimum() { return new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ())); }
        public BlockPos maximum() { return new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ())); }
    }
    private SelectionManager() {}
}
