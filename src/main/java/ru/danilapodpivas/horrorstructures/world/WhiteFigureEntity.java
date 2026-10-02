package ru.danilapodpivas.horrorstructures.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import ru.danilapodpivas.horrorstructures.registry.ModEntities;

/** The apparition lives in the world for six ticks and cleans up its temporary light. */
public final class WhiteFigureEntity extends Entity {
    private BlockPos lightPos;

    public WhiteFigureEntity(EntityType<? extends WhiteFigureEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static WhiteFigureEntity create(Level level) {
        return ModEntities.WHITE_FIGURE.get().create(level);
    }

    public void illuminate() {
        if (level().isClientSide) return;
        BlockPos candidate = blockPosition().above();
        if (level().hasChunkAt(candidate) && level().getBlockState(candidate).isAir()) {
            level().setBlockAndUpdate(candidate, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 12));
            lightPos = candidate;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount >= 6) {
            clearLight();
            discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) clearLight();
        super.remove(reason);
    }

    private void clearLight() {
        if (lightPos != null && level().hasChunkAt(lightPos) && level().getBlockState(lightPos).is(Blocks.LIGHT)) {
            level().setBlockAndUpdate(lightPos, Blocks.AIR.defaultBlockState());
        }
        lightPos = null;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("LightX")) lightPos = new BlockPos(tag.getInt("LightX"), tag.getInt("LightY"), tag.getInt("LightZ"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (lightPos != null) {
            tag.putInt("LightX", lightPos.getX());
            tag.putInt("LightY", lightPos.getY());
            tag.putInt("LightZ", lightPos.getZ());
        }
    }
}
