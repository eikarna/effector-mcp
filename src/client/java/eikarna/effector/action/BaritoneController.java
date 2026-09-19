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
                setSetting(settings, "blockBreakAdditionalPenalty", 2.0);
                setSetting(settings, "freeLook", false);
                setSetting(settings, "remainWithExistingLookDirection", false);
                setSetting(settings, "antiCheatCompatibility", true);
                setSetting(settings, "smoothLook", true);
                runBaritoneCommand("set blocksToDisallowBreaking chest,trapped_chest,ender_chest,barrel,shulker_box,hopper,furnace,smoker,blast_furnace,brewing_stand,crafting_table,oak_door,iron_door,oak_trapdoor,iron_trapdoor,ladder");
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

        String cmd = "goto " + x + " " + y + " " + z;
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
    public JsonObject navigateTo(JsonObject arguments) {
        if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z")) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Missing required parameters: 'x', 'y', 'z'");
            return err;
        }

        int targetX = arguments.get("x").getAsInt();
        int targetY = arguments.get("y").getAsInt();
        int targetZ = arguments.get("z").getAsInt();
        int timeoutSec = arguments.has("timeout_seconds") ? arguments.get("timeout_seconds").getAsInt() : 20;

        AutonomousReflexController.getInstance().resetStallReason();

        JsonObject gotoRes = gotoPos(arguments);
        if (gotoRes.has("isError") && gotoRes.get("isError").getAsBoolean()) {
            return gotoRes;
        }

        long startMs = System.currentTimeMillis();
        long maxDurationMs = timeoutSec * 1000L;

        while (System.currentTimeMillis() - startMs < maxDurationMs) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client.player != null) {
                double dx = client.player.getX() - (targetX + 0.5);
                double dy = client.player.getY() - targetY;
                double dz = client.player.getZ() - (targetZ + 0.5);
                double dist = Math.sqrt(dx * dx + dz * dz);

                if (dist <= 1.5 && Math.abs(dy) <= 2.0) {
                    JsonObject success = new JsonObject();
                    success.addProperty("success", true);
                    success.addProperty("status", "ARRIVED");
                    JsonObject pos = new JsonObject();
                    pos.addProperty("x", client.player.getX());
                    pos.addProperty("y", client.player.getY());
                    pos.addProperty("z", client.player.getZ());
                    success.add("position", pos);
                    return success;
                }
            }

            String stallReason = AutonomousReflexController.getInstance().getLastStallReason();
            if (stallReason != null) {
                JsonObject fail = new JsonObject();
                fail.addProperty("success", false);
                fail.addProperty("status", "STALL_ABORTED");
                fail.addProperty("reason", stallReason);
                if (client.player != null) {
                    JsonObject pos = new JsonObject();
                    pos.addProperty("x", client.player.getX());
                    pos.addProperty("y", client.player.getY());
                    pos.addProperty("z", client.player.getZ());
                    fail.add("position", pos);
                }
                fail.addProperty("recommendation", "Path blocked by " + stallReason + ". Choose alternative waypoint or clear path.");
                return fail;
            }

            JsonObject bStatus = getStatus();
            boolean isPathing = bStatus.has("is_pathing") && bStatus.get("is_pathing").getAsBoolean();
            if (!isPathing && (System.currentTimeMillis() - startMs > 1500)) {
                break;
            }
        }

        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        JsonObject timeoutRes = new JsonObject();
        timeoutRes.addProperty("success", false);
        timeoutRes.addProperty("status", "TIMEOUT_OR_BLOCKED");
        timeoutRes.addProperty("reason", "Failed to reach target within " + timeoutSec + "s");
        if (client.player != null) {
            JsonObject pos = new JsonObject();
            pos.addProperty("x", client.player.getX());
            pos.addProperty("y", client.player.getY());
            pos.addProperty("z", client.player.getZ());
            timeoutRes.add("position", pos);
        }
        return timeoutRes;
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
    public JsonObject buildSchematic(JsonObject arguments) {
        Object baritone = getPrimaryBaritone();
        if (baritone == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Baritone is not available");
            return err;
        }

        if (!arguments.has("schematic")) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Missing required parameter: 'schematic'");
            return err;
        }

        String schemName = arguments.get("schematic").getAsString();
        String cmd;
        if (arguments.has("x") && arguments.has("y") && arguments.has("z")) {
            int x = arguments.get("x").getAsInt();
            int y = arguments.get("y").getAsInt();
            int z = arguments.get("z").getAsInt();
            cmd = "build " + schemName + " " + x + " " + y + " " + z;
        } else {
            cmd = "build " + schemName;
        }

        boolean executed = runBaritoneCommand(cmd);
        JsonObject res = new JsonObject();
        res.addProperty("success", executed);
        res.addProperty("command", cmd);
        res.addProperty("schematic", schemName);
        if (arguments.has("x") && arguments.has("y") && arguments.has("z")) {
            res.addProperty("originX", arguments.get("x").getAsInt());
            res.addProperty("originY", arguments.get("y").getAsInt());
            res.addProperty("originZ", arguments.get("z").getAsInt());
        }
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
