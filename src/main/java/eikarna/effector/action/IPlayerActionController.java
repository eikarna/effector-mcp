package eikarna.effector.action;

import com.google.gson.JsonObject;

public interface IPlayerActionController {
    JsonObject setLook(JsonObject arguments);
    JsonObject lookAt(JsonObject arguments);
    JsonObject interactBlock(JsonObject arguments);
    JsonObject placeBlock(JsonObject arguments);
    JsonObject updateSign(JsonObject arguments);
    JsonObject attackBlock(JsonObject arguments);
    JsonObject mineBlock(JsonObject arguments);
    JsonObject attackEntity(JsonObject arguments);
    JsonObject interactEntity(JsonObject arguments);
    JsonObject useItem(JsonObject arguments);
    JsonObject selectSlot(JsonObject arguments);
    JsonObject swapHands();
}
