package eikarna.effector.action;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class SmoothLookController {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmoothLookController.class);
    private static SmoothLookController instance;

    private volatile boolean active = false;
    private float startYaw = 0;
    private float startPitch = 0;
    private float targetYaw = 0;
    private float targetPitch = 0;
    private float deltaYaw = 0;
    private float deltaPitch = 0;
    private int totalTicks = 0;
    private int currentTick = 0;
    private CompletableFuture<Void> currentFuture = null;

    public static synchronized SmoothLookController getInstance() {
        if (instance == null) {
            instance = new SmoothLookController();
        }
        return instance;
    }

    public static float wrapDegrees(float value) {
        float wrapped = value % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    /**
     * Start smooth look towards target yaw and pitch with natural easing.
     * Duration ticks are calculated based on angular distance if <= 0.
     */
    public CompletableFuture<Void> lookAtSmooth(LocalPlayer player, float targetYaw, float targetPitch, int durationTicks) {
        float curYaw = player.getYRot();
        float curPitch = player.getXRot();

        float dy = wrapDegrees(targetYaw - curYaw);
        float dp = Math.max(-90.0f, Math.min(90.0f, targetPitch)) - curPitch;

        float angularDistance = (float) Math.hypot(dy, dp);

        if (angularDistance < 0.5f) {
            player.setYRot(targetYaw);
            player.setXRot(targetPitch);
            active = false;
            return CompletableFuture.completedFuture(null);
        }

        int ticks = durationTicks;
        if (ticks <= 0) {
            // Human mouse flick scaling (cubic ease-out curve):
            if (angularDistance < 20.0f) {
                ticks = 3; // ~150ms
            } else if (angularDistance < 60.0f) {
                ticks = 5; // ~250ms
            } else if (angularDistance < 120.0f) {
                ticks = 7; // ~350ms
            } else {
                ticks = 9; // ~450ms
            }
        }

        this.startYaw = curYaw;
        this.startPitch = curPitch;
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.deltaYaw = dy;
        this.deltaPitch = dp;
        this.totalTicks = Math.max(1, ticks);
        this.currentTick = 0;
        this.active = true;

        CompletableFuture<Void> future = new CompletableFuture<>();
        this.currentFuture = future;
        return future;
    }

    /**
     * Update interpolation each client tick.
     */
    public void onClientTick(Minecraft client) {
        if (!active || client.player == null) {
            return;
        }

        LocalPlayer player = client.player;
        currentTick++;
        float t = (float) currentTick / (float) totalTicks;
        if (t >= 1.0f) {
            t = 1.0f;
            active = false;
        }

        // Cubic ease-out: 1 - (1 - t)^3
        float ease = 1.0f - (float) Math.pow(1.0f - t, 3);

        float newYaw = startYaw + deltaYaw * ease;
        float newPitch = startPitch + deltaPitch * ease;

        // Subtle human hand tremor / micro-jitter during camera travel
        if (active) {
            newYaw += (float) ThreadLocalRandom.current().nextDouble(-0.06, 0.06);
            newPitch += (float) ThreadLocalRandom.current().nextDouble(-0.04, 0.04);
        }

        newYaw = wrapDegrees(newYaw);
        newPitch = Math.max(-90.0f, Math.min(90.0f, newPitch));

        player.setYRot(newYaw);
        player.setXRot(newPitch);
        player.yRotO = newYaw;
        player.xRotO = newPitch;
        player.yHeadRot = newYaw;
        player.yHeadRotO = newYaw;
        player.yBodyRot = newYaw;
        player.yBodyRotO = newYaw;

        if (player.connection != null) {
            player.connection.send(new ServerboundMovePlayerPacket.Rot(
                newYaw, newPitch, player.onGround(), player.horizontalCollision
            ));
        }

        if (!active && currentFuture != null && !currentFuture.isDone()) {
            currentFuture.complete(null);
        }
    }

    public boolean isActive() {
        return active;
    }

    public void cancel() {
        active = false;
        if (currentFuture != null && !currentFuture.isDone()) {
            currentFuture.complete(null);
        }
    }
}
