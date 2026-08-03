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
 * {@code GET /heart} 处理器。
 * <p>返回当前心率、设备名、连接状态与最新采样时间戳（秒）。</p>
 *
 * <p>响应示例：</p>
 * <pre>
 * {
 *   "heartRate": 76,
 *   "device": "Mi Band 10",
 *   "connected": true,
 *   "timestamp": 1754220000
 * }
 * </pre>
 *
 * <p>未连接设备时 {@code device} 为 {@code null}、{@code heartRate} 为 0、
 * {@code timestamp} 为 0。</p>
 */
public class HeartRateHandler implements HttpHandler {

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
        long timestampMs = 0L;
        if (connected && heartRate > 0) {
            HeartRateStorage storage = mgr.getStorage();
            if (storage.size() > 0) {
                var history = storage.getHistory(1);
                if (!history.isEmpty()) {
                    timestampMs = history.get(0).timestamp();
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("heartRate", heartRate);
        body.put("device", (connected && device != null) ? device.getName() : null);
        body.put("connected", connected);
        body.put("timestamp", timestampMs / 1000L);

        HttpUtil.sendJson(exchange, HttpUtil.GSON.toJson(body));
    }
}
