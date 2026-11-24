package dev.uncrafter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

final class StationBlock extends BaseEntityBlock {
    final UncrafterPlus.Mode mode;
    final int tier;

    StationBlock(Properties properties, UncrafterPlus.Mode mode, int tier) {
        super(properties);
        this.mode = mode;
        this.tier = tier;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationEntity(pos, state);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || mode == UncrafterPlus.Mode.STORAGE) return null;
        return createTickerHelper(type, UncrafterPlus.stationType, (world, pos, block, entity) -> {
            if (!entity.isEmpty() && (world.getGameTime() + pos.asLong()) % (40 >> tier) == 0)
                entity.automate((ServerLevel) world, this);
        });
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof StationEntity station)
            station.use(server, this, stack, player);
        return InteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof StationEntity station)
            station.inspect(this, player);
        return InteractionResult.SUCCESS;
    }
}
