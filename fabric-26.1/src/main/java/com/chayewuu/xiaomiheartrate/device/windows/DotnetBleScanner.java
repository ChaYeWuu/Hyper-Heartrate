package com.chayewuu.xiaomiheartrate.device.windows;

import com.chayewuu.xiaomiheartrate.device.BleDevice;
import com.chayewuu.xiaomiheartrate.device.BleScanner;
import com.chayewuu.xiaomiheartrate.device.DeviceFilter;
import com.chayewuu.xiaomiheartrate.device.DeviceType;
import com.chayewuu.xiaomiheartrate.device.ScanCallback;
import com.chayewuu.xiaomiheartrate.util.ModLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 .NET ble-tool 的 BLE 扫描器。
 * <p>
 * 通过启动 {@code dotnet ble-tool.dll scan} 进程，调用 WinRT
 * {@code BluetoothLEAdvertisementWatcher} 扫描 BLE 广播设备。该方案可发现
 * <b>未配对</b>的 BLE 设备（如开启了"心率广播"功能的小米手环/Redmi Watch）。
 * </p>
 *
 * <p><b>输出协议：</b>ble-tool 进程 stdout 每行格式为 {@code MAC|Name|RSSI}，
 * 扫描开始/结束通过 stderr 输出 {@code SCAN_START} / {@code SCAN_END}。</p>
 *
 * <p><b>依赖：</b>系统需安装 .NET 10 运行时。Mod 资源目录内置 {@code ble-tool.dll}，
 * 首次使用时解压到 {@code config/heartrate/ble-tool.dll}。</p>
 *
 * <p>参考实现：https://github.com/Tnze/miband-heart-rate</p>
 */
public class DotnetBleScanner implements BleScanner {
    /** 日志前缀 */
    private static final String LOG_TAG = "[DotnetBleScanner]";

    /** 单次扫描持续时间（秒） */
    private static final int SCAN_DURATION_SECONDS = 30;

