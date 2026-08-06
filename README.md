# Hyper Heartrate

一个 Minecraft Fabric Mod，用于读取全世界心率广播设备的实时心率并显示到游戏内 HUD。支持华为/苹果/三星/佳明/卡西欧等 80+ 品牌 GATT 心率设备。

## 功能特性

- **实时心率显示**：通过 BLE 连接心率广播设备，实时读取心率数据并在游戏 HUD 中显示
- **联机心率同步**：服务端可广播心率给附近玩家，在玩家 NameTag 上方/内部/下方显示心率，TAB 列表同步显示
- **模块化 HUD**：支持模块化模式，可独立调整心率图标、心率数值、设备名称的位置
- **HUD 总开关**：主界面右下角一键控制游戏内心率显示，关闭后未连接时不显示灰色心率
- **HUD 背景板**：游戏内心率显示可开启圆角半透明背景板，支持透明度调节
- **字体大小调节**：心率文字、设备名称、GUI 面板、心率图标均可独立调节大小
- **心跳模式**：根据实时心率自动变色（蓝→绿→黄→红），并支持子菜单独立控制图标/心率/BPM 各自是否变色
- **设备品牌识别**：自动识别 80+ 全球品牌设备并分类显示（如 [华为设备]、[苹果设备]、[佳明设备]）
- **HTTP API**：本地 HTTP 服务，支持 OBS 浏览器源接入，含专用 `/obs` 跳动心脏动画接口
- **RGB 颜色自定义**：支持 RGBA 四通道颜色调节与实时预览
- **进程安全管理**：PID 记录 + 启动清理 + taskkill 进程树，防止 .NET Host 残留

## 环境要求

