package io.github.zrandrews.invmanager.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import io.github.zrandrews.invmanager.InvManagerConfig;
import io.github.zrandrews.invmanager.logic.InventorySorter;
import io.github.zrandrews.invmanager.logic.SortMode;

/**
 * The Sorter.
 * <ul>
 * <li>Right-click or punch a chest, barrel or shulker box: sort it.</li>
 * <li>Right-click or punch a llama/donkey/mule with a chest, a chest boat or chest minecart: sort it.</li>
 * <li>Right-click the air: sort your own inventory.</li>
 * <li>Sneak + right-click the air: switch sort mode (Generic, Alphabetical, Color).</li>
 * </ul>
 * Block and entity clicks are handled in {@link io.github.zrandrews.invmanager.InvManagerEvents}.
 */
public class SorterItem extends Item {
	private static final String MODE_KEY = "invmanager_mode";

	public SorterItem(Properties properties) {
		super(properties);
	}

	public static SortMode getMode(ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return SortMode.byIndex(data.copyTag().getIntOr(MODE_KEY, 0));
	}

	public static void setMode(ItemStack stack, SortMode mode) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(MODE_KEY, mode.ordinal()));
	}

	public static Component modeName(SortMode mode) {
		return Component.translatable("sortmode.invmanager." + mode.key);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) return InteractionResult.SUCCESS;

		if (player.isSecondaryUseActive()) {
			SortMode mode = getMode(stack).next();
			setMode(stack, mode);
			player.sendOverlayMessage(Component.translatable("message.invmanager.mode", modeName(mode)));
			level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.0f);
		} else {
			Inventory inventory = player.getInventory();
			int from = InvManagerConfig.get().sortIncludesHotbar ? 0 : Inventory.SELECTION_SIZE;
			InventorySorter.sort(inventory, from, Inventory.INVENTORY_SIZE, getMode(stack));
			player.inventoryMenu.broadcastChanges();
			player.sendOverlayMessage(Component.translatable("message.invmanager.sorted.inventory", modeName(getMode(stack))));
			level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.6f, 1.2f);
		}
		return InteractionResult.SUCCESS;
	}

	/** Sorts a container the player clicked and tells them. Server side. */
	public static void sortAndReport(Level level, Player player, ItemStack sorter, Container container, net.minecraft.core.BlockPos soundPos) {
		SortMode mode = getMode(sorter);
		InventorySorter.sort(container, mode);
		player.sendOverlayMessage(Component.translatable("message.invmanager.sorted.container", modeName(mode)));
		level.playSound(null, soundPos, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.BLOCKS, 0.6f, 1.0f);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.invmanager.sorter.mode", modeName(getMode(stack))).withStyle(ChatFormatting.GOLD));
		builder.accept(Component.translatable("tooltip.invmanager.sorter.line1").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.invmanager.sorter.line2").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.invmanager.sorter.line3").withStyle(ChatFormatting.GRAY));
	}
}
