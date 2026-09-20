package eikarna.effector.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class BlockScanner implements eikarna.effector.utils.IBlockScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockScanner.class);
    
    @Override
    public JsonObject scanBlocksInArea(JsonObject fromPos, JsonObject toPos, int maxAreaSize) {
        return scanBlocksInAreaStatic(fromPos, toPos, maxAreaSize);
    }

    @Override
    public JsonObject scanChunk(JsonObject arguments) {
        return scanChunkStatic(arguments);
    }

    @Override
    public JsonObject getBlockInfo(JsonObject arguments) {
        return getBlockInfoStatic(arguments);
    }

    @Override
    public JsonObject getBlock(JsonObject arguments) {
        return getBlockInfoStatic(arguments);
    }

    @Override
    public JsonObject auditEnclosure(JsonObject arguments) {
        return auditEnclosureStatic(arguments);
    }

    @Override
    public JsonObject getOrthographicSlice(JsonObject arguments) {
        return getOrthographicSliceStatic(arguments);
    }

    @Override
    public JsonObject getPerceptualRadar(JsonObject arguments) {
        return getPerceptualRadarStatic(arguments);
    }

    public static JsonObject getBlockInfoStatic(JsonObject arguments) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null) {
                return createErrorResponse("World not available");
            }

            if (!arguments.has("x") || !arguments.has("y") || !arguments.has("z")) {
                return createErrorResponse("Missing required parameters: 'x', 'y', 'z'");
            }

            int x = arguments.get("x").getAsInt();
            int y = arguments.get("y").getAsInt();
            int z = arguments.get("z").getAsInt();

            BlockPos pos = new BlockPos(x, y, z);
            var blockState = client.level.getBlockState(pos);
            String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();

            JsonObject result = new JsonObject();
            result.addProperty("x", x);
            result.addProperty("y", y);
            result.addProperty("z", z);
            result.addProperty("blockType", blockId);
            result.addProperty("isAir", blockState.isAir());
            result.addProperty("isSolid", blockState.isSolid());
            result.addProperty("lightLevel", client.level.getMaxLocalRawBrightness(pos));
            result.addProperty("hardness", blockState.getDestroySpeed(client.level, pos));

            JsonObject props = new JsonObject();
            for (var prop : blockState.getProperties()) {
                props.addProperty(prop.getName(), blockState.getValue(prop).toString());
            }
            result.add("properties", props);

            var blockEntity = client.level.getBlockEntity(pos);
            if (blockEntity instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                JsonObject signObj = new JsonObject();
                var frontText = sign.getFrontText();
                var backText = sign.getBackText();
                com.google.gson.JsonArray frontLines = new com.google.gson.JsonArray();
                com.google.gson.JsonArray backLines = new com.google.gson.JsonArray();
                for (int i = 0; i < 4; i++) {
                    frontLines.add(frontText.getMessage(i, false).getString());
                    backLines.add(backText.getMessage(i, false).getString());
                }
                signObj.add("front_messages", frontLines);
                signObj.add("back_messages", backLines);
                result.add("sign", signObj);
            }

            return result;
        } catch (Exception e) {
            LOGGER.error("Error getting block info", e);
            return createErrorResponse("Failed to get block info: " + e.getMessage());
        }
    }

    public static JsonObject scanBlocksInAreaStatic(JsonObject fromPos, JsonObject toPos, int maxAreaSize) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null) {
                return createErrorResponse("World not available");
            }
            
            Level world = client.level;
            
            int fromX = fromPos.get("x").getAsInt();
            int fromY = fromPos.get("y").getAsInt();  
            int fromZ = fromPos.get("z").getAsInt();
            
            int toX = toPos.get("x").getAsInt();
            int toY = toPos.get("y").getAsInt();
            int toZ = toPos.get("z").getAsInt();
            
            int minX = Math.min(fromX, toX);
            int maxX = Math.max(fromX, toX);
            int minY = Math.min(fromY, toY);
            int maxY = Math.max(fromY, toY);
            int minZ = Math.min(fromZ, toZ);
            int maxZ = Math.max(fromZ, toZ);
            
            int sizeX = maxX - minX + 1;
            int sizeY = maxY - minY + 1;
            int sizeZ = maxZ - minZ + 1;
            
            // Allow larger scans up to 128 blocks per axis
            int effectiveMax = Math.max(maxAreaSize, 128);
            if (sizeX > effectiveMax || sizeY > effectiveMax || sizeZ > effectiveMax) {
                return createErrorResponse(String.format(
                    "Area too large. Maximum size per axis: %d blocks. Requested: %dx%dx%d", 
                    effectiveMax, sizeX, sizeY, sizeZ
                ));
            }
            
            JsonObject result = new JsonObject();
            
            JsonObject areaInfo = new JsonObject();
            JsonObject fromCoords = new JsonObject();
            fromCoords.addProperty("x", minX);
            fromCoords.addProperty("y", minY);
            fromCoords.addProperty("z", minZ);
            
            JsonObject toCoords = new JsonObject();
            toCoords.addProperty("x", maxX);
            toCoords.addProperty("y", maxY);  
            toCoords.addProperty("z", maxZ);
            
            areaInfo.add("from", fromCoords);
            areaInfo.add("to", toCoords);
            areaInfo.addProperty("size", String.format("%dx%dx%d", sizeX, sizeY, sizeZ));
            result.add("area", areaInfo);
            
            List<BlockCompressor.BlockData> blockList = new ArrayList<>();
            int totalBlocks = 0;
            
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        var blockState = world.getBlockState(pos);
                        
                        if (blockState.is(Blocks.AIR) || blockState.is(Blocks.VOID_AIR) || blockState.is(Blocks.CAVE_AIR)) {
                            continue;
                        }
                        
                        blockList.add(new BlockCompressor.BlockData(x, y, z, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString()));
                        totalBlocks++;
                    }
                }
            }
            
            JsonObject compressedBlocks = BlockCompressor.compressBlocks(blockList);
            result.addProperty("total_blocks", totalBlocks);
            result.add("blocks", compressedBlocks.get("blocks"));
            
            LOGGER.info("Scanned area {}x{}x{}, found {} non-air blocks", sizeX, sizeY, sizeZ, totalBlocks);
            return result;
            
        } catch (Exception e) {
            LOGGER.error("Error scanning blocks in area", e);
            return createErrorResponse("Failed to scan blocks: " + e.getMessage());
        }
    }

    public static JsonObject scanChunkStatic(JsonObject arguments) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.player == null) {
                return createErrorResponse("World or player not available");
            }
            
            Level world = client.level;
            int playerChunkX = client.player.getBlockX() >> 4;
            int playerChunkZ = client.player.getBlockZ() >> 4;
            
            int chunkX = (arguments != null && arguments.has("chunk_x")) ? arguments.get("chunk_x").getAsInt() : playerChunkX;
            int chunkZ = (arguments != null && arguments.has("chunk_z")) ? arguments.get("chunk_z").getAsInt() : playerChunkZ;
            int radius = (arguments != null && arguments.has("radius")) ? Math.min(2, Math.max(0, arguments.get("radius").getAsInt())) : 0;
            
            int minY = (arguments != null && arguments.has("min_y")) ? arguments.get("min_y").getAsInt() : Math.max(world.getMinY(), client.player.getBlockY() - 25);
            int maxY = (arguments != null && arguments.has("max_y")) ? arguments.get("max_y").getAsInt() : Math.min(world.getMaxY(), client.player.getBlockY() + 25);
            
            if (minY > maxY) {
                int t = minY; minY = maxY; maxY = t;
            }
            
            List<String> filters = new ArrayList<>();
            if (arguments != null && arguments.has("filter")) {
                if (arguments.get("filter").isJsonArray()) {
                    arguments.getAsJsonArray("filter").forEach(el -> filters.add(el.getAsString().toLowerCase(Locale.ROOT)));
                } else if (arguments.get("filter").isJsonPrimitive()) {
                    filters.add(arguments.get("filter").getAsString().toLowerCase(Locale.ROOT));
                }
            }
            
            int minChunkX = chunkX - radius;
            int maxChunkX = chunkX + radius;
            int minChunkZ = chunkZ - radius;
            int maxChunkZ = chunkZ + radius;
            
            int minWorldX = minChunkX * 16;
            int maxWorldX = (maxChunkX * 16) + 15;
            int minWorldZ = minChunkZ * 16;
            int maxWorldZ = (maxChunkZ * 16) + 15;
            
            List<BlockCompressor.BlockData> blockList = new ArrayList<>();
            int totalMatching = 0;
            
            for (int x = minWorldX; x <= maxWorldX; x++) {
                for (int z = minWorldZ; z <= maxWorldZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        var blockState = world.getBlockState(pos);
                        if (blockState.is(Blocks.AIR) || blockState.is(Blocks.VOID_AIR) || blockState.is(Blocks.CAVE_AIR)) {
                            continue;
                        }
                        
                        String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
                        if (!filters.isEmpty()) {
                            boolean match = false;
                            for (String f : filters) {
                                if (blockId.contains(f)) {
                                    match = true;
                                    break;
                                }
                            }
                            if (!match) continue;
                        }
                        
                        blockList.add(new BlockCompressor.BlockData(x, y, z, blockId));
                        totalMatching++;
                    }
                }
            }
            
            JsonObject result = new JsonObject();
            JsonObject bounds = new JsonObject();
            bounds.addProperty("chunk_x", chunkX);
            bounds.addProperty("chunk_z", chunkZ);
            bounds.addProperty("radius_chunks", radius);
            bounds.addProperty("min_x", minWorldX);
            bounds.addProperty("max_x", maxWorldX);
            bounds.addProperty("min_y", minY);
            bounds.addProperty("max_y", maxY);
            bounds.addProperty("min_z", minWorldZ);
            bounds.addProperty("max_z", maxWorldZ);
            result.add("bounds", bounds);
            
            JsonObject compressed = BlockCompressor.compressBlocks(blockList);
            result.addProperty("total_matching_blocks", totalMatching);
            result.add("blocks", compressed.get("blocks"));
            
            LOGGER.info("Chunk scan ({}, {}) radius {} [Y: {}..{}] found {} blocks", chunkX, chunkZ, radius, minY, maxY, totalMatching);
            return result;
        } catch (Exception e) {
            LOGGER.error("Error scanning chunk", e);
            return createErrorResponse("Failed to scan chunk: " + e.getMessage());
        }
    }
    
    public static JsonObject auditEnclosureStatic(JsonObject arguments) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null) {
                return createErrorResponse("World not available");
            }

            int startX, startY, startZ;
            if (arguments.has("start") && arguments.get("start").isJsonObject()) {
                JsonObject start = arguments.getAsJsonObject("start");
                startX = start.get("x").getAsInt();
                startY = start.get("y").getAsInt();
                startZ = start.get("z").getAsInt();
            } else if (client.player != null) {
                startX = client.player.getBlockX();
                startY = client.player.getBlockY();
                startZ = client.player.getBlockZ();
            } else {
                return createErrorResponse("Missing required parameter: 'start' coordinate object");
            }

            if (!arguments.has("bounding_box") || !arguments.get("bounding_box").isJsonObject()) {
                return createErrorResponse("Missing required parameter: 'bounding_box' object with min_x, max_x, min_y, max_y, min_z, max_z");
            }
            JsonObject bb = arguments.getAsJsonObject("bounding_box");
            int minX = bb.get("min_x").getAsInt();
            int maxX = bb.get("max_x").getAsInt();
            int minY = bb.get("min_y").getAsInt();
            int maxY = bb.get("max_y").getAsInt();
            int minZ = bb.get("min_z").getAsInt();
            int maxZ = bb.get("max_z").getAsInt();

            if (minX > maxX || minY > maxY || minZ > maxZ) {
                return createErrorResponse("Invalid bounding_box: min coordinates must be <= max coordinates");
            }

            int maxVolume = arguments.has("max_volume") ? arguments.get("max_volume").getAsInt() : 15000;

            Level level = client.level;
            BlockPos startPos = new BlockPos(startX, startY, startZ);

            var startState = level.getBlockState(startPos);
            if (!startState.getCollisionShape(level, startPos).isEmpty()) {
                JsonObject err = new JsonObject();
                err.addProperty("is_enclosed", false);
                err.addProperty("error", "Start position " + startPos.toShortString() + " is inside a solid block (" + startState.getBlock().getName().getString() + ")");
                return err;
            }

            Queue<BlockPos> queue = new ArrayDeque<>();
            Set<Long> visited = new HashSet<>();
            queue.add(startPos);
            visited.add(startPos.asLong());

            Map<String, List<BlockPos>> leaksByFace = new HashMap<>();
            leaksByFace.put("west", new ArrayList<>());
            leaksByFace.put("east", new ArrayList<>());
            leaksByFace.put("floor", new ArrayList<>());
            leaksByFace.put("ceiling", new ArrayList<>());
            leaksByFace.put("north", new ArrayList<>());
            leaksByFace.put("south", new ArrayList<>());

            int[] dx = {1, -1, 0, 0, 0, 0};
            int[] dy = {0, 0, 1, -1, 0, 0};
            int[] dz = {0, 0, 0, 0, 1, -1};

            int visitedCount = 0;
            while (!queue.isEmpty() && visitedCount < maxVolume) {
                BlockPos curr = queue.poll();
                visitedCount++;

                for (int i = 0; i < 6; i++) {
                    int nx = curr.getX() + dx[i];
                    int ny = curr.getY() + dy[i];
                    int nz = curr.getZ() + dz[i];

                    if (nx < minX) {
                        leaksByFace.get("west").add(new BlockPos(nx, ny, nz));
                        continue;
                    }
                    if (nx > maxX) {
                        leaksByFace.get("east").add(new BlockPos(nx, ny, nz));
                        continue;
                    }
                    if (ny < minY) {
                        leaksByFace.get("floor").add(new BlockPos(nx, ny, nz));
                        continue;
                    }
                    if (ny > maxY) {
                        leaksByFace.get("ceiling").add(new BlockPos(nx, ny, nz));
                        continue;
                    }
                    if (nz < minZ) {
                        leaksByFace.get("north").add(new BlockPos(nx, ny, nz));
                        continue;
                    }
                    if (nz > maxZ) {
                        leaksByFace.get("south").add(new BlockPos(nx, ny, nz));
                        continue;
                    }

                    BlockPos nextPos = new BlockPos(nx, ny, nz);
                    long key = nextPos.asLong();
                    if (!visited.contains(key)) {
                        visited.add(key);
                        var state = level.getBlockState(nextPos);
                        if (state.getCollisionShape(level, nextPos).isEmpty()) {
                            queue.add(nextPos);
                        }
                    }
                }
            }

            int totalLeaks = 0;
            JsonArray leakSummary = new JsonArray();

            for (Map.Entry<String, List<BlockPos>> entry : leaksByFace.entrySet()) {
                String face = entry.getKey();
                List<BlockPos> leakList = entry.getValue();
                if (!leakList.isEmpty()) {
                    totalLeaks += leakList.size();

                    Map<Integer, List<BlockPos>> byY = new HashMap<>();
                    for (BlockPos p : leakList) {
                        byY.computeIfAbsent(p.getY(), k -> new ArrayList<>()).add(p);
                    }

                    for (Map.Entry<Integer, List<BlockPos>> yEntry : byY.entrySet()) {
                        int yLevel = yEntry.getKey();
                        List<BlockPos> pts = yEntry.getValue();

                        int minLeakZ = pts.stream().mapToInt(BlockPos::getZ).min().orElse(0);
                        int maxLeakZ = pts.stream().mapToInt(BlockPos::getZ).max().orElse(0);
                        int minLeakX = pts.stream().mapToInt(BlockPos::getX).min().orElse(0);
                        int maxLeakX = pts.stream().mapToInt(BlockPos::getX).max().orElse(0);

                        JsonObject item = new JsonObject();
                        item.addProperty("face", face);
                        item.addProperty("y", yLevel);
                        item.addProperty("missing_count", pts.size());
                        item.addProperty("x_range", "[" + minLeakX + " .. " + maxLeakX + "]");
                        item.addProperty("z_range", "[" + minLeakZ + " .. " + maxLeakZ + "]");
                        item.addProperty("suggestion", "Place solid blocks on " + face + " boundary at Y: " + yLevel);
                        leakSummary.add(item);
                    }
                }
            }

            JsonObject res = new JsonObject();
            res.addProperty("is_enclosed", totalLeaks == 0);
            res.addProperty("total_leak_points", totalLeaks);
            res.addProperty("interior_air_volume", visitedCount);
            res.add("leak_summary", leakSummary);
            if (totalLeaks == 0) {
                res.addProperty("message", "Structure is completely solid and 100% enclosed within the specified bounding box.");
            }
            return res;
        } catch (Exception e) {
            LOGGER.error("Error in auditEnclosure: ", e);
            return createErrorResponse("Failed to audit enclosure: " + e.getMessage());
        }
    }

    public static JsonObject getOrthographicSliceStatic(JsonObject arguments) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null) return createErrorResponse("World not available");

            String plane = arguments.has("plane") ? arguments.get("plane").getAsString().toLowerCase(Locale.ROOT) : "horizontal";
            int levelCoord = arguments.has("level") ? arguments.get("level").getAsInt() : (client.player != null ? client.player.getBlockY() : 64);

            int minU = arguments.has("min_u") ? arguments.get("min_u").getAsInt() : (client.player != null ? client.player.getBlockX() - 8 : -8);
            int maxU = arguments.has("max_u") ? arguments.get("max_u").getAsInt() : (client.player != null ? client.player.getBlockX() + 8 : 8);
            int minV = arguments.has("min_v") ? arguments.get("min_v").getAsInt() : (client.player != null ? client.player.getBlockZ() - 8 : -8);
            int maxV = arguments.has("max_v") ? arguments.get("max_v").getAsInt() : (client.player != null ? client.player.getBlockZ() + 8 : 8);

            if (maxU - minU > 64 || maxV - minV > 64) {
                return createErrorResponse("Slice area too large. Maximum 64x64 blocks.");
            }

            Level level = client.level;
            StringBuilder ascii = new StringBuilder();
            ascii.append(String.format("Slice [%s] at %d (U: %d..%d, V: %d..%d)\n", plane, levelCoord, minU, maxU, minV, maxV));

            for (int v = minV; v <= maxV; v++) {
                ascii.append(String.format("%5d ", v));
                for (int u = minU; u <= maxU; u++) {
                    BlockPos pos;
                    if ("horizontal".equals(plane)) {
                        pos = new BlockPos(u, levelCoord, v);
                    } else if ("vertical_x".equals(plane)) {
                        pos = new BlockPos(levelCoord, v, u);
                    } else {
                        pos = new BlockPos(u, v, levelCoord);
                    }

                    var state = level.getBlockState(pos);
                    char c;
                    if (state.isAir()) {
                        c = '.';
                    } else if (state.getBlock() instanceof net.minecraft.world.level.block.TorchBlock) {
                        c = 'T';
                    } else if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
                        c = 'C';
                    } else if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                        c = 'D';
                    } else if (!state.getCollisionShape(level, pos).isEmpty()) {
                        c = '#';
                    } else {
                        c = '~';
                    }
                    ascii.append(c).append(' ');
                }
                ascii.append('\n');
            }

            JsonObject res = new JsonObject();
            res.addProperty("plane", plane);
            res.addProperty("level", levelCoord);
            res.addProperty("ascii_grid", ascii.toString());
            return res;
        } catch (Exception e) {
            return createErrorResponse("Failed to generate orthographic slice: " + e.getMessage());
        }
    }

    public static JsonObject getPerceptualRadarStatic(JsonObject arguments) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.player == null) return createErrorResponse("Player or world not available");

            int maxDistance = arguments.has("max_distance") ? Math.min(arguments.get("max_distance").getAsInt(), 32) : 16;
            var player = client.player;
            var level = client.level;

            int px = player.getBlockX();
            int py = player.getBlockY();
            int pz = player.getBlockZ();

            JsonObject res = new JsonObject();
            res.addProperty("player_x", px);
            res.addProperty("player_y", py);
            res.addProperty("player_z", pz);

            String[] dirNames = {"NORTH", "NORTH_EAST", "EAST", "SOUTH_EAST", "SOUTH", "SOUTH_WEST", "WEST", "NORTH_WEST"};
            int[] dirX = {0, 1, 1, 1, 0, -1, -1, -1};
            int[] dirZ = {-1, -1, 0, 1, 1, 1, 0, -1};

            int[] yOffsets = {-1, 0, 2};
            String[] tierNames = {"foot_level_y_minus_1", "eye_level_y", "ceiling_level_y_plus_2"};

            for (int t = 0; t < 3; t++) {
                int targetY = py + yOffsets[t];
                JsonObject tierObj = new JsonObject();

                for (int d = 0; d < 8; d++) {
                    int stepX = dirX[d];
                    int stepZ = dirZ[d];

                    int hitDist = maxDistance;
                    String hitBlock = "minecraft:air";
                    boolean foundSolid = false;

                    for (int dist = 1; dist <= maxDistance; dist++) {
                        BlockPos checkPos = new BlockPos(px + (stepX * dist), targetY, pz + (stepZ * dist));
                        var state = level.getBlockState(checkPos);
                        if (!state.getCollisionShape(level, checkPos).isEmpty()) {
                            hitDist = dist;
                            hitBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                            foundSolid = true;
                            break;
                        }
                    }

                    JsonObject ray = new JsonObject();
                    ray.addProperty("distance", hitDist);
                    ray.addProperty("hit_solid", foundSolid);
                    ray.addProperty("block", hitBlock);
                    tierObj.add(dirNames[d], ray);
                }

                res.add(tierNames[t], tierObj);
            }

            return res;
        } catch (Exception e) {
            return createErrorResponse("Failed to compute perceptual radar: " + e.getMessage());
        }
    }

    private static JsonObject createErrorResponse(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return error;
    }
}
