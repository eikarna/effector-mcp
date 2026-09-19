package eikarna.effector.action;

import com.google.gson.JsonObject;

public interface IReflexController {
    JsonObject configure(JsonObject arguments);
    JsonObject getStatus();
}
