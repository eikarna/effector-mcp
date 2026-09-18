package eikarna.effector.bridge;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EventBroadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventBroadcaster.class);
    private static final EventBroadcaster INSTANCE = new EventBroadcaster();

    private final List<SSEClient> clients = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mcp-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    private EventBroadcaster() {
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeat, 15, 15, TimeUnit.SECONDS);
    }

    public static EventBroadcaster getInstance() {
        return INSTANCE;
    }

    public void registerClient(HttpExchange exchange) {
        try {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, 0);

            OutputStream os = exchange.getResponseBody();
            SSEClient client = new SSEClient(exchange, os);
            clients.add(client);

            JsonObject initObj = new JsonObject();
            initObj.addProperty("status", "connected");
            initObj.addProperty("active_clients", clients.size());
            initObj.addProperty("timestamp", System.currentTimeMillis());
            client.sendEvent("connected", initObj.toString());

            LOGGER.info("SSE client connected from {}. Total subscribers: {}", exchange.getRemoteAddress(), clients.size());
        } catch (Exception e) {
            LOGGER.error("Failed to initialize SSE client connection", e);
        }
    }

    public void broadcast(String eventType, JsonObject data) {
        if (clients.isEmpty()) return;
        String payload = data != null ? data.toString() : "{}";
        for (SSEClient client : clients) {
            if (!client.sendEvent(eventType, payload)) {
                clients.remove(client);
            }
        }
    }

    private void sendHeartbeat() {
        if (clients.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (SSEClient client : clients) {
            if (!client.sendComment("ping " + now)) {
                clients.remove(client);
            }
        }
    }

    public int getClientCount() {
        return clients.size();
    }

    public void closeAll() {
        for (SSEClient client : clients) {
            client.close();
        }
        clients.clear();
    }

    private static class SSEClient {
        final HttpExchange exchange;
        final OutputStream os;

        SSEClient(HttpExchange exchange, OutputStream os) {
            this.exchange = exchange;
            this.os = os;
        }

        synchronized boolean sendEvent(String eventName, String data) {
            try {
                String msg = "event: " + eventName + "\ndata: " + data + "\n\n";
                os.write(msg.getBytes(StandardCharsets.UTF_8));
                os.flush();
                return true;
            } catch (Exception e) {
                close();
                return false;
            }
        }

        synchronized boolean sendComment(String comment) {
            try {
                String msg = ": " + comment + "\n\n";
                os.write(msg.getBytes(StandardCharsets.UTF_8));
                os.flush();
                return true;
            } catch (Exception e) {
                close();
                return false;
            }
        }

        void close() {
            try {
                os.close();
            } catch (Exception ignored) {}
            try {
                exchange.close();
            } catch (Exception ignored) {}
        }
    }
}
