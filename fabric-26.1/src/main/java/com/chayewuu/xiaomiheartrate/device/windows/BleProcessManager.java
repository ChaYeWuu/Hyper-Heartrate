package com.chayewuu.xiaomiheartrate.device.windows;

import com.chayewuu.xiaomiheartrate.util.ModLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BLE 子进程生命周期管理器。
 * <p>
 * 统一管理所有由本 Mod 启动的 ble-tool（.NET Host）子进程，提供：
 * <ul>
 *   <li><b>PID 记录</b>：进程启动时记录 PID 到内存集合与磁盘文件，
 *       供游戏崩溃后恢复时清理残留进程；</li>
 *   <li><b>启动清理</b>：Mod 初始化时读取上次遗留的 PID 文件，
 *       逐个验证后通过 {@code taskkill /T /F /PID} 杀掉进程树
 *       （仅杀本 Mod 启动的进程，不误杀其他 dotnet 进程）；</li>
 *   <li><b>关闭清理</b>：JVM 关闭钩子中杀掉所有已注册进程的进程树，
 *       然后删除 PID 文件。</li>
 * </ul>
 * </p>
 *
 * <p><b>安全性</b>：</p>
 * <ul>
 *   <li>仅杀 PID 文件中记录的进程（这些 PID 一定是本 Mod 启动的）；</li>
 *   <li>杀进程前通过 {@code tasklist} 验证进程映像名为 {@code dotnet.exe}，
 *       防止 PID 被复用后误杀无关进程；</li>
 *   <li>使用 {@code taskkill /T /F} 杀整棵进程树（.NET Host 可能派生子进程）。</li>
 * </ul>
 */
public final class BleProcessManager {
    private static final String LOG_TAG = "[BleProcessManager]";
    private static final String PID_FILE_NAME = ".ble-pids";

    /** 运行时已注册的 PID 集合（线程安全） */
    private static final Set<Long> registeredPids = ConcurrentHashMap.newKeySet();

    /** PID 文件路径（init 后确定） */
    private static volatile Path pidFilePath;

    private BleProcessManager() {
    }

    /**
     * 初始化：确定 PID 文件路径，清理上次残留进程。
     * <p>应在 Mod 初始化最早期调用（先于任何 BLE 操作）。</p>
     */
    public static void init() {
        pidFilePath = resolvePidFile();
        cleanupLeftoverProcesses();
    }

    /**
     * 注册一个已启动的子进程。
     * <p>将 PID 加入内存集合并追加到磁盘文件，供崩溃恢复使用。</p>
     *
     * @param process 已启动的 Process 对象
     */
    public static void register(Process process) {
        try {
            long pid = process.pid();
            registeredPids.add(pid);
            appendPidToFile(pid);
            ModLogger.info("{} 已注册进程 PID={}", LOG_TAG, pid);
        } catch (Throwable t) {
            ModLogger.warn("{} 注册进程 PID 失败: {}", LOG_TAG, t.getMessage());
        }
    }

    /**
     * 注销一个已正常退出的进程。
     * <p>从内存集合移除 PID，下次 shutdownAll 不会重复杀。</p>
     *
     * @param process 已退出的 Process 对象
     */
    public static void unregister(Process process) {
        try {
            long pid = process.pid();
            registeredPids.remove(pid);
        } catch (Throwable ignored) {
            // 忽略
        }
    }

    /**
     * 杀掉指定进程及其整棵子进程树。
     * <p>Windows 上使用 {@code taskkill /T /F /PID}，其他平台用
     * {@code ProcessHandle.destroyForcibly()}。</p>
     *
     * @param process 要杀掉的进程
     */
    public static void killProcessTree(Process process) {
        try {
            long pid = process.pid();
            killProcessTreeByPid(pid);
            registeredPids.remove(pid);
        } catch (Throwable t) {
            ModLogger.warn("{} killProcessTree 异常: {}", LOG_TAG, t.getMessage());
            try {
                process.destroyForcibly();
            } catch (Throwable ignored) {
                // 忽略
            }
        }
    }

    /**
     * 关闭所有已注册的进程并清理 PID 文件。
     * <p>由 JVM 关闭钩子调用。</p>
     */
    public static void shutdownAll() {
        ModLogger.info("{} 关闭所有已注册进程，共 {} 个", LOG_TAG, registeredPids.size());
        for (Long pid : registeredPids) {
            killProcessTreeByPid(pid);
        }
        registeredPids.clear();
        deletePidFile();
    }

