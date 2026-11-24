package dev.uncrafter;

import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

final class SalvageRecipes {
    record Route(Identifier id, ItemStack product, List<ItemStack> materials, int tier) {
        List<ItemStack> recover(ItemStack input) {
            var result = new ArrayList<ItemStack>();
            for (var material : materials) {
                int count = material.getCount();
                if (input.isDamaged()) {
                    count = (int) ((long) count * (input.getMaxDamage() - input.getDamageValue()) / input.getMaxDamage());
                }
                if (count > 0) result.add(material.copyWithCount(count));
            }
            return result;
        }
    }

    // Values contain item stacks and ids only. Old recipe managers can be collected after /reload.
    private static final Map<RecipeManager, Map<Item, List<Route>>> CACHE = new WeakHashMap<>();

    static List<Route> routes(RecipeManager manager, ItemStack input) {
        var routes = CACHE.computeIfAbsent(manager, SalvageRecipes::index).getOrDefault(input.getItem(), List.of());
        ItemStack normalized = input.copy();
        if (normalized.isDamaged()) normalized.setDamageValue(0);
        return routes.stream().filter(r -> ItemStack.isSameItemSameComponents(normalized, r.product())).toList();
    }

    private static Map<Item, List<Route>> index(RecipeManager manager) {
        Map<Item, List<Route>> byItem = new HashMap<>();
        for (var entry : manager.getRecipes()) {
            var recipe = entry.value();
            if (recipe.getClass() != ShapedRecipe.class && recipe.getClass() != ShapelessRecipe.class) continue;
            var crafting = (CraftingRecipe) recipe;
            var product = crafting.assemble(CraftingInput.EMPTY);
            if (product.isEmpty() || product.getCount() > product.getMaxStackSize()) continue;
            var materials = materials(crafting, product);
            if (materials.isEmpty()) continue;
            int tier = materials.stream().mapToInt(s -> tierOf(s.getItem())).max().orElse(0);
            byItem.computeIfAbsent(product.getItem(), ignore -> new ArrayList<>()).add(
                    new Route(entry.id().identifier(), product, materials, tier));
        }
        byItem.forEach((item, routes) -> {
            var itemId = BuiltInRegistries.ITEM.getKey(item);
            routes.sort(Comparator.comparing((Route r) -> !r.id().equals(itemId)).thenComparing(Route::id));
        });
        return byItem;
    }

    private static List<ItemStack> materials(CraftingRecipe recipe, ItemStack product) {
        var counts = new LinkedHashMap<Item, Integer>();
        int interchangeableWithProduct = 0;
        for (var ingredient : recipe.placementInfo().ingredients()) {
            // A one-for-one recoloring recipe could refund the input item plus a free dye.
            if (ingredient.test(product) && ++interchangeableWithProduct >= product.getCount()) return List.of();
            // Use a consistent representative of a tag; prefer oak for generic wood recipes.
            var items = ingredient.items().map(Holder::value).toList();
            if (items.isEmpty() || items.stream().anyMatch(i -> i.getCraftingRemainder() != null)) return List.of();
            var representative = items.stream().filter(i -> ingredient.test(i.getDefaultInstance()))
                    .min(Comparator.comparing((Item i) -> !BuiltInRegistries.ITEM.getKey(i)
                    .getPath().startsWith("oak_")).thenComparing(BuiltInRegistries.ITEM::getKey));
            if (representative.isEmpty()) return List.of();
            var chosen = representative.get();
            counts.merge(chosen, 1, Integer::sum);
        }
        return counts.entrySet().stream().map(e -> new ItemStack(e.getKey(), e.getValue())).toList();
    }

    private static int tierOf(Item item) {
        String name = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (name.contains("netherite") || name.equals("ancient_debris")) return 3;
        if (name.contains("diamond") || name.equals("emerald") || name.equals("emerald_block")) return 2;
        if (name.contains("iron") || name.contains("gold_ingot") || name.equals("gold_block")) return 1;
        return 0;
    }
}
