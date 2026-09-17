package cuspymd.mcp.mod;

import net.fabricmc.api.ClientModInitializer;
import cuspymd.mcp.mod.action.ActionQueueController;
import cuspymd.mcp.mod.action.ContainerController;
import cuspymd.mcp.mod.action.PlayerActionController;
import cuspymd.mcp.mod.bridge.HTTPMCPServer;
import cuspymd.mcp.mod.config.MCPConfig;
import cuspymd.mcp.mod.utils.ScreenshotUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCPServerModClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("mcp-server-mod");
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
						new cuspymd.mcp.mod.command.CommandExecutor(config),
						new cuspymd.mcp.mod.utils.PlayerInfoProvider(),
						new cuspymd.mcp.mod.utils.BlockScanner(),
						new cuspymd.mcp.mod.utils.ScreenshotUtils(),
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
