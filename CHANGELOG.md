# Changelog

## 2026-08-07 (title bar + VS Code logo)

- **Brought back the VS Code logo (top left)** — the top title bar is visible again so the `window-appicon` logo icon renders at the top-left of the WebView (hidden while the whole title bar was `display:none`).
- **Removed the "Visual Studio Code (Preview)" pill** — the command-center pill in the top bar (`part.titlebar .command-center`) is now hidden; the editor area starts below the restored 35px title bar.
- The menu bar and desktop-only title bar controls (window buttons, layout actions) stay hidden on Android.

## 2026-08-07 (MainScreen focus + marketplace fix)

- **Back to the MainScreen WebView** — the app no longer opens `vscode.dev` in a Chrome Custom Tab; it loads directly inside `MainActivity`'s WebView.
- **Removed the request proxy/interceptor** — `CustomWebViewClient.shouldInterceptRequest` (the CDN-bypass re-fetching hack) is gone. All traffic now goes through the WebView's native network stack.
- **Fixed `net::ERR_BLOCKED_BY_RESPONSE` on marketplace descriptions** — `vscode.dev` runs cross-origin isolation (COEP/COOP), so CDN-served content (extension descriptions/READMEs from open-vsx.org / blob CDN) that lacks a `Cross-Origin-Resource-Policy` header was being discarded by the browser. The initial URL now carries `?vscode-coi=off`, which disables cross-origin isolation and lets the marketplace load normally.
- **Desktop user-agent moved to the real setup point** — the UA override now runs right after the WebView is created (the old block referenced the WebView before it existed and never executed).

## 2026-08-07 (CDN description fix)

- **Fix broken marketplace descriptions in the WebView** — the CDN-bypass interceptor in `CustomWebViewClient` was re-fetching every request as a `GET` with a manual `Accept-Encoding: gzip, deflate, br` header:
  - Open VSX's gallery API (`extensionquery`) uses **POST**, so the extension list and descriptions failed to load — non-GET requests are now left to the WebView's native stack (with the desktop user agent).
  - The manual `Accept-Encoding` header disabled transparent decompression, so CDN responses (READMEs, extension descriptions, images) arrived as raw gzip bytes — it is now removed so `HttpURLConnection` handles gzip transparently.
  - Response mime type and charset are now parsed from the `Content-Type` header instead of forcing `utf-8` on every response.

## 2026-08-07 (marketplace)

- **Open VSX marketplace in WebView** — the actual marketplace used by `vscode.dev` (`open-vsx.org` and its extension-file CDN `*.blob.core.windows.net`) is now whitelisted as an internal domain, so the extension gallery, extension details and installs load inside the WebView instead of being kicked out to a Custom Tab.
- **GitHub auth in-app** — `github.com`, `*.githubusercontent.com` and `*.githubapp.com` load internally so marketplace publisher verification and GitHub sign-in complete without leaving the app.
- **Microsoft login broadened** — `login.live.com` and `login.microsoft.com` added alongside `login.microsoftonline.com`.
- **Service workers enabled** — `ServiceWorkerControllerCompat` is now configured (cache mode + content access), which the web-worker extension host and marketplace rely on.

## 2026-08-05 (settings)

- **UA spoof** — Android now reports a desktop Chrome user agent so `vscode.dev` serves the full desktop client.
- **Marketplace** — `marketplace.visualstudio.com` / `gallerycdn.vsassets.io` / `vsassets.io` load inside the app (extension installs and gallery links no longer jump to an external browser).
- **Hardware acceleration** — enabled in the manifest for smoother webview rendering.
- **Storage permissions** — enabled `WRITE_EXTERNAL_STORAGE` (API ≤ 28) and `READ_EXTERNAL_STORAGE` (API ≤ 32) for downloads to public storage.
- **Keep screen on** — screen now stays awake while the app is in the foreground.
- **File picker** — added `*/*` `GET_CONTENT` visibility query so file managers show up on Android 11+.

## 2026-08-05

- **Design overhaul** — new app icon, splash screen and brand palette (VSCode-inspired blue + deep navy) across light and dark themes.
- Cross-platform icon generator (`tools/generate_icons.py`) replaces the macOS-only `sips`/ImageMagick scripts.
- Web UI refresh: `customCSS.css` / `androidCustomCSS.css` now injected into `vscode.dev` to align accent colors, polish chrome and improve touch targets on mobile.
- Redesigned offline page to match the new brand.
- Fixed the navigation drawer opening full-screen (was `match_parent` + an ignored `layout_weight`).
- Theming switched to `androidTheme: auto` (follows system light/dark).

## 2014-01-04

- Fix a crash on reload with no page loaded.

## 2015-01-02

- Update to latest gradle and build tools versions, making the project compatible with Android Studio 1.0.
- Fix bugs related to syncing of tabs with sidebar menu.

## 2014-12-23

- Allow setting of viewport while preserving ability to zoom.
- Allow dynamic config of navigation title image URLs.
- Various bug fixes involving javascript after page load, and tab coloring, tab animations, and a crash on application resume.

## 2014-12-22

- Fix various threading bugs where UI methods were called from non-UI threads.

## 2014-12-05

- Support showing the navigation title image on specific URLs.

## 2014-12-03

- Support customizing user agent per URL.
- Add color styling options for tabs.

## 2014-11-30

- New tabs with better material design and animations.
- Fix some automatic icon generation scripts.

## 2014-11-26

- Fix a crash involving webview pools.

## 2014-11-25

- Add support for custom actions in action bar.
