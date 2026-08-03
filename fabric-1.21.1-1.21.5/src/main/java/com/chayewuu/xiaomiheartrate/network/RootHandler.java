package com.chayewuu.xiaomiheartrate.network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code GET /} 根路径处理器。
 * <p>返回服务基本信息（端点列表、运行状态），便于浏览器直接访问根路径探活。</p>
 *
 * <p>响应示例：</p>
 * <pre>
 * {
 *   "service": "XiaomiHeartrate HTTP Server",
 *   "version": "1.0.0",
 *   "running": true,
 *   "endpoints": {
 *     "heart": "GET /heart - 当前心率快照",
 *     "status": "GET /status - 综合状态",
 *     "graph": "GET /graph - 实时图表页面",
 *     "events": "GET /graph/events - SSE 实时推送"
 *   }
 * }
 * </pre>
 */
public class RootHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendMethodNotAllowed(exchange);
            return;
        }
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("heart", "GET /heart - 当前心率快照");
        endpoints.put("status", "GET /status - 综合状态");
        endpoints.put("graph", "GET /graph - 实时图表页面");
        endpoints.put("events", "GET /graph/events - SSE 实时推送");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "XiaomiHeartrate HTTP Server");
        body.put("version", "1.0.0");
        body.put("running", HttpServerManager.getInstance().isRunning());
        body.put("address", HttpServerManager.getInstance().getAddress());
        body.put("endpoints", endpoints);

        HttpUtil.sendJson(exchange, HttpUtil.GSON.toJson(body));
    }
}
