package eikarna.effector.utils;

import com.google.gson.JsonObject;

public interface IBlockScanner {
    JsonObject scanBlocksInArea(JsonObject fromPos, JsonObject toPos, int maxAreaSize);
    JsonObject scanChunk(JsonObject arguments);
}
