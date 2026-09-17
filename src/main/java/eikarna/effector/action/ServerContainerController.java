package eikarna.effector.action;

import com.google.gson.JsonObject;

public class ServerContainerController implements IContainerController {
    private JsonObject notAvailable() {
        JsonObject res = new JsonObject();
        res.addProperty("isError", true);
        res.addProperty("error", "Container interaction is not available in dedicated server mode");
        return res;
    }

    @Override
    public JsonObject getOpenContainer() {
        return notAvailable();
    }

    @Override
    public JsonObject clickSlot(JsonObject arguments) {
        return notAvailable();
    }

    @Override
    public JsonObject clickButton(JsonObject arguments) {
        return notAvailable();
    }

    @Override
    public JsonObject closeContainer() {
        return notAvailable();
    }
}
