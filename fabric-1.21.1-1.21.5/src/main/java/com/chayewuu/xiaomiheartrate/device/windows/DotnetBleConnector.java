package com.chayewuu.xiaomiheartrate.device.windows;

import com.chayewuu.xiaomiheartrate.device.BleCharacteristic;
import com.chayewuu.xiaomiheartrate.device.BleConnector;
import com.chayewuu.xiaomiheartrate.device.BleDevice;
import com.chayewuu.xiaomiheartrate.device.ConnectionCallback;
import com.chayewuu.xiaomiheartrate.device.NotificationCallback;
import com.chayewuu.xiaomiheartrate.util.ModLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 .NET ble-tool 的 BLE 连接器。
 * <p>
 * 通过启动 {@code dotnet ble-tool.dll connect <mac>} 进程，调用 WinRT
 * {@code BluetoothLEDevice.FromBluetoothAddressAsync} 连接设备（支持未配对设备），
 * 获取心率服务（0x180D）与心率测量特征值（0x2A37），订阅通知并持续读取心率数据。
 * </p>
 *
 * <p><b>输出协议：</b>ble-tool 进程通过 stdout 输出心率数据，每行格式：
 * <ul>
 *   <li>{@code HEART|<value>}：心率值更新</li>
 *   <li>{@code READY|...}：连接成功，等待心率数据</li>
 * </ul>
 * stderr 输出状态信息：
 * <ul>
 *   <li>{@code CONNECTING|<mac>}：正在连接</li>
 *   <li>{@code CONNECTED|<name>}：已连接</li>
 *   <li>{@code NOTIFY_OK}：通知订阅成功</li>
 *   <li>{@code ERROR|<msg>}：错误</li>
 * </ul>
 * </p>
 *
 * <p>停止连接时，向 ble-tool 进程的 stdin 写入 {@code STOP}，进程会清理订阅后退出。
 * 或直接销毁进程。</p>
 *
 * <p>参考实现：https://github.com/Tnze/miband-heart-rate</p>
 */
public class DotnetBleConnector implements BleConnector {
    /** 日志前缀 */
    private static final String LOG_TAG = "[DotnetBleConnector]";

    /** 心率测量特征值 UUID 后缀（标准 GATT Heart Rate Service） */
    private static final String HEART_RATE_CHAR_UUID_SUFFIX = "2A37";

