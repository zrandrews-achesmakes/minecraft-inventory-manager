package io.github.zrandrews.invmanager.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import io.github.zrandrews.invmanager.mixin.AbstractHorseAccessor;

/** Finds the storage container behind a block or an entity. */
public final class Containers {
	private Containers() {
	}

	/** Chests (incl. trapped and copper chests), barrels and shulker boxes. Same list as the Bedrock add-on. */
	public static boolean isStorageBlockEntity(BlockEntity be) {
		return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
	}

	/**
	 * The container at pos, or null. A double chest returns both halves as one container.
	 * If fillLoot, unopened loot is generated first (as if the player opened it); and respects locked containers.
	 */
	public static Container storageAt(Level level, BlockPos pos, Player player, boolean fillLoot) {
		BlockEntity be = level.getBlockEntity(pos);
		if (!isStorageBlockEntity(be)) return null;
		if (be instanceof BaseContainerBlockEntity base && !base.canOpen(player)) return null;

		BlockState state = level.getBlockState(pos);
		Container container = (Container) be;
		if (state.getBlock() instanceof ChestBlock chestBlock) {
			Container combined = ChestBlock.getContainer(chestBlock, state, level, pos, true);
			if (combined != null) container = combined;
		}
		if (!fillLoot) return container;
		unpackLoot(level, pos, player);
		if (container != be) {
			// The other half of a double chest may also hold unopened loot.
			for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				unpackLoot(level, pos.relative(dir), player);
			}
		}
		return container;
	}

	private static void unpackLoot(Level level, BlockPos pos, Player player) {
		if (level.getBlockEntity(pos) instanceof RandomizableContainer randomizable) {
			randomizable.unpackLootTable(player);
		}
	}

	/** True if the entity carries a chest the Sorter can sort (llama/donkey/mule with a chest, chest boat, chest minecart). */
	public static boolean hasStorage(Entity entity) {
		if (entity instanceof AbstractChestedHorse horse) return horse.hasChest();
		return entity instanceof ContainerEntity;
	}

	/** The entity's chest, or null. */
	public static Container storageOf(Entity entity, Player player) {
		if (entity instanceof AbstractChestedHorse horse) {
			if (!horse.hasChest()) return null;
			Container inventory = ((AbstractHorseAccessor) horse).invmanager$getInventory();
			return inventory.getContainerSize() > 0 ? inventory : null;
		}
		if (entity instanceof ContainerEntity chestVehicle) {
			chestVehicle.unpackChestVehicleLootTable(player);
			return chestVehicle;
		}
		return null;
	}
}
