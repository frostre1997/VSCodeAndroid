# Visual Code Studio for Android

An open-source Android client for [vscode.dev](https://vscode.dev/) — a fully functional development environment on your mobile device. Sign in, use remote repositories, connect over SSH, install extensions, run code, and more.

> This is a redesigned fork of the abandoned [`Fundiuman/VSCodeAndroid`](https://github.com/Fundiman/VSCodeAndroid) project. It is built on the GoNative webview wrapper, so most styling is driven by `app/src/main/assets/appConfig.json`.

## Features

- **Login & Authentication**: Sign into GitHub and other remote repositories.
- **Remote Repositories**: Clone, push, and pull from Git repositories.
- **SSH**: Access remote servers directly from the app.
- **Extensions**: Install and manage VS Code extensions.
- **Run Code**: Execute code on your device, with support for Python, JavaScript, and more.
- **Full VS Code functionality**: Almost all VS Code features are available.

## Feature status

| Feature | Status |
| --- | --- |
| Code editing (JS, Python, HTML, CSS, …) | ✅ Fully working |
| Syntax highlighting | ✅ Fully working |
| Themes (dark/light) | ✅ Fully working |
| File explorer | ✅ Fully working |
| Search & Replace | ✅ Fully working |
| Extensions (themes, linters, formatters) | ✅ Working (Open VSX marketplace now loads in the WebView) |
| Git (add, commit, push, pull) | 🔧 Only for remote repos (GitHub integration); local Git would need a JGit bridge |
| Marketplace | ✅ Open VSX (`open-vsx.org`) and its CDN load in-app; GitHub auth for publisher verification completes in-app |
| Terminal | ❌ Impossible — needs a server-side backend |
| Python / C++ / Java language servers | ❌ Impossible — needs Node/WASM language server backends |
| Remote / Codespaces | ⚠️ Partially — GitHub auth works, Codespaces depends on the remote service |

## The redesign

- **New identity** — an original blue-to-navy "code chevron" icon, matching splash screen and offline page.
- **VSCode-inspired palette** — accent `#007ACC`, dark `#1E1E1E` / `#252526` surfaces, deep navy `#0A1A2F` splash.
- **Auto theming** — the app follows the system light/dark setting (`androidTheme: auto`).
- **Web UI refresh** — `app/src/main/assets/customCSS.css` (and `androidCustomCSS.css`) are injected into every `vscode.dev` page to align the accent color, polish the chrome and enlarge touch targets on mobile.
- **Fixed navigation drawer** — no longer opens full-screen (capped at 320dp).

## Build & install

### Prerequisites

- **JDK 17** (or newer)
- **Gradle 8.5+** (the project ships a wrapper in `gradlew`)
- **Android SDK** (compile SDK 34)

### Commands

```bash
git clone <your-fork-url> VSCodeAndroid
cd VSCodeAndroid

# Debug build
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# Release build
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

> Note: the `signingConfigs.release` block references `release.keystore`. Replace it with your own keystore before shipping.

## Regenerating assets

Icons, splash and logos are generated from a single design by a **cross-platform** Python script (no ImageMagick or `sips` needed):

```bash
pip install pillow
python3 tools/generate_icons.py
```

The legacy `generate-app-icons.sh` / `generate-header-images.sh` scripts (macOS-only) are kept for reference but are no longer required.

## Theming

| Where | What it controls |
| --- | --- |
| `appConfig.json` → `styling` | All native chrome colors (action bar, status bar, tabs, sidebar, splash) and theme mode |
| `res/values/colors.xml` | Light-theme native colors |
| `res/values-night/colors.xml` | Dark-theme native colors |
| `assets/customCSS.css` | Injected styles for the web UI (accent color, touch targets) |
| `assets/androidCustomCSS.css` | Android-only web UI overrides |
| `assets/offline.html` | Offline page |

## License

MIT — see [LICENSE](LICENSE).

## Disclaimer

Unofficial client for Visual Studio Code. Not endorsed by or affiliated with Microsoft. The original VS Code app is developed and maintained by Microsoft.
