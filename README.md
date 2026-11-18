# 华水信息流（NCWU Flow）

面向华北水利水电大学学生的 Kotlin / Jetpack Compose 信息聚合应用：把「第二课堂」和「学科竞赛」的常用信息收拢到一个 MD3 界面里，并在后台帮你盯新消息。

[![GitHub release](https://img.shields.io/github/v/release/SuperMite233/NCWU-Flow?label=release)](https://github.com/SuperMite233/NCWU-Flow/releases/latest)
[![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3ddc84)](#下载与安装)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7f52ff)](#构建)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 功能特性

**第二课堂**

- 活动报名：拉取当前「报名中」活动，展示活动时间、主办单位、名额等详情，支持在 App 内直接确认报名。
- 活动验证：内置学校官方验证页面，可用相机扫描活动二维码，也可从 Excel / CSV 批量导入验证码（按当前学号匹配）。
- 成绩单与已报活动：分页浏览已报活动与成绩，单页最多 10 条。
- 对于需要选座、任务或自定义表单的活动，可在 App 内嵌的学校原始页面中完成。

**学科竞赛**

- 解析竞赛管理系统完整时间轴，展示承办学院、报名起止日期与当前状态。
- 列表默认隐藏结束超过 7 天的竞赛，支持按截止日期「近 → 远 / 远 → 近」排序。
- 竞赛消息独立查看。

**后台提醒**

- 可开关的 WorkManager 周期扫描，可选 15 分钟、30 分钟、1 小时、3 小时、6 小时。
- 扫描范围可选全部、仅第二课堂、仅竞赛。
- 首次扫描只建立基线，之后仅对新增信息发送系统通知，避免一次性刷屏。

**登录与隐私**

- 两种登录方式：学校官方 WebView 手动登录，或在本机用 Android Keystore 加密保存凭证以支持启动、后台扫描及登录态失效后的自动续登。
- 密码不会进入源码、日志或普通明文配置；设置页可一键删除本机加密凭证并清除全部校园网站 Cookie。
- 应用仅申请网络访问与 Android 13+ 通知权限。

## 下载与安装

安装包发布在 [Releases](https://github.com/SuperMite233/NCWU-Flow/releases/latest) 页面：

1. 下载最新的 `NCWU-Flow-*.apk`。
2. 在手机上允许「安装未知来源应用」后完成安装。
3. 首次打开后按提示选择登录方式（推荐先试学校官方 WebView 登录）。

> 当前安装包使用 Android 调试证书签名，用于个人分发与测试；升级安装需与已安装版本保持同一签名。

## 构建

项目自带 Gradle Wrapper，使用 Android Studio 直接打开，或在 JDK 17 环境下执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

完整校验（单元测试 + Lint + 打包）：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

需要 `local.properties` 指向本机 Android SDK，该文件不会提交到仓库。

## 项目结构

```
.
├─ app/
│  ├─ build.gradle.kts            # 版本号、依赖（Compose BOM / OkHttp / Jsoup / WorkManager）
│  └─ src/
│     ├─ main/java/cn/edu/ncwu/flow/
│     │  ├─ MainActivity.kt        # Compose 页面、三栏导航、设置与安全 WebView
│     │  ├─ MainViewModel.kt       # 页面状态与业务编排
│     │  ├─ background/            # BackgroundScanner：周期扫描、基线与通知
│     │  ├─ data/                  # 校园页请求、解析、凭证加密、验证码导入
│     │  ├─ model/                 # 数据模型
│     │  └─ ui/theme/              # 校园蓝 MD3 主题
│     └─ test/                     # CampusParser 解析单元测试
├─ docs/design/                   # 功能设计文档
├─ gradle/                        # Gradle Wrapper
└─ README.md
```

## 数据与限制

- 学校没有公开接口，本项目依据 2026-09-05 的实际网页结构解析。若学校调整 HTML、字段名或登录流程，需要同步更新 `CampusParser` / `CampusRepository`。
- 后台扫描受 Android 调度约束，15 分钟是系统允许的最小周期，实际执行时间可能因省电策略延后。
- 手动登录模式的扫描依赖仍然有效的学校 Cookie；「本机保存密码」模式可在会话过期后自动续登，密码错误或需要验证码时会明确提示。

## 免责声明

本项目为个人学习用途的第三方客户端，与华北水利水电大学官方无关。请自行评估账号与数据风险，不要将本机保存的凭证分享给他人。

## License

本项目基于 [MIT License](LICENSE) 开源，Copyright (c) 2026 SuperMite233。

MIT 许可仅覆盖本仓库的代码；使用本软件访问学校系统时，仍需遵守学校关于信息系统使用、账号管理与数据安全的相关规定。
