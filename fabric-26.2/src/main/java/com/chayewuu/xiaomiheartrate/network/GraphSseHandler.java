package com.chayewuu.xiaomiheartrate.network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.chayewuu.xiaomiheartrate.device.BleDevice;
import com.chayewuu.xiaomiheartrate.heart.HeartRateListener;
import com.chayewuu.xiaomiheartrate.heart.HeartRateManager;
import com.chayewuu.xiaomiheartrate.util.ModLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code GET /graph/events} 处理器（Server-Sent Events）。
 * <p>保持长连接，实时向浏览器推送心率变化事件。</p>
 *
 * <p>事件格式：</p>
 * <pre>
 * data: {"heartRate":76,"timestamp":1754220000}
 *
 * </pre>
 *
 * <p>行为：</p>
 * <ul>
 *     <li>客户端连接时立即推送一次当前心率快照；</li>
 *     <li>注册 {@link HeartRateListener}，心率变化时向所有客户端广播事件；</li>
 *     <li>5 秒内无数据变化时推送心跳保活（SSE 注释行 {@code ": heartbeat"}）；</li>
 *     <li>客户端断开（写失败）或服务停止时清理连接。</li>
 * </ul>
 *
 * <p>线程模型：每个客户端由 HttpServer executor 线程阻塞服务，
 * 事件通过无界 {@link LinkedBlockingQueue} 投递，写入失败即认为客户端已断开。</p>
 */
public class GraphSseHandler implements HttpHandler {

    /** 心跳保活间隔（毫秒） */
    private static final long HEARTBEAT_INTERVAL_MS = 5000L;
    /** 客户端阻塞 poll 超时（毫秒） */
    private static final long POLL_TIMEOUT_MS = 1000L;

    /** 已连接的客户端列表 */
    private final CopyOnWriteArrayList<ClientConnection> clients = new CopyOnWriteArrayList<>();

    /** 心跳保活调度器 */
    private final ScheduledExecutorService heartbeatScheduler;

    /** 心率监听器（向所有客户端广播事件） */
    private final HeartRateListener listener;

    /** 停止标志 */
    private volatile boolean shutdown = false;

