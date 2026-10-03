# TopBar Plugin for Kiosk Satellite

**TopBar** is a lightweight, floating control bar overlay plugin designed for [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) on Android tablets.

It seamlessly integrates with Home Assistant to display customizable action buttons that dynamically adapt their state and appearance based on real-time feedback.

---

## ✨ Key Features

- **Floating Overlay Control**: Stays accessible over the main kiosk interface with glassmorphism design.
- **Home Assistant Integration**: Trigger scripts, scenes, automations, or device actions (`light`, `switch`, etc.) directly from your tablet overlay.
- **Live State Tracking**: Stateful entities (like lights and switches) automatically change color dynamically (Active Color when ON, Muted Grey when OFF).
- **Stateless Actions**: Scripts, scenes, and automations maintain their configured accent color.
- **Auto-Hide & Foreground Detection**: Automatically hides when navigating to background apps or when screensaver activates, ensuring no interference with other applications.
- **Day/Night Theme Sync**: Automatically switches between dark and light themes based on Kiosk Satellite screen/dimmer state.
- **Customizable**: Adjustable position (Left, Center, Right), size scaling (80%, 100%, 120%), and custom MDI icons/colors for up to 3 buttons.

---

## 🎨 Appearance (Light & Dark Theme)

| Light Theme | Dark Theme |
| :---: | :---: |
| ![Light Theme](https://ai-code-interpreter.usercontent.google.com/usercontent/0?file=image0.png) | ![Dark Theme](https://ai-code-interpreter.usercontent.google.com/usercontent/0?file=image1.png) |

---

## 🛠️ Requirements

- [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) (Android SDK 26+)
- Home Assistant instance with a Long-Lived Access Token.

---

## 📥 Installation

### Method 1: Via GitHub Repository (Recommended for Auto-Updates)

Installing via GitHub repository enables automatic update checks directly inside Kiosk Satellite.

1. Open **Kiosk Satellite** on your Android device.
2. Navigate to **Settings** > **Plugin Manager**.
3. Tap **Add plugin** and paste the GitHub repository URL:
   ```text
   https://github.com/nelsonamen/TopBar
   ```
4. Tap **Preview**, review the manifest, and tap **Trust and install**.
5. Enable **TopBar** on its entry row.

---

### Method 2: Manual ZIP Installation

1. Download the latest `top-bar-YYYY.MM.DD.zip` from the [Releases](https://github.com/nelsonamen/TopBar/releases) page.
2. Open **Kiosk Satellite** on your Android device.
3. Navigate to **Plugin Manager** > **Developer Tools** > **Install from ZIP**.
4. Select the downloaded ZIP file to install/update.
5. Enable **TopBar** on its entry row.

---

## 📄 License

Distributed under the MIT License.
