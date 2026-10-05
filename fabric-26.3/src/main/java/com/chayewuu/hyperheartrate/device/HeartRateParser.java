package com.chayewuu.hyperheartrate.device;

/**
 * 心率数据解析器接口。
 * <p>
 * 负责将设备通过特征值通知上报的原始字节数据解析为心率数值。
 * 不同厂商、不同协议（标准 GATT 0x2A37 vs 小米私有协议）的解析逻辑不同，
 * 通过实现该接口提供多种解析器。
 * </p>
 *
 * <p>典型实现：</p>
 * <ul>
 *     <li>{@code StandardHeartRateParser} — 解析标准 GATT 心率服务 0x2A37；</li>
 *     <li>{@code XiaomiHeartRateParser} — 解析小米手环/手表私有协议。</li>
 * </ul>
 */
public interface HeartRateParser {
    /**
     * 解析心率值。
     *
     * @param data 特征值通知收到的原始字节数据
     * @return 解析得到的心率值（BPM），数据无效时返回 {@code 0} 或负数
     */
    int parseHeartRate(byte[] data);

    /**
     * 判断当前解析器是否能处理指定设备的数据。
     * <p>上层据此为扫描到的设备匹配合适的解析器。</p>
     *
     * @param device 待判定设备
     * @return {@code true} 表示该解析器可解析此设备的心率数据
     */
    boolean canParse(BleDevice device);
}