    /** 扫描后台线程池 */
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeartRateMod-BLE-Scanner");
        t.setDaemon(true);
        return t;
    });

    /** stderr 读取线程池 */
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeartRateMod-BLE-Scanner-Err");
        t.setDaemon(true);
        return t;
    });

    /** 扫描进行中标志 */
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    /** 当前扫描回调 */
    private final AtomicReference<ScanCallback> currentCallback = new AtomicReference<>();

    /** 当前 ble-tool 进程 */
    private final AtomicReference<Process> currentProcess = new AtomicReference<>();

    /** 已上报设备 MAC 集合（去重） */
    private final Set<String> reportedMacs = ConcurrentHashMap.newKeySet();

    /** 最近一次扫描错误信息（供 GUI 显示） */
    private volatile String lastError;

    /**
     * 获取最近一次扫描错误信息。
     *
     * @return 错误信息，无错误返回 {@code null}
     */
    public String getLastError() {
        return lastError;
    }

    @Override
    public void startScan(ScanCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("扫描回调不能为 null");
        }
        if (!scanning.compareAndSet(false, true)) {
            ModLogger.warn("{} 扫描已在进行中，将替换回调", LOG_TAG);
        }
        reportedMacs.clear();
        currentCallback.set(callback);
        scanExecutor.submit(this::runScan);
        ModLogger.info("{} BLE 扫描已启动（后台线程）", LOG_TAG);
    }

    @Override
    public void stopScan() {
        if (scanning.compareAndSet(true, false)) {
            ModLogger.info("{} 停止 BLE 扫描", LOG_TAG);
        }
        currentCallback.set(null);
        Process proc = currentProcess.getAndSet(null);
        if (proc != null) {
            try {
                BleProcessManager.killProcessTree(proc);
            } catch (Throwable ignored) {
                // 忽略
            }
        }
    }

    @Override
    public boolean isScanning() {
        return scanning.get();
    }

    /**
     * 启动 ble-tool 进程执行扫描，并读取输出。
     */
    private void runScan() {
        Process process = null;
        lastError = null;
        try {
            // 1. 解析 dotnet 完整路径（Minecraft PATH 可能不包含 dotnet）
            String dotnetPath = DotnetResolver.resolveDotnetPath();
            if (dotnetPath == null) {
                lastError = "未找到 dotnet，请安装 .NET 10 运行时";
                ModLogger.error("{} {}", LOG_TAG, lastError);
                scanning.set(false);
                return;
            }

            // 2. 解析 ble-tool.dll 路径
            Path dllPath = BleToolResolver.resolveDllPath();
            if (dllPath == null) {
                lastError = "无法定位 ble-tool.dll";
                ModLogger.error("{} {}", LOG_TAG, lastError);
                scanning.set(false);
                return;
            }

            ModLogger.info("{} 启动 ble-tool 扫描: {} {} scan {}",
                    LOG_TAG, dotnetPath, dllPath, SCAN_DURATION_SECONDS);

            ProcessBuilder pb = new ProcessBuilder(
                    dotnetPath, dllPath.toString(), "scan", String.valueOf(SCAN_DURATION_SECONDS)
            );
            // 设置工作目录为 DLL 所在目录，确保 dotnet 能加载依赖 DLL
            pb.directory(dllPath.getParent().toFile());
            // 合并 stderr 到 stdout，防止错误信息丢失
            pb.redirectErrorStream(true);
            // 重定向 stdin（避免 ble-tool 收到 EOF 立即退出）
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
            process = pb.start();
            currentProcess.set(process);
            BleProcessManager.register(process);

            readOutput(process);

            int exitCode = process.waitFor();
            ModLogger.info("{} ble-tool 进程退出，exitCode={}", LOG_TAG, exitCode);
            BleProcessManager.unregister(process);
            if (exitCode != 0 && reportedMacs.isEmpty()) {
                lastError = "ble-tool 退出码 " + exitCode + "（未发现设备）";
            }
        } catch (Throwable t) {
            lastError = "扫描启动失败: " + t.getMessage();
            ModLogger.error("{} BLE 扫描异常", t, LOG_TAG);
        } finally {
            scanning.set(false);
            currentProcess.set(null);
            if (process != null) {
                try {
                    BleProcessManager.killProcessTree(process);
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
            ModLogger.info("{} BLE 扫描循环已退出", LOG_TAG);
        }
    }

    /**
     * 读取进程合并输出（stdout + stderr），解析设备数据。
     */
    private void readOutput(Process process) {
        try (java.io.InputStream is = process.getInputStream();
             java.io.BufferedReader reader = new java.io.BufferedReader(
                     new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ModLogger.debug("{} ble-tool: {}", LOG_TAG, line);
                // stderr 的状态信息也混在一起，统一解析
                parseAndReportDevice(line);
            }
        } catch (java.io.IOException e) {
            if (scanning.get()) {
                ModLogger.error("{} 读取 ble-tool 输出异常", e, LOG_TAG);
            }
        }
    }

    /**
     * 解析一行输出并上报设备。
     * <p>设备格式：{@code MAC|Name|RSSI}，例如 {@code AA:BB:CC:DD:EE:FF|Mi Band 10|-60}</p>
     * <p>状态行：{@code SCAN_START}、{@code SCAN_END}、{@code ERROR|msg}，需过滤</p>
     */
    private void parseAndReportDevice(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        // 过滤状态信息行
        if (line.equals("SCAN_START") || line.equals("SCAN_END")) {
            ModLogger.info("{} ble-tool 状态: {}", LOG_TAG, line);
            return;
        }
        if (line.startsWith("ERROR|")) {
            lastError = line.substring("ERROR|".length());
            ModLogger.error("{} ble-tool 错误: {}", LOG_TAG, lastError);
            return;
        }
        // UPDATE_NAME 行：GAP 名称查询结果，更新已有设备名称并重新上报
        if (line.startsWith("UPDATE_NAME|")) {
            handleNameUpdate(line.substring("UPDATE_NAME|".length()));
            return;
        }
        // MAC 格式校验：必须以两位十六进制开头，含冒号或直接12位hex
        String trimmed = line.trim();
        if (trimmed.length() < 12) {
            // 太短不可能是 MAC|Name|RSSI，跳过
            return;
        }

        String[] parts = trimmed.split("\\|", 4);
        if (parts.length < 1) {
            return;
        }
        String mac = parts[0].trim();
        // 简单 MAC 格式校验：包含冒号且长度为17，或纯hex长度12
        boolean macValid = (mac.contains(":") && mac.length() == 17)
                || (mac.matches("[0-9A-Fa-f]{12}"));
        if (!macValid) {
            return;
        }

        String name = parts.length > 1 ? parts[1].trim() : "";
        int rssi = 0;
        if (parts.length > 2) {
            try {
                rssi = Integer.parseInt(parts[2].trim());
            } catch (NumberFormatException ignored) {
                // 使用默认值
            }
        }

        // 第4字段为逗号分隔的短 UUID 列表（如 "180d,180a"），用于识别心率设备
        String serviceUuids = parts.length > 3 ? parts[3].trim() : "";

        if (!reportedMacs.add(mac)) {
            return;
        }

        DeviceType type = DeviceFilter.getDeviceType(name);
        BleDevice device = new WindowsBleDevice(name, mac, rssi, null, type);

        ModLogger.info("{} 发现设备: {} ({}) RSSI={} type={} uuids={}",
                LOG_TAG, name.isEmpty() ? "Unknown" : name, mac, rssi, type, serviceUuids);

        ScanCallback cb = currentCallback.get();
        if (cb != null && scanning.get()) {
            try {
                cb.onDeviceFound(device);
            } catch (Throwable t) {
                ModLogger.error("{} 扫描回调异常", t, LOG_TAG);
            }
        }
    }

    /**
     * 处理 ble-tool 的 GAP 名称查询结果。
     * <p>格式：{@code MAC|Name}，例如 {@code AA:BB:CC:DD:EE:FF|xinlvguangbo-Iphone}</p>
     * <p>移除旧设备后重新上报（此时名称正确，DeviceType 可能从 UNKNOWN 变为可识别类型）。</p>
     */
    private void handleNameUpdate(String payload) {
        String[] parts = payload.split("\\|", 2);
        if (parts.length < 2) return;
        String mac = parts[0].trim();
        String name = parts[1].trim();
        if (mac.isEmpty() || name.isEmpty()) return;

        // 允许该 MAC 重新上报（移除去重记录）
        reportedMacs.remove(mac);

        DeviceType type = DeviceFilter.getDeviceType(name);
        BleDevice device = new WindowsBleDevice(name, mac, 0, null, type);

        ModLogger.info("{} 名称已更新: {} → {} type={}", LOG_TAG, mac, name, type);

        ScanCallback cb = currentCallback.get();
        if (cb != null && scanning.get()) {
            try {
                cb.onDeviceFound(device);
            } catch (Throwable t) {
                ModLogger.error("{} 名称更新回调异常", t, LOG_TAG);
            }
        }
    }

    /**
     * 关闭扫描器，释放资源。
     */
    public void shutdown() {
        stopScan();
        scanExecutor.shutdownNow();
        readerExecutor.shutdownNow();
        ModLogger.info("{} 扫描器已关闭", LOG_TAG);
    }
}
