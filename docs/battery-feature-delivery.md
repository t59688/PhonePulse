# 电池分析与养护功能交付记录

## 已完成

沿用现有 Compose 电量页、MainViewModel、Flow、Room 与 ScreenTrackerService，新增电流监测、净电荷积分、可恢复充放电会话、容量保持率、容量趋势、记录排除、情境剩余时间预测、充电目标提醒与校准设置。没有新增生产依赖。

关键实现位置：

- `battery/BatteryTelemetrySource.kt`：公开 BatteryManager 和电池广播读数，未知值保留为 null。
- `battery/BatteryAnalytics.kt`：净电流积分、计数器验证、亮灭屏分桶、长缺口/休眠处理、会话恢复、合格容量估算。
- `battery/BatteryEstimates.kt`：最近五个合格容量样本、离散程度、mAh/SOC 历史预测和充电 SOC 分段预测。
- `battery/BatteryMonitorRepository.kt`：单一状态源、串行事务、分钟 checkpoint、校准隔离、排除和提醒持久化。
- `battery/BatteryChargeAlarm.kt`：独立通知频道、目标提示、通知静音动作；兼容 API 24。
- `ui/components/BatteryMonitorCards.kt`：实时、健康/趋势、养护/预测、会话详情，适配既有卡片风格。

## 测量规则

电流和电荷计数器分别按自身单位校准，电流倍率不改变公开计数器的固定 µAh 单位。保留电池净流入/流出的符号，不把充电器输入功率当成电池充入电荷。相邻连续清醒采样最多跨 60 秒；长缺口和 CPU 挂起只能由通过验证的计数器补充，不能用最后一个瞬时电流填满。

电流与计数器都不可用时，连续清醒的 SOC 观测单独标记为 SOC_ONLY，可支持百分比预测，不能计入 mAh 或健康测量覆盖。静止或异常计数器、SOC 跳变、校准变化和不完整恢复均有明确处理；状态切换前的区间归属原会话，事件读数在入队时即捕获，避免快速插拔被合并。

健康估算需要充电 SOC 增幅至少 60 个百分点、有效测量覆盖至少 90%、正向净电荷及无前缀异常。首次 100% 与确认充电终止分别记录：SOC 100%、系统 FULL、净电流绝对值 ≤100 mA 的连续有效观测达到五分钟后，冻结合格容量前缀；后续维护充电、休眠或重启不污染该前缀。未确认终止时使用拔线边界处的合格充电量。

健康显示最近五个合格样本的平均容量/设计容量，保留超过 100% 的估算结果并提示核对口径。用户可排除异常记录。修改电流符号、倍率或电芯口径会切开并隔离不同校准版本；单独修改设计容量只重新计算比率。

预测优先使用合格实测容量与历史 mAh 速率，缺少该证据时使用真实连续 SOC 变化；至少十分钟、两个百分点的有效观测才形成速率。充电历史按插电类型和 SOC 十个百分点分段。没有足够数据时显示“暂无可靠估算”，不使用假定默认耗电率。

## 数据兼容与运行

Room 升至 v3，显式注册 v1→v2→v3 迁移，保留原屏幕会话和电量记录，没有破坏性重建。新增表索引和导出的 v3 schema。分钟区间保留 30 天，已结束周期保留 365 天；活跃周期不清除，UI/预测查询有数量上限。

沿用一个前台服务，亮屏或充电每五秒采样，息屏非充电时每三十秒采样；不持有 WakeLock、不使用精确闹钟。系统休眠会自然延迟执行，覆盖率按真实采样证据记录。分钟落盘和服务结束 checkpoint 限制写入量；进程被直接杀死可能丢失最后不足一分钟的未落盘测量，恢复时显式记录缺口。

提醒按充电会话去重，持久化静音；更改目标或重新启用可重新评估。发送通知、静音及设置变化经过同一串行状态边界，通知失败可重试。普通应用只提醒拔线，不硬件停止充电。

## 已验证

2026-10-08，执行完整 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`：

- 102 个测试全部通过，无失败、无错误；包括算法、预测、历史率、Room v1/v2 升级迁移、Repository、提醒 API 24/36、Compose UI，以及已有回归测试。
- lint：0 errors、66 warnings、1 hint。剩余报告包含已有 API 弃用、依赖更新建议、既有 Locale/资源等警告，没有为通过检查降低规则。
- debug APK 成功构建，并使用覆盖安装在 API 37 模拟器运行。
- 模拟器人工冒烟：电池页实时读数/覆盖率/未知健康状态；设计容量设置保存；79→80% 充电目标通知；本次静音后 81% 不重复提醒；切换充放电后生成会话。检查 AndroidRuntime/BatteryMonitor 错误日志未见崩溃；电池模拟已执行 `dumpsys battery reset`。
- 独立复审发现的计数器停更、通知竞争、事件合并和趋势筛选不一致均已修复。
- `git diff --check` 通过。

常规验证命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

本机 JDK 的 Unix socket 临时目录问题使用 WindowsSelectorProvider/短临时路径环境选项规避；Robolectric 运行包缓存到被忽略的 `.gradle/robolectric`，并通过本地验证 init script 使用。这些属于本机验证环境，没有进入应用依赖或运行逻辑。

构建产物：`app/build/outputs/apk/debug/app-debug.apk`。测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`。lint：`app/build/reports/lint-results-debug.html`。

## 未验证边界

尚未进行 OEM 真机长期采样、休眠、传感器单位/符号/双电芯口径、容量误差及采样耗电测试。软件估算不能保证电池物理容量精度，也不能据 100% 后净电荷断言危险过充。

本次交付 debug 验证包，未制作或发布生产签名 release 包；既有 release 构建要求发布密钥环境配置。生产签名、R8 release 运行验证和上述 OEM 实机矩阵应在正式发布前完成。没有修改签名配置、版本号或发布流程。
