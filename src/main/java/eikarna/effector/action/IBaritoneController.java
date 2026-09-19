package eikarna.effector.action;

import com.google.gson.JsonObject;

public interface IBaritoneController {
    JsonObject gotoPos(JsonObject arguments);
    JsonObject mine(JsonObject arguments);
    JsonObject clearArea(JsonObject arguments);
    JsonObject stop();
    JsonObject buildSchematic(JsonObject arguments);
    JsonObject executeCommand(JsonObject arguments);
    JsonObject getStatus();
}
