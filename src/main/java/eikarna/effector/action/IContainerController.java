package eikarna.effector.action;

import com.google.gson.JsonObject;

public interface IContainerController {
    JsonObject getOpenContainer();
    JsonObject clickSlot(JsonObject arguments);
    JsonObject clickButton(JsonObject arguments);
    JsonObject closeContainer();
}
