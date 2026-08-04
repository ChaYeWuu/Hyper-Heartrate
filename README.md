# Xiaomi Heartrate

一个 Minecraft Fabric Mod，用于读取标准GATT设备的实时心率并显示到游戏内 HUD。

## 功能特性

- **实时心率显示**：通过 BLE 连接小米穿戴设备，实时读取心率数据并在游戏 HUD 中显示
- **模块化 HUD**：支持模块化模式，可独立调整心率图标、心率数值、设备名称的位置
- **字体大小调节**：心率文字、设备名称、GUI 面板、心率图标均可独立调节大小
- **心跳模式**：根据实时心率自动变色（蓝→绿→黄→红），并支持子菜单独立控制图标/心率/BPM 各自是否变色
- **HTTP API**：本地 HTTP 服务，支持 OBS 浏览器源接入
- **RGB 颜色自定义**：支持 RGBA 四通道颜色调节与实时预览
- **进程安全管理**：PID 记录 + 启动清理 + taskkill 进程树，防止 .NET Host 残留

## 环境要求

- [.NET 10](https://dotnet.microsoft.com/download)（BLE 功能依赖）
- Windows 系统（BLE 通过 WinRT 实现）
- 标准GATT设备需在设置中开启「心率广播」

### Minecraft 版本与对应构建

| 目录 | Minecraft 版本 | Fabric Loader | Fabric API | Java | Loom |
|------|---------------|---------------|-----------|------|------|
| `fabric-26.2/` | 26.2.x | 0.19.3+ | 0.152.0+ | 25 | 1.17 (非混淆) |
| `fabric-26.1/` | 26.1.x | 0.19.2+ | 0.150.0+ | 25 | 1.15 (非混淆) |
| `fabric-1.21.11/` | 1.21.11+ | 0.18.5+ | 0.141.4+ | 21 | 1.14 (Yarn remap) |
| `fabric-1.21.6-1.21.10/` | 1.21.6–1.21.10 | 0.18.5+ | 0.128.2+ | 21 | 1.14 (Yarn remap) |
| `fabric-1.21.1-1.21.5/` | 1.21.1–1.21.5 | 0.16.14+ | 0.119.2+ | 21 | 1.14 (Yarn remap) |

> 各子项目源码逻辑一致，仅因 Minecraft/Fabric API 差异分别适配。请根据游戏版本选择对应目录构建。

#### 版本差异说明

- **1.21.1–1.21.5**：`KeyBinding` 构造使用 `KeyBinding.Category` record 之前的旧签名；`Screen.renderBackground` 会应用原版模糊，已通过 `BaseModScreen` 重写为空操作规避
- **1.21.6–1.21.10**：`KeyBinding` 构造使用 String category（`KeyBinding.Category` record 在 1.21.11 才引入）；鼠标事件使用旧签名 `mouseClicked(double, double, int)`
- **1.21.11+**：`KeyBinding` 构造使用 `KeyBinding.Category` record；鼠标事件使用新签名 `mouseClicked(Click, boolean)`

## 安装

1. 将对应版本的 `xiaomi-heartrate-1.2.0_fabric-*.jar` 放入 `.minecraft/mods/` 目录
2. 安装 [.NET 10](https://dotnet.microsoft.com/download)
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
├── fabric-26.2/                # Minecraft 26.2.x（Mojang 非混淆映射）
├── fabric-26.1/                # Minecraft 26.1.x（Mojang 非混淆映射）
├── fabric-1.21.11/             # Minecraft 1.21.11+（Yarn 映射）
├── fabric-1.21.6-1.21.10/      # Minecraft 1.21.6–1.21.10（Yarn 映射）
├── fabric-1.21.1-1.21.5/       # Minecraft 1.21.1–1.21.5（Yarn 映射）
│   └── src/main/java/com/chayewuu/xiaomiheartrate/
│       ├── config/             # 配置管理
│       ├── device/             # BLE 设备管理
│       ├── gui/                # GUI 界面
│       ├── heart/              # 心率数据管理
│       ├── network/            # HTTP API
│       └── util/               # 工具类
├── tools/                      # ble-tool (.NET BLE 工具)
└── .trae/                      # TRAE 项目配置
```

## 开发

根据目标 Minecraft 版本进入对应目录构建：

```bash
# Minecraft 26.2.x
cd fabric-26.2 && ./gradlew clean build

# Minecraft 26.1.x
cd fabric-26.1 && ./gradlew clean build

# Minecraft 1.21.11+
cd fabric-1.21.11 && ./gradlew clean build

# Minecraft 1.21.6–1.21.10
cd fabric-1.21.6-1.21.10 && ./gradlew clean build

# Minecraft 1.21.1–1.21.5
cd fabric-1.21.1-1.21.5 && ./gradlew clean build
```

> ⚠️ 必须使用 `clean build` 而非 `build`。Loom remap 任务存在增量编译缓存问题，直接 `build` 会因残留的旧产物导致编译失败或产物不更新。

构建产物位于 `<对应目录>/build/libs/`，命名格式为 `xiaomi-heartrate-1.2.0_fabric-<版本范围>.jar`：

| 目录 | 产物名 |
|------|--------|
| `fabric-26.2/` | `xiaomi-heartrate-1.2.0_fabric-26.2.jar` |
| `fabric-26.1/` | `xiaomi-heartrate-1.2.0_fabric-26.1.jar` |
| `fabric-1.21.11/` | `xiaomi-heartrate-1.2.0_fabric-1.21.11.jar` |
| `fabric-1.21.6-1.21.10/` | `xiaomi-heartrate-1.2.0_fabric-1.21.6-1.21.10.jar` |
| `fabric-1.21.1-1.21.5/` | `xiaomi-heartrate-1.2.0_fabric-1.21.1-1.21.5.jar` |

## 更新日志

### v1.2.0
- 新增 支持所有标准 GATT的设备（华为/苹果/三星/佳明等）
- 优化 非米系设备统一显示为「[通用心率广播设备]」
- 优化 模组稳定性,提高模组流畅度

### v1.1.0
- 新增 适配 Minecraft 1.21-26.2 所有版本
- 优化 规范项目目录命名与构建产物命名
- 修复 部分版本渲染异常的问题
- 修复 部分版本启动失败的问题

## 协议

[MIT License](LICENSE)

## 作者

茶叶Wuu — [GitHub](https://github.com/ChaYeWuu/Xiaomi-Heartrate)
