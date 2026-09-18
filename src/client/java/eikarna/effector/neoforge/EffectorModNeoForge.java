package eikarna.effector.neoforge;

import eikarna.effector.EffectorModClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EffectorModNeoForge {
    public static final String MODID = "effector_mcp";
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectorModNeoForge.class);

    public EffectorModNeoForge() {
        LOGGER.info("Starting Effector MCP on NeoForge runtime");
        EffectorModClient.init();
    }
}
