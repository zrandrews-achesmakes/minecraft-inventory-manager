package io.github.zrandrews.invmanager;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import io.github.zrandrews.invmanager.block.HotDepositBlock;
import io.github.zrandrews.invmanager.item.SorterItem;

/** Registers the Sorter item and the Hot Deposit block. */
public class ModContent {
	public static final Item SORTER = registerItem("sorter");
	public static final Block HOT_DEPOSIT = registerHotDeposit();

	private static Item registerItem(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, InvManagerMod.id(name));
		Item item = new SorterItem(new Item.Properties().stacksTo(1).setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static Block registerHotDeposit() {
		Identifier id = InvManagerMod.id("hot_deposit");
		BlockItemId ids = BlockItemId.create(id, id);

		Block block = new HotDepositBlock(BlockBehaviour.Properties.of()
				.strength(2.5f)
				.sound(SoundType.WOOD)
				.setId(ids.block()));
		Registry.register(BuiltInRegistries.BLOCK, ids.block(), block);

		BlockItem item = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(ids.item()));
		Registry.register(BuiltInRegistries.ITEM, ids.item(), item);
		return block;
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(tab -> tab.accept(SORTER));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(tab -> tab.accept(HOT_DEPOSIT.asItem()));
	}
}