    /**
     * 构造 SSE 处理器，注册心率监听器并启动心跳保活。
     */
    public GraphSseHandler() {
        this.listener = new HeartRateListener() {
            @Override
            public void onHeartRateChanged(int heartRate, long timestamp) {
                broadcast(heartRate, timestamp);
            }

            @Override
            public void onDeviceConnected(BleDevice device) {
                // 设备连接/断开也广播一次状态，前端可据此刷新
                broadcastCurrentSnapshot("device-connected");
            }

            @Override
            public void onDeviceDisconnected(BleDevice device) {
                broadcastCurrentSnapshot("device-disconnected");
            }
        };
        HeartRateManager.getInstance().addListener(listener);

        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "HeartRateMod-SseHeartbeat-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeats,
                HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtil.sendMethodNotAllowed(exchange);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-transform");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        // 0 表示 chunked 编码，保持连接打开
        exchange.sendResponseHeaders(200, 0);

        OutputStream os = exchange.getResponseBody();
        ClientConnection client = new ClientConnection(os);
        clients.add(client);

        // 立即推送一次当前快照，前端首次加载即可显示
        sendInitialSnapshot(client);

        try {
            while (!shutdown && !client.isClosed()) {
                String event = client.poll(POLL_TIMEOUT_MS);
                if (event == null || event.isEmpty()) {
                    continue;
                }
                if (!client.write(event)) {
                    // 写入失败，客户端已断开
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            clients.remove(client);
            client.closeSilently();
        }
    }

    /**
     * 推送初始心率快照给新连接的客户端。
     *
     * @param client 客户端
     */
    private void sendInitialSnapshot(ClientConnection client) {
        HeartRateManager mgr = HeartRateManager.getInstance();
        int hr = mgr.getCurrentHeartRate();
        long ts = 0L;
        if (mgr.getStorage().size() > 0) {
            var latest = mgr.getStorage().getHistory(1);
            if (!latest.isEmpty()) {
                ts = latest.get(0).timestamp();
            }
        }
        client.offer(buildDataEvent(hr, ts));
    }

    /**
     * 心率变化时向所有客户端广播数据事件。
     *
     * @param heartRate 心率值
     * @param timestamp 时间戳（毫秒）
     */
    private void broadcast(int heartRate, long timestamp) {
        String event = buildDataEvent(heartRate, timestamp);
        for (ClientConnection c : clients) {
            c.offer(event);
        }
    }

    /**
     * 向所有客户端广播当前心率快照（带命名事件）。
     *
     * @param eventName SSE 事件名
     */
    private void broadcastCurrentSnapshot(String eventName) {
        HeartRateManager mgr = HeartRateManager.getInstance();
        int hr = mgr.getCurrentHeartRate();
        long ts = 0L;
        if (mgr.getStorage().size() > 0) {
            var latest = mgr.getStorage().getHistory(1);
            if (!latest.isEmpty()) {
                ts = latest.get(0).timestamp();
            }
        }
        String payload = "{\"heartRate\":" + hr + ",\"timestamp\":" + (ts / 1000L)
                + ",\"connected\":" + mgr.isConnected() + "}";
        String event = "event: " + eventName + "\ndata: " + payload + "\n\n";
        for (ClientConnection c : clients) {
            c.offer(event);
        }
    }

    /**
     * 构造标准数据事件字符串。
     *
     * @param heartRate 心率值
     * @param timestampMs 时间戳（毫秒）
     * @return SSE 事件文本
     */
    private static String buildDataEvent(int heartRate, long timestampMs) {
        return "data: {\"heartRate\":" + heartRate + ",\"timestamp\":" + (timestampMs / 1000L) + "}\n\n";
    }

    /**
     * 向所有客户端发送心跳保活注释。
     * <p>5 秒内无数据变化时调用，避免代理/浏览器因超时关闭连接。</p>
     */
    private void sendHeartbeats() {
        if (clients.isEmpty()) {
            return;
        }
        String heartbeat = ": heartbeat\n\n";
        for (ClientConnection c : clients) {
            c.offer(heartbeat);
        }
    }

    /**
     * 关闭所有客户端连接，停止心跳调度器并注销监听器。
     * <p>由 {@link HttpServerManager#stop()} 调用。</p>
     */
    public void shutdown() {
        shutdown = true;
        heartbeatScheduler.shutdownNow();
        try {
            HeartRateManager.getInstance().removeListener(listener);
        } catch (Throwable t) {
            ModLogger.warn("注销 SSE 心率监听器异常", t);
        }
        for (ClientConnection c : clients) {
            c.close();
        }
        clients.clear();
    }

    /**
     * 获取当前已连接的 SSE 客户端数量。
     *
     * @return 客户端数
     */
    public int getClientCount() {
        return clients.size();
    }

    /**
     * 单个 SSE 客户端连接。
     * <p>封装输出流与事件队列，提供线程安全的事件投递与阻塞消费。</p>
     */
    private static final class ClientConnection {
        private final OutputStream os;
        private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        private volatile boolean closed = false;

        ClientConnection(OutputStream os) {
            this.os = os;
        }

        /**
         * 投递事件到队列（非阻塞）。
         *
         * @param event 事件文本
         */
        void offer(String event) {
            if (!closed) {
                queue.offer(event);
            }
        }

        /**
         * 阻塞拉取下一个事件。
         *
         * @param timeoutMillis 超时毫秒
         * @return 事件文本；超时返回 {@code null}
         * @throws InterruptedException 被中断
         */
        String poll(long timeoutMillis) throws InterruptedException {
            return queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        /**
         * 写入并刷新事件到输出流。
         *
         * @param event 事件文本
         * @return {@code true} 写入成功；{@code false} 写入失败（客户端已断开）
         */
        synchronized boolean write(String event) {
            if (closed) {
                return false;
            }
            try {
                os.write(event.getBytes(StandardCharsets.UTF_8));
                os.flush();
                return true;
            } catch (IOException e) {
                closed = true;
                return false;
            }
        }

        boolean isClosed() {
            return closed;
        }

        /**
         * 关闭连接，唤醒阻塞的 poll。
         */
        void close() {
            closed = true;
            queue.offer(""); // 唤醒阻塞的 poll
        }

        /**
         * 静默关闭输出流（不抛异常）。
         */
        void closeSilently() {
            closed = true;
            try {
                os.close();
            } catch (IOException ignored) {
                // 静默忽略
            }
        }
    }
}
