using System;
using System.Runtime.InteropServices.WindowsRuntime;
using System.Threading;
using System.Threading.Tasks;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Devices.Bluetooth.GenericAttributeProfile;
using Windows.Devices.Enumeration;

namespace HyperHeartrate.BleTool;

/// <summary>
/// BLE 工具控制台程序。
/// <para>提供两个子命令：</para>
/// <list type="bullet">
///   <item><c>scan &lt;seconds&gt;</c>：扫描 BLE 广播设备，输出 MAC|Name|RSSI</item>
///   <item><c>connect &lt;mac&gt;</c>：连接设备并订阅心率通知，输出 HEART|value 或 ERROR|msg</item>
/// </list>
/// <para>参考：https://github.com/Tnze/miband-heart-rate</para>
/// </summary>
internal static class Program
{
    /// <summary>
    /// 心率服务 UUID：0x180D
    /// </summary>
    private static readonly Guid HeartRateServiceUuid = Guid.Parse("0000180D-0000-1000-8000-00805F9B34FB");

    /// <summary>
    /// 心率测量特征值 UUID：0x2A37
    /// </summary>
    private static readonly Guid HeartRateMeasurementUuid = Guid.Parse("00002A37-0000-1000-8000-00805F9B34FB");

    private static async Task<int> Main(string[] args)
    {
        // 统一使用 UTF-8 输出，避免中文乱码
        Console.OutputEncoding = System.Text.Encoding.UTF8;

        if (args.Length == 0)
        {
            Console.Error.WriteLine("Usage: ble-tool scan <seconds> | connect <mac>");
            return 1;
        }

        try
        {
            return args[0].ToLowerInvariant() switch
            {
                "scan" => await RunScan(args),
                "connect" => await RunConnect(args),
                _ => PrintUsageError()
            };
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("ERROR|" + ex.Message);
            return 1;
        }
    }

    /// <summary>
    /// 扫描 BLE 广播设备。
    /// <para>使用 BluetoothLEAdvertisementWatcher 以 Active 模式扫描，</para>
    /// <para>每发现一个设备输出一行：MAC|Name|RSSI</para>
    /// </summary>
    private static async Task<int> RunScan(string[] args)
    {
        int durationSeconds = args.Length >= 2 && int.TryParse(args[1], out var d) ? d : 10;

        var watcher = new BluetoothLEAdvertisementWatcher
        {
            ScanningMode = BluetoothLEScanningMode.Active
        };

        var seen = new System.Collections.Generic.HashSet<ulong>();
        var tcs = new TaskCompletionSource<bool>();

        watcher.Received += (_, args) =>
        {
            if (seen.Add(args.BluetoothAddress))
            {
                string mac = FormatMac(args.BluetoothAddress);
                string name = string.IsNullOrEmpty(args.Advertisement.LocalName) ? "" : args.Advertisement.LocalName;
                Console.WriteLine($"{mac}|{name}|{args.RawSignalStrengthInDBm}");
            }
        };

        watcher.Stopped += (_, _) => tcs.TrySetResult(true);

        Console.Error.WriteLine("SCAN_START");
        watcher.Start();

        // 等待指定时间
        await Task.Delay(durationSeconds * 1000);

        watcher.Stop();
        await tcs.Task;
        Console.Error.WriteLine("SCAN_END");

        return 0;
    }

