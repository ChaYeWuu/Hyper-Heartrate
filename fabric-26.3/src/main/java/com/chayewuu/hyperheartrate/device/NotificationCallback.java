package com.chayewuu.hyperheartrate.device;

/**
 * BLE 特征值通知回调。
 * <p>
 * 当启用通知的特征值收到设备下发的数据时，由 {@link BleCharacteristic#enableNotifications(NotificationCallback)}
 * 注册的回调被触发，参数为收到的原始字节数据。
 * </p>
 *
 * <p>该接口为函数式接口，可使用 Lambda 表达式实现。</p>
 */
@FunctionalInterface
public interface NotificationCallback {
    /**
     * 收到特征值通知时调用。
     *
     * @param data 设备下发的原始字节数据，不为 {@code null}
     */
    void onNotification(byte[] data);
}
