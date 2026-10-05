package com.chayewuu.hyperheartrate.device;

/**
 * BLE 平台适配器接口（工厂模式）。
 * <p>
 * 用于屏蔽不同平台（Windows / macOS / Linux）与不同 BLE 后端
 * （系统 API / JNA / 第三方库）的差异，向上层提供统一的扫描器与连接器创建入口。
 * </p>
 *
 * <p>不同平台对应不同实现类，由上层在运行时选取合适的实现。</p>
 */
public interface BleAdapter {
    /**
     * 创建一个新的扫描器实例。
     *
     * @return BLE 扫描器
     */
    BleScanner createScanner();

    /**
     * 创建一个新的连接器实例。
     *
     * @return BLE 连接器
     */
    BleConnector createConnector();

    /**
     * 查询当前平台是否支持 BLE。
     *
     * @return {@code true} 表示当前平台具备 BLE 能力可供使用
     */
    boolean isSupported();
}
