package com.schematicsynchronizer;

import com.schematicsynchronizer.network.ModPackets;
import com.schematicsynchronizer.server.ModServerHandler;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchematicSynchronizer implements ModInitializer {
	public static final String MOD_ID = "schematic-synchronizer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModPackets.registerCommon();
		ModServerHandler.init();
	}
}
