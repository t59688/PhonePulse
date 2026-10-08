# AccuBattery 2.1.8 原理复核与 PhonePulse 接入方案

分析日期：2026-10-08。范围：用户提供的 APK 静态分析、PhonePulse 当前源码、Android 官方 API 文档。本文是分析与实施设计，尚未修改业务代码或数据库。

## 1. 结论与证据边界

AccuBattery 将电池传感器读数组织成充放电会话：读取并校准电流，累计电荷量，按屏幕状态分桶，以较长充电会话的电荷量与电量百分比变化外推容量。它另有基于电压曲线的磨损模型、基于百分比变化速度的剩余时间预测、基于电池事件的目标电量提醒。这些是不同计算链路，不能统称为一个健康预测算法。

PhonePulse 已有电池广播、屏幕事件、Room 和前台服务，可复用这些入口。真正需要新增的是具有单一写入者的采样/积分管线、可恢复的会话状态、测量质量记录，以及容量与预测计算。先让计量可信，再展示健康度。

样本 SHA-256：`5503BF7D19C81EC180D07A5B25C7FA6B6D6694F3838D50AD3E84102C0B93A054`。现有 apktool 元数据为 versionName `2.1.8`、versionCode `201008`、minSdk `24`、targetSdk `35`。

本次从原 APK 重新运行 JADX 和 apktool，输出位于 `.gradle/accubattery-analysis/`。JADX 完成导出但退出码为 1，报告 3201 个反编译错误；不能将其视为完整、可重新编译的源码。以下核心方法有可读输出，深睡比例中丢失的浮点转换另用新生成的 smali 交叉核实。分析也参考了 `thridparty/accubattery-2-1-8/` 中已有的资源与 Manifest。未安装 APK、未 Hook、未做真机电流或耗电测试。

证据索引（路径相对 `thridparty/accubattery-2-1-8/jadx/sources/`；核心结论亦已对照重新导出的对应类）：

| 内容 | 文件 / 方法 |
|---|---|
| 电流 API、失败哨兵值 | `ab/V0.java`，`MediaBrowserCompatCustomActionResultReceiver()` |
| 电流 profile、采样线程 | `ab/X0.java`，`write()`、内部 `write.run()` |
| 多 provider 选择与单位校准 | `ab/W0.java`、`ab/Z.java` |
| 积分与间隔上限 | `ab/getCustomTabsNavigationAbortedPostbacks.java`，`read()`、`RemoteActionCompatParcelizer()` |
| 分桶电荷与显示倍率 | `ab/getCustomTabsWarmupUrls.java`，`RemoteActionCompatParcelizer` |
| 会话状态、深睡、容量、预测 | `ab/getCustomTabsNavigationFailedPostbacks.java` |
| 合格周期筛选 / 近五次均值 | `ab/A0.java`、`ab/D0.java` |
| 默认异常阈值 | `com/digibites/abatterysaver/core/BatterySaverApplication.java:709` |
| 长期放电统计与深睡推算 | `ab/getCustomTabsTabShownPostbacks.java` |
| 磨损曲线 | `ab/X.java`、`ab/isCustomTabsClientWarmupEnabled.java` |
| 提醒状态机 / 通知控制 | `ab/n2ExternalSyntheticLambda2.java`、`ab/n2ExternalSyntheticLambda0.java` |
| 通知栏预测调用 | `com/digibites/abatterysaver/service/NotificationUpdater.java` |
| 提醒静音操作 | `com/digibites/abatterysaver/receiver/ChargeAlarmReceiver.java` |

## 2. 对原分析的修正

