# Xiaomi Heartrate

一个 Minecraft Fabric Mod，用于读取米系穿戴设备的实时心率并显示到游戏内 HUD。

## 功能特性

- **实时心率显示**：通过 BLE 连接小米穿戴设备，实时读取心率数据并在游戏 HUD 中显示
- **模块化 HUD**：支持模块化模式，可独立调整心率图标、心率数值、设备名称的位置
- **字体大小调节**：心率文字、设备名称、GUI 面板、心率图标均可独立调节大小
- **心跳模式**：根据实时心率自动变色（蓝→绿→黄→红），并支持子菜单独立控制图标/心率/BPM 各自是否变色
- **HTTP API**：本地 HTTP 服务，支持 OBS 浏览器源接入
- **RGB 颜色自定义**：支持 RGBA 四通道颜色调节与实时预览
- **进程安全管理**：PID 记录 + 启动清理 + taskkill 进程树，防止 .NET Host 残留

## 环境要求

- Minecraft 26.2
- Fabric Loader 0.19.3+
- Fabric API 0.152.0+
- Java 25
- .NET 10 运行时（BLE 功能依赖）
- Windows 系统（BLE 通过 WinRT 实现）
- 小米设备需在设置中开启「心率广播」

## 安装

1. 将 `xiaomi-heartrate-1.0.0.jar` 放入 `.minecraft/mods/` 目录
2. 安装 [.NET 10 运行时](https://dotnet.microsoft.com/download)
3. 启动游戏，Mod 会自动提取 BLE 工具文件到 `.minecraft/config/heartrate/`
4. 按 `H` 键打开主界面，点击「连接设备」扫描并连接

## 使用说明

### 按键
- `H` — 打开/关闭主界面

### 主界面按钮
| 按钮 | 功能 |
|------|------|
| 连接设备 | 打开设备扫描界面 |
| 断开连接 | 断开当前设备 |
| GUI 设置 | 打开显示设置（字体、颜色、位置等） |
| 心率设置 | 心率采集参数 |
| API 设置 | HTTP/Mod API 开关 |
| 项目主页 | 打开 GitHub 仓库 |
| 心跳模式 | 开启后心率图标根据心率值自动变色 |

### GUI 设置
- **心率显示位置**：点击进入拖动模式，可切换模块化模式独立调整各模块位置
- **字体大小**：各模块独立调节大小（心率文字/设备名称/GUI 面板/心率图标）
- **RGB 颜色**：四通道滑块 + 实时预览
- **心动模式**：主开关 + 自定义子菜单（独立控制心率图标/心率数值/BPM 后缀是否变色）
- **显示图标/设备名/BPM后缀**：独立开关

### 心跳模式颜色对照
| 心率范围 | 颜色 | 含义 |
|----------|------|------|
| < 60 BPM | 蓝色 | 平静 |
| 60-100 BPM | 绿色 | 正常 |
| 100-140 BPM | 黄色 | 偏高 |
| > 140 BPM | 红色 | 过高 |

### HTTP API
启用后可通过 `http://127.0.0.1:端口/` 获取心率数据，适用于 OBS 浏览器源接入。

## 技术架构

- **BLE 通信**：通过 .NET ble-tool 工具实现 WinRT BLE 扫描与 GATT 通信
- **进程管理**：BleProcessManager 管理 .NET Host 生命周期，PID 文件 + taskkill 进程树
- **HUD 渲染**：Fabric HUD API + Matrix3x2fStack 实现缩放渲染

## 项目结构

```
xiaomiheartrate/
├── fabric-26.2/          # Fabric Mod 主项目
│   └── src/main/java/com/chayewuu/xiaomiheartrate/
│       ├── config/       # 配置管理
│       ├── device/       # BLE 设备管理
│       ├── gui/          # GUI 界面
│       ├── heart/        # 心率数据管理
│       ├── network/      # HTTP API
│       └── util/         # 工具类
├── tools/                # ble-tool (.NET BLE 工具)
└── .trae/                # TRAE 项目配置
```

## 开发

```bash
cd fabric-26.2
./gradlew build
```

构建产物：`fabric-26.2/build/libs/xiaomi-heartrate-1.0.0.jar`

## 协议

[MIT License](LICENSE)

## 作者

茶叶Wuu — [GitHub](https://github.com/ChaYeWuu/Xiaomi-Heartrate)
