package eikarna.effector.utils;

import com.google.gson.JsonObject;

public interface IBlockScanner {
    JsonObject scanBlocksInArea(JsonObject fromPos, JsonObject toPos, int maxAreaSize);
    JsonObject scanChunk(JsonObject arguments);
    JsonObject getBlockInfo(JsonObject arguments);
    JsonObject getBlock(JsonObject arguments);
    JsonObject auditEnclosure(JsonObject arguments);
    JsonObject getOrthographicSlice(JsonObject arguments);
    JsonObject getPerceptualRadar(JsonObject arguments);
}
