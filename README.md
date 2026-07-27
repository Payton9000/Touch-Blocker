# Touch Blocker / 触控屏蔽器

## English

### Overview

Touch Blocker is an Android app that blocks touches at selected screen locations using system overlay windows. It provides tools to record points, manage them, test blocking behavior, and restore the active configuration after boot.

### Features

- Start and stop overlay blocking with a foreground service.
- Record, enable, disable, and manage block points.
- Test blocking behavior in a dedicated screen.
- Preview the current display, cutouts, hinges, and configured points.
- Support fullscreen coverage, including system bars and display cutouts.
- Select profiles for foldable display regions and fold postures.
- Use Debug Overlay and export logs for troubleshooting.
- Support Material 3 light and dark themes.

Blocking uses square interception windows. The preview may draw circular point markers, but hit testing follows the complete square window, including screen edges, system bars, cutouts, and fold hinges when those regions are exposed.

### Permissions

- `SYSTEM_ALERT_WINDOW` for overlay windows.
- `FOREGROUND_SERVICE` to keep overlays active.
- `RECEIVE_BOOT_COMPLETED` for optional boot restore.
- `POST_NOTIFICATIONS` for the foreground-service notification on Android 13+.
- `INTERNET` for log export or diagnostics.

### Usage

1. Open the app and grant overlay permission.
2. Review the screen preview and select a profile when profile controls are available.
3. Use `START RECORD` to capture block points.
4. Use `MANAGE BLOCK POINTS` to edit, enable, disable, or remove points.
5. Use `TEST BLOCKING` to verify behavior.
6. Enable the overlay service when the configuration is ready.

### Build and test

From the project root on Windows:

```powershell
.\gradlew.bat :app2:testDebugUnitTest
.\gradlew.bat :app2:assembleDebug
.\gradlew.bat :app2:assembleDebugAndroidTest
.\gradlew.bat :app2:lintDebug
```

### Notes

Overlays require special permission and may be restricted by some device vendors. The app targets Android API 33, supports Android 5.0 (API 21) and newer, and uses `versionName "2.0"` with `versionCode 5`.

---

## 中文

### 概述

Touch Blocker 是一个通过系统悬浮窗在指定位置屏蔽触摸的 Android 应用，提供屏蔽点录制、管理、测试以及开机恢复功能。

### 功能

- 通过前台服务开启或关闭屏蔽悬浮窗。
- 录制、启用、禁用和管理屏蔽点。
- 在测试界面验证屏蔽效果。
- 预览当前屏幕、挖孔、铰链和已配置的屏蔽点。
- 支持包含系统栏和挖孔区域的全屏覆盖。
- 支持折叠屏显示区域和折叠姿态配置档案。
- 提供 Debug Overlay、日志导出以及 Material 3 浅色/深色主题。

预览中的圆形标记只用于视觉展示，实际命中范围是完整的方形拦截窗口；在支持的设备上，系统栏、挖孔和折叠铰链区域也会纳入覆盖范围。

### 权限

- `SYSTEM_ALERT_WINDOW` 用于悬浮窗。
- `FOREGROUND_SERVICE` 用于保持悬浮窗持续运行。
- `RECEIVE_BOOT_COMPLETED` 用于可选的开机恢复。
- `POST_NOTIFICATIONS` 用于 Android 13 及以上版本的前台服务通知。
- `INTERNET` 用于日志导出或诊断。

### 使用步骤

1. 打开应用并授予悬浮窗权限。
2. 查看屏幕预览，并在折叠设备上选择对应配置。
3. 使用 `START RECORD` 记录需要屏蔽的触摸位置。
4. 使用 `MANAGE BLOCK POINTS` 编辑、启用、禁用或删除屏蔽点。
5. 使用 `TEST BLOCKING` 验证屏蔽效果。
6. 确认配置后启动屏蔽服务。

### 备注

悬浮窗需要特殊权限，部分厂商可能会限制后台服务或悬浮窗行为。应用目标 API 为 33，支持 Android 5.0（API 21）及以上版本，版本号为 `2.0`，版本代码为 `5`。

发布 Tag：`Version2.0FullscreenCoverage&Foldable&Material3UI`
