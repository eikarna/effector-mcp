# Real-Time SSE Streaming

Effector features a high-throughput Server-Sent Events (SSE) broadcaster listening on:

`http://127.0.0.1:8080/events`

---

## Why SSE?

Traditional agents poll HTTP endpoints every second to check if a macro has finished or if a player said something in chat. This burns tokens, wastes bandwidth, and introduces latency.

With SSE, the Minecraft client pushes events to your agent instantly the exact millisecond they happen.

---

## Python Subscriber Example

```python
import urllib.request

req = urllib.request.Request(
    "http://127.0.0.1:8080/events",
    headers={"Accept": "text/event-stream"}
)

with urllib.request.urlopen(req) as stream:
    print("Connected to Effector SSE Stream!")
    while True:
        line = stream.readline().decode("utf-8").strip()
        if line.startswith("event:"):
            event_name = line.split(":", 1)[1].strip()
        elif line.startswith("data:"):
            data = line.split(":", 1)[1].strip()
            print(f"[{event_name}] {data}")
```

### Event Types
* `connected`: Emitted upon initial handshake.
* `chat_message`: Emitted when any chat or command message appears in game.
* `action_queue_started`: Emitted when a physical macro begins execution.
* `action_queue_completed`: Emitted the moment all macro steps finish.