- [.NET 10](https://dotnet.microsoft.com/download)（BLE 功能依赖）
- Windows 系统（BLE 通过 WinRT 实现）
- 小米设备需在设置中开启「心率广播」；其他品牌设备需开启心率广播或 GATT 心率服务（0x180D / 0x2A37）

### Minecraft 版本与对应构建

| 目录 | Minecraft 版本 | Fabric Loader | Fabric API | Java | Loom |
|------|---------------|---------------|-----------|------|------|
| `fabric-26.2/` | 26.2.x | 0.19.3+ | 0.152.0+ | 25 | 1.17 (非混淆) |
| `fabric-26.1/` | 26.1.x | 0.19.2+ | 0.150.0+ | 25 | 1.15 (非混淆) |
| `fabric-1.21.11/` | 1.21.11+ | 0.18.5+ | 0.141.4+ | 21 | 1.14 (Yarn remap) |
| `fabric-1.21.6-1.21.10/` | 1.21.6–1.21.10 | 0.18.5+ | 0.128.2+ | 21 | 1.14 (Yarn remap) |
| `fabric-1.21.4-1.21.5/` | 1.21.4–1.21.5 | 0.16.14+ | 0.119.2+ | 21 | 1.14 (Yarn remap) |
| `fabric-1.21.1-1.21.3/` | 1.21.1–1.21.3 | 0.16.10+ | 0.116.5+ | 21 | 1.14 (Yarn remap) |

> 各子项目源码逻辑一致，仅因 Minecraft/Fabric API 差异分别适配。请根据游戏版本选择对应目录构建。

#### 版本差异说明

- **1.21.1–1.21.3**：`EntityRenderState` 尚未引入，NameTag 渲染使用旧管线 `renderLabelIfPresent(T entity, Text, ...)` 直接传入实体对象；`KeyBinding` 构造使用 String category
- **1.21.4–1.21.5**：引入 `EntityRenderState`，NameTag 渲染改为 `renderLabelIfPresent(S state, ...)`；`Screen.renderBackground` 会应用原版模糊，已通过 `BaseModScreen` 重写为空操作规避
- **1.21.6–1.21.10**：`KeyBinding` 构造使用 String category（`KeyBinding.Category` record 在 1.21.11 才引入）；鼠标事件使用旧签名 `mouseClicked(double, double, int)`
- **1.21.11+**：`KeyBinding` 构造使用 `KeyBinding.Category` record；鼠标事件使用新签名 `mouseClicked(Click, boolean)`；NameTag 渲染为队列提交式（`OrderedRenderCommandQueue.submitLabel`）

## 安装

1. 将对应版本的 `hyper-heartrate-1.3.0_fabric-*.jar` 放入 `.minecraft/mods/` 目录
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
| 模组设置 | 打开子菜单（GUI设置 / 心率设置 / API设置） |
| 联机功能 | 联机心率同步设置（NameTag位置、显示元素、TAB列表） |
| B站主页 | 打开 Bilibili 空间页 |
| 项目主页 | 打开 GitHub 仓库 |
| HUD 开关 | 右下角总开关，控制游戏内心率 HUD 显隐 |

### 联机功能
- **NameTag 显示位置**：上方（心率在名字上方）/ 内部（同名同行）/ 下方（心率在名字下方）
- **显示元素**：独立控制心率图标、BPM 后缀的显隐
- **心动模式**：远程玩家心率也可独立变色，支持子菜单自定义
- **TAB 列表**：TAB 键打开玩家列表后，在名字旁显示心率

### GUI 设置
- **心率显示位置**：点击进入拖动模式，可切换模块化模式独立调整各模块位置
- **字体大小**：各模块独立调节大小（心率文字/设备名称/GUI 面板/心率图标）
- **RGB 颜色**：四通道滑块 + 实时预览
- **HUD 背景板**：开关 + 透明度滑块，为游戏内心率显示添加圆角半透明背景
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
启用后可通过以下端点获取心率数据，适用于 OBS 浏览器源接入：

| 端点 | 说明 |
|------|------|
| `http://127.0.0.1:端口/` | 原始心率数据接口 |
| `http://127.0.0.1:端口/obs` | OBS 专用接口：左侧跳动心脏动画 + 右侧心率数值，跳动频率跟随实时心率 |
| `http://127.0.0.1:端口/heart` | 心率 JSON 数据 |
| `http://127.0.0.1:端口/status` | 设备连接状态 |
| `http://127.0.0.1:端口/graph` | 心率图表 |

## 技术架构

- **BLE 通信**：通过 .NET ble-tool 工具实现 WinRT BLE 扫描与 GATT 通信
- **进程管理**：BleProcessManager 管理 .NET Host 生命周期，PID 文件 + taskkill 进程树
- **联机同步**：Fabric Networking API（CustomPayload），每秒广播自身心率，服务端转发给附近玩家
- **NameTag 渲染**：Mixin 注入 EntityRenderer，根据版本差异适配三种渲染管线
- **HUD 渲染**：Fabric HUD API + 缩放渲染
- **设备识别**：80+ 品牌关键词分类 + 非心率设备黑名单过滤

## 项目结构

```
hyper-heartrate/
├── fabric-26.2/                # Minecraft 26.2.x（Mojang 非混淆映射）
├── fabric-26.1/                # Minecraft 26.1.x（Mojang 非混淆映射）
├── fabric-1.21.11/             # Minecraft 1.21.11+（Yarn 映射，队列提交式渲染）
├── fabric-1.21.6-1.21.10/      # Minecraft 1.21.6–1.21.10（Yarn 映射）
├── fabric-1.21.4-1.21.5/       # Minecraft 1.21.4–1.21.5（Yarn 映射）
├── fabric-1.21.1-1.21.3/       # Minecraft 1.21.1–1.21.3（Yarn 映射，旧渲染管线）
│   └── src/main/java/com/chayewuu/hyperheartrate/
│       ├── api/                # 对外 API（供其他 Mod 调用）
│       ├── config/             # 配置管理
│       ├── device/             # BLE 设备管理与品牌识别
│       ├── gui/                # GUI 界面
│       ├── heart/              # 心率数据管理
│       ├── network/            # HTTP API + 联机同步
│       └── util/               # 工具类
├── tools/                      # ble-tool (.NET BLE 工具)
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

# Minecraft 1.21.4–1.21.5
cd fabric-1.21.4-1.21.5 && ./gradlew clean build

# Minecraft 1.21.1–1.21.3
cd fabric-1.21.1-1.21.3 && ./gradlew clean build
```

> ⚠️ 必须使用 `clean build` 而非 `build`。Loom remap 任务存在增量编译缓存问题，直接 `build` 会因残留的旧产物导致编译失败或产物不更新。

构建产物位于 `<对应目录>/build/libs/`，命名格式为 `hyper-heartrate-1.3.0_fabric-<版本范围>.jar`：

| 目录 | 产物名 |
|------|--------|
| `fabric-26.2/` | `hyper-heartrate-1.3.0_fabric-26.2.jar` |
| `fabric-26.1/` | `hyper-heartrate-1.3.0_fabric-26.1.jar` |
| `fabric-1.21.11/` | `hyper-heartrate-1.3.0_fabric-1.21.11.jar` |
| `fabric-1.21.6-1.21.10/` | `hyper-heartrate-1.3.0_fabric-1.21.6-1.21.10.jar` |
| `fabric-1.21.4-1.21.5/` | `hyper-heartrate-1.3.0_fabric-1.21.4-1.21.5.jar` |
| `fabric-1.21.1-1.21.3/` | `hyper-heartrate-1.3.0_fabric-1.21.1-1.21.3.jar` |

## 更新日志

### v1.3.0(最高产的一集)
- 新增 联机心率同步：服务端广播心率给附近玩家，NameTag 上方/内部/下方显示心率
- 新增 联机功能设置界面：NameTag 位置、显示元素、心动模式、TAB 列表心率独立配置
- 新增 模组设置子菜单：整合 GUI 设置/心率设置/API 设置入口
- 新增 HUD 总开关：主界面右下角控制游戏内心率显示，关闭后未连接时不显示灰色心率
- 新增 HUD 背景板：游戏内心率显示可开启 MC 原版风格背景板，支持透明度调节
- 新增 OBS 专用接口 `/obs`：左侧跳动心脏动画 + 右侧心率数值，跳动频率跟随实时心率
- 新增 设备品牌识别：支持 80+ 全球品牌设备识别与分类（华为/苹果/三星/佳明/卡西欧等）
- 新增 1.21.4-1.21.5 独立分支：分离低版本兼容，确保各版本最佳体验
- 修复 未连接时 HUD 图标错误显示红色的问题（心动模式关闭时）
- 修复 手表关闭心率广播后 HUD 仍显示最后一次心率的 BUG（10 秒过期机制）
- 修复 联机 NameTag 心率闪烁问题（弱引用 Map → HashMap 稳定索引）
- 修复 1.21.11 队列提交式渲染 NameTag 上下行分离（额外 submitLabel）
- 修复 1.21.1-1.21.3 旧渲染管线 NameTag 兼容（EntityRenderState 不存在版本）
- 修复 1.21.1-1.21.3 退出时不释放 BLE 蓝牙的问题（ClientLifecycleEvents 注册）
- 修复 各版本 HUD 总开关关闭后仍有灰色图标残留的问题
- 重构 GUI 面板风格：所有界面面板从圆角半透明改为 MC 原版风格
- 重构 模组品牌迁移：Xiaomi Heartrate → Hyper Heartrate，包名/Mod ID/资源路径全面更新
- 优化 设备过滤：黑名单过滤非心率设备（家电/未知设备等），扫描结果更精准
- 优化 字体资源：像素心形字体同步到所有 1.21.x 分支

### v1.2.0
- 新增 支持所有标准 GATT 的设备（华为/苹果/三星/佳明等）
- 优化 非米系设备统一显示为「[通用心率广播设备]」
- 优化 模组稳定性，提高模组流畅度

### v1.1.0
- 新增 适配 Minecraft 1.21-26.2 所有版本
- 优化 规范项目目录命名与构建产物命名
- 修复 部分版本渲染异常的问题
- 修复 部分版本启动失败的问题

## 协议

[MIT License](LICENSE)

## 作者

茶叶Wuu — [GitHub](https://github.com/ChaYeWuu/Hyper-Heartrate)