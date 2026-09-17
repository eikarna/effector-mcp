package eikarna.effector.action;

import com.google.gson.JsonObject;

public class ServerActionQueueController implements IActionQueueController {
    private JsonObject notAvailable() {
        JsonObject res = new JsonObject();
        res.addProperty("isError", true);
        res.addProperty("error", "Action queue is not available in dedicated server mode");
        return res;
    }

    @Override
    public JsonObject executeActions(JsonObject arguments) {
        return notAvailable();
    }

    @Override
    public JsonObject cancelActions() {
        return notAvailable();
    }

    @Override
    public JsonObject getQueueStatus() {
        return notAvailable();
    }
}
