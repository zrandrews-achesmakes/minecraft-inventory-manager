package io.github.zrandrews.invmanager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.zrandrews.invmanager.item.SorterItem;
import io.github.zrandrews.invmanager.logic.Containers;
import io.github.zrandrews.invmanager.logic.HotDeposit;

/** Hooks the Sorter and Hot Deposit into clicking blocks and entities. */
public final class InvManagerEvents {
	private static final String STARTER_KIT_TAG = "invmanager.starter_kit";
	/** Holding attack re-fires the punch every tick, so punches are rate limited per player. */
	private static final Map<UUID, Long> lastPunch = new HashMap<>();

	private InvManagerEvents() {
	}

	public static void initialize() {
		// Right-click a container with the Sorter: sort it instead of opening it.
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				sortBlock(player, level, hand, hit.getBlockPos(), false));

		// Punch: Sorter on a container sorts it; anything on a Hot Deposit block deposits.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (player.isSpectator()) return InteractionResult.PASS;

			if (level.getBlockState(pos).is(ModContent.HOT_DEPOSIT) && !player.isShiftKeyDown()) {
				if (level instanceof ServerLevel serverLevel && punchAllowed(player, level)) {
					HotDeposit.deposit(serverLevel, pos, player);
				}
				return InteractionResult.SUCCESS;
			}
			return sortBlock(player, level, hand, pos, true);
		});

		// Right-click or punch a chested llama/donkey/mule, chest boat or chest minecart with the Sorter.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> sortEntity(player, level, hand, entity, false));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> sortEntity(player, level, hand, entity, true));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> giveStarterKit(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> lastPunch.remove(handler.getPlayer().getUUID()));
	}

	private static InteractionResult sortBlock(Player player, Level level, InteractionHand hand, BlockPos pos, boolean punch) {
		ItemStack held = player.getItemInHand(hand);
		if (!(held.getItem() instanceof SorterItem) || player.isSpectator()) return InteractionResult.PASS;
		if (!Containers.isStorageBlockEntity(level.getBlockEntity(pos))) return InteractionResult.PASS;
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (punch && !punchAllowed(player, level)) return InteractionResult.SUCCESS;

		Container container = Containers.storageAt(level, pos, player, true);
		if (container == null) {
			player.sendOverlayMessage(Component.translatable("message.invmanager.locked"));
			return InteractionResult.SUCCESS;
		}
		SorterItem.sortAndReport(level, player, held, container, pos);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult sortEntity(Player player, Level level, InteractionHand hand, Entity entity, boolean punch) {
		ItemStack held = player.getItemInHand(hand);
		if (!(held.getItem() instanceof SorterItem) || player.isSpectator()) return InteractionResult.PASS;
		if (!Containers.hasStorage(entity)) return InteractionResult.PASS;
		if (level.isClientSide()) return InteractionResult.SUCCESS;
		if (punch && !punchAllowed(player, level)) return InteractionResult.SUCCESS;

		Container container = Containers.storageOf(entity, player);
		if (container != null) {
			SorterItem.sortAndReport(level, player, held, container, entity.blockPosition());
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean punchAllowed(Player player, Level level) {
		long now = level.getGameTime();
		Long last = lastPunch.get(player.getUUID());
		if (last != null && now - last < InvManagerConfig.get().punchCooldownTicks && now >= last) return false;
		lastPunch.put(player.getUUID(), now);
		return true;
	}

	private static void giveStarterKit(ServerPlayer player) {
		if (!InvManagerConfig.get().giveStarterKit || player.entityTags().contains(STARTER_KIT_TAG)) return;
		player.addTag(STARTER_KIT_TAG);
		give(player, new ItemStack(ModContent.SORTER));
		give(player, new ItemStack(ModContent.HOT_DEPOSIT.asItem(), 3));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		// Adds to the inventory, or drops it at the player's feet if full.
		player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
	}
}
