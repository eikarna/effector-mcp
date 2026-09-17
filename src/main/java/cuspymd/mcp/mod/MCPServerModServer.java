package cuspymd.mcp.mod;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import cuspymd.mcp.mod.action.ServerActionQueueController;
import cuspymd.mcp.mod.action.ServerContainerController;
import cuspymd.mcp.mod.action.ServerPlayerActionController;
import cuspymd.mcp.mod.bridge.HTTPMCPServer;
import cuspymd.mcp.mod.config.MCPConfig;
import cuspymd.mcp.mod.server.tools.ServerCommandExecutor;
import cuspymd.mcp.mod.server.tools.ServerBlockScanner;
import cuspymd.mcp.mod.server.tools.ServerPlayerInfoProvider;
import cuspymd.mcp.mod.server.tools.ServerScreenshotUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MCPServerModServer implements DedicatedServerModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("mcp-server-mod");
    private HTTPMCPServer httpServer;
    
    @Override
    public void onInitializeServer() {
        LOGGER.info("Initializing Minecraft MCP Server (Dedicated Server Mode)");
        
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
    }
    
    private void onServerStarted(MinecraftServer server) {
        try {
            MCPConfig config = MCPConfig.load();
            if (config.getServer().isAutoStart()) {
                String transport = config.getServer().getTransport();
                
                if ("http".equals(transport)) {
                    httpServer = new HTTPMCPServer(
                        config,
                        new ServerCommandExecutor(config, server),
                        new ServerPlayerInfoProvider(server),
                        new ServerBlockScanner(server),
                        new ServerScreenshotUtils(),
                        new ServerPlayerActionController(),
                        new ServerContainerController(),
                        new ServerActionQueueController(),
                        false
                    );
                    httpServer.start();
                    LOGGER.info("HTTP MCP Server started on port {}", httpServer.getPort());
                } else {
                    LOGGER.warn("Unsupported transport: {}. Only 'http' is supported.", transport);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to start MCP Server", e);
        }
    }
    
    private void onServerStopping(MinecraftServer server) {
        if (httpServer != null) {
            httpServer.stop();
            LOGGER.info("HTTP MCP Server stopped");
        }
    }
}
