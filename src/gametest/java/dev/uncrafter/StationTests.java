package dev.uncrafter;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;

public class StationTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private ChestBlockEntity station(GameTestHelper h, String name) {
        h.setBlock(POS.below(), Blocks.CHEST);
        h.setBlock(POS, UncrafterPlus.BLOCKS.get(name));
        return h.getBlockEntity(POS.below(), ChestBlockEntity.class);
    }

    private Player player(GameTestHelper h) { return h.makeMockPlayer(GameType.SURVIVAL); }

    private void click(GameTestHelper h, Player player, ItemStack held, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        h.useBlock(pos, player);
    }

    @GameTest public void batchesAndShapedTags(GameTestHelper h) {
        var chest = station(h, "stone_uncrafter");
        var player = player(h);
        var slabs = new ItemStack(Items.OAK_SLAB, 5);
        click(h, player, slabs, POS);
        h.assertTrue(slabs.getCount() == 5 && chest.isEmpty(), "Partial batch should remain intact");
        slabs.grow(2);
        click(h, player, slabs, POS);
        h.assertTrue(slabs.getCount() == 1 && chest.countItem(Items.OAK_PLANKS) == 3, "Six slabs recover three planks");
        click(h, player, new ItemStack(Items.CRAFTING_TABLE), POS);
        h.assertTrue(chest.countItem(Items.OAK_PLANKS) == 7, "Four tagged ingredients should resolve to oak");
        h.succeed();
    }

    @GameTest public void tierAndDamage(GameTestHelper h) {
        var chest = station(h, "stone_uncrafter");
        var pick = new ItemStack(Items.DIAMOND_PICKAXE);
        var player = player(h);
        click(h, player, pick, POS);
        h.assertTrue(!pick.isEmpty() && chest.isEmpty(), "Stone machine must reject diamond recipe");
        h.setBlock(POS, UncrafterPlus.BLOCKS.get("diamond_uncrafter"));
        pick.setDamageValue(1);
        click(h, player, pick, POS);
        h.assertTrue(pick.isEmpty() && chest.countItem(Items.DIAMOND) == 2 && chest.countItem(Items.STICK) == 1,
                "Worn pick should recover prorated materials");
        h.succeed();
    }

    @GameTest public void recoverInventoryManagerSorter(GameTestHelper h) {
        var chest = station(h, "iron_uncrafter");
        var sorter = new ItemStack(io.github.zrandrews.invmanager.ModContent.SORTER);
        click(h, player(h), sorter, POS);
        h.assertTrue(sorter.isEmpty() && chest.countItem(Items.IRON_INGOT) == 1
                        && chest.countItem(Items.REDSTONE) == 1 && chest.countItem(Items.STICK) == 1,
                "The Iron Uncrafter should dismantle an Inventory Manager Sorter");
        h.succeed();
    }

    @GameTest(maxTicks = 140) public void hopperAndCustomRecipe(GameTestHelper h) {
        var chest = station(h, "stone_uncrafter");
        h.setBlock(POS.above(), Blocks.HOPPER);
        h.getBlockEntity(POS.above(), HopperBlockEntity.class).setItem(0, new ItemStack(Items.ECHO_SHARD, 3));
        h.succeedWhen(() -> h.assertTrue(chest.countItem(Items.QUARTZ) == 2, "Hopper-fed data-pack batch should reach chest"));
    }

    @GameTest public void recipeStorageSelectsAlternative(GameTestHelper h) {
        var chest = station(h, "stone_uncrafter");
        var storage = POS.east();
        h.setBlock(storage, UncrafterPlus.BLOCKS.get("recipe_storage"));
        var player = player(h);
        var stick = new ItemStack(Items.STICK);
        click(h, player, stick, storage);
        click(h, player, stick, storage);
        h.assertTrue(!stick.isEmpty(), "Recipe preview should preserve sample");
        click(h, player, stick, POS);
        h.assertTrue(stick.isEmpty() && chest.countItem(Items.BAMBOO) == 2, "Selected bamboo recipe should be used");
        h.succeed();
    }

    @GameTest public void extractBooksPreservesGear(GameTestHelper h) {
        var chest = station(h, "unenchanter");
        var gear = new ItemStack(Items.DIAMOND_PICKAXE);
        var registry = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var efficiency = registry.getOrThrow(Enchantments.EFFICIENCY);
        var mending = registry.getOrThrow(Enchantments.MENDING);
        gear.enchant(efficiency, 4);
        gear.enchant(mending, 1);
        gear.setDamageValue(57);
        gear.set(DataComponents.CUSTOM_NAME, Component.literal("Miners' pick"));
        click(h, player(h), gear, POS);
        var stripped = chest.getItem(0);
        h.assertTrue(stripped.is(Items.DIAMOND_PICKAXE) && stripped.getDamageValue() == 57 && !stripped.isEnchanted()
                && stripped.getHoverName().getString().equals("Miners' pick"), "Preserve all non-enchantment gear data");
        int eff = 0, mend = 0;
        for (int i = 1; i < chest.getContainerSize(); i++) {
            var ench = chest.getItem(i).getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            eff += ench.getLevel(efficiency);
            mend += ench.getLevel(mending);
        }
        h.assertTrue(chest.countItem(Items.ENCHANTED_BOOK) == 2 && eff == 4 && mend == 1, "Both real book enchantments retain their levels");
        h.succeed();
    }

    @GameTest public void recyclerProtectsMetadata(GameTestHelper h) {
        station(h, "iron_xp_recycler");
        var player = player(h);
        var stack = new ItemStack(Items.COBBLESTONE, 2);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this"));
        click(h, player, stack, POS);
        h.assertTrue(stack.getCount() == 2, "Do not destroy a named item");
        stack.remove(DataComponents.CUSTOM_NAME);
        click(h, player, stack, POS);
        h.assertTrue(stack.getCount() == 1 && !h.getEntities(EntityTypes.EXPERIENCE_ORB, POS.above(), 3).isEmpty(),
                "Consume one item and spawn XP");
        h.succeed();
    }

    @GameTest public void glitchNeedsPayment(GameTestHelper h) {
        var chest = station(h, "glitched_uncrafter");
        var player = player(h);
        var pick = new ItemStack(Items.DIAMOND_PICKAXE);
        click(h, player, pick, POS);
        h.assertTrue(!pick.isEmpty() && chest.isEmpty(), "Reject unpaid input");
        player.giveExperienceLevels(1);
        click(h, player, pick, POS);
        int diamonds = chest.countItem(Items.DIAMOND);
        h.assertTrue(pick.isEmpty() && player.experienceLevel == 0 && (diamonds == 3 || diamonds == 6),
                "Charge one level and recover a single or double batch");
        h.succeed();
    }

    @GameTest public void catalogAndRemainders(GameTestHelper h) {
        var manager = h.getLevel().getServer().getRecipeManager();
        var counts = new java.util.TreeMap<String,Integer>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM)
            for (var route : SalvageRecipes.routes(manager, new ItemStack(item)))
                counts.merge(route.id().getNamespace(), 1, Integer::sum);
        h.assertTrue(counts.getOrDefault("minecraft", 0) > 600, "Expected at least 600 vanilla reverse recipes");
        h.assertTrue(counts.getOrDefault("uncrafter_plus", 0) == 11, "All eleven workstation recipes must load");
        h.assertTrue(SalvageRecipes.routes(manager, new ItemStack(Items.CAKE)).isEmpty(), "Skip recipes that return buckets");
        System.out.println("Uncrafter GameTest recipe counts by namespace: " + counts);
        h.succeed();
    }

    @GameTest public void bufferSurvivesSaveAndLoad(GameTestHelper h) {
        station(h, "stone_uncrafter");
        var entity = h.getBlockEntity(POS, StationEntity.class);
        entity.setItem(0, new ItemStack(Items.OAK_SLAB, 5));
        var lookup = h.getLevel().registryAccess();
        var saved = entity.saveWithFullMetadata(lookup);
        var loaded = (StationEntity)BlockEntity.loadStatic(entity.getBlockPos(), entity.getBlockState(), saved, lookup);
        h.assertTrue(loaded != null && loaded.getItem(0).getCount() == 5 && loaded.getItem(0).is(Items.OAK_SLAB),
                "A buffered partial batch must survive world save/load");
        h.succeed();
    }

    @GameTest public void fullChestEjectsOutput(GameTestHelper h) {
        var chest = station(h, "stone_uncrafter");
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        click(h, player(h), new ItemStack(Items.OAK_SLAB, 6), POS);
        h.assertItemEntityCountIs(Items.OAK_PLANKS, POS.above(), 3, 3);
        h.succeed();
    }
}