    /// <summary>
    /// 连接设备并订阅心率通知。
    /// <para>参考 Tnze/miband-heart-rate 的实现逻辑：</para>
    /// <list type="number">
    ///   <item>BluetoothLEDevice.FromBluetoothAddressAsync 连接设备（支持未配对）</item>
    ///   <item>GetGattServicesForIdAsync 获取心率服务 0x180D</item>
    ///   <item>GetCharacteristicsForUuidAsync 获取心率测量特征值 0x2A37</item>
    ///   <item>WriteClientCharacteristicConfigurationDescriptorAsync 订阅通知</item>
    ///   <item>ValueChanged 事件解析心率数据并输出</item>
    /// </list>
    /// </summary>
    private static async Task<int> RunConnect(string[] args)
    {
        if (args.Length < 2)
        {
            Console.Error.WriteLine("ERROR|缺少 MAC 地址参数");
            return 1;
        }

        ulong bluetoothAddress = ParseMac(args[1]);
        if (bluetoothAddress == 0)
        {
            Console.Error.WriteLine("ERROR|MAC 地址格式无效");
            return 1;
        }

        // 连接设备（支持未配对设备）
        Console.Error.WriteLine("CONNECTING|" + args[1]);
        BluetoothLEDevice device = await BluetoothLEDevice.FromBluetoothAddressAsync(bluetoothAddress);
        if (device == null)
        {
            Console.Error.WriteLine("ERROR|无法连接设备，FromBluetoothAddressAsync 返回 null");
            return 1;
        }

        // 使用 try/finally 确保任何错误路径下都执行 Dispose 清理，
        // 避免设备端 GATT 会话残留导致后续连接 AccessDenied
        GattDeviceService? heartRateService = null;
        GattCharacteristic? heartRateChar = null;
        try
        {
            Console.Error.WriteLine("CONNECTED|" + device.Name);

            // 等待连接稳定（Unreachable/AccessDenied 常因连接未完全建立）
            await Task.Delay(2000);

            // 获取心率服务 0x180D（带重试，Unreachable 可能是暂时的）
            GattDeviceServicesResult? servicesResult = null;
            for (int attempt = 1; attempt <= 3; attempt++)
            {
                servicesResult = await device.GetGattServicesAsync(BluetoothCacheMode.Uncached);
                if (servicesResult.Status == GattCommunicationStatus.Success && servicesResult.Services.Count > 0)
                {
                    break;
                }
                Console.Error.WriteLine("WARN|获取服务失败 attempt=" + attempt + ", status=" + servicesResult.Status);
                if (attempt < 3)
                {
                    await Task.Delay(3000); // 等待后重试
                }
            }
            if (servicesResult == null || servicesResult.Status != GattCommunicationStatus.Success)
            {
                Console.Error.WriteLine("ERROR|获取服务失败, status=" + servicesResult?.Status);
                return 1;
            }

            foreach (var svc in servicesResult.Services)
            {
                if (svc.Uuid == HeartRateServiceUuid)
                {
                    heartRateService = svc;
                    break;
                }
            }
            if (heartRateService == null)
            {
                Console.Error.WriteLine("ERROR|未找到心率服务 0x180D, 共 " + servicesResult.Services.Count + " 个服务");
                return 1;
            }

            // 获取心率测量特征值 0x2A37
            GattCharacteristicsResult charResult = await heartRateService.GetCharacteristicsForUuidAsync(
                HeartRateMeasurementUuid, BluetoothCacheMode.Uncached);
            if (charResult.Status != GattCommunicationStatus.Success || charResult.Characteristics.Count == 0)
            {
                Console.Error.WriteLine("ERROR|未找到心率测量特征值 0x2A37, status=" + charResult.Status);
                return 1;
            }

            heartRateChar = charResult.Characteristics[0];

            // 订阅通知
            heartRateChar.ValueChanged += (_, args) =>
            {
                int heartRate = ParseHeartRate(args.CharacteristicValue.ToArray());
                Console.WriteLine("HEART|" + heartRate);
            };

            GattCommunicationStatus configStatus = await heartRateChar.WriteClientCharacteristicConfigurationDescriptorAsync(
                GattClientCharacteristicConfigurationDescriptorValue.Notify);
            if (configStatus != GattCommunicationStatus.Success)
            {
                Console.Error.WriteLine("ERROR|订阅通知失败, status=" + configStatus);
                return 1;
            }

            Console.Error.WriteLine("NOTIFY_OK");

            // 保持运行，持续输出心率数据，直到 stdin 收到 "STOP" 或进程被 kill
            Console.WriteLine("READY|已连接，等待心率数据...");
            var stopCts = new CancellationTokenSource();
            _ = Task.Run(async () =>
            {
                string? line;
                while ((line = await Console.In.ReadLineAsync()) != null)
                {
                    if (line.Trim().Equals("STOP", StringComparison.OrdinalIgnoreCase))
                    {
                        stopCts.Cancel();
                        break;
                    }
                }
            });

            try
            {
                await Task.Delay(-1, stopCts.Token);
            }
            catch (TaskCanceledException)
            {
                // 正常停止
            }
        }
        finally
        {
            // 任何路径下都执行清理，避免会话残留导致下次连接 AccessDenied
            if (heartRateChar != null)
            {
                try
                {
                    await heartRateChar.WriteClientCharacteristicConfigurationDescriptorAsync(
                        GattClientCharacteristicConfigurationDescriptorValue.None);
                }
                catch { }
            }
            heartRateService?.Dispose();
            device.Dispose();
            Console.Error.WriteLine("CLEANUP_DONE");
        }

        return 0;
    }

    /// <summary>
    /// 解析标准 GATT 心率测量数据（0x2A37）。
    /// <para>格式参考 Bluetooth SIG Heart Rate Measurement characteristic：</para>
    /// <list type="bullet">
    ///   <item>Byte 0: Flags</item>
    ///   <item>Byte 1: Heart Rate (uint8) 或 Byte 1-2: Heart Rate (uint16, if flag bit 0 set)</item>
    /// </list>
    /// </summary>
    private static int ParseHeartRate(byte[] data)
    {
        if (data == null || data.Length < 2)
        {
            return 0;
        }

        byte flags = data[0];
        // Bit 0: Heart Rate Value Format (0 = uint8, 1 = uint16)
        if ((flags & 0x01) != 0 && data.Length >= 3)
        {
            return data[1] | (data[2] << 8);
        }
        else
        {
            return data[1];
        }
    }

    /// <summary>
    /// 将 ulong BluetoothAddress 格式化为 MAC 地址字符串。
    /// <para>格式：AA:BB:CC:DD:EE:FF（大写）</para>
    /// </summary>
    private static string FormatMac(ulong address)
    {
        return string.Format("{0:X2}:{1:X2}:{2:X2}:{3:X2}:{4:X2}:{5:X2}",
            (address >> 40) & 0xFF,
            (address >> 32) & 0xFF,
            (address >> 24) & 0xFF,
            (address >> 16) & 0xFF,
            (address >> 8) & 0xFF,
            address & 0xFF);
    }

    /// <summary>
    /// 将 MAC 地址字符串解析为 ulong BluetoothAddress。
    /// <para>支持格式：AA:BB:CC:DD:EE:FF 或 AABBCCDDEEFF</para>
    /// </summary>
    private static ulong ParseMac(string mac)
    {
        if (string.IsNullOrWhiteSpace(mac))
        {
            return 0;
        }

        string hex = mac.Replace(":", "").Replace("-", "").Replace(" ", "").Trim();
        if (hex.Length != 12 || !ulong.TryParse(hex, System.Globalization.NumberStyles.HexNumber, null, out ulong result))
        {
            return 0;
        }

        return result;
    }

    private static int PrintUsageError()
    {
        Console.Error.WriteLine("ERROR|未知命令。用法: ble-tool scan <seconds> | connect <mac>");
        return 1;
    }
}
