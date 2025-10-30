# WebSockets

## 01. WebSocket Connection Attributes Issue

### 🧩 Issue
**Header** and **Param** tabs in Postman will **not work** for setting attributes like `userId` or `userName`.

### ⚠️ Why
Spring’s WebSocket server does **not automatically copy** headers or query parameters into `session.getAttributes()`  
unless you use a **HandshakeInterceptor** or handle it manually in your connection logic.

That’s why `session.getAttributes().get("userId")` and `session.getAttributes().get("userName")` return `null`.

---

### ✅ Solution
Manually parse query parameters inside the `afterConnectionEstablished` method:

```java
@Override
public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    URI uri = session.getUri();
    if (uri != null) {
        String query = uri.getQuery(); // e.g., userId=123&userName=Alice
        Map<String, String> params = Arrays.stream(query.split("&"))
            .map(s -> s.split("="))
            .collect(Collectors.toMap(a -> a[0], a -> a[1]));

        String userId = params.get("userId");
        String userName = params.get("userName");

        log.info("Connected user: {} - {}", userId, userName);
        session.getAttributes().put("userId", userId);
        session.getAttributes().put("userName", userName);
    }
}