| 原判断 | 重新分析 |
|---|---|
| BatteryManager 优先，sysfs 失败兜底 | 运行时使用已选定的 profile；初始化选择器同时考察多个候选，不是每次读取失败都依次遍历 sysfs。候选列表中 BatteryManager 也不是第一项。 |
| 梯形积分 | 主积分器收到 tick 时用本次电流乘上一个时间间隔；屏幕状态切换时用最近电流补部分区间。属于矩形近似，未发现该主链路使用相邻电流平均的梯形公式。 |
| 深睡按极低电流补到会话中 | 主会话以 uptime 计量，深睡电荷主要由统计层根据电量变化与校准容量推算。另一个类有固定 60 mA 补偿代码，但未找到其构造调用，且末尾有 `throw null`；不能当作主路径。 |
| 100% 后都算硬件过充 | `overchargeMah` 是软件计量项；到 100% 后仍可能处于正常充电收尾，不能据此认定硬件危险过充或等量寿命损失。 |
| 健康筛选只有“异常区间” | 默认低于设计容量 60% 或高于 125% 的周期会被排除，可由用户设置关闭对应筛选。它可能隐藏真实严重老化或校准错误。 |
| 预测不足时统一回退设计容量 | `getEstimateTo()` 的会话速率为零时直接返回无效。长期放电统计另有容量换算与默认速率，必须区分具体调用路径。 |
| ChargeAlarmReceiver 到目标触发 | 目标检测在 charge monitor 状态机；这个 Receiver 接收 `accubattery.charge-alarm.MUTE`，负责静音。 |
| 在已有 ticker 心跳读取电流 | PhonePulse ticker 只更新通知，息屏即停止；目前电池写入由广播/屏幕事件/页面刷新触发，不能承担连续计量。 |

## 3. 测量：它读到了什么

### 3.1 电流是电池端净电流

`V0` 调用 `BatteryManager.getIntProperty(2)`，即 `CURRENT_NOW`。`Integer.MIN_VALUE` 被转换成不可用错误。`X0` 读取 `CAPACITY`（属性 4），保留 elapsedRealtime 与 uptime 时间戳。

