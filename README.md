# 微流控灌流 Android App

这是一个原生 Android 控制端工程，不依赖 WebView，因此蓝牙控制使用 Android 系统 BLE API 实现。

## 功能

- BLE UART 直连设备，使用与固件一致的 Nordic UART UUID。
- 支持 `START / PAUSE / RESUME / STOP / STATUS` 控制命令。
- 支持 HTTP 服务端刷新状态和远程下发命令。
- 单页手机 App 界面，显示设备编号、运行状态、进度、电池电压和日志。

## 编译 APK

当前工作环境没有 Android SDK/Gradle，所以这里无法直接生成 APK。安装 Android Studio 后：

```powershell
cd D:\code\abc\program_design\android_app
.\gradlew assembleDebug
```

如果当前目录还没有 `gradle/wrapper/gradle-wrapper.jar`，`gradlew.bat` 会自动退回使用系统里的 `gradle` 命令。第一次构建前也可以手动执行 `gradle wrapper` 生成标准 wrapper jar。

也可以在 Android Studio 中打开 `program_design/android_app`，等待 Gradle 同步后点击 `Build > Build APK(s)`。

生成位置通常是：

```text
program_design/android_app/app/build/outputs/apk/debug/app-debug.apk
```

## 真机注意事项

- Android 12 及以上需要授予“附近设备”权限。
- Android 11 及以下扫描 BLE 可能需要位置权限，这是系统要求。
- 远程控制默认连接 `http://127.0.0.1:8080`，真机使用时应改成电脑在同一局域网内的 IP，例如 `http://192.168.1.23:8080`。
