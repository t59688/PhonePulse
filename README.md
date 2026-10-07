# PhonePulse

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" height="120" alt="PhonePulse">
</p>

<p align="center">记录屏幕亮熄节奏，看清时间与电量都去哪了</p>

<p align="center">
  <a href="https://github.com/t59688/PhonePulse/releases"><img src="https://img.shields.io/github/v/release/t59688/PhonePulse?label=release" alt="Release"></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-lightgrey" alt="minSdk 24">
  <img src="https://img.shields.io/badge/targetSdk-36-lightgrey" alt="targetSdk 36">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue" alt="License"></a>
</p>

---

## 为什么做这个东西

做这个小工具的初衷其实非常简单：我想知道自己睡了多久。

除了睡眠，白天也常常有种被手机无声绑架的感觉：不知不觉一天就过去了，总觉得没干什么事，电量却哗哗地往下掉。如果去翻翻手机系统自带的“数字健康”或“屏幕使用时间”，却发现它们的功能实在太粗糙了：
- 它只能按天给一个模糊的总时长，根本看不出昨晚放下手机到今早醒来中间到底隔了几个小时；
- 它看不出每次拿起手机究竟看了多久，分不清自己只是瞄了半分钟回个消息，还是不知不觉连着刷了四十分钟；
- 下拉通知栏看不到当前亮屏了多久，也看不到上次熄屏过去了多久；
- 系统自带的耗电排行更是经常一头雾水，完全摸不透电量是在亮屏使用中耗掉的，还是在熄屏待机时被某个后台偷跑掉的。

为了解决这些切身的困惑，就写了 PhonePulse：把屏幕每一次点亮、每一次熄屏，记录下来。

---

## 功能说明

### 息屏间隔
从上次熄屏到再次亮屏的时长会单独记录，并在通知栏、首页展示（例如「上次息屏：7 小时」），用来对照实际睡觉或离开手机的时间。

### 通知栏计时
下拉通知里常驻显示「本次亮屏」和「上次息屏」时长；亮屏前 1 分钟内按秒刷新，之后按分钟刷新，熄屏后不再跑后台计时。

### 屏幕使用统计
- 当日亮屏、熄屏总时长
- 唤醒次数、单次亮屏平均时长
- 24 小时活跃分布

### 应用使用情况
读取系统 Usage Stats，按应用列出前台时长与启动次数，时间范围可选：今日、昨日、近 7 天、近 30 天。

### 电量与亮熄屏
展示当前电量、电压、温度；把电量采样和亮/熄屏时段对齐，生成 24 小时曲线，并分别估算亮屏使用与熄屏待机下的耗电速度（%/h），方便排查待机异常掉电。

---

## 关于功耗与设计的克制

做常驻后台的监控工具，最忌讳的就是“为了监测电量，自己反而成了耗电大户”。PhonePulse 在设计上有几个原则：

1. **绝不盲目轮询刷新**  
通知栏采用了自适应的低功耗刷新：
- 亮屏刚开始的不满 1 分钟内，为了秒级计时的平滑视觉反馈，按秒对齐更新；
- 满 1 分钟后，因为单位自动进阶为“分”，系统会自动降频到每 60 秒整点刷新一次，不浪费无意义的系统调用；
- 只要屏幕一熄灭，后台计时协程立即彻底销毁，不持任何唤醒锁（WakeLock），让手机正常进入系统的深度睡眠（Doze）模式。

2. **单一时间单位，干净利落**  
通知栏不使用混杂的复合时间格式（不显示“2分15秒”），而是智能进位：不满 60 秒显示“XX秒”，达到 1 分钟显示“XX分”，整小时显示“XX小时”，只保留单一单位，一目了然。

3. **100% 离线，纯净本地隐私**  
所有的屏幕会话记录、电量快照数据，全部存放在手机本地的 SQLite 数据库（Room 框架）中。没有第三方统计分析 SDK，没有云端数据上传，没有多余账号。只有在主动检查更新时，才会去请求官方 GitHub Releases。

---

## 界面概览