Android 约定电流为 µA，正数表示净流入电池，负数表示净流出电池。OEM 读数可能需要适配，但不能把“插着充电线”直接等同于“电流一定为正”：设备负载过高、暂停充电等情形可能出现净放电。这里测量的不是充电器输入电流，也不是 USB 功率，不能通过简单积分得到充电器效率。[Android BatteryManager](https://developer.android.com/reference/android/os/BatteryManager)

`X0` 加载 `current-provider-profile`，其中保存 provider、文件路径、符号反转与倍率。`W0` 选择期间读取多种候选，按连续同号的变化样本判断可用 provider；`Z` 使用绝对值超过 10000 的样本占比判断倍率 1 或 1000。这个启发式不能证明所有低电流场景都能校准准确，PhonePulse 不宜直接照搬。

另有 `currentDisplayMultiplier`：它同时影响当前电流显示与累计电荷的输出，资源说明用于部分双电芯机型只报告一半电流的情况。不能对所有双电芯手机自动乘 2；电流、charge counter 和设计容量必须使用同一容量口径。

设计容量来自保存值、按 `Build.DEVICE` 的机型信息、探测，且 `C4253t0` 包含反射调用 `PowerProfile.getBatteryCapacity()`。后者是内部 API，不能作为现代 Android 必然可用的公开能力。PhonePulse 首版采用用户可修改的设计容量最稳妥。

### 3.2 采样与积分是两层

`X0.write.run()` 调用 `U0(...2000L,100L)` 后重复采样，名义间隔约 2 秒；设备挂起会推迟执行。读数变化才发送电流事件；不变化时，uptime 超过 30 秒也会刷新。2 秒读取不等于每 2 秒写数据库或必定每 2 秒收到积分事件。

主会话将事件的 uptime 和校准电流送入分桶积分器。tick 计算：

```text
delta_mAs = current_uA × delta_uptime_ms × 10^-6
delta_mAh = delta_mAs / 3600
```

状态切换用最近电流补到边界，累计结果分别计入亮屏或息屏桶。充电积分器对超过 60 秒的 tick 间隔忽略该区间；放电构造分支使用很大的间隔上限。主路径仍是 uptime，并未将深睡 wall duration 当作当前电流持续时间。

积分准确性取决于硬件传感器、采样间隔、倍率与丢失区间。它通常能提供百分比之外的细节，但不能保证比系统电量计更准确；系统 fuel gauge 也可能在进行硬件库仑计量。

### 3.3 深睡主要是未覆盖区间的估算

`getDeepSleepMillis()` 返回会话时长减去 counter 累计时长。这个差值也可能包含采样未覆盖时间，因此不能无条件当作硬件深睡的精确测量。

`getDeepSleepFraction()` 的 JADX 表达式看起来像整数除法；smali 实际在两次取 long 后分别执行 `long-to-float`，然后 `div-float`，是浮点比例。

统计层用电量变化对应的电荷量与已累计亮/灭屏电荷的差值推算深睡消耗，并限制到息屏清醒速率允许的范围；长期统计则从亮屏样本标定 mAh/%，再推算待机和深睡。因此深睡 mAh 应标为估计，不能与有传感器覆盖的积分量混为一谈。

## 4. 会话与健康度

### 4.1 会话具有状态机

核心状态为 `DISCHARGING / CHARGING / PENDING_FULL / FULL / NOT_CHARGING`，不是单个 `isCharging` 布尔值。系统报告 FULL 后仍可处于 `PENDING_FULL`；校准电流达到 100000 µA 会刷新计时，低于该条件超过约 5 分钟 uptime 后才转 FULL。

这解释了“系统显示 100%，AccuBattery 仍计量”的行为。健康估算采用的 `estimateFullCapacityMah()` 是整个会话 `getPowerUsage()/deltaSOC`；`calculate100PctMah()` 则使用保存的 `to100PercentMilliAmpHours`。它们不是同一个方法。`to100` 在百分比事件更新时保存当时积分，不能简化为所有路径都严格截取第一次出现 100% 的瞬间。

### 4.2 健康估算公式与筛选

```text
session_full_mAh = session_net_mAh / (deltaSOC / 100)
actual_capacity_mAh = mean(latest up to 5 accepted session_full_mAh)
health_percent = actual_capacity_mAh / design_capacity_mAh × 100
```

`A0` 查询充电会话，时长大于 5000 ms，再筛选：电量增幅至少 60 个百分点、深睡/未计量比例不高于 10%、积分电荷非负，以及启用时的健康范围 60%–125%。`D0` 将最近最多五个合格会话的独立容量估计作算术平均并保存。不是 `sum(mAh)/sum(deltaSOC)` 的加权结果。

另外保留增幅至少 25% 的较短充电数据用于另一组结果/图表，不能把它们与进入健康均值的周期混同。官方帮助也描述了近五次、较长充电会话的规则。[AccuBattery 健康页面说明](https://accubattery.zendesk.com/hc/en-us/articles/209507189-Tab-3-battery-health-screen)

示例：20%→80% 净充入 2400 mAh，可估算为 4000 mAh；只有测量覆盖充分、倍率一致、SOC 没有跳变时才有意义。如果积分覆盖不完整，这个数不能直接作为健康度。

系统 `EXTRA_HEALTH` 的良好、过热、损坏等枚举与容量保持率应分别显示。容量保持率也不是安全诊断，不能发现所有电池损伤。

## 5. 维护：提醒、磨损模型与容量趋势

目标默认值为 80%。charge monitor 根据电池状态和当前百分比进入 `IDLE / CHARGING / TARGET_REACHED / FULL / SILENT`，控制声音、震动和通知。达到阈值通过事件检测，不依赖“预计几点到 80%”的定时器。Receiver 是通知的静音入口。普通 App 没有通用公开的停充 API，PhonePulse 能做目标提醒与状态记录，真实停充需具体 OEM 能力另行适配。

`getBatteryWear()` 调用 `X.write(startPct,endPct)`。`isCustomTabsClientWarmupEnabled` 给出一张约 3.362–4.35 V 的百分比到电压表；`X` 在 65% 以上使用以电压为自变量的指数曲线，在低区间用线性近似。概念上：

```text
F(p >= 65) = 2^(-10 × (Vmax - V(p)))
F(p < 65)  = F(65) × p/65
wear(start,end) = max(0, F(end) - F(start))
```

所以高电量区充同样的百分点会有更大的模型磨损。这个数是通用曲线给出的相对等效磨损，并不是对这块电池本次真实损失容量的测量，未直接包含这次充电的温度、电芯化学或设备实测电压。不能宣传“本次损失了多少实际寿命”。官方说明也将其描述为电压相关的模型。[AccuBattery 磨损模型说明](https://accubattery.zendesk.com/hc/en-us/articles/209507189-Tab-3-battery-health-screen)

`overchargeMah` 建议在 PhonePulse 命名为“显示 100% 后继续充入电量”；记录高温、高 SOC 停留时长更直观。容量趋势要标出换电池、设计容量调整、倍率调整、系统升级和不完整会话，避免把测量口径变化解释成老化。现有证据没有证明它能可靠预测“几个月后电池报废”。

## 6. 剩余时间预测

当前会话预测在 `getEstimateTo()` 中：

```text
rate_pct_per_hour = round(lastPct - startPct) / sessionHours
remainingHours = (targetPct - lastPct) / rate_pct_per_hour
```

速率为零或算出的时间为负则返回无效；通知栏和充电页确实调用这个方法。充电到 100% 的线性预测不充分建模后期电流下降，可能偏乐观；最初没有发生 1% 变化时会无结果。

长期放电统计另外累计亮屏 mAh 与电量变化，得到 `mAh_per_percent = sum(screenOn_mAh)/sum(screenOn_deltaPct)`；无数据时用设计容量（缺失时 3000 mAh）换算。亮屏/待机百分比速率也有 -15%/h、-10%/h 的最终兜底。它们是另一条历史统计链路，不能说所有会话预测都会自动回退。

PhonePulse 首版可显示“按当前速度约…”以及“按近期亮屏/待机习惯约…”，分别说明预测条件。充满预测后续应按 SOC 区间和插电类型积累历史充电速率，学习后段减速；不能用一个瞬时电流覆盖整个剩余过程。

## 7. PhonePulse 当前真实接入点与问题

| 文件 | 当前行为 | 接入要求 |
|---|---|---|
| `service/ScreenTrackerService.kt` | 电池/屏幕事件采集；ticker 更新通知，息屏停止 | 新增独立 sampling job；事件进入同一串行处理管线；通知刷新继续按现有节奏 |
| `data/BatteryRepository.kt` | sticky battery intent；事件写入；合并相同状态与温压噪声 | 保留低频历史，不让记录合并规则丢失高频积分数据 |
| `data/BatteryRecord.kt` | 百分比、状态、温压、屏幕状态 | 保留现有图表用途；另存容量会话与分钟汇总 |
| `data/AppDatabase.kt` | Room v2、`fallbackToDestructiveMigration()` | v2→v3 显式迁移，保留电池与屏幕历史 |
| `ui/MainViewModel.kt` | 与 service 分别创建 Repository；页面刷新也写样本 | 电流积分必须由 service 单一负责，UI 只观察结果 |
| `ui/components/BatteryStatsCard.kt` | 将系统枚举显示为“健康度” | 改为“系统电池状态”，另加估算容量保持率 |
| `data/AppBatteryUsage.kt` | 按应用前台时长占比分配亮屏百分比掉电 | 仍标明估算；设备电流不能直接提供各 UID 真实耗电 |

现有 `computeAllDrainStats()` 的速率只累积发生掉电的区间时间，忽略电量没变的区间，可能高估耗电速度；不足时使用 10.8/0.8%/h，再 clamp 到亮屏 3–35、息屏 0.1–5。不能把这些值当作可信历史直接喂给剩余时间预测。应在预测所需范围修正分母，并让缺数据返回“暂无估算”。

当前 Manifest 的 `BATTERY_STATS` 是受保护权限；声明它不会使普通安装获取系统级各 UID 计量能力。当前前台服务 targetSdk 36、类型 specialUse；接入后需要核对 subtype 描述和实际使用匹配，以及启动、通知授权与 OEM 杀后台行为。

## 8. 最小可维护实施方案

### 8.1 边界与数据流

```text
BatteryManager + battery/screen events
  → ScreenTrackerService 单一采样队列
  → BatteryAnalytics（纯 Kotlin，积分/会话/质量判定）
  → BatteryRepository / Room（checkpoint、会话、分钟汇总）
  → MainViewModel Flow → 现有电池页 / 通知
```

只为传感器读取与时钟定义小接口，便于测试；不新开第二个前台服务，也不引入额外框架。首版不依赖 sysfs 或机型云数据库。

高频采样在内存中积分；落盘以约一分钟的汇总、状态边界和会话 checkpoint 为主。5 秒一条就是每天 17280 条，因此不要把所有采样都无期限写入现有图表表。采样间隔先以充电/亮屏 5–10 秒、息屏且 CPU 醒着时 30–60 秒作为待真机验证的起点；没有保证息屏精确周期执行。前台服务不等于 CPU 永不挂起，不能为了统计常驻 WakeLock 或频繁精确闹钟。[Android 设备唤醒说明](https://developer.android.com/develop/background-work/background-tasks/awake)

### 8.2 推荐最小数据集

```text
BatterySample（内存，不必逐条入库）
  wallTimeMs, elapsedMs, uptimeMs, batteryStatus, plugged, screenOn,
  socPct, rawCurrentUa?, currentUa?, chargeCounterUah?, voltageMv?, temperatureC?,
  calibrationRevision

BatteryInterval（分钟/状态边界汇总）
  start/end, sessionId, screenOn, observedMah,
  measuredDurationMs, missingDurationMs, chargeCounterDeltaUah?, quality

PowerCycle（可恢复会话）
  id, state, start/end, start/endSOC, netMah, to100Mah?,
  measuredDurationMs, missingDurationMs, deepSleepMs,
  estimatedFullMah?, rejectionReason?, calibrationRevision, completionReason

BatterySettings / calibration
  designMah?, designCapacitySource, provider, polarity, currentScale,
  cellFactor, revision, chargeTargetPct, alarmEnabled
```

健康均值、健康百分比是派生结果，首版查询计算即可，避免新增多个相互不一致的缓存字段。checkpoint 保存最近累积值与校准版本；重启、进程死亡、读数缺失都必须标明未覆盖区间，不沿用重启前电流补整个空白。

`rawCurrentUa` 的名字仅适合已知单位的 BatteryManager 通道；若未来添加 sysfs，原始字段应改为 rawValue + unit。`cellFactor` 只在设备证据/用户校准下调整，不能仅凭“双电芯”推断。

### 8.3 采样与积分规则

公开 API 首版读取 `CURRENT_NOW` 和 `CHARGE_COUNTER`（整数属性，µAh），可选读取 `CURRENT_AVERAGE`。后者的硬件平均窗口不保证相同。`ENERGY_COUNTER` 是 long、单位 nWh，首版健康估算不需要它。[Android API 单位与缺失值](https://developer.android.com/reference/android/os/BatteryManager)

将不支持的 `Integer.MIN_VALUE` 映射成 null；如果用 getLongProperty 则处理 `Long.MIN_VALUE`。零电流可能有效，不能和不可用混同，也不能把不可用替换成 0 参与积分。

积分使用单调时钟。对连续、有效、状态一致、无长间隔的样本可采用梯形法：

```text
deltaMah = ((previousUa + currentUa)/2.0) × deltaElapsedMs / 3_600_000_000.0
```

加法先提升到 Double/Long，避免 Int 溢出。屏幕或会话状态切换时关闭旧区间并重新建立基线，不把跨状态的平均电流全部塞进一个桶。

如果 elapsed 与 uptime 差值显示 CPU 挂起，禁止把醒来后的瞬时电流乘整个深睡时间。有经过验证的 charge counter 时，可用相邻 counter 差值覆盖整段净电荷，并与已有积分核对，不能重复相加；它不能直接恢复缺失区间的各应用/亮灭屏归属。没有 counter 时记录缺失区间或估算值，健康筛选只使用充分覆盖的周期。

charge counter 也可能不支持、停更、重置或 SOC 重标定。它只适合交叉校验与候选计量源，不能将单点 `counter/(SOC/100)` 当作实测健康度，否则有将系统内部容量模型重新包装成测量的风险。

### 8.4 容量与提醒规则

首版健康建议采用 ≥60 个百分点的充电会话，而不是 40% 会话也进入同一精度等级；需要覆盖充分、无明显 SOC 跳变、无校准版本变化、容量与电量同口径、非零有效分母、有限数值。报告区分 observed / inferred 电荷，保存不合格原因。

可先用最近五个合格周期均值，但展示样本数与离散程度；一个会话只显示“初步估算”。统计离散度不等于所有系统误差的置信区间。异常容量先标记并允许排除，不应默认屏蔽所有低于设计值 60% 的数据；严重老化和错误倍率需要不同解释。

正常保养充到 80% 与容量校准需要较大 SOC 跨度存在取舍，例如 30%→80% 不够 60 点。不要为了凑样本强制日常满充满放；健康暂缺时仍能提供电流、充电量、提醒和百分比预测。

充电提醒根据实时电池状态检查 `pct >= target`，每个插电会话只提醒一次，支持静音，拔线重置；服务恢复时用持久化 session/alarm 状态避免重复。目标调整、100% 已满、已插电但暂停充电的情形需明确测试。AlarmManager 首版不是必需依赖，不能把预测时刻当真实达标时刻。

### 8.5 预测与 UI

先提供当前会话/近期窗口的百分比速率预测，分母计入所有有效持续时间，包括电量未变化的区间；数据少、状态错向、温度/负载变化很大时隐藏或标低可信度。支持 API 28+ 的 `computeChargeTimeRemaining()` 可作为“系统充满估算”，明确来源；不能用于任意目标电量或放电剩余时间。

有容量数据后再提供 `remainingMah / historicalMahPerHour`，分别输出亮屏、待机及按历史使用比例混合的情境估计。充电后期按 SOC 桶学习减速。首版不做电池寿命日期预测。

现有电池页增量显示：系统电池状态、净电流/可用性、当前会话充放电 mAh、预计到目标/充满时间、设计容量编辑、初步/稳定容量估计、合格周期数与原因、目标提醒设置。磨损模型可后置，不抢占计量基础的开发优先级。

## 9. 实施顺序与验收

| 阶段 | 交付 | 必须验证 |
|---|---|---|
| 1 | 采样、串行积分、分钟汇总、会话 checkpoint、显式 Room 迁移、电流与会话电量 UI | 单位/符号、缺失值、零值、重启断点、屏幕边界、v2 历史保留 |
| 2 | 容量估算、质量筛选、设计容量编辑、合格周期与趋势 | 60 点阈值、缺失区间、SOC 跳变、五次均值、异常与校准版本 |
| 3 | 目标提醒、当前/历史情境预测、SOC 分段充电速率 | 去重/静音/拔线、服务重启、零速率、临近满电减速、无数据降级 |
| 4 | 可选 OEM/provider 适配、双电芯校准、磨损模型 | 各机型口径、倍率切换重算或隔离、耗电开销与用户说明 |

纯算法测试例：1000000 µA 持续 3600000 ms → 1000 mAh；负电流净放电；无效值不积分；跨长空白区间不补瞬时电流；20%→80% / 2400 mAh → 4000 mAh；状态边界归属；有 counter 覆盖时不重复累计；进程恢复不重复累计；设计容量 4000、估算 3600 → 90%。

真机必须覆盖至少一台标准读数设备与目标 OEM，含息屏充电、息屏放电、充满收尾、充电暂停、快速插拔、后台被杀、设备重启、计数器不可用/异常、双电芯（若产品支持）。比较采样开启/关闭后的 CPU 唤醒、数据库增量和待机开销。现有 BatterySamplingTest 验证图表去重，新计量模块必须避免破坏这些约定。

已完成：APK 静态链路复核、关键 smali 核查、当前工程接入点评估与方案。已验证：关键公式、默认阈值、近五次均值、预测无效分支、提醒入口。未验证：传感器真机可靠性、OEM 挂起行为、准确度和新增功能耗电；本次未实现功能，也未运行应用测试/构建，因为业务代码未改。
