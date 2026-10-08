# 最新电池功能精度审查

后续修复状态与数据口径见 [电池数据真实性修复](battery-data-truth-fixes-2026-10-08.md)。本文保留原始审查基线及缺陷复现，不代表修复后仍存在这些缺陷。

审查基线：提交 `12851f1`，以及工作区尚未提交的 BatteryMonitorCards / BatteryLevelChart / BatteryScreen UI 改动。只读审查业务代码；复现用例放在被忽略的 `.gradle/battery-accuracy-audit`，没有改动用户 UI 或测量实现。

结论：基础测量架构和数据缺失处理具备合理设计，但当前仍有可复现的计量与预测缺陷。不能称为已达到高精度，也没有证据证明测量精度超过 AccuBattery。比较范围为此前静态核查的 AccuBattery 2.1.8 APK；没有审查其所有后续版本，也没有进行双应用真机准确度对照。

## 发现

### P1：延迟计数器更新与电流积分交替，重复累计电荷

位置：`battery/BatteryAnalytics.kt:40–47`、`:166–176`。

恒定 360 mA，5 秒采样：charge counter 从 1000000 µAh，5 秒后仍为 1000000，10 秒时更新为 1001000。第一段零 counter 被拒绝并以电流积分计入 0.5 mAh；第二段接受 counter 的 1 mAh 跳变，实际跳变包括此前已积分的半段。十秒真实电荷是 1 mAh，当前累计 1.5 mAh。

这个输入不要求传感器损坏，只要求计数器更新/量化节奏慢于采样。错误可影响充放电电荷、容量健康及后续 mAh 预测；“测量覆盖 100%”也不能排除该误差。50% 是此复现序列的偏差，不代表所有真机都会出现相同幅度。

建议：对可信计数器建立明确锚点与覆盖窗口；窗口内已有电流积分需对账或替换，不能把延迟跳变全部当成最新一段。也可在一个稳定测量窗口固定主计量源，把另一个通道作为交叉校验。增加延迟更新、量化、停更后恢复和双通道切换回归。

### P2：mAh 分段优先级覆盖真实 SOC 历史，扭曲充电预测

位置：`battery/BatteryEstimates.kt:54–64`、`:109–121`。

已接受容量为 4000 mAh，历史 80→90% 实际持续一小时，净充入 600 mAh。SOC 分桶得到 10%/h，但 mAh 分桶用全容量统一换算成 15%/h，并优先使用后者。当前同类型充电器从 80% 到目标 90% 被预测为 40 分钟，而历史直接观测是一小时。

在系统 SOC 与真实电荷非线性或存在区间偏差时，增加容量信息反而会覆盖更直接的实测区间证据。这并不证明未来每次必须恰好一小时；说明当前算法没有保留其声称学习的区间充电速度。

建议：预测目标为系统百分比时，优先使用质量合格的该 SOC 区间实际 deltaSOC/时长；mAh 模型需要学习局部 mAh/百分点，而不是每个区间一律用 fullCapacity/100。

### P2：暂停充电仍显示无条件完成倒计时

位置：`battery/BatteryEstimates.kt:44–45`；UI `BatteryMonitorCards.kt:767–789`。

采样已经含系统状态，但预测仅判断 plugged 和会话 charging（由插电定义）。当 plugged=true、status=BATTERY_STATUS_NOT_CHARGING、current=0、SOC=80，历史有速度时仍给出 40 分钟到 90%。温控暂停、系统充电保护或自适应充电都可能没有可知的恢复时刻。

建议：区分插电/正在充电/暂停/确认满充。暂停时显示状态并隐藏无条件倒计时；若保留历史速度预测，必须明确它是“恢复充电后”的情境值。提醒达到目标本身仍可按插电和 SOC 判断。

### P2：最新 UI 将未测得的亮/熄屏电荷显示为零

位置：`ui/components/BatteryMonitorCards.kt:351–375`。

