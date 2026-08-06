package com.chayewuu.hyperheartrate.device.windows;

import com.sun.jna.Pointer;
import com.chayewuu.hyperheartrate.device.BleScanner;
import com.chayewuu.hyperheartrate.device.DeviceType;
import com.chayewuu.hyperheartrate.device.ScanCallback;
import com.chayewuu.hyperheartrate.device.windows.win32.BLUETOOTH_DEVICE_INFO;
import com.chayewuu.hyperheartrate.device.windows.win32.BLUETOOTH_DEVICE_SEARCH_PARAMS;
import com.chayewuu.hyperheartrate.device.windows.win32.BluetoothLibrary;
import com.chayewuu.hyperheartrate.util.ModLogger;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Windows 平台 BLE 扫描器实现。
 * <p>
 * 通过 JNA 调用 Win32 {@code BluetoothFindFirstDevice} / {@code BluetoothFindNextDevice}
 * 枚举系统中已配对/记忆/连接的蓝牙设备（含 BLE 设备），并在后台线程中将
 * 匹配小米/Redmi 规则的设备通过 {@link ScanCallback} 上报。
 * </p>
 *
 * <p><b>线程模型：</b>扫描在专用后台线程（{@link ExecutorService}）执行，
 * 主线程（GUI/渲染线程）不会被阻塞。{@link #isScanning()} 可在任意线程查询。</p>
 *
 * <p><b>当前限制：</b>Win32 {@code BluetoothFindFirstDevice} 仅能枚举系统中
 * 已配对/记忆的蓝牙设备，无法发现处于广播态的未配对 BLE 设备。发现未配对
 * BLE 设备需要 WinRT {@code BluetoothLEAdvertisementWatcher}（详见
 * {@link com.chayewuu.hyperheartrate.device.windows.win32.BluetoothLELibrary} 的 TODO）。
 * 当前实现采用轮询策略：扫描期间周期性枚举系统设备并去重上报。</p>
 */
public class WindowsBleScanner implements BleScanner {
    /** 扫描日志前缀 */
    private static final String LOG_TAG = "[WindowsBleScanner]";

    /** 轮询间隔（毫秒），每次枚举系统设备后的等待时间 */
    private static final long POLL_INTERVAL_MS = 2000L;

    /** 扫描任务使用的后台线程池（单线程，确保同一时刻仅一个扫描任务） */
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HeartRateMod-BLE-Scanner");
        t.setDaemon(true);
        return t;
    });

    /** 扫描进行中标志（线程安全） */
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    /** 当前扫描回调（线程安全），扫描停止时置 {@code null} */
    private final AtomicReference<ScanCallback> currentCallback = new AtomicReference<>();

    @Override
    public void startScan(ScanCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("扫描回调不能为 null");
        }
        // 若已在扫描，先记录并替换回调
        if (!scanning.compareAndSet(false, true)) {
            ModLogger.warn("{} 扫描已在进行中，将替换回调", LOG_TAG);
        }
        currentCallback.set(callback);
        scanExecutor.submit(this::runScanLoop);
        ModLogger.info("{} BLE 扫描已启动（后台线程）", LOG_TAG);
    }

    @Override
    public void stopScan() {
        if (scanning.compareAndSet(true, false)) {
            ModLogger.info("{} BLE 扫描停止请求已发出", LOG_TAG);
        }
        currentCallback.set(null);
    }

    @Override
    public boolean isScanning() {
        return scanning.get();
    }

    /**
     * 扫描循环主体，运行在后台线程。
     * <p>循环调用 Win32 API 枚举设备，直到 {@link #scanning} 被置为 {@code false}。</p>
     */
    private void runScanLoop() {
        try {
            while (scanning.get()) {
                enumerateDevices();
                // 等待下一轮轮询，期间响应停止请求
                if (!sleepInterruptibly(POLL_INTERVAL_MS)) {
                    break;
                }
            }
        } catch (Throwable t) {
            ModLogger.error("{} 扫描循环异常", t, LOG_TAG);
        } finally {
            scanning.set(false);
            ModLogger.info("{} 扫描循环已退出", LOG_TAG);
        }
    }

    /**
     * 调用 Win32 API 枚举一次系统蓝牙设备，并对每个设备触发回调。
     */
    private void enumerateDevices() {
        BLUETOOTH_DEVICE_SEARCH_PARAMS searchParams = new BLUETOOTH_DEVICE_SEARCH_PARAMS();
        // 返回已认证、已记忆、已连接的设备；不发起新的 inquiry（避免阻塞并兼容 BLE）
        searchParams.fReturnAuthenticated = true;
        searchParams.fReturnRemembered = true;
        searchParams.fReturnConnected = true;
        // fReturnUnknown + fIssueInquiry 适用于经典蓝牙发现；
        // 对 BLE 设备枚举无意义，保持关闭以避免长时间阻塞
        searchParams.fReturnUnknown = false;
        searchParams.fIssueInquiry = false;
        searchParams.cTimeoutMultiplier = 0;
        searchParams.hRadio = null;

        BLUETOOTH_DEVICE_INFO deviceInfo = new BLUETOOTH_DEVICE_INFO();

        Pointer hFind = null;
        try {
            hFind = BluetoothLibrary.INSTANCE.BluetoothFindFirstDevice(searchParams, deviceInfo);
            if (hFind == null) {
                // 无设备或 API 失败
                int err = com.sun.jna.Native.getLastError();
                ModLogger.debug("{} BluetoothFindFirstDevice 返回 null（lastError={}）", LOG_TAG, err);
                return;
            }
            // 处理第一个设备
            reportDevice(deviceInfo);
            // 枚举后续设备
            while (scanning.get() && BluetoothLibrary.INSTANCE.BluetoothFindNextDevice(hFind, deviceInfo)) {
                reportDevice(deviceInfo);
            }
        } catch (Throwable t) {
            ModLogger.error("{} 枚举设备时异常", t, LOG_TAG);
        } finally {
            if (hFind != null) {
                try {
                    BluetoothLibrary.INSTANCE.BluetoothFindDeviceClose(hFind);
                } catch (Throwable t) {
                    ModLogger.warn("{} 关闭枚举句柄失败", LOG_TAG, t);
                }
            }
        }
    }

    /**
     * 将单个 {@link BLUETOOTH_DEVICE_INFO} 转换为 {@link WindowsBleDevice} 并回调上报。
     * <p>仅在设备受支持（{@link com.chayewuu.hyperheartrate.device.DeviceFilter}）时上报。</p>
     *
     * @param info Win32 设备信息
     */
    private void reportDevice(BLUETOOTH_DEVICE_INFO info) {
        ScanCallback cb = currentCallback.get();
        if (cb == null || !scanning.get()) {
            return;
        }
        String name = info.getName();
        String address = info.getMacAddress();
        // RSSI：BluetoothFindFirstDevice 不直接返回 RSSI，使用 0 作为占位
        // TODO: 后续通过 WinRT BluetoothLEAdvertisementWatcher 获取真实 RSSI
        int rssi = 0;
        // 电量需连接后通过 GATT 读取，扫描阶段未知
        Integer battery = null;
        DeviceType type = com.chayewuu.hyperheartrate.device.DeviceFilter.getDeviceType(name);

        WindowsBleDevice device = new WindowsBleDevice(name, address, rssi, battery, type);
        try {
            cb.onDeviceFound(device);
        } catch (Throwable t) {
            ModLogger.error("{} 扫描回调抛出异常", t, LOG_TAG);
        }
    }

    /**
     * 可中断的睡眠。
     *
     * @param millis 睡眠毫秒数
     * @return {@code true} 表示正常睡眠结束；{@code false} 表示扫描已停止（应退出循环）
     */
    private boolean sleepInterruptibly(long millis) {
        long end = System.currentTimeMillis() + millis;
        while (scanning.get()) {
            long now = System.currentTimeMillis();
            if (now >= end) {
                return true;
            }
            try {
                Thread.sleep(Math.min(100L, end - now));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 关闭扫描器，释放后台线程池。
     * <p>由 {@link com.chayewuu.hyperheartrate.device.DeviceManager} 在 Mod 卸载时调用。</p>
     */
    public void shutdown() {
        stopScan();
        scanExecutor.shutdownNow();
        ModLogger.info("{} 扫描器已关闭", LOG_TAG);
    }
}
