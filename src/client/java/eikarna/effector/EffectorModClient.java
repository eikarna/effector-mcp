package eikarna.effector;

import eikarna.effector.action.ActionQueueController;
import eikarna.effector.action.ContainerController;
import eikarna.effector.action.PlayerActionController;
import eikarna.effector.bridge.HTTPMCPServer;
import eikarna.effector.config.MCPConfig;
import eikarna.effector.utils.ScreenshotUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EffectorModClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("effector-mcp");
	private static HTTPMCPServer httpServer;
	private static boolean initialized = false;
	private static ActionQueueController actionQueueControllerInstance;
	
	@Override
	public void onInitializeClient() {
		init();
		
		// Register tick end events for Fabric / Quilt
		try {
			ClientTickEvents.END_CLIENT_TICK.register(ScreenshotUtils::onEndTick);
			if (actionQueueControllerInstance != null) {
				ClientTickEvents.END_CLIENT_TICK.register(actionQueueControllerInstance::onClientTick);
			}
			ClientTickEvents.END_CLIENT_TICK.register(eikarna.effector.action.AutonomousReflexController.getInstance()::onClientTick);
			ClientTickEvents.END_CLIENT_TICK.register(EffectorModClient::processDamageAndDeath);
		} catch (Throwable t) {
			LOGGER.warn("Fabric tick events not registered: {}", t.getMessage());
		}
	}

	public static synchronized void init() {
		if (initialized) return;
		initialized = true;

		LOGGER.info("Initializing Minecraft MCP Client with Enhanced Action Queue & Controllers (Cross-Loader)");

		PlayerActionController playerActionController = new PlayerActionController();
		ContainerController containerController = new ContainerController();
		actionQueueControllerInstance = new ActionQueueController(playerActionController, containerController);

		try {
			MCPConfig config = MCPConfig.load();
			if (config.getServer().isAutoStart()) {
				String transport = config.getServer().getTransport();
				
				if ("http".equals(transport)) {
					httpServer = new HTTPMCPServer(
						config,
						new eikarna.effector.command.CommandExecutor(config),
						new eikarna.effector.utils.PlayerInfoProvider(),
						new eikarna.effector.utils.BlockScanner(),
						new eikarna.effector.utils.ScreenshotUtils(),
						playerActionController,
						containerController,
						actionQueueControllerInstance,
						new eikarna.effector.utils.EntityScanner(),
						true
					);
					httpServer.setBaritoneController(new eikarna.effector.action.BaritoneController());
					httpServer.setReflexController(eikarna.effector.action.AutonomousReflexController.getInstance());
					httpServer.start();
					LOGGER.info("HTTP MCP Server started with ActionQueue on port {}", httpServer.getPort());
				} else {
					LOGGER.warn("Unsupported transport: {}. Only 'http' is supported.", transport);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to start MCP Server", e);
		}
	}

	private static float lastHealth = -1.0f;
	private static boolean lastAlive = true;

	public static void processDamageAndDeath(net.minecraft.client.Minecraft client) {
		if (client.player != null && client.level != null) {
			float curHealth = client.player.getHealth();
			float maxHealth = client.player.getMaxHealth();
			boolean alive = client.player.isAlive();

			if (lastHealth >= 0.0f && curHealth < lastHealth) {
				com.google.gson.JsonObject dmg = new com.google.gson.JsonObject();
				dmg.addProperty("previous_health", Math.round(lastHealth * 10.0f) / 10.0f);
				dmg.addProperty("current_health", Math.round(curHealth * 10.0f) / 10.0f);
				dmg.addProperty("max_health", Math.round(maxHealth * 10.0f) / 10.0f);
				dmg.addProperty("damage", Math.round((lastHealth - curHealth) * 10.0f) / 10.0f);
				dmg.addProperty("timestamp", System.currentTimeMillis());
				eikarna.effector.bridge.EventBroadcaster.getInstance().broadcast("damage_taken", dmg);
			}

			if (lastAlive && !alive) {
				com.google.gson.JsonObject death = new com.google.gson.JsonObject();
				death.addProperty("timestamp", System.currentTimeMillis());
				death.addProperty("x", Math.round(client.player.getX() * 100.0) / 100.0);
				death.addProperty("y", Math.round(client.player.getY() * 100.0) / 100.0);
				death.addProperty("z", Math.round(client.player.getZ() * 100.0) / 100.0);
				eikarna.effector.bridge.EventBroadcaster.getInstance().broadcast("player_died", death);
			}

			lastHealth = curHealth;
			lastAlive = alive;
		} else {
			lastHealth = -1.0f;
			lastAlive = true;
		}
	}
	
	public static void onClientShutdown() {
		if (httpServer != null) {
			httpServer.stop();
			LOGGER.info("HTTP MCP Server stopped");
		}
	}
}
