package com.schematicimporter.paste;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Adapts a live {@link ServerLevel} to the {@link PasteLevelOps} interface.
 *
 * <p>Used by {@link PasteExecutor} in production. Delegates each operation to the
 * underlying {@link ServerLevel}.</p>
 */
public class ServerLevelOpsAdapter implements PasteLevelOps {

    private final ServerLevel level;

    public ServerLevelOpsAdapter(ServerLevel level) {
        this.level = level;
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags) {
        return level.setBlock(pos, state, flags);
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return level.getBlockEntity(pos);
    }

    @Override
    public void setChunkForced(int chunkX, int chunkZ, boolean force) {
        level.setChunkForced(chunkX, chunkZ, force);
    }

    @Override
    public boolean spawnEntityFromNbt(CompoundTag entityNbt) {
        return EntityType.create(entityNbt, level).map(entity -> {
            net.minecraft.world.phys.Vec3 pos = net.minecraft.world.phys.Vec3.ZERO;
            if (entityNbt.contains("Pos", net.minecraft.nbt.Tag.TAG_LIST)) {
                net.minecraft.nbt.ListTag posList = entityNbt.getList("Pos", 6);
                if (posList.size() == 3) {
                    pos = new net.minecraft.world.phys.Vec3(
                        posList.getDouble(0), posList.getDouble(1), posList.getDouble(2));
                }
            }
            entity.moveTo(pos.x, pos.y, pos.z, entity.getYRot(), entity.getXRot());
            level.addFreshEntity(entity);
            return true;
        }).orElse(false);
    }
}
