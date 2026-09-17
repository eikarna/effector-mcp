package cuspymd.mcp.mod.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ActionQueueController implements IActionQueueController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActionQueueController.class);

    private final IPlayerActionController playerActionController;
    private final IContainerController containerController;
    private final Queue<ActionStep> queue = new LinkedList<>();
    private final Object lock = new Object();
    
    private ActionStep currentStep = null;
    private int currentStepRemainingTicks = 0;
    private CompletableFuture<JsonObject> currentBatchFuture = null;
    private int totalBatchSteps = 0;
    private int completedBatchSteps = 0;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public ActionQueueController(IPlayerActionController playerActionController, IContainerController containerController) {
        this.playerActionController = playerActionController;
        this.containerController = containerController;
    }

    private static class ActionStep {
        final String type;
        final JsonObject params;
        final int durationTicks;

        ActionStep(String type, JsonObject params, int durationTicks) {
            this.type = type;
            this.params = params;
            this.durationTicks = durationTicks;
        }
    }

    public void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            cancelActionsInternal(client);
            return;
        }

        synchronized (lock) {
            if (!isRunning.get() && queue.isEmpty()) {
                return;
            }

            // If an active timed step is ticking
            if (currentStep != null && currentStepRemainingTicks > 0) {
                currentStepRemainingTicks--;
                if (currentStepRemainingTicks <= 0) {
                    finishCurrentStep(client);
                } else {
                    // Still executing timed step (e.g. holding movement keys)
                    return;
                }
            }

            // Process next steps from queue
            while (!queue.isEmpty()) {
                ActionStep step = queue.poll();
                currentStep = step;
                completedBatchSteps++;

                if ("move".equalsIgnoreCase(step.type)) {
                    applyMovementKeys(client, step.params, true);
                    currentStepRemainingTicks = Math.max(1, step.durationTicks);
                    return; // Wait for movement duration to elapse over subsequent ticks
                } else if ("wait".equalsIgnoreCase(step.type)) {
                    currentStepRemainingTicks = Math.max(1, step.durationTicks);
                    return; // Pause for duration
                } else {
                    // Instant actions executed on the client thread
                    executeInstantAction(step);
                    currentStep = null;
                }
            }

            // All steps in batch completed
            if (queue.isEmpty() && isRunning.get()) {
                isRunning.set(false);
                releaseAllMovementKeys(client);
                if (currentBatchFuture != null && !currentBatchFuture.isDone()) {
                    JsonObject res = new JsonObject();
                    res.addProperty("success", true);
                    res.addProperty("message", "All " + completedBatchSteps + " actions executed successfully");
                    res.addProperty("total_actions", totalBatchSteps);
                    currentBatchFuture.complete(res);
                }
            }
        }
    }

    private void executeInstantAction(ActionStep step) {
        try {
            switch (step.type.toLowerCase()) {
                case "look" -> playerActionController.setLook(step.params);
                case "look_at" -> playerActionController.lookAt(step.params);
                case "interact" -> playerActionController.interactBlock(step.params);
                case "attack" -> playerActionController.attackBlock(step.params);
                case "use_item" -> playerActionController.useItem(step.params);
                case "select_slot" -> playerActionController.selectSlot(step.params);
                case "swap_hands" -> playerActionController.swapHands();
                case "click_slot" -> containerController.clickSlot(step.params);
                case "close_container" -> containerController.closeContainer();
                default -> LOGGER.warn("Unknown action step type in queue: {}", step.type);
            }
        } catch (Exception e) {
            LOGGER.error("Error executing instant action step {}", step.type, e);
        }
    }

    private void applyMovementKeys(Minecraft client, JsonObject params, boolean state) {
        if (client.options == null) return;
        boolean forward = params.has("forward") && params.get("forward").getAsBoolean();
        boolean backward = params.has("backward") && params.get("backward").getAsBoolean();
        boolean left = params.has("left") && params.get("left").getAsBoolean();
        boolean right = params.has("right") && params.get("right").getAsBoolean();
        boolean jump = params.has("jump") && params.get("jump").getAsBoolean();
        boolean sneak = params.has("sneak") && params.get("sneak").getAsBoolean();
        boolean sprint = params.has("sprint") && params.get("sprint").getAsBoolean();

        if (forward) client.options.keyUp.setDown(state);
        if (backward) client.options.keyDown.setDown(state);
        if (left) client.options.keyLeft.setDown(state);
        if (right) client.options.keyRight.setDown(state);
        if (jump) client.options.keyJump.setDown(state);
        if (sneak) client.options.keyShift.setDown(state);
        if (sprint) client.options.keySprint.setDown(state);
    }

    private void finishCurrentStep(Minecraft client) {
        if (currentStep != null && "move".equalsIgnoreCase(currentStep.type)) {
            applyMovementKeys(client, currentStep.params, false);
        }
        currentStep = null;
    }

    private void releaseAllMovementKeys(Minecraft client) {
        if (client.options != null) {
            client.options.keyUp.setDown(false);
            client.options.keyDown.setDown(false);
            client.options.keyLeft.setDown(false);
            client.options.keyRight.setDown(false);
            client.options.keyJump.setDown(false);
            client.options.keyShift.setDown(false);
            client.options.keySprint.setDown(false);
        }
    }

    private void cancelActionsInternal(Minecraft client) {
        synchronized (lock) {
            queue.clear();
            currentStep = null;
            currentStepRemainingTicks = 0;
            isRunning.set(false);
            releaseAllMovementKeys(client);
            if (currentBatchFuture != null && !currentBatchFuture.isDone()) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Action queue was cancelled");
                currentBatchFuture.complete(err);
            }
        }
    }

    @Override
    public JsonObject executeActions(JsonObject arguments) {
        if (!arguments.has("actions") || !arguments.get("actions").isJsonArray()) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Missing required 'actions' array parameter");
            return err;
        }

        JsonArray actionsArray = arguments.getAsJsonArray("actions");
        boolean waitCompletion = !arguments.has("wait_completion") || arguments.get("wait_completion").getAsBoolean();

        synchronized (lock) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Player not available");
                return err;
            }

            // If previous batch was running, clear and override
            releaseAllMovementKeys(client);
            queue.clear();
            currentStep = null;
            currentStepRemainingTicks = 0;
            totalBatchSteps = actionsArray.size();
            completedBatchSteps = 0;

            for (int i = 0; i < actionsArray.size(); i++) {
                JsonObject stepObj = actionsArray.get(i).getAsJsonObject();
                String type = stepObj.has("type") ? stepObj.get("type").getAsString() : (stepObj.has("action") ? stepObj.get("action").getAsString() : "");
                int ticks = stepObj.has("ticks") ? stepObj.get("ticks").getAsInt() : 1;
                queue.add(new ActionStep(type, stepObj, ticks));
            }

            currentBatchFuture = new CompletableFuture<>();
            isRunning.set(true);
        }

        if (waitCompletion) {
            try {
                // Wait up to 30 seconds for the macro batch to complete
                return currentBatchFuture.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Action batch timed out or interrupted: " + e.getMessage());
                return err;
            }
        } else {
            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("status", "queued");
            res.addProperty("queued_actions", totalBatchSteps);
            return res;
        }
    }

    @Override
    public JsonObject cancelActions() {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> cancelActionsInternal(client));
        JsonObject res = new JsonObject();
        res.addProperty("success", true);
        res.addProperty("message", "Action queue cancelled and all movement keys released");
        return res;
    }

    @Override
    public JsonObject getQueueStatus() {
        synchronized (lock) {
            JsonObject res = new JsonObject();
            res.addProperty("running", isRunning.get());
            res.addProperty("remaining_steps", queue.size());
            res.addProperty("total_steps", totalBatchSteps);
            res.addProperty("completed_steps", completedBatchSteps);
            res.addProperty("current_step", currentStep != null ? currentStep.type : "none");
            res.addProperty("remaining_ticks", currentStepRemainingTicks);
            return res;
        }
    }
}
