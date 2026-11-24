package io.github.zrandrews.invmanager.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import io.github.zrandrews.invmanager.logic.HotDeposit;

/**
 * Hot Deposit block.
 * Punch it: items in your inventory go into nearby chests/barrels/shulker boxes that already hold them.
 * Right-click it: shows the search range. Sneak + punch: break it normally.
 * Punching is handled in {@link io.github.zrandrews.invmanager.InvManagerEvents}.
 */
public class HotDepositBlock extends Block {
	public HotDepositBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel serverLevel) {
			HotDeposit.showRange(serverLevel, pos);
		}
		return InteractionResult.SUCCESS;
	}
}
