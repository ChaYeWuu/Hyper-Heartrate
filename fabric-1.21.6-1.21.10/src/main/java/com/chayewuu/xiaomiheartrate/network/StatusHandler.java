package com.chayewuu.xiaomiheartrate.network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.chayewuu.xiaomiheartrate.device.BleDevice;
import com.chayewuu.xiaomiheartrate.heart.HeartRateManager;
import com.chayewuu.xiaomiheartrate.heart.HeartRateStorage;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code GET /status} 处理器。
 * <p>返回当前连接状态、设备信息、心率与 HTTP 服务地址的综合状态快照。</p>
 *
 * <p>响应示例：</p>
 * <pre>
 * {
 *   "connected": true,
 *   "deviceName": "Mi Band 10",
 *   "deviceAddress": "AA:BB:CC:DD:EE:FF",
 *   "connectionState": "CONNECTED",
 *   "currentHeartRate": 76,
 *   "lastUpdate": 1754220000,
 *   "httpServer": "127.0.0.1:24557"
 * }
 * </pre>
 *
 * <p>{@code connectionState} 由 {@link HeartRateManager#isConnected()} 派生：
 * 已连接返回 {@code "CONNECTED"}，否则返回 {@code "DISCONNECTED"}。</p>
 */
public class StatusHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendMethodNotAllowed(exchange);
            return;
        }
        HeartRateManager mgr = HeartRateManager.getInstance();
        BleDevice device = mgr.getCurrentDevice();
        boolean connected = mgr.isConnected();
        int heartRate = mgr.getCurrentHeartRate();

        // 取最新一条记录的时间戳（毫秒）
        long lastUpdateMs = 0L;
        HeartRateStorage storage = mgr.getStorage();
        if (storage.size() > 0) {
            var latest = storage.getHistory(1);
            if (!latest.isEmpty()) {
                lastUpdateMs = latest.get(0).timestamp();
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connected", connected);
        body.put("deviceName", (connected && device != null) ? device.getName() : null);
        body.put("deviceAddress", (connected && device != null) ? device.getAddress() : null);
        body.put("connectionState", connected ? "CONNECTED" : "DISCONNECTED");
        body.put("currentHeartRate", heartRate);
        body.put("lastUpdate", lastUpdateMs / 1000L);
        body.put("httpServer", HttpServerManager.getInstance().getAddress());

        HttpUtil.sendJson(exchange, HttpUtil.GSON.toJson(body));
    }
}
