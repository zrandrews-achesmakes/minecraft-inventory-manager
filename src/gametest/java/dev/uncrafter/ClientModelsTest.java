package dev.uncrafter;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;

public class ClientModelsTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var models = client.getModelManager();
            var states = models.getBlockStateModelSet();
            var missingItem = models.getItemModel(UncrafterPlus.id("missing_test_item"));
            UncrafterPlus.BLOCKS.forEach((name, block) -> {
                var state = block.defaultBlockState();
                var particle = states.getParticleMaterial(state).sprite().contents().name();
                if (states.get(state) == states.missingModel()
                        || !particle.equals(UncrafterPlus.id("block/" + name + "_side"))
                        || models.getItemModel(UncrafterPlus.id(name)) == missingItem)
                    throw new AssertionError("Incomplete model for " + name);
            });
        });
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            var pos = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos target = player.blockPosition().north(3);
                player.level().setBlockAndUpdate(target, UncrafterPlus.BLOCKS.get("stone_uncrafter").defaultBlockState());
                return target;
            });
            context.waitFor(client -> client.level != null
                    && client.level.getBlockState(pos).is(UncrafterPlus.BLOCKS.get("stone_uncrafter"))
                    && client.level.getBlockEntity(pos) instanceof StationEntity);
        }
    }
}
