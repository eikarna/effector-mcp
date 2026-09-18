package eikarna.effector.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class VoxelPathfinder {
    private static final Direction[] HORIZONTALS = new Direction[]{
        Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    public static class PathNode implements Comparable<PathNode> {
        public final BlockPos pos;
        public final PathNode parent;
        public final double gCost;
        public final double hCost;
        public final double fCost;

        public PathNode(BlockPos pos, PathNode parent, double gCost, double hCost) {
            this.pos = pos;
            this.parent = parent;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
        }

        @Override
        public int compareTo(PathNode o) {
            return Double.compare(this.fCost, o.fCost);
        }
    }

    private static class Transition {
        final BlockPos pos;
        final double cost;

        Transition(BlockPos pos, double cost) {
            this.pos = pos;
            this.cost = cost;
        }
    }

    public static List<BlockPos> findPath(Level level, BlockPos start, BlockPos goal, int maxNodes) {
        BlockPos currentStart = adjustToGround(level, start);
        BlockPos currentGoal = adjustToGround(level, goal);

        if (currentStart.equals(currentGoal)) {
            return Collections.emptyList();
        }

        PriorityQueue<PathNode> openSet = new PriorityQueue<>();
        Map<BlockPos, Double> bestG = new HashMap<>();
        Set<BlockPos> closedSet = new HashSet<>();

        PathNode startNode = new PathNode(currentStart, null, 0.0, heuristic(currentStart, currentGoal));
        openSet.add(startNode);
        bestG.put(currentStart, 0.0);

        PathNode bestClosest = startNode;
        double minDistance = heuristic(currentStart, currentGoal);

        int iterations = 0;
        while (!openSet.isEmpty() && iterations++ < maxNodes) {
            PathNode current = openSet.poll();
            if (closedSet.contains(current.pos)) continue;
            closedSet.add(current.pos);

            double d = heuristic(current.pos, currentGoal);
            if (d < minDistance) {
                minDistance = d;
                bestClosest = current;
            }

            if (current.pos.equals(currentGoal) || (d <= 1.5 && Math.abs(current.pos.getY() - currentGoal.getY()) <= 1)) {
                return reconstruct(current);
            }

            for (Transition trans : getTransitions(level, current.pos)) {
                if (closedSet.contains(trans.pos)) continue;

                double tentativeG = current.gCost + trans.cost;
                if (tentativeG < bestG.getOrDefault(trans.pos, Double.MAX_VALUE)) {
                    bestG.put(trans.pos, tentativeG);
                    PathNode neighbor = new PathNode(trans.pos, current, tentativeG, heuristic(trans.pos, currentGoal));
                    openSet.add(neighbor);
                }
            }
        }

        if (minDistance < heuristic(currentStart, currentGoal) - 2.0) {
            return reconstruct(bestClosest);
        }
        return Collections.emptyList();
    }

    private static BlockPos adjustToGround(Level level, BlockPos pos) {
        if (isWalkableStand(level, pos)) return pos;
        if (isWalkableStand(level, pos.above())) return pos.above();
        for (int d = 1; d <= 5; d++) {
            if (isWalkableStand(level, pos.below(d))) return pos.below(d);
        }
        return pos;
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static List<BlockPos> reconstruct(PathNode node) {
        LinkedList<BlockPos> path = new LinkedList<>();
        PathNode curr = node;
        while (curr != null) {
            path.addFirst(curr.pos);
            curr = curr.parent;
        }
        return path;
    }

    private static List<Transition> getTransitions(Level level, BlockPos pos) {
        List<Transition> list = new ArrayList<>(4);
        for (Direction dir : HORIZONTALS) {
            BlockPos flat = pos.relative(dir);

            // 1. Flat walk (same Y)
            if (isWalkableStand(level, flat)) {
                list.add(new Transition(flat, 1.0));
                continue;
            }

            // 2. Jump up 1 block (Y + 1)
            BlockPos jump = flat.above();
            if (isPassable(level, pos.above(2)) && isWalkableStand(level, jump)) {
                var obstacleShape = level.getBlockState(flat).getCollisionShape(level, flat);
                if (!obstacleShape.isEmpty() && obstacleShape.max(Direction.Axis.Y) > 1.0) {
                    continue; // Reject unjumpable 1.5-block fence/wall
                }
                list.add(new Transition(jump, 1.3));
                continue;
            }

            // 3. Step/drop down 1..3 blocks
            if (isPassable(level, flat) && isPassable(level, flat.above())) {
                for (int drop = 1; drop <= 3; drop++) {
                    BlockPos fallPos = flat.below(drop);
                    if (isPassable(level, fallPos) && isSolid(level, fallPos.below())) {
                        list.add(new Transition(fallPos, 1.0 + (drop * 0.3)));
                        break;
                    }
                    if (!isPassable(level, fallPos)) {
                        break;
                    }
                }
            }
        }
        return list;
    }

    public static boolean isWalkableStand(Level level, BlockPos pos) {
        return isPassable(level, pos) 
            && isPassable(level, pos.above()) 
            && isSolid(level, pos.below());
    }

    public static boolean isPassable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (isHazard(state)) return false;
        return state.getCollisionShape(level, pos).isEmpty();
    }

    public static boolean isSolid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (isHazard(state)) return false;
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isHazard(BlockState state) {
        return state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.MAGMA_BLOCK);
    }

    public static JsonArray pathToActions(List<BlockPos> path, boolean sprint) {
        JsonArray actions = new JsonArray();
        if (path == null || path.size() < 2) return actions;

        int i = 0;
        while (i < path.size() - 1) {
            BlockPos curr = path.get(i);
            BlockPos next = path.get(i + 1);

            int dx = next.getX() - curr.getX();
            int dy = next.getY() - curr.getY();
            int dz = next.getZ() - curr.getZ();

            float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;

            if (dy > 0) {
                // Jump up
                JsonObject look = new JsonObject();
                look.addProperty("type", "look");
                look.addProperty("yaw", yaw);
                look.addProperty("pitch", -15.0f);
                actions.add(look);

                JsonObject move = new JsonObject();
                move.addProperty("type", "move");
                move.addProperty("forward", true);
                move.addProperty("jump", true);
                move.addProperty("sprint", sprint);
                move.addProperty("ticks", 9);
                actions.add(move);

                JsonObject wait = new JsonObject();
                wait.addProperty("type", "wait");
                wait.addProperty("ticks", 2);
                actions.add(wait);

                i++;
            } else if (dy < 0) {
                // Drop down
                JsonObject look = new JsonObject();
                look.addProperty("type", "look");
                look.addProperty("yaw", yaw);
                look.addProperty("pitch", 20.0f);
                actions.add(look);

                JsonObject move = new JsonObject();
                move.addProperty("type", "move");
                move.addProperty("forward", true);
                move.addProperty("sprint", false);
                move.addProperty("ticks", 6 + (-dy * 3));
                actions.add(move);

                JsonObject wait = new JsonObject();
                wait.addProperty("type", "wait");
                wait.addProperty("ticks", 2);
                actions.add(wait);

                i++;
            } else {
                // Flat walk. Count straight steps in same direction
                int straightSteps = 1;
                while (i + straightSteps + 1 < path.size()) {
                    BlockPos p1 = path.get(i + straightSteps);
                    BlockPos p2 = path.get(i + straightSteps + 1);
                    if (p2.getY() == p1.getY() && (p2.getX() - p1.getX()) == dx && (p2.getZ() - p1.getZ()) == dz) {
                        straightSteps++;
                    } else {
                        break;
                    }
                }

                JsonObject look = new JsonObject();
                look.addProperty("type", "look");
                look.addProperty("yaw", yaw);
                look.addProperty("pitch", 0.0f);
                actions.add(look);

                int ticksPerBlock = sprint ? 4 : 6;
                JsonObject move = new JsonObject();
                move.addProperty("type", "move");
                move.addProperty("forward", true);
                move.addProperty("sprint", sprint);
                move.addProperty("ticks", straightSteps * ticksPerBlock);
                actions.add(move);

                i += straightSteps;
            }
        }

        return actions;
    }
}
