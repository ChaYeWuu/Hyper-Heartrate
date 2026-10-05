package com.chayewuu.hyperheartrate.network;

import com.chayewuu.hyperheartrate.config.ConfigManager;
import com.chayewuu.hyperheartrate.util.ModLogger;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP Server 管理器（单例）。
 * <p>
 * 在本地启动一个轻量 HTTP Server，对外暴露当前心率、设备状态、历史图表数据，
 * 供 OBS 浏览器源、外部工具通过 HTTP 调用获取。
 * </p>
 *
 * <p>端口选择策略：</p>
 * <ul>
 *     <li>若 {@link com.chayewuu.hyperheartrate.config.ModConfig#getHttpPort()} 不为 {@code null}，
 *         使用指定端口；</li>
 *     <li>否则从 {@link #RANDOM_PORT_START}（24557）开始递增尝试，最多尝试
 *         {@link #RANDOM_PORT_MAX_ATTEMPTS}（100）个端口；</li>
 *     <li>若全部不可用，使用 {@code ServerSocket(0)} 让 OS 分配随机端口。</li>
 * </ul>
 *
 * <p>线程模型：HTTP Server 使用独立的守护线程池作为 executor，不阻塞主线程。
 * SSE 长连接由 {@link GraphSseHandler} 内部维护。</p>
 */
public class HttpServerManager {
    /** 随机端口扫描起始端口 */
    private static final int RANDOM_PORT_START = 24557;
    /** 随机端口扫描最大尝试次数 */
    private static final int RANDOM_PORT_MAX_ATTEMPTS = 100;

    private static volatile HttpServerManager instance;

    private HttpServer server;
    private int port = -1;
    private String bindAddress = "127.0.0.1";
    private volatile boolean running = false;

    /** HTTP Server 后台线程池 */
    private ExecutorService executor;

    /** SSE 心率事件推送器 */
    private final GraphSseHandler sseHandler = new GraphSseHandler();

    /**
     * 私有构造器（单例）。
     */
    private HttpServerManager() {
    }

    /**
     * 获取单例实例。
     *
     * @return 全局唯一实例
     */
    public static synchronized HttpServerManager getInstance() {
        if (instance == null) {
            instance = new HttpServerManager();
        }
        return instance;
    }

    /**
     * 启动 HTTP Server。
     * <p>从配置读取绑定地址与端口；端口为 {@code null} 时按端口选择策略分配。
     * Server 在后台线程运行，不阻塞调用线程。</p>
     */
    public synchronized void start() {
        if (running) {
            ModLogger.warn("HTTP Server 已在运行，端口: {}", port);
            return;
        }
        String addr = ConfigManager.getConfig().getBindAddress();
        Integer configPort = ConfigManager.getConfig().getHttpPort();
        if (addr != null && !addr.isEmpty()) {
            this.bindAddress = addr;
        }

        int bindPort = resolveBindPort(configPort);

        try {
            InetSocketAddress socketAddress = new InetSocketAddress(bindAddress, bindPort);
            server = HttpServer.create(socketAddress, 0);
            setupRoutes();
            executor = Executors.newFixedThreadPool(8, new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "HeartRateMod-HttpServer-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });
            server.setExecutor(executor);
            server.start();
            this.port = server.getAddress().getPort();
            this.running = true;
            ModLogger.info("HTTP Server 已启动: {}", getAddress());
        } catch (IOException e) {
            ModLogger.error("HTTP Server 启动失败: " + bindAddress + ":" + bindPort, e);
        }
    }

    /**
     * 解析实际绑定端口。
     * <p>配置端口非空时直接使用；否则从 24557 起递增扫描可用端口；
     * 全部不可用时回退到 OS 随机分配。</p>
     *
     * @param configPort 配置端口，可为 {@code null}
     * @return 实际可绑定端口
     */
    private int resolveBindPort(Integer configPort) {
        if (configPort != null) {
            return configPort;
        }
        // 从 24557 起尝试最多 100 个端口
        for (int i = 0; i < RANDOM_PORT_MAX_ATTEMPTS; i++) {
            int candidate = RANDOM_PORT_START + i;
            if (isPortAvailable(bindAddress, candidate)) {
                ModLogger.info("HTTP Server 选中可用端口: {}", candidate);
                return candidate;
            }
        }
        // 全部不可用，让 OS 分配随机端口
        int osAssigned = findRandomFreePort();
        ModLogger.info("HTTP Server 由 OS 分配随机端口: {}", osAssigned);
        return osAssigned;
    }

    /**
     * 探测指定地址+端口是否可用（未被占用）。
     *
     * @param addr 绑定地址
     * @param port 端口
     * @return {@code true} 表示可绑定
     */
    private static boolean isPortAvailable(String addr, int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(addr, port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 由 OS 分配一个随机空闲端口。
     * <p>用 {@code ServerSocket(0)} 临时占用并读取端口后立即关闭。</p>
     *
     * @return OS 分配的端口号
     */
    private static int findRandomFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            // 极端情况下仍失败，回退到固定 0 让 HttpServer 自行处理
            ModLogger.warn("OS 随机端口分配失败，将使用 0", e);
            return 0;
        }
    }

    /**
     * 停止 HTTP Server。
     * <p>等待最多 2 秒让已建立连接处理完毕，再关闭 executor。</p>
     */
    public synchronized void stop() {
        if (!running || server == null) {
            return;
        }
        sseHandler.shutdown();
        server.stop(2);
        server = null;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
        executor = null;
        running = false;
        port = -1;
        ModLogger.info("HTTP Server 已停止");
    }

    /**
     * 查询是否正在运行。
     *
     * @return {@code true} 表示运行中
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 获取实际监听端口。
     *
     * @return 端口号；未启动时为 -1
     */
    public int getPort() {
        return port;
    }

    /**
     * 获取监听地址字符串。
     *
     * @return 形如 {@code "127.0.0.1:8080"}；未启动时端口部分为 -1
     */
    public String getAddress() {
        return bindAddress + ":" + port;
    }

    /**
     * 获取 SSE 推送处理器（供 {@link GraphHandler} 注册事件源用）。
     *
     * @return SSE 处理器实例
     */
    GraphSseHandler getSseHandler() {
        return sseHandler;
    }

    /**
     * 设置 HTTP 路由。
     * <p>注册 {@code /heart}、{@code /status}、{@code /graph}、{@code /graph/events}、{@code /obs} 端点。</p>
     */
    private void setupRoutes() {
        server.createContext("/heart", new HeartRateHandler());
        server.createContext("/status", new StatusHandler());
        server.createContext("/graph/events", sseHandler);
        server.createContext("/graph", new GraphHandler());
        server.createContext("/obs", new ObsHandler());
        // 根路径返回服务信息，便于浏览器直接访问根路径探活
        server.createContext("/", new RootHandler());
    }
}