    // ====== 内部实现 ======

    /**
     * 清理上次遗留的进程（Mod 启动时调用）。
     * <p>读取 PID 文件，逐个验证后杀掉进程树，最后删除文件。</p>
     */
    private static void cleanupLeftoverProcesses() {
        if (pidFilePath == null || !Files.exists(pidFilePath)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(pidFilePath, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                deletePidFile();
                return;
            }
            ModLogger.info("{} 发现上次遗留 PID 文件，共 {} 条记录", LOG_TAG, lines.size());
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    long pid = Long.parseLong(trimmed);
                    if (isProcessAlive(pid)) {
                        if (isDotnetProcess(pid)) {
                            ModLogger.info("{} 清理残留 ble-tool 进程 PID={}", LOG_TAG, pid);
                            killProcessTreeByPid(pid);
                        } else {
                            ModLogger.warn("{} PID={} 仍存活但非 dotnet 进程，跳过（避免误杀）",
                                    LOG_TAG, pid);
                        }
                    }
                } catch (NumberFormatException e) {
                    ModLogger.warn("{} PID 文件行格式无效: {}", LOG_TAG, trimmed);
                }
            }
        } catch (IOException e) {
            ModLogger.warn("{} 读取 PID 文件失败: {}", LOG_TAG, e.getMessage());
        }
        deletePidFile();
    }

    /**
     * 通过 PID 杀掉进程树。
     */
    private static void killProcessTreeByPid(long pid) {
        if (!isWindows()) {
            ProcessHandle.of(pid).ifPresent(h -> {
                h.descendants().forEach(ProcessHandle::destroyForcibly);
                h.destroyForcibly();
            });
            return;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "taskkill", "/T", "/F", "/PID", String.valueOf(pid)
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // 读取输出避免阻塞
            p.getInputStream().readAllBytes();
            int code = p.waitFor();
            ModLogger.info("{} taskkill /T /F /PID {} → exitCode={}", LOG_TAG, pid, code);
        } catch (Throwable t) {
            ModLogger.warn("{} taskkill PID={} 失败: {}，回退 destroyForcibly",
                    LOG_TAG, pid, t.getMessage());
            ProcessHandle.of(pid).ifPresent(h -> {
                h.descendants().forEach(ProcessHandle::destroyForcibly);
                h.destroyForcibly();
            });
        }
    }

    /**
     * 检查进程是否存活。
     */
    private static boolean isProcessAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    /**
     * 验证指定 PID 的进程映像名是否为 dotnet.exe。
     * <p>使用 {@code tasklist} 查询，防止 PID 被复用后误杀无关进程。</p>
     */
    private static boolean isDotnetProcess(long pid) {
        if (!isWindows()) {
            return true; // 非 Windows 不验证
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "tasklist", "/FI", "PID eq " + pid, "/FO", "CSV", "/NH"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            // 输出格式："dotnet.exe","12345","Console","1","12,345 K"
            String lower = output.toLowerCase();
            return lower.contains("dotnet");
        } catch (Throwable t) {
            // 无法验证时保守处理：不杀
            ModLogger.warn("{} 无法验证 PID={} 进程名: {}", LOG_TAG, pid, t.getMessage());
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // ====== PID 文件 I/O ======

    private static Path resolvePidFile() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir().resolve("heartrate");
            Files.createDirectories(configDir);
            return configDir.resolve(PID_FILE_NAME);
        } catch (Throwable t) {
            ModLogger.warn("{} 获取配置目录失败，降级使用 user.dir", LOG_TAG);
            String mcDir = System.getProperty("user.dir", ".");
            return Paths.get(mcDir, "config", "heartrate", PID_FILE_NAME);
        }
    }

    private static void appendPidToFile(long pid) {
        if (pidFilePath == null) {
            return;
        }
        try {
            String line = pid + "\n";
            Files.writeString(pidFilePath, line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            ModLogger.warn("{} 追加 PID 到文件失败: {}", LOG_TAG, e.getMessage());
        }
    }

    private static void deletePidFile() {
        if (pidFilePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(pidFilePath);
        } catch (IOException e) {
            ModLogger.warn("{} 删除 PID 文件失败: {}", LOG_TAG, e.getMessage());
        }
    }
}