应用分为五个主要板块：
- **实时概览**：实时亮熄指示卡片、全天关键指标便签、24小时使用分布图与历史流。
- **应用活跃**：各 App 前台运行时间排行与占比，支持多日周期切换。
- **电量统计**：实时电池状态、24小时电量走向图与充放电速率分析。
- **状态明细**：完整的亮屏与熄屏历史列表，记录开始时刻、结束时刻与持续时间。
- **系统设置**：版本更新检测、防杀保活健康分诊断、核心权限配置以及各品牌手机的防杀后台指南。首页顶栏保持绝对清爽，有新版本时仅在底栏“系统设置”显示红点提示。

---

## 技术架构

- 开发语言：Kotlin
- UI 框架：Jetpack Compose + Material 3（高对比度深色模式）
- 异步框架：Coroutines + StateFlow 响应式数据流
- 本地存储：Jetpack Room（SQLite）+ KSP
- 后台机制：Foreground Service（specialUse 类型）+ BroadcastReceiver
- 系统版本：支持 Android 7.0（API 24）及以上，目标适配 Android 16（API 36）

代码工程目录：

```
app/src/main/java/com/aizeek/phonepulse/
├── data/       本地 Room 数据库、DAO 访问层与数据仓库
├── receiver/   开机启动与系统广播监听
├── service/    屏幕状态监听服务、自适应通知与状态缓存
├── ui/         Compose 页面、组件、主题与 ViewModel
├── update/     GitHub Releases 检查与安全安装流程
└── util/       时间换算与系统保活辅助工具
```

---

## 本地构建与开发

编译环境要求：JDK 17 或 21，Android SDK（API 36）。

```bash
# 编译 Debug 安装包
./gradlew assembleDebug          # macOS / Linux
.\gradlew.bat assembleDebug      # Windows

# 选模拟器 / 设备并安装运行（Windows）
.\dev.bat

# 签名 Release（需配置 .env.android.local）
.\build-release.bat

# 运行单元测试
.\gradlew.bat testDebugUnitTest
```

Release 签名可在本地 `.env.android.local` 文件或系统环境变量中配置：
`KEYSTORE_PATH`、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
如果推送到 GitHub，也可以通过打 `v*` 格式的 tag 触发 GitHub Actions 自动完成 Release 构建。

---

## 权限说明

为了在熄屏和锁屏状态下依然能够精准捕捉状态切换，应用声明了以下必要权限：

| 权限名称 | 用途说明 |
| --- | --- |
| `FOREGROUND_SERVICE` / `SPECIAL_USE` | 运行前台常驻服务，保障状态监听不被系统随意杀后台 |
| `POST_NOTIFICATIONS` | 在通知栏展示常驻的亮熄计时状态（Android 13+ 必需） |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 申请加入电池优化白名单，防止熄屏深睡时广播被系统冻结 |
| `PACKAGE_USAGE_STATS` | 获取各应用前台活跃时长，用于生成应用使用榜单 |
| `RECEIVE_BOOT_COMPLETED` | 手机重启后自动恢复屏幕追踪服务，无需每次手动打开 |
| `INTERNET` | 仅在系统设置中检测或下载 GitHub Releases 的新版本时使用 |
| `REQUEST_INSTALL_PACKAGES` | 允许应用下载新版本后拉起系统安装器完成自更新 |

---

## 后台保活建议

在部分后台管控严格的定制系统（如 HyperOS、OriginOS、ColorOS、HarmonyOS 等）上，长时间熄屏待机后记录可能会被系统中断。建议在应用内“系统设置”页面参考提示进行配置：
1. 确保保活诊断健康分达到满分；
2. 在系统多任务界面中，将 PhonePulse 应用卡片进行“加锁”；
3. 在手机系统设置的“应用管理”中，允许本应用“自启动”并关闭“省电策略限制”。

---

## 参与贡献

欢迎提交 Issue 反馈问题或建议。如果想贡献代码：
1. Fork 本仓库并新建分支；
2. 提交修改并保证通过单元测试（`.\gradlew.bat testDebugUnitTest`）；
3. 提交 Pull Request 即可。

---

## 开源许可

本项目遵循 [Apache License 2.0](LICENSE) 协议开放源代码。
