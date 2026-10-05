package com.chayewuu.hyperheartrate.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 响应工具类。
 * <p>封装各 Handler 共用的写响应、错误码、JSON 序列化等逻辑，避免重复代码。</p>
 *
 * <p>该类为工具类，禁止实例化。</p>
 */
public final class HttpUtil {
    /** 共享 GSON 实例（禁用 HTML 转义，保留 JSON 字段顺序由调用方用 LinkedHashMap 保证） */
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private HttpUtil() {
    }

    /**
     * 写入 JSON 响应（HTTP 200）。
     *
     * @param exchange HTTP 交换
     * @param payload  JSON 字符串
     * @throws IOException 写入失败
     */
    public static void sendJson(HttpExchange exchange, String payload) throws IOException {
        sendJson(exchange, 200, payload);
    }

    /**
     * 写入 JSON 响应（指定状态码）。
     *
     * @param exchange   HTTP 交换
     * @param statusCode HTTP 状态码
     * @param payload    JSON 字符串
     * @throws IOException 写入失败
     */
    public static void sendJson(HttpExchange exchange, int statusCode, String payload) throws IOException {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    /**
     * 写入 HTML 响应（HTTP 200）。
     *
     * @param exchange HTTP 交换
     * @param html     HTML 字符串
     * @throws IOException 写入失败
     */
    public static void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] data = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    /**
     * 写入纯文本响应（指定状态码）。
     *
     * @param exchange   HTTP 交换
     * @param statusCode HTTP 状态码
     * @param text       文本
     * @throws IOException 写入失败
     */
    public static void sendText(HttpExchange exchange, int statusCode, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    /**
     * 响应 405 Method Not Allowed。
     *
     * @param exchange HTTP 交换
     * @throws IOException 写入失败
     */
    public static void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendText(exchange, 405, "405 Method Not Allowed");
    }

    /**
     * 响应 404 Not Found。
     *
     * @param exchange HTTP 交换
     * @throws IOException 写入失败
     */
    public static void sendNotFound(HttpExchange exchange) throws IOException {
        sendText(exchange, 404, "404 Not Found");
    }
}
