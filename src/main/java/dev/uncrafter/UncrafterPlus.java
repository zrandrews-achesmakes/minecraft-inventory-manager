package dev.uncrafter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class UncrafterPlus implements ModInitializer {
    enum Mode { UNCRAFT, RECYCLE, GLITCH, UNENCHANT, STORAGE }
    static final Map<String, StationBlock> BLOCKS = new LinkedHashMap<>();
    static BlockEntityType<StationEntity> stationType;

    @Override public void onInitialize() {
        String[] tiers = {"stone", "iron", "diamond", "netherite"};
        for (int i = 0; i < tiers.length; i++) {
            register(tiers[i] + "_uncrafter", Mode.UNCRAFT, i);
            register(tiers[i] + "_xp_recycler", Mode.RECYCLE, i);
        }
        register("glitched_uncrafter", Mode.GLITCH, 3);
        register("unenchanter", Mode.UNENCHANT, 0);
        register("recipe_storage", Mode.STORAGE, 0);
        stationType = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("station"),
                new BlockEntityType<>(StationEntity::new, Set.copyOf(BLOCKS.values())));
        var tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));
        CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> BLOCKS.values().forEach(output::accept));
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("uncrafter_plus", path);
    }

    private static void register(String name, Mode mode, int tier) {
        var id = id(name);
        var blockKey = ResourceKey.create(Registries.BLOCK, id);
        var block = new StationBlock(BlockBehaviour.Properties.of().setId(blockKey)
                .strength(3.5F).sound(SoundType.METAL).pushReaction(PushReaction.IMMOVEABLE), mode, tier);
        Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
        var itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block,
                new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        BLOCKS.put(name, block);
    }
}