    /** 连接后台线程池 */
    private final ExecutorService connectExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeartRateMod-BLE-Connector");
        t.setDaemon(true);
        return t;
    });

    /** 已连接标志 */
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /** 关闭模式标志：shutdown() 时设为 true，disconnectInternal 不再等待 STOP 优雅退出 */
    private volatile boolean shutdownMode = false;

    /** 当前 ble-tool 进程 */
    private volatile Process currentProcess;

    /** 当前设备 */
    private volatile BleDevice currentDevice;

    /** 当前连接回调 */
    private volatile ConnectionCallback currentCallback;

    /** 心率通知回调（由上层 DeviceManagerImpl 设置） */
    private volatile NotificationCallback heartRateNotificationCallback;

    @Override
    public void connect(BleDevice device, ConnectionCallback callback) {
        if (device == null) {
            throw new IllegalArgumentException("目标设备不能为 null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("连接回调不能为 null");
        }
        // 若已连接，先断开
        if (connected.get()) {
            disconnectInternal(false);
        }
        currentDevice = device;
        currentCallback = callback;
        connectExecutor.submit(() -> doConnect(device, callback));
    }

    /**
     * 实际连接逻辑（后台线程执行）。
     */
    private void doConnect(BleDevice device, ConnectionCallback callback) {
        Process process = null;
        try {
            // 1. 解析 dotnet 完整路径
            String dotnetPath = DotnetResolver.resolveDotnetPath();
            if (dotnetPath == null) {
                callback.onError("未找到 dotnet，请安装 .NET 10 运行时", null);
                return;
            }

            Path dllPath = BleToolResolver.resolveDllPath();
            if (dllPath == null) {
                callback.onError("无法定位 ble-tool.dll", null);
                return;
            }

            String mac = normalizeMac(device.getAddress());
            if (mac.isEmpty()) {
                callback.onError("MAC 地址无效: " + device.getAddress(), null);
                return;
            }

            ModLogger.info("{} 启动 ble-tool 连接: {} {} connect {} ({})",
                    LOG_TAG, dotnetPath, dllPath, device.getName(), mac);

            ProcessBuilder pb = new ProcessBuilder(
                    dotnetPath, dllPath.toString(), "connect", mac
            );
            // 设置工作目录为 DLL 所在目录
            pb.directory(dllPath.getParent().toFile());
            // 合并 stderr 到 stdout
            pb.redirectErrorStream(true);
            // 重定向 stdin，避免进程收到 EOF 退出
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
            process = pb.start();
            currentProcess = process;
            BleProcessManager.register(process);

            // 主线程读取合并输出（stdout + stderr）。
            // readOutput 在收到 READY 时会立即触发 onConnected 回调（设置通知回调等），
            // 随后持续读取 HEART| 数据，直到 ble-tool 进程退出（EOF）。
            // 注意：readOutput 收到 ERROR 时不立即触发 onError，而是先收集错误，
            // 等进程退出（确保 ble-tool 执行完 finally 清理 GATT 会话）后再触发回调，
            // 避免设备端会话残留导致下次连接 AccessDenied。
            String pendingError = readOutput(process, callback);

            // 等待进程退出（ble-tool 的 finally 会执行 GATT 取消订阅和 Dispose）
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                ModLogger.warn("{} ble-tool 进程 5 秒内未退出，强制销毁", LOG_TAG);
                BleProcessManager.killProcessTree(process);
            }
            ModLogger.info("{} ble-tool 进程退出，exitCode={}", LOG_TAG, process.exitValue());
            BleProcessManager.unregister(process);

            // 进程退出后再触发错误/断开回调，此时设备端会话已清理完毕
            if (pendingError != null) {
                if (connected.getAndSet(false)) {
                    ModLogger.info("{} 设备因错误断开: {}", LOG_TAG, mac);
                }
                callback.onError(pendingError, null);
            } else if (connected.getAndSet(false)) {
                ModLogger.info("{} 设备已断开: {}", LOG_TAG, mac);
                callback.onDisconnected(device);
            }
        } catch (Throwable t) {
            ModLogger.error("{} 连接设备异常", t, LOG_TAG);
            callback.onError("连接设备异常: " + t.getMessage(), t);
        } finally {
            connected.set(false);
            if (process != null) {
                try {
                    BleProcessManager.killProcessTree(process);
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
            currentProcess = null;
        }
    }

    /**
     * 读取合并输出（stdout + stderr），解析心率数据和状态信息。
     * <p>收到 {@code READY|} 表示连接成功。持续读取 {@code HEART|<value>} 心率数据。
     * {@code ERROR|msg} 触发 onError 回调。</p>
     *
     * @param process  ble-tool 进程
     * @param callback 连接回调（仅在 READY 时触发 onConnected，ERROR 延迟到进程退出后触发）
     * @return 收到的 ERROR 消息（无错误返回 {@code null}）
     */
    private String readOutput(Process process, ConnectionCallback callback) {
        String errorMsg = null;
        try (InputStream is = process.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ModLogger.info("{} ble-tool: {}", LOG_TAG, line);
                if (line.startsWith("CONNECTED|")) {
                    // ble-tool 输出设备真实名称（如 REDMI Watch 6 27DB），
                    // 替换扫描时的短广播名（如 Xiaomi 27DB）
                    String realName = line.substring("CONNECTED|".length()).trim();
                    if (!realName.isEmpty() && currentDevice != null
                            && !realName.equals(currentDevice.getName())) {
                        BleDevice updated = new WindowsBleDevice(
                                realName, currentDevice.getAddress(), currentDevice.getRssi(),
                                currentDevice.getBatteryLevel(), currentDevice.getType());
                        currentDevice = updated;
                        ModLogger.info("{} 更新设备名为真实名称: {}", LOG_TAG, realName);
                    }
                } else if (line.startsWith("READY|")) {
                    // 收到 READY 表示连接已建立且通知订阅成功。
                    // 必须在此立即触发 onConnected 回调，使上层（DeviceManagerImpl）
                    // 设置 heartRateNotificationCallback，否则后续 HEART| 数据无法传递。
                    if (!connected.get()) {
                        connected.set(true);
                        ModLogger.info("{} 设备连接成功，触发 onConnected 回调", LOG_TAG);
                        callback.onConnected(currentDevice);
                    }
                } else if (line.startsWith("HEART|")) {
                    handleHeartRateLine(line);
                } else if (line.startsWith("ERROR|")) {
                    // 不立即触发 onError，先缓存，等进程退出（清理完成）后再触发
                    errorMsg = line.substring("ERROR|".length());
                }
            }
        } catch (IOException e) {
            if (connected.get()) {
                ModLogger.error("{} 读取 ble-tool 输出异常", e, LOG_TAG);
            }
        }
        return errorMsg;
    }

    /**
     * 处理心率数据行 {@code HEART|<value>}。
     *
     * @param line 心率数据行
     */
    private void handleHeartRateLine(String line) {
        try {
            int sep = line.indexOf('|');
            if (sep < 0) {
                return;
            }
            int heartRate = Integer.parseInt(line.substring(sep + 1).trim());
            if (heartRate <= 0) {
                return;
            }
            ModLogger.debug("{} 心率: {} BPM", LOG_TAG, heartRate);

            // 通过通知回调转发到 HeartRateNotificationHandler
            NotificationCallback cb = heartRateNotificationCallback;
            if (cb != null) {
                // 构造标准 GATT 心率测量数据包（flags + uint8 心率值）
                byte[] data = new byte[2];
                data[0] = 0x00; // flags: uint8 心率值
                data[1] = (byte) heartRate;
                cb.onNotification(data);
            }
        } catch (NumberFormatException e) {
            ModLogger.warn("{} 心率数据格式无效: {}", LOG_TAG, line);
        }
    }

    /**
     * 设置心率通知回调。
     * <p>由 {@link com.chayewuu.xiaomiheartrate.device.DeviceManagerImpl} 在连接成功后调用，
     * 传入 {@link com.chayewuu.xiaomiheartrate.device.HeartRateNotificationHandler}。</p>
     *
     * @param callback 通知回调
     */
    public void setHeartRateNotificationCallback(NotificationCallback callback) {
        this.heartRateNotificationCallback = callback;
    }

    @Override
    public void disconnect() {
        disconnectInternal(true);
    }

    /**
     * 内部断开连接实现。
     *
     * @param notifyCallback 是否回调通知上层
     */
    private void disconnectInternal(boolean notifyCallback) {
        boolean wasConnected = connected.getAndSet(false);
        BleDevice device = currentDevice;
        ConnectionCallback callback = currentCallback;

        ModLogger.info("{} 断开设备连接: {}",
                LOG_TAG, device == null ? "未知" : device.getAddress());

        // 向 ble-tool 进程 stdin 发送 STOP，让其优雅清理 GATT 订阅并 Dispose 设备
        // （避免设备端会话残留导致后续连接 AccessDenied）
        // shutdown 模式下直接 destroyForcibly 不等待，避免 JVM 关闭时卡住
        Process proc = currentProcess;
        if (proc != null) {
            if (!shutdownMode) {
                try {
                    proc.getOutputStream().write("STOP\n".getBytes(StandardCharsets.UTF_8));
                    proc.getOutputStream().flush();
                } catch (IOException ignored) {
                    // 忽略
                }
                // 等待进程优雅退出（最多 3 秒），让 ble-tool 执行清理逻辑
                try {
                    if (!proc.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                        // 超时仍未退出，杀掉进程树
                        BleProcessManager.killProcessTree(proc);
                    } else {
                        BleProcessManager.unregister(proc);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    try {
                        BleProcessManager.killProcessTree(proc);
                    } catch (Throwable ignored) {
                        // 忽略
                    }
                } catch (Throwable ignored) {
                    // 忽略
                }
            } else {
                // shutdown 模式：直接杀掉进程树，不等待
                try {
                    BleProcessManager.killProcessTree(proc);
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
        }
        currentProcess = null;

        if (notifyCallback && wasConnected && callback != null) {
            try {
                callback.onDisconnected(device);
            } catch (Throwable t) {
                ModLogger.error("{} 断开回调异常", t, LOG_TAG);
            }
        }

        currentDevice = null;
        currentCallback = null;
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public List<BleCharacteristic> discoverServices() {
        // ble-tool 内部完成服务发现，此处返回空列表
        return Collections.emptyList();
    }

    /**
     * 规范化 MAC 地址为大写冒号格式。
     *
     * @param mac 原始 MAC 地址
     * @return 规范化后的 MAC，无效返回空串
     */
    private static String normalizeMac(String mac) {
        if (mac == null) {
            return "";
        }
        String hex = mac.replace(":", "").replace("-", "").replace(" ", "").replace("_", "").replace("{", "").replace("}", "");
        if (hex.length() != 12) {
            return "";
        }
        // 转大写并加冒号
        hex = hex.toUpperCase();
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(hex.charAt(i));
            sb.append(hex.charAt(i + 1));
        }
        return sb.toString();
    }

    /**
     * 关闭连接器。
     * <p>设置 shutdown 模式，强制销毁所有 ble-tool 子进程，不等待优雅退出。</p>
     */
    public void shutdown() {
        shutdownMode = true;
        disconnect();
        connectExecutor.shutdownNow();
        ModLogger.info("{} 连接器已关闭", LOG_TAG);
    }
}
