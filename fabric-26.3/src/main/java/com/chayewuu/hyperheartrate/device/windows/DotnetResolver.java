package com.chayewuu.hyperheartrate.device.windows;

import com.chayewuu.hyperheartrate.util.ModLogger;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * dotnet 可执行文件路径解析器。
 * <p>
 * Minecraft 启动时的 PATH 环境变量可能不包含 dotnet 安装目录，
 * 直接调用 {@code "dotnet"} 会失败。本类按以下顺序查找 dotnet：
 * </p>
 * <ol>
 *     <li>PATH 环境变量中的 dotnet / dotnet.exe</li>
 *     <li>常见安装位置：{@code C:\Program Files\dotnet\dotnet.exe}</li>
 *     <li>x86 安装位置：{@code C:\Program Files (x86)\dotnet\dotnet.exe}</li>
 *     <li>用户级安装位置：{@code %LOCALAPPDATA%\Microsoft\dotnet\dotnet.exe}</li>
 * </ol>
 */
public final class DotnetResolver {
    /** 日志前缀 */
    private static final String LOG_TAG = "[DotnetResolver]";

    /** 常见 dotnet 安装位置 */
    private static final String[] COMMON_LOCATIONS = {
            "C:\\Program Files\\dotnet\\dotnet.exe",
            "C:\\Program Files (x86)\\dotnet\\dotnet.exe"
    };

    /** 缓存的 dotnet 路径 */
    private static volatile String cachedPath;

    private DotnetResolver() {
        // 工具类
    }

    /**
     * 解析 dotnet 可执行文件完整路径。
     *
     * @return dotnet 完整路径，找不到返回 {@code null}
     */
    public static String resolveDotnetPath() {
        if (cachedPath != null) {
            return cachedPath;
        }

        // 1. 尝试直接用 "dotnet" 或 "dotnet.exe"（依赖 PATH）
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] dirs = pathEnv.split(File.pathSeparator);
            for (String dir : dirs) {
                if (dir == null || dir.isEmpty()) {
                    continue;
                }
                File exe = new File(dir, "dotnet.exe");
                if (exe.exists() && exe.canExecute()) {
                    cachedPath = exe.getAbsolutePath();
                    ModLogger.info("{} 在 PATH 中找到 dotnet: {}", LOG_TAG, cachedPath);
                    return cachedPath;
                }
                exe = new File(dir, "dotnet");
                if (exe.exists() && exe.canExecute()) {
                    cachedPath = exe.getAbsolutePath();
                    ModLogger.info("{} 在 PATH 中找到 dotnet: {}", LOG_TAG, cachedPath);
                    return cachedPath;
                }
            }
        }

        // 2. 查找常见安装位置
        for (String location : COMMON_LOCATIONS) {
            File exe = new File(location);
            if (exe.exists() && exe.canExecute()) {
                cachedPath = exe.getAbsolutePath();
                ModLogger.info("{} 在常见位置找到 dotnet: {}", LOG_TAG, cachedPath);
                return cachedPath;
            }
        }

        // 3. 用户级安装位置
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isEmpty()) {
            File exe = new File(localAppData + "\\Microsoft\\dotnet\\dotnet.exe");
            if (exe.exists() && exe.canExecute()) {
                cachedPath = exe.getAbsolutePath();
                ModLogger.info("{} 在用户目录找到 dotnet: {}", LOG_TAG, cachedPath);
                return cachedPath;
            }
        }

        ModLogger.error("{} 未找到 dotnet 可执行文件，请确认已安装 .NET 10 运行时", LOG_TAG);
        return null;
    }
}
