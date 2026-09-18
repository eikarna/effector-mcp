package eikarna.effector;

import net.fabricmc.api.ClientModInitializer;
import eikarna.effector.action.ActionQueueController;
import eikarna.effector.action.ContainerController;
import eikarna.effector.action.PlayerActionController;
import eikarna.effector.bridge.HTTPMCPServer;
import eikarna.effector.config.MCPConfig;
import eikarna.effector.utils.ScreenshotUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EffectorModClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("effector-mcp");
	private HTTPMCPServer httpServer;
	
	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Minecraft MCP Client with Enhanced Action Queue & Controllers");

		PlayerActionController playerActionController = new PlayerActionController();
		ContainerController containerController = new ContainerController();
		ActionQueueController actionQueueController = new ActionQueueController(playerActionController, containerController);

		// Register tick end events
		ClientTickEvents.END_CLIENT_TICK.register(ScreenshotUtils::onEndTick);
		ClientTickEvents.END_CLIENT_TICK.register(actionQueueController::onClientTick);
		
		// Register damage & death SSE event watcher
		ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
			private float lastHealth = -1.0f;
			private boolean lastAlive = true;

			@Override
			public void onEndTick(net.minecraft.client.Minecraft client) {
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
		});
		
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
						actionQueueController,
						new eikarna.effector.utils.EntityScanner(),
						true
					);
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
	
	public void onClientShutdown() {
		if (httpServer != null) {
			httpServer.stop();
			LOGGER.info("HTTP MCP Server stopped");
		}
	}
}
