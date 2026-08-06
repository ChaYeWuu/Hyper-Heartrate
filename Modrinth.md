# Hyper Heartrate

**Display real-time heart rate from your wearable device inside Minecraft!**

Hyper Heartrate reads heart rate data from Bluetooth Low Energy (BLE) wearable devices and displays it directly on your in-game HUD. Supports 80+ global brands including Xiaomi, Apple, Samsung, Huawei, Garmin, and any standard GATT heart rate device (0x180D / 0x2A37).

---

## ✨ Features

- **Real-time HUD** — Heart rate icon, BPM value, and device name displayed on your HUD
- **Multiplayer Sync** — Broadcast your heart rate to nearby players; show heart rate on their NameTags (above/inside/below) and in the TAB list
- **Modular HUD** — Drag & drop individual elements (icon, heart rate, device name) to any position
- **Heartbeat Color Mode** — Auto-color your heart rate display based on BPM value: blue (calm) → green (normal) → yellow (elevated) → red (high)
- **Customizable** — Adjust font size, RGB color, opacity, background panel, and toggle icon/BPM/device name independently
- **HTTP API** — Local HTTP server with OBS browser source support (animated heart + BPM)
- **Device Brand Recognition** — Automatically identifies 80+ brands (Xiaomi, Apple, Samsung, Garmin, Huawei, etc.) with non-heart-rate device filtering
- **HUD Toggle** — One-click toggle in the main menu to hide/show the HUD
- **HUD Background** — Optional rounded semi-transparent background panel for the in-game heart rate display

### Heartbeat Color Reference

| BPM Range | Color | Meaning |
|-----------|-------|---------|
| < 60 BPM | Blue | Calm |
| 60–100 BPM | Green | Normal |
| 100–140 BPM | Yellow | Elevated |
| > 140 BPM | Red | High |

---

## 📋 Requirements

- **Windows** (BLE via WinRT)
- **[.NET 10](https://dotnet.microsoft.com/download)** (BLE backend)
- **Fabric API** (required)
- A wearable device with heart rate broadcast support (GATT 0x180D / 0x2A37)

> For Xiaomi devices: enable "Heart Rate Broadcast" in the device settings. For other brands: enable heart rate broadcast or GATT heart rate service.

---

## 🎮 Supported Minecraft Versions

| Directory | Minecraft | Fabric Loader | Fabric API | Java |
|-----------|-----------|---------------|------------|------|
| `fabric-26.2/` | 26.2.x | 0.19.3+ | 0.152.0+ | 25 |
| `fabric-26.1/` | 26.1.x | 0.19.2+ | 0.150.0+ | 25 |
| `fabric-1.21.11/` | 1.21.11+ | 0.18.5+ | 0.141.4+ | 21 |
| `fabric-1.21.6-1.21.10/` | 1.21.6–1.21.10 | 0.18.5+ | 0.128.2+ | 21 |
| `fabric-1.21.4-1.21.5/` | 1.21.4–1.21.5 | 0.16.14+ | 0.119.2+ | 21 |
| `fabric-1.21.1-1.21.3/` | 1.21.1–1.21.3 | 0.16.10+ | 0.116.5+ | 21 |

> Download the jar that matches your Minecraft version. Each version is independently built and maintained.

---

## 🔧 Installation

1. Install [.NET 10](https://dotnet.microsoft.com/download) (required for BLE)
2. Download the correct jar for your Minecraft version
3. Place the jar in `.minecraft/mods/`
4. Launch the game — the mod will automatically extract the BLE tool to `.minecraft/config/heartrate/`
5. Press `H` to open the main menu, click "Connect Device" to scan and connect

---

## 🎯 Controls

| Key | Action |
|-----|--------|
| `H` | Open/close main menu |

### Main Menu Buttons

| Button | Function |
|--------|----------|
| Connect Device | Open device scanner |
| Disconnect | Disconnect current device |
| Mod Settings | Submenu: GUI / Heart Rate / API settings |
| Multiplayer | NameTag position, display elements, TAB list |
| Bilibili Home | Open Bilibili space page |
| Project Home | Open GitHub repository |
| HUD Toggle | Show/hide in-game heart rate HUD |

---

## 🌐 HTTP API

Enable the HTTP server in API settings. Endpoints:

| Endpoint | Description |
|----------|-------------|
| `/` | Raw heart rate data (JSON) |
| `/obs` | OBS browser source: animated heart + BPM |
| `/heart` | Heart rate JSON data |
| `/status` | Device connection status |
| `/graph` | Heart rate chart |

---

## 📦 Download

Get the latest release from the [Releases](https://github.com/ChaYeWuu/Hyper-Heartrate/releases) page.

---

## 📝 License

[MIT](LICENSE)

## 👤 Author

**茶叶Wuu** — [GitHub](https://github.com/ChaYeWuu/Hyper-Heartrate)