package cuspymd.mcp.mod.action;

import com.google.gson.JsonObject;

public interface IContainerController {
    JsonObject getOpenContainer();
    JsonObject clickSlot(JsonObject arguments);
    JsonObject clickButton(JsonObject arguments);
    JsonObject closeContainer();
}
