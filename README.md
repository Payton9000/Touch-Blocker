# Touch Blocker / 触控屏蔽器

## English

### Overview
Touch Blocker is an Android app that blocks touches at specific points using system overlay windows. It provides tools to record points, manage them, and test blocking behavior.

### Features
- Start/stop overlay blocking with a foreground service notification.
- Record touch points and manage block points.
- Test blocking behavior in a dedicated screen.
- Optional Debug Overlay to visualize block points.
- Export logs for debugging.

### Permissions
- `SYSTEM_ALERT_WINDOW` for overlay windows.
- `FOREGROUND_SERVICE` to keep overlays active.
- `INTERNET` for log export or diagnostics.

### Usage
1. Open the app and grant overlay permission.
2. Tap `START OVERLAY` to enable blocking.
3. Use `START RECORD` to capture block points.
4. Use `MANAGE BLOCK POINTS` to edit or remove points.
5. Use `TEST BLOCKING` to verify behavior.
6. Toggle `Debug Overlay` to show or hide block points.

### Debug Overlay
When enabled, the app shows visual markers for block points to help verify placement.

### Logs
Use `EXPORT LOGS` to share log files for troubleshooting.

### Notes
Overlays require special permission and may be restricted by some device vendors.

---

## 中文

### 概述
Touch Blocker 是一个通过系统悬浮窗在指定位置屏蔽点击的 Android 应用，提供记录、管理以及测试屏蔽点的功能。

### 功能
- 通过前台服务开启/关闭屏蔽悬浮窗。
- 记录触控点并管理屏蔽点。
- 在测试界面验证屏蔽效果。
- 可选的 Debug Overlay 用于显示屏蔽点位置。
- 导出日志便于排查问题。

### 权限
- `SYSTEM_ALERT_WINDOW` 用于悬浮窗。
- `FOREGROUND_SERVICE` 用于保持悬浮窗持续运行。
- `INTERNET` 用于日志导出或诊断。

### 使用步骤
1. 打开应用并授予悬浮窗权限。
2. 点击 `START OVERLAY` 开启屏蔽。
3. 点击 `START RECORD` 记录屏蔽点。
4. 使用 `MANAGE BLOCK POINTS` 编辑或删除屏蔽点。
5. 使用 `TEST BLOCKING` 验证效果。
6. 切换 `Debug Overlay` 显示或隐藏屏蔽点。

### Debug Overlay
开启后会显示屏蔽点的可视标记，方便确认位置。

### 日志
通过 `EXPORT LOGS` 导出日志文件用于排查问题。

### 备注
悬浮窗需要特殊权限，部分机型可能有限制。

