package eikarna.effector.action;

import com.google.gson.JsonObject;

public interface IActionQueueController {
    JsonObject executeActions(JsonObject arguments);
    JsonObject cancelActions();
    JsonObject getQueueStatus();
}
