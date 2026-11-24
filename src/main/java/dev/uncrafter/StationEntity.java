package dev.uncrafter;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.*;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import static dev.uncrafter.UncrafterPlus.Mode.*;

final class StationEntity extends BlockEntity implements WorldlyContainer {
    private final NonNullList<ItemStack> buffer = NonNullList.withSize(1, ItemStack.EMPTY);
    private static final int[] INPUT = {0}, NONE = {};
    private String selected = "";
    private int charges;

    StationEntity(BlockPos pos, BlockState state) {
        super(UncrafterPlus.stationType, pos, state);
    }

    void use(ServerLevel level, StationBlock station, ItemStack held, Player player) {
        if (station.mode == STORAGE) {
            var routes = SalvageRecipes.routes(level.getServer().getRecipeManager(), held);
            if (routes.isEmpty()) { tell(player, "no_recipe"); return; }
            int next = 0;
            for (int i = 0; i < routes.size(); i++)
                if (routes.get(i).id().toString().equals(selected)) next = (i + 1) % routes.size();
            var route = routes.get(next);
            selected = route.id().toString();
            setChanged();
            String materials = String.join(", ", route.materials().stream()
                    .map(s -> s.getCount() + " × " + s.getHoverName().getString()).toList());
            tell(player, "preview", route.product().getCount(), held.getHoverName(), materials, next + 1, routes.size());
            return;
        }
        if (station.mode == GLITCH && held.is(Items.EXPERIENCE_BOTTLE)) {
            if (charges >= 64) { tell(player, "charge_full"); return; }
            held.consume(1, player);
            charges++;
            setChanged();
            tell(player, "charged", charges);
            return;
        }
        process(level, station, held, player);
    }

    void inspect(StationBlock station, Player player) {
        if (!isEmpty()) {
            player.getInventory().placeItemBackInInventory(removeItemNoUpdate(0), Prediction.SERVER_ONLY);
            setChanged();
            tell(player, "returned");
        } else if (station.mode == STORAGE && !selected.isEmpty()) {
            tell(player, "selected", selected);
        } else if (station.mode == GLITCH) {
            tell(player, "glitch_help", charges);
        } else {
            tell(player, "help_" + station.mode.name().toLowerCase(Locale.ROOT));
        }
    }

    void automate(ServerLevel level, StationBlock station) {
        if (process(level, station, getItem(0), null)) setChanged();
    }

    private boolean process(ServerLevel level, StationBlock station, ItemStack input, Player player) {
        if (input.isEmpty()) return false;
        if (station.mode == RECYCLE) {
            // Never silently destroy a filled container, named gear, or other customized stack.
            var plain = input.copyWithCount(1);
            if (plain.isDamaged()) plain.setDamageValue(0);
            if (!ItemStack.isSameItemSameComponents(plain, input.getItem().getDefaultInstance()))
                return tell(player, "customized");
            consume(input, 1, player);
            ExperienceOrb.award(level, Vec3.atCenterOf(worldPosition.above()), 1 + station.tier);
            return true;
        }
        if (station.mode == UNENCHANT) return unenchant(level, input, player);
        if (station.mode != UNCRAFT && station.mode != GLITCH) return false;
        var routes = SalvageRecipes.routes(level.getServer().getRecipeManager(), input);
        if (routes.isEmpty()) return tell(player, "no_recipe");
        var route = select(level, routes);
        if (route.tier() > station.tier) return tell(player, "tier", Component.translatable(
                "block.uncrafter_plus." + List.of("stone", "iron", "diamond", "netherite").get(route.tier()) + "_uncrafter"));
        int batch = route.product().getCount();
        if (input.getCount() < batch) return tell(player, "batch", batch, input.getHoverName());
        var materials = route.recover(input);
        if (materials.isEmpty()) return tell(player, "worn");
        int multiplier = 1;
        if (station.mode == GLITCH) {
            if (charges > 0) { charges--; setChanged(); }
            else if (player != null && (player.isCreative() || player.experienceLevel >= 1)) {
                if (!player.isCreative()) player.giveExperienceLevels(-1);
            } else return tell(player, "need_xp");
            multiplier = level.getRandom().nextInt(4) == 0 ? 2 : 1;
        }
        consume(input, batch, player);
        for (int i = 0; i < multiplier; i++)
            for (var stack : materials) output(level, stack.copy());
        if (player != null) tell(player, multiplier == 2 ? "bonus" : "recovered", batch, route.product().getHoverName());
        return true;
    }

    private SalvageRecipes.Route select(ServerLevel level, List<SalvageRecipes.Route> routes) {
        if (routes.size() > 1) for (var face : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(face)) instanceof StationEntity other &&
                    other.getBlockState().getBlock() instanceof StationBlock block && block.mode == STORAGE)
                for (var route : routes) if (route.id().toString().equals(other.selected)) return route;
        }
        return routes.getFirst();
    }

    private boolean unenchant(ServerLevel level, ItemStack input, Player player) {
        var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(input);
        if (enchantments.isEmpty() || input.is(Items.ENCHANTED_BOOK)) return tell(player, "need_enchantment");
        var stripped = input.copyWithCount(1);
        EnchantmentHelper.setEnchantments(stripped, ItemEnchantments.EMPTY);
        consume(input, 1, player);
        output(level, stripped);
        for (var enchantment : enchantments.keySet()) {
            var book = new ItemStack(Items.ENCHANTED_BOOK);
            book.enchant(enchantment, enchantments.getLevel(enchantment));
            output(level, book);
        }
        return true;
    }

    private void output(ServerLevel level, ItemStack stack) {
        Container below = HopperBlockEntity.getContainerAt(level, worldPosition.below());
        if (below != null) {
            stack = HopperBlockEntity.addItem(this, below, stack, Direction.UP);
        }
        if (!stack.isEmpty()) {
            // Ejection is a machine operation, independent of the block-drop gamerule.
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05,
                    worldPosition.getZ() + 0.5, stack);
        }
    }

    private static void consume(ItemStack stack, int count, Player player) {
        if (player == null) stack.shrink(count);
        else stack.consume(count, player);
    }

    private static boolean tell(Player player, String key, Object... args) {
        if (player != null) player.sendOverlayMessage(Component.translatable("hint.uncrafter_plus." + key, args));
        return false;
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, buffer);
        selected = input.getStringOr("SelectedRecipe", "");
        charges = Math.clamp(input.getIntOr("Charges", 0), 0, 64);
    }
    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, buffer);
        output.putString("SelectedRecipe", selected);
        output.putInt("Charges", charges);
    }
    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return getItem(0).isEmpty(); }
    @Override public ItemStack getItem(int slot) { return buffer.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        var taken = ContainerHelper.removeItem(buffer, slot, count);
        if (!taken.isEmpty()) setChanged();
        return taken;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(buffer, slot); }
    @Override public void setItem(int slot, ItemStack stack) { buffer.set(slot, stack); setChanged(); }
    @Override public void clearContent() { buffer.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(this, player); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && ((StationBlock)getBlockState().getBlock()).mode != STORAGE;
    }
    @Override public int[] getSlotsForFace(Direction face) { return face == Direction.DOWN ? NONE : INPUT; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction face) {
        return face != Direction.DOWN && canPlaceItem(slot, stack);
    }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction face) { return false; }
}