两个 tile 无条件格式化 screenOnMah/screenOffMah。刚建立会话或完全没有电流/counter 覆盖时，两者默认 0，被显示为 0 mAh；同页“已测净电荷”却正确显示暂无有效测量。一个桶没有覆盖、另一个桶有测量时也无法区分未知与实测零。

熄屏 tile 又标为“后台休眠待机净电荷”，但 screenOffMah 包含熄屏清醒以及有计数器覆盖的挂起区间，不是单独的深睡测量；没有计数器时深睡会被标缺失。

建议：记录或派生每个桶的测量时长/覆盖率；未知显示“暂无有效测量”，实测零保留 0；将熄屏量称为“熄屏已测净电荷”，明确未覆盖休眠区间。

## 验证证据

本次运行既有全套测试及三个临时审查用例：105 项，102 通过、3 失败。三个失败正对应上述前三项，断言输出：

```text
delayedCounterMustNotDuplicateAlreadyIntegratedCharge:
  expected 1.0 mAh, actual 1.5 mAh
learnedSocBinShouldRetainObservedChargingDuration:
  expected 3600000 ms, actual 2400000 ms
pausedChargingCannotGiveAnUnqualifiedCompletionCountdown:
  expected null, actual 2400000 ms
```

本机复现命令（init scripts 位于忽略目录，不影响常规构建）：

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain `
  -I .gradle/verify.gradle -I .gradle/battery-accuracy-audit.gradle
```

本机依旧需要前次交付记录中的 JDK WindowsSelectorProvider 环境选项。现有测试通过只证明被覆盖的输入行为，不能证明传感器精度、SOC 模型或实机准确度。

## 与 AccuBattery 的比较

| 维度 | 当前 PhonePulse | 比较判断 |
|---|---|---|
| 健康均值 | ≥60 个百分点、≥90% 时间覆盖、近五次均值 | 主要思路相当，没有独立准确度优势证据 |
| 测量来源 | BatteryManager、手动符号/倍率/电芯换算 | 2.1.8 有多 provider 选择与校准，当前兼容范围不能称领先 |
| 采样 | 充电/亮屏名义 5 秒，息屏非充电 30 秒 | 2.1.8 名义 2 秒读取；采样密度和积分方式本身均不能证明更准 |
| 缺失与质量表达 | 显式缺失、覆盖、排除、校准版本、样本离散度 | 信息透明是优势方向；当前 UI 桶零值和计量切换尚有缺陷 |
| 预测 | SOC 区间/插电类型历史与 mAh 情境模型 | 设计有潜力优于 2.1.8 的部分会话线性预测，但现有错误和无误差评估阻止领先结论 |
| 提醒 | 目标事件检测、去重、静音 | 核心能力相当；两者普通安装都不等于硬件停充 |
| 容量精度 | 没有真机参考容量对照 | 无法判定超过 AccuBattery |

官方资料核对：[Android BatteryManager](https://developer.android.com/reference/android/os/BatteryManager)、[AccuBattery 健康规则](https://accubattery.zendesk.com/hc/en-us/articles/209507189-Tab-3-battery-health-screen)、[AccuBattery 手动基准说明](https://accubattery.zendesk.com/hc/en-us/articles/213575425-How-to-manually-benchmark-your-battery-health)。Android 保证公开属性的单位与符号，不保证 OEM 计数器的刷新节奏。AccuBattery 官方也明确部分充电外推会受设备电量计与历史数量影响。

优先顺序：修复重复计量 → 修复预测状态与区间模型 → 修正 UI 未知值 → 做目标 OEM 真机对照。若要证明领先，需要同一设备/条件下比较容量偏差、预测绝对误差和不可用率，以及 CPU/待机开销。参考容量应来自可靠电池端计量或受控检测，USB 输入端电量不是电池净容量的直接真值；重复样本一致也不能排除相同的系统误差。
