package eikarna.effector.forge;

import eikarna.effector.EffectorModClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EffectorModForge {
    public static final String MODID = "effector_mcp";
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectorModForge.class);

    public EffectorModForge() {
        LOGGER.info("Starting Effector MCP on Forge runtime");
        EffectorModClient.init();
    }
}
