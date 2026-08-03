package com.chayewuu.xiaomiheartrate.device.windows;

import com.chayewuu.xiaomiheartrate.HeartRateMod;
import com.chayewuu.xiaomiheartrate.util.ModLogger;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * ble-tool 相关资源文件解析器。
 * <p>
 * Mod 内置 ble-tool 所需的全部文件（.NET 框架依赖部署 FDD 模式）：
 * </p>
 * <ul>
 *   <li>{@code ble-tool.dll} - 主程序</li>
 *   <li>{@code ble-tool.runtimeconfig.json} - 运行时配置</li>
 *   <li>{@code ble-tool.deps.json} - 依赖描述</li>
 *   <li>{@code Microsoft.Windows.SDK.NET.dll} - Windows SDK .NET 互操作库</li>
 *   <li>{@code WinRT.Runtime.dll} - WinRT 运行时</li>
 * </ul>
 * <p>
 * 首次使用时解压到 {@code config/heartrate/}，后续直接复用。
 * </p>
 *
 * <p>ble-tool 是 .NET 10 控制台程序，通过 WinRT API 实现 BLE 扫描与连接，
 * 参考 https://github.com/Tnze/miband-heart-rate 的实现思路。</p>
 */
public final class BleToolResolver {
    /** 日志前缀 */
    private static final String LOG_TAG = "[BleToolResolver]";

    /** 解压目标目录名 */
    private static final String CONFIG_DIR_NAME = "heartrate";

    /** 需要解压的资源文件列表 */
    private static final String[] RESOURCE_FILES = {
            "ble-tool.dll",
            "ble-tool.runtimeconfig.json",
            "ble-tool.deps.json",
            "Microsoft.Windows.SDK.NET.dll",
            "WinRT.Runtime.dll"
    };

    /** 缓存的 DLL 路径（首次解析后缓存） */
    private static volatile Path cachedPath;

    private BleToolResolver() {
        // 工具类
    }

    /**
     * 解析 ble-tool.dll 路径。
     * <p>首次调用时从 Mod 资源解压全部依赖文件到 {@code config/heartrate/}，
     * 后续直接返回缓存路径。</p>
     *
     * @return DLL 路径，失败返回 {@code null}
     */
    public static Path resolveDllPath() {
        if (cachedPath != null && Files.exists(cachedPath)) {
            return cachedPath;
        }

        try {
            Path configDir = resolveConfigDir();
            Path dllPath = configDir.resolve("ble-tool.dll");

            Files.createDirectories(configDir);
            // 逐个检查资源与本地文件大小是否一致，不一致才更新
            // （避免文件被占用时 AccessDenied；旧版本文件存在但被锁也能继续用）
            for (String fileName : RESOURCE_FILES) {
                Path target = configDir.resolve(fileName);
                String resourcePath = "/" + fileName;
                try (InputStream is = HeartRateMod.class.getResourceAsStream(resourcePath)) {
                    if (is == null) {
                        ModLogger.error("{} 资源 {} 不存在", LOG_TAG, resourcePath);
                        return null;
                    }
                    long resSize = is.available();
                    boolean needUpdate = true;
                    if (Files.exists(target)) {
                        try {
                            long fileSize = Files.size(target);
                            if (fileSize == resSize) {
                                needUpdate = false;
                            }
                        } catch (IOException ignored) {
                            // 读取失败则更新
                        }
                    }
                    if (needUpdate) {
                        // 重新获取流（上面的 available 调用可能已消耗部分数据）
                        try (InputStream is2 = HeartRateMod.class.getResourceAsStream(resourcePath)) {
                            Files.copy(is2, target, StandardCopyOption.REPLACE_EXISTING);
                            ModLogger.info("{} 已解压 {} 到: {}", LOG_TAG, fileName, target);
                        } catch (IOException ex) {
                            if (Files.exists(target)) {
                                // 文件被占用无法更新，但旧文件可用
                                ModLogger.warn("{} {} 被占用，使用旧版本", LOG_TAG, fileName);
                            } else {
                                ModLogger.error("{} 解压 {} 失败", ex, LOG_TAG, fileName);
                                return null;
                            }
                        }
                    } else {
                        ModLogger.debug("{} {} 已是最新，跳过更新", LOG_TAG, fileName);
                    }
                }
            }

            cachedPath = dllPath;
            return dllPath;
        } catch (Throwable t) {
            ModLogger.error("{} 解析 ble-tool 路径失败", t, LOG_TAG);
            return null;
        }
    }

    /**
     * 获取 Mod 配置目录（{@code .minecraft/config/heartrate/}）。
     * <p>使用 FabricLoader 获取正确的配置目录，避免依赖 user.dir。</p>
     *
     * @return 配置目录路径
     */
    private static Path resolveConfigDir() {
        try {
            // Fabric 推荐方式：FabricLoader.getConfigDir() 返回 .minecraft/config
            return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DIR_NAME);
        } catch (Throwable t) {
            // 降级方案：使用 user.dir
            ModLogger.warn("{} FabricLoader 获取配置目录失败，降级使用 user.dir", LOG_TAG);
            String mcDir = System.getProperty("user.dir", ".");
            return Paths.get(mcDir, "config", CONFIG_DIR_NAME);
        }
    }
}
