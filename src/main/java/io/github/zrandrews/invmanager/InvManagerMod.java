package io.github.zrandrews.invmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class InvManagerMod implements ModInitializer {
	public static final String MOD_ID = "invmanager";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		InvManagerConfig.load();
		ModContent.initialize();
		InvManagerEvents.initialize();
		LOGGER.info("Inventory Manager loaded (hot deposit radius {})", InvManagerConfig.get().hotDepositRadius);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
