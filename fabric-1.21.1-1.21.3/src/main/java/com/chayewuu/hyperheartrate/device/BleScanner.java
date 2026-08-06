package com.chayewuu.hyperheartrate.device;

/**
 * BLE 扫描器接口。
 * <p>
 * 封装平台 BLE 扫描能力。实现类应保证扫描在后台线程执行，
 * 避免阻塞游戏主线程（渲染线程）。
 * </p>
 *
 * <p>使用流程：</p>
 * <ol>
 *     <li>{@link #startScan(ScanCallback)} 启动扫描；</li>
 *     <li>扫描结果通过 {@link ScanCallback} 回调上报；</li>
 *     <li>找到目标设备后调用 {@link #stopScan()} 停止扫描。</li>
 * </ol>
 */
public interface BleScanner {
    /**
     * 启动后台扫描。
     * <p>调用后扫描在后台线程进行，结果通过 {@code callback} 异步回调。</p>
     *
     * @param callback 扫描结果回调
     */
    void startScan(ScanCallback callback);

    /**
     * 停止扫描。
     * <p>若当前未扫描则该方法为空操作。</p>
     */
    void stopScan();

    /**
     * 查询是否正在扫描。
     *
     * @return {@code true} 表示扫描进行中
     */
    boolean isScanning();
}
