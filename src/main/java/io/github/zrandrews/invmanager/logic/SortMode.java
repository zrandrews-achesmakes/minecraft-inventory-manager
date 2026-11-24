package io.github.zrandrews.invmanager.logic;

import java.util.Comparator;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/** The three ways the Sorter can order items, same as the Bedrock add-on. */
public enum SortMode {
	/** Groups items the way the game registers them: blocks together, tools together, food together... */
	GENERIC("generic"),
	/** A to Z by the item's displayed name (renamed items sort by their new name). */
	ALPHABETICAL("alphabetical"),
	/** By color around the rainbow, then grays/whites/blacks, then items without a clear color. */
	COLOR("color");

	public final String key;

	SortMode(String key) {
		this.key = key;
	}

	public SortMode next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public static SortMode byIndex(int index) {
		SortMode[] all = values();
		return index >= 0 && index < all.length ? all[index] : GENERIC;
	}

	public Comparator<ItemStack> comparator() {
		Comparator<ItemStack> byRegistry = Comparator.comparingInt(s -> BuiltInRegistries.ITEM.getId(s.getItem()));
		Comparator<ItemStack> byName = Comparator.comparing(s -> s.getHoverName().getString().toLowerCase(Locale.ROOT));
		Comparator<ItemStack> tieBreak = byName.thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());

		return switch (this) {
			case GENERIC -> byRegistry.thenComparing(tieBreak);
			case ALPHABETICAL -> byName.thenComparing(byRegistry).thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());
			case COLOR -> Comparator.comparingDouble(SortMode::colorKey).thenComparing(byRegistry).thenComparing(tieBreak);
		};
	}

	// Dye names, longest first so "light_blue" wins over "blue".
	private static final DyeColor[] DYES_BY_NAME_LENGTH = java.util.Arrays.stream(DyeColor.values())
			.sorted(Comparator.comparingInt((DyeColor d) -> d.getName().length()).reversed())
			.toArray(DyeColor[]::new);

	/**
	 * A number that puts colored items in rainbow order (0..360), then
	 * low-color items by brightness (1000..1001), then items with no color (2000).
	 */
	static double colorKey(ItemStack stack) {
		int rgb = colorOf(stack);
		if (rgb < 0) return 2000;

		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;

		// Whites, grays, blacks: after the rainbow, light to dark.
		if (max == 0 || delta / max < 0.18f) return 1000 + (1 - max);

		float hue;
		if (max == r) hue = 60 * (((g - b) / delta) % 6);
		else if (max == g) hue = 60 * (((b - r) / delta) + 2);
		else hue = 60 * (((r - g) / delta) + 4);
		if (hue < 0) hue += 360;
		return hue;
	}

	/** The item's main color as 0xRRGGBB, or -1 if it has none. */
	private static int colorOf(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (DyeColor dye : DYES_BY_NAME_LENGTH) {
			String name = dye.getName();
			if (path.equals(name) || path.startsWith(name + "_") || path.endsWith("_" + name) || path.contains("_" + name + "_")) {
				return dye.getTextureDiffuseColor() & 0xFFFFFF;
			}
		}
		if (stack.getItem() instanceof BlockItem blockItem) {
			int col = blockItem.getBlock().defaultMapColor().col;
			if (col != 0) return col & 0xFFFFFF;
		}
		return -1;
	}
}
