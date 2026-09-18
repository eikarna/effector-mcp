package eikarna.effector.server.tools;

import com.google.gson.JsonObject;
import eikarna.effector.utils.IEntityScanner;

public class ServerEntityScanner implements IEntityScanner {
    @Override
    public JsonObject scanEntities(JsonObject arguments) {
        JsonObject err = new JsonObject();
        err.addProperty("isError", true);
        err.addProperty("error", "EntityScanner is not supported in dedicated server mode without active client context");
        return err;
    }
}
