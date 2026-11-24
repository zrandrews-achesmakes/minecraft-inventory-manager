package io.github.zrandrews.invmanager.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import io.github.zrandrews.invmanager.InvManagerConfig;

/**
 * Hot Deposit: puts items from the player's inventory into nearby containers
 * that already hold the same kind of item.
 */
public final class HotDeposit {
	private HotDeposit() {
	}

	private record Target(BlockPos pos, Container container, int[] moved) {
	}

	/** Runs a hot deposit around the block at center. Server side only. */
	public static void deposit(ServerLevel level, BlockPos center, Player player) {
		InvManagerConfig config = InvManagerConfig.get();
		List<Target> targets = findContainers(level, center, player, config.hotDepositRadius);

		if (targets.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.invmanager.deposit.no_containers", config.hotDepositRadius));
			level.playSound(null, center, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.6f, 1.2f);
			return;
		}

		Inventory inventory = player.getInventory();
		int firstSlot = config.hotDepositIncludesHotbar ? 0 : Inventory.SELECTION_SIZE;
		int totalMoved = 0;

		for (int slot = firstSlot; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) continue;

			for (Target target : targets) {
				if (stack.isEmpty()) break;
				if (!containsSameItem(target.container, stack)) continue;
				int before = stack.getCount();
				insert(target.container, stack);
				int moved = before - stack.getCount();
				if (moved > 0) {
					target.moved[0] += moved;
					totalMoved += moved;
				}
			}
			inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
		}

		int containersUsed = 0;
		for (Target target : targets) {
			if (target.moved[0] == 0) continue;
			containersUsed++;
			target.container.setChanged();
			level.sendParticles(ParticleTypes.END_ROD,
					target.pos.getX() + 0.5, target.pos.getY() + 1.1, target.pos.getZ() + 0.5,
					12, 0.3, 0.2, 0.3, 0.02);
		}

		if (totalMoved == 0) {
			player.sendOverlayMessage(Component.translatable("message.invmanager.deposit.nothing"));
			level.playSound(null, center, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.6f, 1.2f);
		} else {
			player.inventoryMenu.broadcastChanges();
			player.sendOverlayMessage(Component.translatable("message.invmanager.deposit.done", totalMoved, containersUsed));
			level.playSound(null, center, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 1.0f, 0.9f);
		}
	}

	/** Containers within radius of center, nearest first. A double chest counts once. */
	private static List<Target> findContainers(ServerLevel level, BlockPos center, Player player, int radius) {
		List<Target> targets = new ArrayList<>();
		for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			BlockEntity be = level.getBlockEntity(mutable);
			if (!Containers.isStorageBlockEntity(be)) continue;
			if (alreadyCovered(targets, (Container) be)) continue;

			BlockPos pos = mutable.immutable();
			Container container = Containers.storageAt(level, pos, player, false);
			if (container != null) targets.add(new Target(pos, container, new int[1]));
		}
		targets.sort(Comparator.comparingDouble(t -> t.pos.distSqr(center)));
		return targets;
	}

	private static boolean alreadyCovered(List<Target> targets, Container half) {
		for (Target target : targets) {
			if (target.container == half) return true;
			if (target.container instanceof CompoundContainer compound && compound.contains(half)) return true;
		}
		return false;
	}

	private static boolean containsSameItem(Container container, ItemStack stack) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (ItemStack.isSameItem(container.getItem(i), stack)) return true;
		}
		return false;
	}

	/** Moves as much of stack as fits into the container: topping up matching stacks first, then empty slots. */
	private static void insert(Container container, ItemStack stack) {
		int size = container.getContainerSize();

		for (int i = 0; i < size && !stack.isEmpty(); i++) {
			ItemStack existing = container.getItem(i);
			if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) continue;
			if (!container.canPlaceItem(i, stack)) continue;
			int limit = Math.min(existing.getMaxStackSize(), container.getMaxStackSize(existing));
			int moved = Math.min(limit - existing.getCount(), stack.getCount());
			if (moved <= 0) continue;
			existing.grow(moved);
			stack.shrink(moved);
		}

		for (int i = 0; i < size && !stack.isEmpty(); i++) {
			if (!container.getItem(i).isEmpty() || !container.canPlaceItem(i, stack)) continue;
			int moved = Math.min(container.getMaxStackSize(stack), stack.getCount());
			container.setItem(i, stack.split(moved));
		}
	}

	/** Draws the edges of the search box with particles for a moment, so players can see the range. */
	public static void showRange(ServerLevel level, BlockPos center) {
		int r = InvManagerConfig.get().hotDepositRadius;
		double minX = center.getX() - r, minY = center.getY() - r, minZ = center.getZ() - r;
		double maxX = center.getX() + r + 1, maxY = center.getY() + r + 1, maxZ = center.getZ() + r + 1;
		double[] xs = {minX, maxX};
		double[] ys = {minY, maxY};
		double[] zs = {minZ, maxZ};

		for (double t = 0; t <= 1.0001; t += 1.0 / (2 * r + 1)) {
			for (double y : ys) for (double z : zs) dot(level, minX + (maxX - minX) * t, y, z);
			for (double x : xs) for (double z : zs) dot(level, x, minY + (maxY - minY) * t, z);
			for (double x : xs) for (double y : ys) dot(level, x, y, minZ + (maxZ - minZ) * t);
		}
		level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.2f);
	}

	private static void dot(ServerLevel level, double x, double y, double z) {
		level.sendParticles(ParticleTypes.WAX_OFF, x, y, z, 1, 0, 0, 0, 0);
	}
}
