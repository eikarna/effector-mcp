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
