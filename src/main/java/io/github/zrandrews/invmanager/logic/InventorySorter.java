package io.github.zrandrews.invmanager.logic;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Merges and sorts the items in a range of container slots. */
public final class InventorySorter {
	private InventorySorter() {
	}

	/** Sorts the whole container. Returns how many slots hold items afterwards. */
	public static int sort(Container container, SortMode mode) {
		return sort(container, 0, container.getContainerSize(), mode);
	}

	/**
	 * Sorts slots [from, to) of the container: stacks of the same item are merged,
	 * ordered by the mode, and packed into the first slots of the range.
	 * Unlike the Bedrock add-on, tools, potions, banners, beds etc. are sorted too.
	 */
	public static int sort(Container container, int from, int to, SortMode mode) {
		List<ItemStack> merged = new ArrayList<>();

		for (int slot = from; slot < to; slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) continue;
			ItemStack remaining = stack.copy();

			for (ItemStack existing : merged) {
				if (remaining.isEmpty()) break;
				if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue;
				int limit = Math.min(existing.getMaxStackSize(), container.getMaxStackSize(existing));
				int room = limit - existing.getCount();
				if (room <= 0) continue;
				int moved = Math.min(room, remaining.getCount());
				existing.grow(moved);
				remaining.shrink(moved);
			}
			if (!remaining.isEmpty()) merged.add(remaining);
		}

		merged.sort(mode.comparator());

		// Should never happen (merging only frees slots), but never delete items.
		if (merged.size() > to - from) return -1;

		for (int slot = from; slot < to; slot++) {
			int index = slot - from;
			container.setItem(slot, index < merged.size() ? merged.get(index) : ItemStack.EMPTY);
		}
		container.setChanged();
		return merged.size();
	}
}
