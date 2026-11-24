package io.github.zrandrews.invmanager.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.zrandrews.invmanager.ModContent;
import io.github.zrandrews.invmanager.item.SorterItem;
import io.github.zrandrews.invmanager.logic.InventorySorter;
import io.github.zrandrews.invmanager.logic.SortMode;
import io.github.zrandrews.invmanager.mixin.AbstractHorseAccessor;

/** Server game tests: {@code gradlew runGameTest}. Each test gets an empty 8x8x8 box. */
public class InvManagerGameTest {

	// ------------------------------------------------------------------ helpers

	private static ChestBlockEntity chest(GameTestHelper h, int x, int y, int z) {
		h.setBlock(x, y, z, Blocks.CHEST);
		return (ChestBlockEntity) h.getBlockEntity(new BlockPos(x, y, z), ChestBlockEntity.class);
	}

	private static List<Item> itemsInOrder(Container c, int from, int to) {
		List<Item> items = new ArrayList<>();
		for (int i = from; i < to; i++) {
			if (!c.getItem(i).isEmpty()) items.add(c.getItem(i).getItem());
		}
		return items;
	}

	private static int count(Container c, Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
		}
		return n;
	}

	/** Non-empty slots must come first, with no gaps. */
	private static void assertPacked(GameTestHelper h, Container c, int from, int to) {
		boolean seenEmpty = false;
		for (int i = from; i < to; i++) {
			boolean empty = c.getItem(i).isEmpty();
			h.assertFalse(seenEmpty && !empty, "gap before slot " + i);
			if (empty) seenEmpty = true;
		}
	}

	private static Player playerWithSorter(GameTestHelper h, SortMode mode) {
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		ItemStack sorter = new ItemStack(ModContent.SORTER);
		SorterItem.setMode(sorter, mode);
		player.setItemInHand(InteractionHand.MAIN_HAND, sorter);
		return player;
	}

	// ------------------------------------------------------------------ sorting

	@GameTest
	public void genericSortMergesAndPacks(GameTestHelper h) {
		ChestBlockEntity chest = chest(h, 1, 1, 1);
		chest.setItem(3, new ItemStack(Items.DIRT, 10));
		chest.setItem(7, new ItemStack(Items.STONE, 5));
		chest.setItem(12, new ItemStack(Items.DIRT, 20));
		chest.setItem(20, new ItemStack(Items.DIAMOND, 1));
		chest.setItem(26, new ItemStack(Items.DIRT, 40));

		InventorySorter.sort(chest, SortMode.GENERIC);

		h.assertValueEqual(count(chest, Items.DIRT), 70, "dirt total");
		h.assertValueEqual(itemsInOrder(chest, 0, 27).size(), 4, "stacks after merge (stone, dirt 64, dirt 6, diamond)");
		assertPacked(h, chest, 0, 27);
		List<Item> order = itemsInOrder(chest, 0, 27);
		for (int i = 1; i < order.size(); i++) {
			h.assertTrue(BuiltInRegistries.ITEM.getId(order.get(i - 1)) <= BuiltInRegistries.ITEM.getId(order.get(i)), "generic order");
		}
		h.assertValueEqual(chest.getItem(order.indexOf(Items.DIRT)).getCount(), 64, "first dirt stack is full");
		h.succeed();
	}

	@GameTest
	public void alphabeticalSort(GameTestHelper h) {
		ChestBlockEntity chest = chest(h, 1, 1, 1);
		chest.setItem(5, new ItemStack(Items.STONE));
		chest.setItem(9, new ItemStack(Items.APPLE));
		chest.setItem(1, new ItemStack(Items.DIAMOND));

		InventorySorter.sort(chest, SortMode.ALPHABETICAL);

		h.assertValueEqual(itemsInOrder(chest, 0, 27), List.of(Items.APPLE, Items.DIAMOND, Items.STONE), "A-Z order");
		h.succeed();
	}

	@GameTest
	public void colorSort(GameTestHelper h) {
		ChestBlockEntity chest = chest(h, 1, 1, 1);
		chest.setItem(0, new ItemStack(Items.STICK));
		chest.setItem(1, new ItemStack(Items.WOOL.white()));
		chest.setItem(2, new ItemStack(Items.WOOL.blue()));
		chest.setItem(3, new ItemStack(Items.WOOL.lime()));
		chest.setItem(4, new ItemStack(Items.WOOL.red()));

		InventorySorter.sort(chest, SortMode.COLOR);

		h.assertValueEqual(itemsInOrder(chest, 0, 27),
				List.of(Items.WOOL.red(), Items.WOOL.lime(), Items.WOOL.blue(), Items.WOOL.white(), Items.STICK), "rainbow order");
		h.succeed();
	}

	@GameTest
	public void toolsAndPotionsAreSortedToo(GameTestHelper h) {
		ChestBlockEntity chest = chest(h, 1, 1, 1);
		ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
		pick.setDamageValue(100);
		chest.setItem(10, pick);
		chest.setItem(11, new ItemStack(Items.POTION));
		chest.setItem(20, new ItemStack(Items.BED.red()));

		InventorySorter.sort(chest, SortMode.ALPHABETICAL);

		assertPacked(h, chest, 0, 27);
		h.assertValueEqual(itemsInOrder(chest, 0, 27).size(), 3, "nothing lost");
		h.assertTrue(chest.getItem(itemsInOrder(chest, 0, 27).indexOf(Items.DIAMOND_PICKAXE)).getDamageValue() == 100, "pickaxe keeps its damage");
		h.succeed();
	}

	// ------------------------------------------------------------------ Sorter item

	@GameTest
	public void rightClickDoubleChestWithSorter(GameTestHelper h) {
		BlockState left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT);
		BlockState right = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT);
		h.setBlock(new BlockPos(2, 1, 2), left);
		h.setBlock(new BlockPos(3, 1, 2), right);
		BlockPos clicked = h.absolutePos(new BlockPos(2, 1, 2));
		Container combined = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, h.getLevel().getBlockState(clicked), h.getLevel(), clicked, true);
		h.assertValueEqual(combined.getContainerSize(), 54, "double chest size");
		combined.setItem(50, new ItemStack(Items.EMERALD, 3));
		combined.setItem(30, new ItemStack(Items.EMERALD, 4));

		Player player = playerWithSorter(h, SortMode.GENERIC);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false);
		InteractionResult result = UseBlockCallback.EVENT.invoker().interact(player, h.getLevel(), InteractionHand.MAIN_HAND, hit);

		h.assertTrue(result.consumesAction(), "sorter click handled (chest not opened)");
		h.assertTrue(combined.getItem(0).is(Items.EMERALD) && combined.getItem(0).getCount() == 7, "emeralds merged into slot 0 of the double chest");
		h.succeed();
	}

	@GameTest
	public void punchChestWithSorter(GameTestHelper h) {
		ChestBlockEntity chest = chest(h, 1, 1, 1);
		chest.setItem(26, new ItemStack(Items.IRON_INGOT, 5));
		Player player = playerWithSorter(h, SortMode.GENERIC);

		InteractionResult result = AttackBlockCallback.EVENT.invoker()
				.interact(player, h.getLevel(), InteractionHand.MAIN_HAND, h.absolutePos(new BlockPos(1, 1, 1)), Direction.UP);

		h.assertTrue(result.consumesAction(), "punch handled (chest not broken)");
		h.assertTrue(chest.getItem(0).is(Items.IRON_INGOT), "sorted to slot 0");
		h.succeed();
	}

	@GameTest
	public void sorterInAirSortsInventoryButNotHotbar(GameTestHelper h) {
		Player player = playerWithSorter(h, SortMode.ALPHABETICAL);
		Inventory inv = player.getInventory();
		inv.setItem(1, new ItemStack(Items.STONE));            // hotbar: must stay
		inv.setItem(30, new ItemStack(Items.STONE, 3));
		inv.setItem(15, new ItemStack(Items.APPLE, 2));
		inv.setItem(35, new ItemStack(Items.APPLE, 5));

		player.getMainHandItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);

		h.assertTrue(inv.getItem(1).is(Items.STONE) && inv.getItem(1).getCount() == 1, "hotbar untouched");
		h.assertTrue(inv.getItem(9).is(Items.APPLE) && inv.getItem(9).getCount() == 7, "apples merged first");
		h.assertTrue(inv.getItem(10).is(Items.STONE) && inv.getItem(10).getCount() == 3, "then stone");
		h.assertTrue(inv.getItem(30).isEmpty() && inv.getItem(35).isEmpty(), "old slots cleared");
		h.succeed();
	}

	@GameTest
	public void sneakUseCyclesMode(GameTestHelper h) {
		Player player = playerWithSorter(h, SortMode.GENERIC);
		player.setShiftKeyDown(true);

		player.getMainHandItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
		h.assertValueEqual(SorterItem.getMode(player.getMainHandItem()), SortMode.ALPHABETICAL, "first cycle");
		player.getMainHandItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
		h.assertValueEqual(SorterItem.getMode(player.getMainHandItem()), SortMode.COLOR, "second cycle");
		player.getMainHandItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
		h.assertValueEqual(SorterItem.getMode(player.getMainHandItem()), SortMode.GENERIC, "wraps around");
		h.succeed();
	}

	@GameTest
	public void sorterSortsDonkeyChest(GameTestHelper h) {
		Donkey donkey = h.spawn(EntityTypes.DONKEY, new BlockPos(3, 1, 3));
		// Put a chest on the donkey the way a player does (this also builds its chest inventory).
		donkey.setTamed(true);
		Player owner = h.makeMockPlayer(GameType.SURVIVAL);
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST));
		donkey.mobInteract(owner, InteractionHand.MAIN_HAND);
		h.assertTrue(donkey.hasChest(), "donkey got a chest");
		Container inv = ((AbstractHorseAccessor) donkey).invmanager$getInventory();
		int last = inv.getContainerSize() - 1;
		inv.setItem(last, new ItemStack(Items.WHEAT, 10));
		inv.setItem(1, new ItemStack(Items.WHEAT, 5));

		Player player = playerWithSorter(h, SortMode.GENERIC);
		InteractionResult result = UseEntityCallback.EVENT.invoker()
				.interact(player, h.getLevel(), InteractionHand.MAIN_HAND, donkey, new EntityHitResult(donkey));

		h.assertTrue(result.consumesAction(), "click handled (donkey menu not opened)");
		h.assertTrue(inv.getItem(0).is(Items.WHEAT) && inv.getItem(0).getCount() == 15, "donkey chest merged and sorted");
		h.succeed();
	}

	// ------------------------------------------------------------------ Hot Deposit

	@GameTest
	public void hotDepositMovesMatchingItems(GameTestHelper h) {
		h.setBlock(1, 1, 1, ModContent.HOT_DEPOSIT);
		ChestBlockEntity cobbleChest = chest(h, 3, 1, 1);
		cobbleChest.setItem(0, new ItemStack(Items.COBBLESTONE, 1));
		h.setBlock(1, 1, 3, Blocks.BARREL);
		Container barrel = (Container) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(1, 1, 3)));
		barrel.setItem(4, new ItemStack(Items.OAK_LOG, 1));
		ChestBlockEntity emptyChest = chest(h, 5, 1, 5);

		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		Inventory inv = player.getInventory();
		inv.setItem(0, new ItemStack(Items.COBBLESTONE, 10));  // hotbar: stays
		inv.setItem(9, new ItemStack(Items.COBBLESTONE, 30));
		inv.setItem(10, new ItemStack(Items.OAK_LOG, 5));
		inv.setItem(11, new ItemStack(Items.DIAMOND, 3));      // no chest has diamonds: stays

		InteractionResult result = AttackBlockCallback.EVENT.invoker()
				.interact(player, h.getLevel(), InteractionHand.MAIN_HAND, h.absolutePos(new BlockPos(1, 1, 1)), Direction.UP);

		h.assertTrue(result.consumesAction(), "punch handled (block not broken)");
		h.assertValueEqual(count(cobbleChest, Items.COBBLESTONE), 31, "cobblestone deposited");
		h.assertValueEqual(count(barrel, Items.OAK_LOG), 6, "logs deposited into the barrel");
		h.assertTrue(inv.getItem(9).isEmpty() && inv.getItem(10).isEmpty(), "player slots emptied");
		h.assertValueEqual(inv.getItem(0).getCount(), 10, "hotbar kept");
		h.assertValueEqual(inv.getItem(11).getCount(), 3, "diamonds kept");
		h.assertTrue(emptyChest.isEmpty(), "chest without matches stays empty");
		h.assertBlockPresent(ModContent.HOT_DEPOSIT, 1, 1, 1);
		h.succeed();
	}

	@GameTest
	public void sneakPunchBreaksHotDepositNormally(GameTestHelper h) {
		h.setBlock(1, 1, 1, ModContent.HOT_DEPOSIT);
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);

		InteractionResult result = AttackBlockCallback.EVENT.invoker()
				.interact(player, h.getLevel(), InteractionHand.MAIN_HAND, h.absolutePos(new BlockPos(1, 1, 1)), Direction.UP);

		h.assertValueEqual(result, InteractionResult.PASS, "sneak-punch passes through to normal breaking");
		h.succeed();
	}
}
