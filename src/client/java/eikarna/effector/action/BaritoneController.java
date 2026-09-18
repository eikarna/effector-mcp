package eikarna.effector.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class BaritoneController implements IBaritoneController {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaritoneController.class);

    public BaritoneController() {
        tryConfigureSettings();
    }

    private void tryConfigureSettings() {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Method getSettingsMethod = apiClass.getMethod("getSettings");
            Object settings = getSettingsMethod.invoke(null);
            if (settings != null) {
                setSetting(settings, "chatControl", false);
                setSetting(settings, "allowBreak", true);
                setSetting(settings, "allowPlace", true);
                setSetting(settings, "allowSprint", true);
                setSetting(settings, "allowParkour", true);
                setSetting(settings, "allowParkourAscend", true);
                setSetting(settings, "allowDownward", true);
                setSetting(settings, "buildIgnoreExisting", false);
                LOGGER.info("Successfully configured Baritone settings via reflection");
            }
        } catch (Throwable t) {
            LOGGER.debug("Baritone settings not configured (Baritone may not be installed): {}", t.getMessage());
        }
    }

    private void setSetting(Object settings, String fieldName, Object val) {
        try {
            Field field = settings.getClass().getField(fieldName);
            Object settingObj = field.get(settings);
            if (settingObj != null) {
                Field valueField = settingObj.getClass().getField("value");
                valueField.set(settingObj, val);
            }
        } catch (Throwable ignored) {}
    }

    private Object getPrimaryBaritone() {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Method getProviderMethod = apiClass.getMethod("getProvider");
            Object provider = getProviderMethod.invoke(null);
            Method getPrimary = provider.getClass().getMethod("getPrimaryBaritone");
            return getPrimary.invoke(provider);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean runBaritoneCommand(String command) {
        try {
            Object baritone = getPrimaryBaritone();
            if (baritone == null) return false;
            Method getCmdMgr = baritone.getClass().getMethod("getCommandManager");
            Object cmdMgr = getCmdMgr.invoke(baritone);
            if (cmdMgr == null) return false;
            Method exec = cmdMgr.getClass().getMethod("execute", String.class);
            exec.invoke(cmdMgr, command);
            return true;
        } catch (Throwable t) {
            LOGGER.error("Failed to execute Baritone command '{}': {}", command, t.getMessage());
            return false;
        }
    }

    @Override
    public JsonObject gotoPos(JsonObject arguments) {
        Object baritone = getPrimaryBaritone();
        if (baritone == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Baritone is not available. Please verify the Baritone mod is installed.");
            return err;
        }

        if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z")) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Missing required parameters: 'x', 'y', 'z'");
            return err;
        }

        int x = arguments.get("x").getAsInt();
        int y = arguments.get("y").getAsInt();
        int z = arguments.get("z").getAsInt();
        int distance = arguments.has("distance") ? arguments.get("distance").getAsInt() : 0;

        String cmd = (distance > 0) ? ("goto " + x + " " + y + " " + z + " " + distance) : ("goto " + x + " " + y + " " + z);
        boolean executed = runBaritoneCommand(cmd);

        JsonObject res = new JsonObject();
        res.addProperty("success", executed);
        res.addProperty("command", cmd);
        res.addProperty("targetX", x);
        res.addProperty("targetY", y);
        res.addProperty("targetZ", z);
        res.addProperty("distance", distance);
        return res;
    }

    @Override
    public JsonObject mine(JsonObject arguments) {
        Object baritone = getPrimaryBaritone();
        if (baritone == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Baritone is not available");
            return err;
        }

        if (!arguments.has("blocks") || !arguments.get("blocks").isJsonArray()) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Missing or invalid 'blocks' parameter (must be an array)");
            return err;
        }

        JsonArray blocksArr = arguments.getAsJsonArray("blocks");
        List<String> blockNames = new ArrayList<>();
        for (int i = 0; i < blocksArr.size(); i++) {
            blockNames.add(blocksArr.get(i).getAsString().replace("minecraft:", ""));
        }

        String cmd = "mine " + String.join(" ", blockNames);
        boolean executed = runBaritoneCommand(cmd);

        JsonObject res = new JsonObject();
        res.addProperty("success", executed);
        res.addProperty("command", cmd);
        res.add("blocks", blocksArr);
        return res;
    }

    @Override
    public JsonObject clearArea(JsonObject arguments) {
        Object baritone = getPrimaryBaritone();
        if (baritone == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Baritone is not available");
            return err;
        }

        if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z")) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Missing required parameters: 'x', 'y', 'z'");
            return err;
        }

        int x1 = arguments.get("x").getAsInt();
        int y1 = arguments.get("y").getAsInt();
        int z1 = arguments.get("z").getAsInt();

        int x2 = arguments.has("x2") ? arguments.get("x2").getAsInt() : x1;
        int y2 = arguments.has("y2") ? arguments.get("y2").getAsInt() : y1;
        int z2 = arguments.has("z2") ? arguments.get("z2").getAsInt() : z1;

        runBaritoneCommand("sel 1 " + x1 + " " + y1 + " " + z1);
        runBaritoneCommand("sel 2 " + x2 + " " + y2 + " " + z2);
        boolean executed = runBaritoneCommand("sel ca");

        JsonObject res = new JsonObject();
        res.addProperty("success", executed);
        res.addProperty("command", "sel ca");
        res.addProperty("x1", x1);
        res.addProperty("y1", y1);
        res.addProperty("z1", z1);
        res.addProperty("x2", x2);
        res.addProperty("y2", y2);
        res.addProperty("z2", z2);
        return res;
    }

    @Override
    public JsonObject stop() {
        Object baritone = getPrimaryBaritone();
        if (baritone == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Baritone is not available");
            return err;
        }

        boolean executed = runBaritoneCommand("stop");
        JsonObject res = new JsonObject();
        res.addProperty("success", executed);
        res.addProperty("command", "stop");
        return res;
    }

    @Override
    public JsonObject executeCommand(JsonObject arguments) {
        Object baritone = getPrimaryBaritone();
        if (baritone == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Baritone is not available");
            return err;
        }

        if (!arguments.has("command")) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Missing required parameter: 'command'");
            return err;
        }

        String command = arguments.get("command").getAsString();
        boolean executed = runBaritoneCommand(command);

        JsonObject res = new JsonObject();
        res.addProperty("success", executed);
        res.addProperty("command", command);
        return res;
    }

    @Override
    public JsonObject getStatus() {
        Object baritone = getPrimaryBaritone();
        JsonObject status = new JsonObject();
        if (baritone == null) {
            status.addProperty("available", false);
            status.addProperty("is_pathing", false);
            return status;
        }

        status.addProperty("available", true);
        try {
            Method getPb = baritone.getClass().getMethod("getPathingBehavior");
            Object pb = getPb.invoke(baritone);
            if (pb != null) {
                Method isPathing = pb.getClass().getMethod("isPathing");
                Method hasPath = pb.getClass().getMethod("hasPath");
                status.addProperty("is_pathing", (Boolean) isPathing.invoke(pb));
                status.addProperty("has_path", (Boolean) hasPath.invoke(pb));
            }
        } catch (Throwable t) {
            status.addProperty("is_pathing", false);
        }

        return status;
    }
}
