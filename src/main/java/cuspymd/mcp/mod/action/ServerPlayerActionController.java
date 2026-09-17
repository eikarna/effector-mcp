package cuspymd.mcp.mod.action;

import com.google.gson.JsonObject;

public class ServerPlayerActionController implements IPlayerActionController {
    private JsonObject notAvailable() {
        JsonObject res = new JsonObject();
        res.addProperty("isError", true);
        res.addProperty("error", "Player action tool is not available in dedicated server mode");
        return res;
    }

    @Override
    public JsonObject setLook(JsonObject arguments) {
        return notAvailable();
    }

    @Override
    public JsonObject lookAt(JsonObject arguments) {
        return notAvailable();
    }

    @Override
    public JsonObject interactBlock(JsonObject arguments) {
        return notAvailable();
    }

    @Override
    public JsonObject attackBlock(JsonObject arguments) {
        return notAvailable();
    }

    @Override
    public JsonObject useItem(JsonObject arguments) {
        return notAvailable();
    }

    @Override
    public JsonObject selectSlot(JsonObject arguments) {
        return notAvailable();
    }

    @Override
    public JsonObject swapHands() {
        return notAvailable();
    }
}
