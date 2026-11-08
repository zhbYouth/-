# 吐纳

呼吸练习应用（tǔ nà）。同一份代码可以当网页用、装到手机主屏幕，也可以打包成 Android 安装包。界面自动跟随系统深浅色。

## 安装

**Android 安装包（APK）**
仓库的 [Releases](../../releases/tag/latest) 页面里下载 `tuna.apk`，在手机上打开安装。首次安装需要允许「安装未知来源应用」。这是调试签名的测试版，适合自己使用，不能直接上架应用商店。

**网页版 / 添加到主屏幕**
开启 GitHub Pages 后访问 `https://zhbyouth.github.io/-/`，用手机浏览器选择「添加到主屏幕」即可像 App 一样使用，离线也能打开。

## 功能

- 7 种呼吸方式：箱式呼吸、4-7-8、共振呼吸、生理叹息、4-6 放松、唤醒呼吸，以及可自定义吸 / 屏 / 呼 / 停秒数的节奏
- 练习界面：呼吸光球随节奏缩放，显示阶段、倒数秒数、第几轮和剩余时间，可暂停
- 提示音与中文语音引导（可单独开关）
- 时长 1 / 3 / 5 / 10 分钟，按完整一轮结束
- 首页按时间段推荐，可按放松 / 专注 / 助眠 / 活力筛选
- 连续天数与最近 7 天练习时长，记录只保存在本机（localStorage）

## 自己构建

```bash
npm install
npm run build:web      # 把网页文件整理到 www/
npx cap sync android   # 同步到 Android 项目
cd android && ./gradlew assembleDebug
# APK 输出：android/app/build/outputs/apk/debug/app-debug.apk
```

构建 APK 需要 Node 18+、JDK 17 和 Android SDK。推送到 `main` 后，GitHub Actions 会自动完成构建并更新 Releases 里的安装包。

## 目录

| 路径 | 说明 |
| --- | --- |
| `index.html` | 整个应用（页面、样式、脚本） |
| `manifest.webmanifest`、`sw.js`、`icons/` | 添加到主屏幕和离线使用 |
| `android/`、`capacitor.config.json` | Android 项目（Capacitor） |
| `.github/workflows/` | 自动打包 APK、发布网页版 |

## 提示

练习中出现头晕、手脚发麻时，请恢复自然呼吸并停止。孕期或有心肺疾病时，练习前先咨询医生。
