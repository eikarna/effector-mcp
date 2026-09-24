package eikarna.effector.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.level.block.state.BlockState;

public class PlayerActionController implements IPlayerActionController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerActionController.class);
    private static volatile long lastPlaceTimestamp = 0;

    private Vec3 calculateJitteredHitVec(BlockPos pos, Direction face) {
        double cx = pos.getX() + 0.5 + face.getStepX() * 0.5;
        double cy = pos.getY() + 0.5 + face.getStepY() * 0.5;
        double cz = pos.getZ() + 0.5 + face.getStepZ() * 0.5;

        double jitter1 = java.util.concurrent.ThreadLocalRandom.current().nextDouble(-0.20, 0.20);
        double jitter2 = java.util.concurrent.ThreadLocalRandom.current().nextDouble(-0.20, 0.20);
        double hx = cx, hy = cy, hz = cz;
        if (face.getAxis() == Direction.Axis.Y) {
            hx += jitter1;
            hz += jitter2;
        } else if (face.getAxis() == Direction.Axis.X) {
            hy += jitter1;
            hz += jitter2;
        } else {
            hx += jitter1;
            hy += jitter2;
        }
        return new Vec3(hx, hy, hz);
    }

    private void lookAtVec(LocalPlayer player, Vec3 target) {
        double eyeX = player.getX();
        double eyeY = player.getEyeY();
        double eyeZ = player.getZ();

        double dx = target.x - eyeX;
        double dy = target.y - eyeY;
        double dz = target.z - eyeZ;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) -(Math.atan2(dy, horizontalDist) * 180.0 / Math.PI);

        yaw = SmoothLookController.wrapDegrees(yaw);
        pitch = Math.max(-90.0f, Math.min(90.0f, pitch));

        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;

        if (player.connection != null) {
            player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
        }
    }

    private static final Set<String> PROTECTED_BLOCKS = Set.of(
        "chest", "trapped_chest", "ender_chest", "barrel", "shulker_box",
        "hopper", "furnace", "smoker", "blast_furnace", "brewing_stand",
        "crafting_table", "anvil", "chipped_anvil", "damaged_anvil",
        "enchanting_table", "dispenser", "dropper", "beacon", "respawn_anchor",
        "bed", "white_bed", "orange_bed", "magenta_bed", "light_blue_bed",
        "yellow_bed", "lime_bed", "pink_bed", "gray_bed", "light_gray_bed",
        "cyan_bed", "purple_bed", "blue_bed", "brown_bed", "green_bed", "red_bed", "black_bed",
        "oak_door", "iron_door", "spruce_door", "birch_door", "jungle_door", "acacia_door", "dark_oak_door", "mangrove_door", "cherry_door", "bamboo_door", "crimson_door", "warped_door",
        "oak_trapdoor", "iron_trapdoor", "spruce_trapdoor", "birch_trapdoor", "jungle_trapdoor", "acacia_trapdoor", "dark_oak_trapdoor", "mangrove_trapdoor", "cherry_trapdoor", "bamboo_trapdoor", "crimson_trapdoor", "warped_trapdoor",
        "ladder"
    );

    public static boolean isProtectedBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return PROTECTED_BLOCKS.contains(name);
    }

    private JsonObject runOnClientThread(ActionSupplier supplier) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Player or world is not available");
            return err;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            try {
                JsonObject res = supplier.run(client, client.player);
                future.complete(res);
            } catch (Exception e) {
                LOGGER.error("Error executing player action on client thread", e);
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                future.complete(err);
            }
        });

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Action timed out: " + e.getMessage());
            return err;
        }
    }

    @FunctionalInterface
    private interface ActionSupplier {
        JsonObject run(Minecraft client, LocalPlayer player) throws Exception;
    }

    @Override
    public JsonObject setLook(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("yaw") || !arguments.has("pitch")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameters: 'yaw' and 'pitch'");
                return err;
            }

            float yaw = arguments.get("yaw").getAsFloat();
            float pitch = arguments.get("pitch").getAsFloat();

            pitch = Math.max(-90.0f, Math.min(90.0f, pitch));
            yaw = yaw % 360.0f;
            if (yaw > 180.0f) yaw -= 360.0f;
            if (yaw < -180.0f) yaw += 360.0f;

            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yaw;
            player.xRotO = pitch;
            player.yHeadRot = yaw;
            player.yHeadRotO = yaw;
            player.yBodyRot = yaw;
            player.yBodyRotO = yaw;

            if (player.connection != null) {
                player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("yaw", yaw);
            res.addProperty("pitch", pitch);
            return res;
        });
    }

    @Override
    public JsonObject lookAt(JsonObject arguments) {
        if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z")) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Missing required parameters: 'x', 'y', 'z'");
            return err;
        }

        double targetX = arguments.get("x").getAsDouble();
        double targetY = arguments.get("y").getAsDouble();
        double targetZ = arguments.get("z").getAsDouble();
        int durationTicks = arguments.has("ticks") ? arguments.get("ticks").getAsInt() : 0;
        boolean smooth = !arguments.has("smooth") || arguments.get("smooth").getAsBoolean();

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Player is null");
            return err;
        }

        LocalPlayer player = client.player;
        double eyeX = player.getX();
        double eyeY = player.getEyeY();
        double eyeZ = player.getZ();

        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) -(Math.atan2(dy, horizontalDist) * 180.0 / Math.PI);

        yaw = SmoothLookController.wrapDegrees(yaw);
        pitch = Math.max(-90.0f, Math.min(90.0f, pitch));

        final float finalYaw = yaw;
        final float finalPitch = pitch;

        if (smooth) {
            CompletableFuture<Void> lookFuture = new CompletableFuture<>();
            client.execute(() -> {
                if (client.player != null) {
                    SmoothLookController.getInstance().lookAtSmooth(client.player, finalYaw, finalPitch, durationTicks)
                        .whenComplete((res, ex) -> lookFuture.complete(null));
                } else {
                    lookFuture.complete(null);
                }
            });
            try {
                lookFuture.get(800, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {}
        } else {
            client.execute(() -> {
                if (client.player != null) {
                    client.player.setYRot(finalYaw);
                    client.player.setXRot(finalPitch);
                    client.player.yRotO = finalYaw;
                    client.player.xRotO = finalPitch;
                    client.player.yHeadRot = finalYaw;
                    client.player.yHeadRotO = finalYaw;
                    client.player.yBodyRot = finalYaw;
                    client.player.yBodyRotO = finalYaw;
                    if (client.player.connection != null) {
                        client.player.connection.send(new ServerboundMovePlayerPacket.Rot(finalYaw, finalPitch, client.player.onGround(), client.player.horizontalCollision));
                    }
                }
            });
        }

        JsonObject res = new JsonObject();
        res.addProperty("success", true);
        res.addProperty("yaw", finalYaw);
        res.addProperty("pitch", finalPitch);
        res.addProperty("targetX", targetX);
        res.addProperty("targetY", targetY);
        res.addProperty("targetZ", targetZ);
        res.addProperty("smooth", smooth);
        return res;
    }

    @Override
    public JsonObject interactBlock(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameters: 'x', 'y', 'z'");
                return err;
            }

            int x = arguments.get("x").getAsInt();
            int y = arguments.get("y").getAsInt();
            int z = arguments.get("z").getAsInt();

            Direction face = Direction.UP;
            if (arguments.has("direction")) {
                Direction parsed = Direction.byName(arguments.get("direction").getAsString().toLowerCase(Locale.ROOT));
                if (parsed != null) {
                    face = parsed;
                }
            }

            InteractionHand hand = InteractionHand.MAIN_HAND;
            if (arguments.has("hand")) {
                String handStr = arguments.get("hand").getAsString().toLowerCase(Locale.ROOT);
                if (handStr.contains("off") || handStr.contains("second")) {
                    hand = InteractionHand.OFF_HAND;
                }
            }

            BlockPos targetPos = new BlockPos(x, y, z);
            Vec3 hitVec = calculateJitteredHitVec(targetPos, face);
            lookAtVec(player, hitVec);
            BlockHitResult hitResult = new BlockHitResult(hitVec, face, targetPos, false);

            if (client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "GameMode is null");
                return err;
            }

            InteractionResult result = client.gameMode.useItemOn(player, hand, hitResult);

            JsonObject res = new JsonObject();
            res.addProperty("success", result.consumesAction());
            res.addProperty("result", result.toString());
            res.addProperty("targetX", x);
            res.addProperty("targetY", y);
            res.addProperty("targetZ", z);
            res.addProperty("face", face.getName());
            res.addProperty("hand", hand.name());
            return res;
        });
    }

    @Override
    public JsonObject placeBlock(JsonObject arguments) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastPlaceTimestamp;
        if (elapsed < 50) {
            try {
                Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextLong(35, 65));
            } catch (InterruptedException ignored) {}
        }
        lastPlaceTimestamp = System.currentTimeMillis();

        return runOnClientThread((client, player) -> {
            if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameters: 'x', 'y', 'z'");
                return err;
            }

            int targetX = arguments.get("x").getAsInt();
            int targetY = arguments.get("y").getAsInt();
            int targetZ = arguments.get("z").getAsInt();
            BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);

            if (client.level == null || client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Level or GameMode is null");
                return err;
            }

            Direction placeFace = Direction.UP;
            BlockPos supportPos = targetPos.below();
            if (arguments.has("face") || arguments.has("direction")) {
                String dName = arguments.has("face") ? arguments.get("face").getAsString() : arguments.get("direction").getAsString();
                Direction parsed = Direction.byName(dName.toLowerCase(Locale.ROOT));
                if (parsed != null) {
                    placeFace = parsed;
                    if (client.level.getBlockState(targetPos).isAir()) {
                        supportPos = targetPos.relative(parsed.getOpposite());
                    } else {
                        supportPos = targetPos;
                    }
                }
            } else {
                for (Direction d : Direction.values()) {
                    BlockPos neighbor = targetPos.relative(d);
                    if (client.level.getBlockState(neighbor).isSolid()) {
                        supportPos = neighbor;
                        placeFace = d.getOpposite();
                        break;
                    }
                }
            }

            InteractionHand hand = InteractionHand.MAIN_HAND;
            if (arguments.has("hand") && arguments.get("hand").getAsString().toLowerCase(Locale.ROOT).contains("off")) {
                hand = InteractionHand.OFF_HAND;
            }

            boolean sneak = arguments.has("sneak") && arguments.get("sneak").getAsBoolean();
            boolean wasSneaking = player.isShiftKeyDown();
            if (sneak) {
                client.options.keyShift.setDown(true);
                if (player.connection != null) {
                    player.connection.send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, true, false)));
                }
            }

            Vec3 hitVec = calculateJitteredHitVec(supportPos, placeFace);
            lookAtVec(player, hitVec);
            BlockHitResult hitResult = new BlockHitResult(hitVec, placeFace, supportPos, false);

            InteractionResult result = client.gameMode.useItemOn(player, hand, hitResult);
            player.swing(hand);

            if (sneak && !wasSneaking) {
                client.options.keyShift.setDown(false);
                if (player.connection != null) {
                    player.connection.send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, false, false)));
                }
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", result.consumesAction());
            res.addProperty("result", result.toString());
            res.addProperty("targetX", targetX);
            res.addProperty("targetY", targetY);
            res.addProperty("targetZ", targetZ);
            res.addProperty("supportPos", supportPos.toShortString());
            res.addProperty("placeFace", placeFace.getName());
            return res;
        });
    }

    @Override
    public JsonObject updateSign(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameters: 'x', 'y', 'z'");
                return err;
            }

            int x = arguments.get("x").getAsInt();
            int y = arguments.get("y").getAsInt();
            int z = arguments.get("z").getAsInt();
            BlockPos targetPos = new BlockPos(x, y, z);

            if (client.level == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Level is null");
                return err;
            }

            var blockEntity = client.level.getBlockEntity(targetPos);
            if (!(blockEntity instanceof SignBlockEntity sign)) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target block is not a sign");
                return err;
            }

            boolean isFront = !arguments.has("side") || !"back".equalsIgnoreCase(arguments.get("side").getAsString());
            List<String> lines = new ArrayList<>();
            if (arguments.has("lines") && arguments.get("lines").isJsonArray()) {
                var arr = arguments.getAsJsonArray("lines");
                for (int i = 0; i < Math.min(4, arr.size()); i++) {
                    lines.add(arr.get(i).getAsString());
                }
            }

            while (lines.size() < 4) lines.add("");

            player.openTextEdit(sign, isFront);

            String l1 = lines.get(0);
            String l2 = lines.get(1);
            String l3 = lines.get(2);
            String l4 = lines.get(3);

            if (player.connection != null) {
                player.connection.send(new ServerboundSignUpdatePacket(targetPos, isFront, l1, l2, l3, l4));
            }

            sign.updateText(text -> text.setMessage(0, net.minecraft.network.chat.Component.literal(l1))
                .setMessage(1, net.minecraft.network.chat.Component.literal(l2))
                .setMessage(2, net.minecraft.network.chat.Component.literal(l3))
                .setMessage(3, net.minecraft.network.chat.Component.literal(l4)), isFront);

            if (client.gui != null && client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.SignEditScreen) {
                client.setScreenAndShow(null);
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("targetX", x);
            res.addProperty("targetY", y);
            res.addProperty("targetZ", z);
            res.addProperty("isFront", isFront);
            JsonArray appliedLines = new JsonArray();
            for (String l : lines) appliedLines.add(l);
            res.add("lines", appliedLines);
            return res;
        });
    }

    @Override
    public JsonObject attackBlock(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameters: 'x', 'y', 'z'");
                return err;
            }

            int x = arguments.get("x").getAsInt();
            int y = arguments.get("y").getAsInt();
            int z = arguments.get("z").getAsInt();

            Direction face = Direction.UP;
            if (arguments.has("direction")) {
                Direction parsed = Direction.byName(arguments.get("direction").getAsString().toLowerCase(Locale.ROOT));
                if (parsed != null) {
                    face = parsed;
                }
            }

            BlockPos targetPos = new BlockPos(x, y, z);
            if (client.level == null || client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "GameMode or level is null");
                return err;
            }

            BlockState state = client.level.getBlockState(targetPos);
            boolean force = arguments.has("force") && arguments.get("force").getAsBoolean();
            if (isProtectedBlock(state) && !force) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "PROTECTED_BLOCK");
                err.addProperty("message", "Action refused: Target block is a protected base asset (" + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + ")");
                err.addProperty("targetX", x);
                err.addProperty("targetY", y);
                err.addProperty("targetZ", z);
                return err;
            }

            float destroySpeed = state.getDestroySpeed(client.level, targetPos);
            if (destroySpeed < 0.0f) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "IMMUTABLE_BLOCK");
                err.addProperty("message", "Target block is indestructible (hardness < 0: barrier, bedrock, or portal)");
                err.addProperty("blockType", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                err.addProperty("targetX", x);
                err.addProperty("targetY", y);
                err.addProperty("targetZ", z);
                return err;
            }

            boolean started = client.gameMode.startDestroyBlock(targetPos, face);
            player.swing(InteractionHand.MAIN_HAND);

            JsonObject res = new JsonObject();
            res.addProperty("success", started);
            res.addProperty("started", started);
            res.addProperty("targetX", x);
            res.addProperty("targetY", y);
            res.addProperty("targetZ", z);
            return res;
        });
    }

    private void autoSelectBestTool(Minecraft client, LocalPlayer player, BlockState state) {
        if (player == null || state == null) return;

        int bestHotbarSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                float speed = stack.getDestroySpeed(state);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestHotbarSlot = i;
                }
            }
        }

        int bestBackpackSlot = -1;
        if (bestSpeed <= 1.0f) {
            for (int i = 9; i < 36; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    float speed = stack.getDestroySpeed(state);
                    if (speed > bestSpeed) {
                        bestSpeed = speed;
                        bestBackpackSlot = i;
                    }
                }
            }
        }

        if (bestHotbarSlot != -1) {
            player.getInventory().setSelectedSlot(bestHotbarSlot);
            if (player.connection != null) {
                player.connection.send(new ServerboundSetCarriedItemPacket(bestHotbarSlot));
            }
        } else if (bestBackpackSlot != -1 && client.gameMode != null) {
            int currentHotbar = player.getInventory().getSelectedSlot();
            client.gameMode.handleContainerInput(0, bestBackpackSlot, currentHotbar, ContainerInput.SWAP, player);
        }
    }

    @Override
    public JsonObject mineBlock(JsonObject arguments) {
        if (arguments.has("x2") || arguments.has("y2") || arguments.has("z2")) {
            BaritoneController bc = new BaritoneController();
            return bc.clearArea(arguments);
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
        BlockPos targetPos = new BlockPos(x, y, z);

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Level is null");
            return err;
        }

        BlockState state = client.level.getBlockState(targetPos);
        boolean force = arguments.has("force") && arguments.get("force").getAsBoolean();

        if (client.player != null) {
            double distSq = client.player.getEyePosition().distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
            if (distSq > 30.25) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "OUT_OF_REACH");
                err.addProperty("message", "Target block is out of reach distance (" + String.format(Locale.ROOT, "%.2f", Math.sqrt(distSq)) + " blocks, max reach is 4.5)");
                err.addProperty("targetX", x);
                err.addProperty("targetY", y);
                err.addProperty("targetZ", z);
                return err;
            }

            boolean autoTool = !arguments.has("auto_tool") || arguments.get("auto_tool").getAsBoolean();
            if (autoTool) {
                autoSelectBestTool(client, client.player, state);
            }
        }
        if (isProtectedBlock(state) && !force) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "PROTECTED_BLOCK");
            err.addProperty("message", "Action refused: Target block is a protected base asset (" + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + ")");
            err.addProperty("targetX", x);
            err.addProperty("targetY", y);
            err.addProperty("targetZ", z);
            return err;
        }

        if (state.getDestroySpeed(client.level, targetPos) < 0.0f) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "IMMUTABLE_BLOCK");
            err.addProperty("message", "Target block is indestructible (hardness < 0)");
            return err;
        }

        Direction face = Direction.UP;
        if (arguments.has("direction")) {
            Direction parsed = Direction.byName(arguments.get("direction").getAsString().toLowerCase(Locale.ROOT));
            if (parsed != null) face = parsed;
        }

        AutonomousReflexController arc = AutonomousReflexController.getInstance();
        arc.startMiningBlock(targetPos, face, 120);

        long startTime = System.currentTimeMillis();
        while (arc.isMiningActive() && (System.currentTimeMillis() - startTime < 5500)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}
        }

        JsonObject res = new JsonObject();
        if (arc.isMiningCompleted() || (client.level != null && client.level.getBlockState(targetPos).isAir())) {
            res.addProperty("success", true);
            res.addProperty("status", "MINED");
            res.addProperty("elapsedMs", System.currentTimeMillis() - startTime);
            res.addProperty("targetX", x);
            res.addProperty("targetY", y);
            res.addProperty("targetZ", z);
            arc.cancelMining();
            return res;
        } else {
            res.addProperty("success", false);
            res.addProperty("error", arc.getMiningFailReason() != null ? arc.getMiningFailReason() : "Mining in progress or timed out");
            res.addProperty("targetX", x);
            res.addProperty("targetY", y);
            res.addProperty("targetZ", z);
            arc.cancelMining();
            return res;
        }
    }

    @Override
    public JsonObject attackEntity(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            Entity target = findEntity(client, arguments);
            if (target == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target entity not found. Specify valid 'entity_id' (int) or 'uuid' (string)");
                return err;
            }

            if (!target.isAlive()) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target entity is dead or despawned");
                return err;
            }

            double dist = Math.sqrt(player.distanceToSqr(target));
            if (dist > 6.0) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target entity is out of reach (distance: " + (Math.round(dist * 100.0) / 100.0) + ", max reach: 6.0)");
                return err;
            }

            if (client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "GameMode is null");
                return err;
            }

            client.gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND);

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("attacked_entity_id", target.getId());
            res.addProperty("attacked_entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
            res.addProperty("distance", Math.round(dist * 100.0) / 100.0);
            return res;
        });
    }

    @Override
    public JsonObject interactEntity(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            Entity target = findEntity(client, arguments);
            if (target == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target entity not found. Specify valid 'entity_id' (int) or 'uuid' (string)");
                return err;
            }

            if (!target.isAlive()) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target entity is dead or despawned");
                return err;
            }

            double dist = Math.sqrt(player.distanceToSqr(target));
            if (dist > 6.0) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Target entity is out of reach (distance: " + (Math.round(dist * 100.0) / 100.0) + ", max reach: 6.0)");
                return err;
            }

            if (client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "GameMode is null");
                return err;
            }

            InteractionHand hand = InteractionHand.MAIN_HAND;
            if (arguments != null && arguments.has("hand")) {
                String handStr = arguments.get("hand").getAsString().toLowerCase(Locale.ROOT);
                if (handStr.contains("off") || handStr.contains("second")) {
                    hand = InteractionHand.OFF_HAND;
                }
            }

            EntityHitResult hitResult = new EntityHitResult(target, target.position());
            InteractionResult result = client.gameMode.interact(player, target, hitResult, hand);
            player.swing(hand);

            JsonObject res = new JsonObject();
            res.addProperty("success", result.consumesAction());
            res.addProperty("result", result.toString());
            res.addProperty("interacted_entity_id", target.getId());
            res.addProperty("interacted_entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
            res.addProperty("hand", hand.name());
            return res;
        });
    }

    private Entity findEntity(Minecraft client, JsonObject arguments) {
        if (arguments == null || client.level == null) return null;
        if (arguments.has("entity_id")) {
            int id = arguments.get("entity_id").getAsInt();
            return client.level.getEntity(id);
        }
        if (arguments.has("uuid")) {
            String uuidStr = arguments.get("uuid").getAsString();
            try {
                UUID uuid = UUID.fromString(uuidStr);
                for (Entity e : client.level.entitiesForRendering()) {
                    if (e.getUUID().equals(uuid)) return e;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    public JsonObject useItem(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            InteractionHand hand = InteractionHand.MAIN_HAND;
            if (arguments != null && arguments.has("hand")) {
                String handStr = arguments.get("hand").getAsString().toLowerCase(Locale.ROOT);
                if (handStr.contains("off") || handStr.contains("second")) {
                    hand = InteractionHand.OFF_HAND;
                }
            }

            if (client.gameMode == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "GameMode is null");
                return err;
            }

            InteractionResult result = client.gameMode.useItem(player, hand);

            JsonObject res = new JsonObject();
            res.addProperty("success", result.consumesAction());
            res.addProperty("result", result.toString());
            res.addProperty("hand", hand.name());
            res.addProperty("usedItem", hand == InteractionHand.MAIN_HAND 
                ? (player.getMainHandItem().isEmpty() ? "empty" : player.getMainHandItem().getItem().toString())
                : (player.getOffhandItem().isEmpty() ? "empty" : player.getOffhandItem().getItem().toString()));
            return res;
        });
    }

    @Override
    public JsonObject selectSlot(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("slot")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameter: 'slot' (0-8)");
                return err;
            }

            int slot = arguments.get("slot").getAsInt();
            if (slot < 0 || slot > 8) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Hotbar slot must be between 0 and 8, got " + slot);
                return err;
            }

            player.getInventory().setSelectedSlot(slot);
            if (player.connection != null) {
                player.connection.send(new ServerboundSetCarriedItemPacket(slot));
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("selectedSlot", slot);
            return res;
        });
    }

    @Override
    public JsonObject swapHands() {
        return runOnClientThread((client, player) -> {
            if (player.connection != null) {
                player.connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO,
                    Direction.DOWN
                ));
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("message", "Swapped items between main hand and second hand (off-hand)");
            res.addProperty("mainHandItem", player.getMainHandItem().isEmpty() ? "empty" : player.getMainHandItem().getItem().toString());
            res.addProperty("offHandItem", player.getOffhandItem().isEmpty() ? "empty" : player.getOffhandItem().getItem().toString());
            return res;
        });
    }

    @Override
    public JsonObject loadWorld(JsonObject arguments) {
        if (!arguments.has("world_name")) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Missing required parameter: 'world_name'");
            return err;
        }

        String worldName = arguments.get("world_name").getAsString();
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        client.execute(() -> {
            try {
                client.createWorldOpenFlows().openWorld(worldName, () -> {});
            } catch (Throwable t) {
                LOGGER.error("Failed to open world '{}': {}", worldName, t.getMessage());
            }
        });

        JsonObject res = new JsonObject();
        res.addProperty("success", true);
        res.addProperty("message", "Triggered world load: " + worldName);
        return res;
    }

    private static volatile String lastConnectedAddress = "mc2.faizharleyda.gay:25432";

    public static String getLastConnectedAddress() {
        return lastConnectedAddress;
    }

    public static void setLastConnectedAddress(String address) {
        lastConnectedAddress = address;
    }

    @Override
    public JsonObject connectServer(JsonObject arguments) {
        if (!arguments.has("address")) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Missing required parameter: 'address'");
            return err;
        }

        String addressStr = arguments.get("address").getAsString();
        setLastConnectedAddress(addressStr);
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        client.execute(() -> {
            try {
                net.minecraft.client.multiplayer.resolver.ServerAddress address = 
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(addressStr);
                net.minecraft.client.multiplayer.ServerData serverData = 
                    new net.minecraft.client.multiplayer.ServerData(addressStr, addressStr, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                    client.gui != null ? client.gui.screen() : null, 
                    client, 
                    address, 
                    serverData, 
                    false, 
                    null
                );
            } catch (Throwable t) {
                LOGGER.error("Failed to connect to server '{}': {}", addressStr, t.getMessage());
            }
        });

        JsonObject res = new JsonObject();
        res.addProperty("success", true);
        res.addProperty("message", "Triggered connection to server: " + addressStr);
        return res;
    }

    @Override
    public JsonObject equipItem(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("item")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameter: 'item'");
                return err;
            }

            String search = arguments.get("item").getAsString().toLowerCase(Locale.ROOT).trim();
            int targetHotbar = arguments.has("hotbar_slot") ? arguments.get("hotbar_slot").getAsInt() : player.getInventory().getSelectedSlot();
            if (targetHotbar < 0 || targetHotbar > 8) {
                targetHotbar = player.getInventory().getSelectedSlot();
            }

            // Check if already in hotbar
            for (int h = 0; h < 9; h++) {
                ItemStack stack = player.getInventory().getItem(h);
                if (!stack.isEmpty()) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                    String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                    if (id.equals(search) || id.contains(search) || name.contains(search)) {
                        player.getInventory().setSelectedSlot(h);
                        if (player.connection != null) {
                            player.connection.send(new ServerboundSetCarriedItemPacket(h));
                        }
                        JsonObject res = new JsonObject();
                        res.addProperty("success", true);
                        res.addProperty("action", "SELECTED_EXISTING_HOTBAR");
                        res.addProperty("slot", h);
                        res.addProperty("item", id);
                        res.addProperty("count", stack.getCount());
                        return res;
                    }
                }
            }

            // Search in main backpack (slots 9 to 35)
            int foundSlot = -1;
            ItemStack foundStack = ItemStack.EMPTY;
            for (int i = 9; i < 36; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                    String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                    if (id.equals(search) || id.contains(search) || name.contains(search)) {
                        foundSlot = i;
                        foundStack = stack;
                        break;
                    }
                }
            }

            if (foundSlot == -1) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "ITEM_NOT_FOUND");
                err.addProperty("message", "Item matching '" + search + "' not found in player inventory");
                return err;
            }

            // In InventoryMenu (container 0), slot 9-35 are container slots 9-35
            client.gameMode.handleContainerInput(0, foundSlot, targetHotbar, ContainerInput.SWAP, player);

            player.getInventory().setSelectedSlot(targetHotbar);
            if (player.connection != null) {
                player.connection.send(new ServerboundSetCarriedItemPacket(targetHotbar));
            }

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("action", "SWAPPED_FROM_BACKPACK");
            res.addProperty("fromSlot", foundSlot);
            res.addProperty("toHotbarSlot", targetHotbar);
            res.addProperty("item", BuiltInRegistries.ITEM.getKey(foundStack.getItem()).toString());
            res.addProperty("count", foundStack.getCount());
            return res;
        });
    }

    @Override
    public JsonObject swapInventorySlots(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("from_slot") || !arguments.has("to_slot")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameters: 'from_slot', 'to_slot'");
                return err;
            }

            int fromSlot = arguments.get("from_slot").getAsInt();
            int toSlot = arguments.get("to_slot").getAsInt();

            if (fromSlot < 0 || fromSlot >= 46 || toSlot < 0 || toSlot >= 46) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Slot indices out of range [0, 45]");
                return err;
            }

            client.gameMode.handleContainerInput(0, fromSlot, 0, ContainerInput.PICKUP, player);
            client.gameMode.handleContainerInput(0, toSlot, 0, ContainerInput.PICKUP, player);
            client.gameMode.handleContainerInput(0, fromSlot, 0, ContainerInput.PICKUP, player);

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("fromSlot", fromSlot);
            res.addProperty("toSlot", toSlot);
            return res;
        });
    }

    @Override
    public JsonObject sealBoundaries(JsonObject arguments) {
        if (!arguments.has("from") || !arguments.has("to")) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Missing required parameters: 'from' and 'to' positions");
            return err;
        }

        JsonObject f = arguments.getAsJsonObject("from");
        JsonObject t = arguments.getAsJsonObject("to");
        int minX = Math.min(f.get("x").getAsInt(), t.get("x").getAsInt());
        int maxX = Math.max(f.get("x").getAsInt(), t.get("x").getAsInt());
        int minY = Math.min(f.get("y").getAsInt(), t.get("y").getAsInt());
        int maxY = Math.max(f.get("y").getAsInt(), t.get("y").getAsInt());
        int minZ = Math.min(f.get("z").getAsInt(), t.get("z").getAsInt());
        int maxZ = Math.max(f.get("z").getAsInt(), t.get("z").getAsInt());

        boolean dryRun = arguments.has("dry_run") && arguments.get("dry_run").getAsBoolean();

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "World or player not available");
            return err;
        }

        List<BlockPos> breaches = new ArrayList<>();
        var level = client.level;

        // Floor (minY - 1) and Ceiling (maxY + 1)
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos floorPos = new BlockPos(x, minY - 1, z);
                if (level.getBlockState(floorPos).isAir()) breaches.add(floorPos);

                BlockPos ceilPos = new BlockPos(x, maxY + 1, z);
                if (level.getBlockState(ceilPos).isAir()) breaches.add(ceilPos);
            }
        }

        // North (minZ - 1) and South (maxZ + 1)
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                BlockPos northPos = new BlockPos(x, y, minZ - 1);
                if (level.getBlockState(northPos).isAir()) breaches.add(northPos);

                BlockPos southPos = new BlockPos(x, y, maxZ + 1);
                if (level.getBlockState(southPos).isAir()) breaches.add(southPos);
            }
        }

        // West (minX - 1) and East (maxX + 1)
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                BlockPos westPos = new BlockPos(minX - 1, y, z);
                if (level.getBlockState(westPos).isAir()) breaches.add(westPos);

                BlockPos eastPos = new BlockPos(maxX + 1, y, z);
                if (level.getBlockState(eastPos).isAir()) breaches.add(eastPos);
            }
        }

        JsonObject res = new JsonObject();
        res.addProperty("success", true);
        res.addProperty("breaches_detected", breaches.size());
        res.addProperty("dry_run", dryRun);

        JsonArray breachArray = new JsonArray();
        for (BlockPos bp : breaches) {
            JsonObject bObj = new JsonObject();
            bObj.addProperty("x", bp.getX());
            bObj.addProperty("y", bp.getY());
            bObj.addProperty("z", bp.getZ());
            breachArray.add(bObj);
        }
        res.add("breach_positions", breachArray);

        if (dryRun || breaches.isEmpty()) {
            return res;
        }

        // Place blocks to seal detected breaches
        int placedCount = 0;
        for (BlockPos bp : breaches) {
            Direction targetFace = null;
            BlockPos supportPos = null;
            for (Direction d : Direction.values()) {
                BlockPos neighbor = bp.relative(d);
                if (level.getBlockState(neighbor).isSolid()) {
                    supportPos = neighbor;
                    targetFace = d.getOpposite();
                    break;
                }
            }

            if (supportPos != null && targetFace != null) {
                JsonObject placeArgs = new JsonObject();
                placeArgs.addProperty("x", bp.getX());
                placeArgs.addProperty("y", bp.getY());
                placeArgs.addProperty("z", bp.getZ());
                placeArgs.addProperty("face", targetFace.getName());
                JsonObject placeRes = placeBlock(placeArgs);
                if (placeRes.has("success") && placeRes.get("success").getAsBoolean()) {
                    placedCount++;
                }
            }
        }

        res.addProperty("blocks_placed", placedCount);
        res.addProperty("sealed", placedCount == breaches.size());
        return res;
    }

    @Override
    public JsonObject eatFood(JsonObject arguments) {
        String foodSearch = arguments != null && arguments.has("food") ? arguments.get("food").getAsString() : null;
        boolean waitCompletion = arguments == null || !arguments.has("wait_completion") || arguments.get("wait_completion").getAsBoolean();

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Player not available");
            return err;
        }

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        client.execute(() -> {
            boolean triggered = AutonomousReflexController.getInstance().triggerEat(client, client.player, foodSearch, future);
            if (!triggered) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "NO_FOOD_FOUND");
                err.addProperty("message", "No edible food found in inventory" + (foodSearch != null ? " matching '" + foodSearch + "'" : ""));
                future.complete(err);
            }
        });

        if (!waitCompletion) {
            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("action", "EATING_STARTED");
            return res;
        }

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Eating action timed out: " + e.getMessage());
            return err;
        }
    }

    @Override
    public JsonObject buildStructure(JsonObject arguments) {
        if (!arguments.has("primitive") || !arguments.has("origin")) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Missing required parameters: 'primitive' and 'origin'");
            return err;
        }

        String primitive = arguments.get("primitive").getAsString().toLowerCase(Locale.ROOT);
        JsonObject originObj = arguments.getAsJsonObject("origin");
        int ox = originObj.get("x").getAsInt();
        int oy = originObj.get("y").getAsInt();
        int oz = originObj.get("z").getAsInt();

        JsonObject sizeObj = arguments.has("size") ? arguments.getAsJsonObject("size") : null;
        int sx = sizeObj != null && sizeObj.has("dx") ? sizeObj.get("dx").getAsInt() : 1;
        int sy = sizeObj != null && sizeObj.has("dy") ? sizeObj.get("dy").getAsInt() : 1;
        int sz = sizeObj != null && sizeObj.has("dz") ? sizeObj.get("dz").getAsInt() : 1;

        String material = arguments.has("material") ? arguments.get("material").getAsString() : "cobblestone";
        boolean replaceAirOnly = !arguments.has("replace_air_only") || arguments.get("replace_air_only").getAsBoolean();
        boolean dryRun = arguments.has("dry_run") && arguments.get("dry_run").getAsBoolean();

        List<BlockPos> targetVoxels = new ArrayList<>();

        switch (primitive) {
            case "wall" -> {
                int dxStep = sx >= 0 ? 1 : -1;
                int dyStep = sy >= 0 ? 1 : -1;
                int dzStep = sz >= 0 ? 1 : -1;
                if (Math.abs(sx) >= Math.abs(sz)) {
                    for (int x = 0; x != sx; x += dxStep) {
                        for (int y = 0; y != sy; y += dyStep) {
                            targetVoxels.add(new BlockPos(ox + x, oy + y, oz));
                        }
                    }
                } else {
                    for (int z = 0; z != sz; z += dzStep) {
                        for (int y = 0; y != sy; y += dyStep) {
                            targetVoxels.add(new BlockPos(ox, oy + y, oz + z));
                        }
                    }
                }
            }
            case "floor" -> {
                int dxStep = sx >= 0 ? 1 : -1;
                int dzStep = sz >= 0 ? 1 : -1;
                for (int x = 0; x != sx; x += dxStep) {
                    for (int z = 0; z != sz; z += dzStep) {
                        targetVoxels.add(new BlockPos(ox + x, oy, oz + z));
                    }
                }
            }
            case "pillar" -> {
                int dyStep = sy >= 0 ? 1 : -1;
                for (int y = 0; y != sy; y += dyStep) {
                    targetVoxels.add(new BlockPos(ox, oy + y, oz));
                }
            }
            case "hollow_box" -> {
                int minX = Math.min(ox, ox + sx);
                int maxX = Math.max(ox, ox + sx);
                int minY = Math.min(oy, oy + sy);
                int maxY = Math.max(oy, oy + sy);
                int minZ = Math.min(oz, oz + sz);
                int maxZ = Math.max(oz, oz + sz);

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            boolean isShell = (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ);
                            if (isShell) {
                                targetVoxels.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                }
            }
            case "arch" -> {
                int height = Math.max(2, Math.abs(sy));
                int span = Math.max(2, Math.abs(sx != 1 ? sx : sz));
                boolean spanX = Math.abs(sx) >= Math.abs(sz);

                for (int y = 0; y < height; y++) {
                    targetVoxels.add(new BlockPos(ox, oy + y, oz));
                    if (spanX) targetVoxels.add(new BlockPos(ox + span, oy + y, oz));
                    else targetVoxels.add(new BlockPos(ox, oy + y, oz + span));
                }
                for (int s = 0; s <= span; s++) {
                    if (spanX) targetVoxels.add(new BlockPos(ox + s, oy + height, oz));
                    else targetVoxels.add(new BlockPos(ox, oy + height, oz + s));
                }
            }
            case "alcove" -> {
                int w = Math.max(2, Math.abs(sx));
                int h = Math.max(2, Math.abs(sy));
                int d = Math.max(1, Math.abs(sz));
                for (int y = 0; y < h; y++) {
                    for (int z = 0; z < d; z++) targetVoxels.add(new BlockPos(ox, oy + y, oz + z));
                }
                for (int y = 0; y < h; y++) {
                    for (int z = 0; z < d; z++) targetVoxels.add(new BlockPos(ox + w, oy + y, oz + z));
                }
                for (int x = 0; x <= w; x++) {
                    for (int y = 0; y < h; y++) targetVoxels.add(new BlockPos(ox + x, oy + y, oz + d));
                }
                for (int x = 0; x <= w; x++) {
                    for (int z = 0; z <= d; z++) targetVoxels.add(new BlockPos(ox + x, oy + h, oz + z));
                }
            }
            default -> {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Unknown primitive: " + primitive + ". Supported: wall, floor, pillar, hollow_box, arch, alcove");
                return err;
            }
        }

        JsonObject res = new JsonObject();
        res.addProperty("success", true);
        res.addProperty("primitive", primitive);
        res.addProperty("material", material);
        res.addProperty("total_voxels", targetVoxels.size());
        res.addProperty("dry_run", dryRun);

        if (dryRun) {
            JsonArray vArray = new JsonArray();
            for (BlockPos bp : targetVoxels) {
                JsonObject vObj = new JsonObject();
                vObj.addProperty("x", bp.getX());
                vObj.addProperty("y", bp.getY());
                vObj.addProperty("z", bp.getZ());
                vArray.add(vObj);
            }
            res.add("voxels", vArray);
            return res;
        }

        JsonObject eqArgs = new JsonObject();
        eqArgs.addProperty("item", material);
        JsonObject eqRes = equipItem(eqArgs);
        if (eqRes.has("isError") && eqRes.get("isError").getAsBoolean()) {
            return eqRes;
        }

        Minecraft client = Minecraft.getInstance();
        var level = client.level;
        if (level == null) {
            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "Level is null");
            return err;
        }

        int placed = 0;
        int skipped = 0;

        for (BlockPos bp : targetVoxels) {
            if (replaceAirOnly && !level.getBlockState(bp).isAir()) {
                skipped++;
                continue;
            }

            Direction targetFace = null;
            BlockPos supportPos = null;
            for (Direction d : Direction.values()) {
                BlockPos neighbor = bp.relative(d);
                if (level.getBlockState(neighbor).isSolid()) {
                    supportPos = neighbor;
                    targetFace = d.getOpposite();
                    break;
                }
            }

            if (supportPos != null && targetFace != null) {
                JsonObject placeArgs = new JsonObject();
                placeArgs.addProperty("x", bp.getX());
                placeArgs.addProperty("y", bp.getY());
                placeArgs.addProperty("z", bp.getZ());
                placeArgs.addProperty("face", targetFace.getName());
                JsonObject placeRes = placeBlock(placeArgs);
                if (placeRes.has("success") && placeRes.get("success").getAsBoolean()) {
                    placed++;
                }
            }
        }

        res.addProperty("blocks_placed", placed);
        res.addProperty("blocks_skipped", skipped);
        return res;
    }

    @Override
    public JsonObject craftItem(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            if (!arguments.has("item")) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "Missing required parameter: 'item'");
                return err;
            }

            String search = arguments.get("item").getAsString().toLowerCase(Locale.ROOT);
            AbstractContainerMenu menu = player.containerMenu;
            if (menu == null) {
                JsonObject err = new JsonObject();
                err.addProperty("isError", true);
                err.addProperty("error", "No active container menu");
                return err;
            }

            Slot outputSlot = menu.slots.get(0);
            ItemStack outputStack = outputSlot.getItem();

            if (!outputStack.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(outputStack.getItem()).toString().toLowerCase(Locale.ROOT);
                String name = outputStack.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (id.contains(search) || name.contains(search)) {
                    int beforeCount = outputStack.getCount();
                    client.gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.QUICK_MOVE, player);
                    JsonObject res = new JsonObject();
                    res.addProperty("success", true);
                    res.addProperty("crafted_item", id);
                    res.addProperty("count", beforeCount);
                    return res;
                }
            }

            JsonObject err = new JsonObject();
            err.addProperty("isError", true);
            err.addProperty("error", "CRAFTING_OUTPUT_EMPTY");
            err.addProperty("message", "Output slot does not contain matching item '" + search + "'. Ensure recipe ingredients are placed in the crafting grid.");
            return err;
        });
    }

    @Override
    public JsonObject respawn(JsonObject arguments) {
        return runOnClientThread((client, player) -> {
            player.respawn();
            if (client.gui != null) {
                client.gui.setScreen(null);
            }
            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("message", "Triggered respawn");
            return res;
        });
    }
}
