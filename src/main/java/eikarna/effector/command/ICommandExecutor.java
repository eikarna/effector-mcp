package eikarna.effector.command;

import com.google.gson.JsonObject;

public interface ICommandExecutor {
    JsonObject executeCommands(JsonObject arguments);
}
