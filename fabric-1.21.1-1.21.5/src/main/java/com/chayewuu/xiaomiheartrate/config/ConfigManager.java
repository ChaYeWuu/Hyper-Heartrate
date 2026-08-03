package com.chayewuu.xiaomiheartrate.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.chayewuu.xiaomiheartrate.util.ModLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 配置管理器（GSON 读写）。
 * <p>
 * 负责将 {@link ModConfig} 持久化到 {@code config/heartrate.json}，
 * 支持懒加载、同步/异步保存。配置文件不存在时使用默认值并自动创建。
 * </p>
 *
 * <p>线程模型：</p>
 * <ul>
 *     <li>{@link #getConfig()} 返回全局单例，懒加载（首次调用触发 {@link #load()}）；</li>
 *     <li>{@link #load()} / {@link #saveSync()} 使用 {@code synchronized} 串行化；</li>
 *     <li>{@link #save()} 提交到单线程守护执行器异步执行，避免阻塞 GUI 线程；</li>
 *     <li>退出时调用 {@link #shutdown()} 优雅关闭执行器。</li>
 * </ul>
 *
 * <p>容错：配置文件不存在或解析失败时使用默认值，绝不抛出异常到调用方。</p>
 */
public class ConfigManager {
    /** 配置目录名（相对游戏运行根目录） */
    private static final String CONFIG_DIR = "config";
    /** 配置文件名 */
    private static final String CONFIG_FILE = "heartrate.json";

    /**
     * GSON 实例。
     * <p>启用美化输出与 null 序列化（确保 {@code httpPort=null} 等字段被写入文件）。</p>
     */
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    /** 当前配置实例，懒加载 */
    private static ModConfig config;

    /**
     * 异步保存执行器（单线程，串行化写入，守护线程）。
     * <p>使用单线程确保多次 {@link #save()} 调用按提交顺序串行落盘，避免并发写文件冲突。</p>
     */
    private static final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        /** 线程序号计数器（用于命名） */
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "HeartRateMod-ConfigSaver-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    /**
     * 获取配置实例（懒加载）。
     * <p>首次调用时从文件读取，文件不存在则使用默认值并尝试创建文件。</p>
     *
     * @return 当前配置
     */
    public static ModConfig getConfig() {
        if (config == null) {
            load();
        }
        return config;
    }

    /**
     * 从 {@code config/heartrate.json} 加载配置。
     * <p>文件不存在或解析失败时使用默认值，并尝试回写创建文件。</p>
     * <p>该方法线程安全，多次调用幂等。</p>
     */
    public static synchronized void load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                ModConfig loaded = gson.fromJson(json, ModConfig.class);
                config = loaded != null ? loaded : new ModConfig();
                ModLogger.info("配置已加载: {}", path);
            } catch (JsonParseException e) {
                // JSON 格式错误：用默认值并尝试覆盖回写
                ModLogger.error("配置文件格式损坏，使用默认值: " + path, e);
                config = new ModConfig();
                safeCreateDefaultFile(path);
            } catch (IOException e) {
                // 读取 IO 错误：用默认值，不覆盖文件
                ModLogger.error("配置文件读取失败，使用默认值: " + path, e);
                config = new ModConfig();
            } catch (Exception e) {
                // 兜底：任何未预期异常都用默认值
                ModLogger.error("配置加载出现未预期异常，使用默认值: " + path, e);
                config = new ModConfig();
            }
        } else {
            ModLogger.info("配置文件不存在，使用默认值: {}", path);
            config = new ModConfig();
            safeCreateDefaultFile(path);
        }
    }

    /**
     * 异步保存配置到文件。
     * <p>提交到单线程守护执行器执行，避免阻塞调用线程（通常是 GUI/渲染线程）。</p>
     * <p>多次调用按提交顺序串行落盘。</p>
     */
    public static void save() {
        saveExecutor.submit(ConfigManager::saveSync);
    }

    /**
     * 同步保存配置到文件。
     * <p>通常由 {@link #save()} 异步派发；如需立即落盘（如退出前 flush）可直接调用。</p>
     */
    public static synchronized void saveSync() {
        if (config == null) {
            return;
        }
        Path path = getConfigPath();
        try {
            // 确保目录存在
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            String json = gson.toJson(config);
            Files.writeString(path, json);
            ModLogger.debug("配置已保存: {}", path);
        } catch (IOException e) {
            ModLogger.error("配置保存失败: " + path, e);
        } catch (Exception e) {
            ModLogger.error("配置保存出现未预期异常: " + path, e);
        }
    }

    /**
     * 获取配置文件路径。
     * <p>路径为 {@code config/heartrate.json}（相对游戏运行目录）。</p>
     *
     * @return 配置文件 Path
     */
    public static Path getConfigPath() {
        return Paths.get(CONFIG_DIR, CONFIG_FILE);
    }

    /**
     * 关闭配置保存执行器。
     * <p>等待已提交任务完成（最多 5 秒）后关闭，避免遗留任务丢失。
     * 通常在 Mod 卸载或 JVM 关闭钩子中调用。</p>
     */
    public static void shutdown() {
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ModLogger.warn("配置保存执行器未在 5 秒内关闭，强制关闭");
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            saveExecutor.shutdownNow();
        }
    }

    /**
     * 尝试同步写回默认配置文件（不抛出异常）。
     * <p>用于配置文件不存在或损坏时创建初始文件。</p>
     *
     * @param path 配置文件路径
     */
    private static void safeCreateDefaultFile(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            // 注意：此处复用当前 config（已设为默认值）进行写回
            String json = gson.toJson(config);
            Files.writeString(path, json);
            ModLogger.info("默认配置文件已创建: {}", path);
        } catch (Exception e) {
            ModLogger.error("默认配置文件创建失败: " + path, e);
        }
    }
}
