package cuspymd.mcp.mod.utils;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class BlockScanner implements cuspymd.mcp.mod.utils.IBlockScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockScanner.class);
    
    @Override
    public JsonObject scanBlocksInArea(JsonObject fromPos, JsonObject toPos, int maxAreaSize) {
        return scanBlocksInAreaStatic(fromPos, toPos, maxAreaSize);
    }

    @Override
    public JsonObject scanChunk(JsonObject arguments) {
        return scanChunkStatic(arguments);
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
    
    private static JsonObject createErrorResponse(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return error;
    }
}
