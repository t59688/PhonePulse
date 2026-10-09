# 森林兔子重构实施计划

**Goal:** 实现 SVG 兔子、可扩展多收获旅行、从零建屋、来信风景朋友和装扮。

**Architecture:** 沿用 companion Repository/Store 与 Compose。内容定义独立于已收到事件，结算与家园操作使用纯函数，界面和分享共用分层静态渲染。

**Tech Stack:** Kotlin、Compose、现有 JSON 本地存储、Android Canvas/VectorDrawable，不新增生产依赖。

**Spec:** ../specs/2026-10-09-forest-rabbit-redesign.md

用户于 2026-10-09 明确要求无需保留旧陪伴数据并开始开发。本计划由当前会话直接执行，不增加确认环节。使用新陪伴存储名称；屏幕、电量和应用记录不重置。不自动提交或推送。

## 全局约束与检查重点

- 真实熄屏记录结算，10 分钟门槛、实体礼物每日 5 次，单次建设计入最多 180 分钟、每日最多 240 分钟。
- 信件和朋友历史独立于最近 60 条旅行，重复结算不可重复发奖。
- 建造明确消费材料；重复点击、存储失败、取消试穿保持状态正确。
- 初始帽和斗篷可以卸下；同一家具不得重复布置；建造前不显示已建屋。
- 不增加后台定时、唤醒锁或网络任务；静置无无限动画。

## 任务

- [x] 1. `ForestJourneyTest.kt`：先验证短休息无扣分、首休息多收获、旅途故事、建设、每日上限、结算去重、收藏历史留存；运行看到失败。
- [x] 2. `ForestWorld.kt`：地点/事件/朋友/明信片内容目录和选择；`ForestHome.kt`：家园阶段、材料、建设、摆放纯函数。扩展 `CompanionModels.kt`、`CompanionRules.kt`；测试通过。
- [x] 3. `CompanionStore.kt`：新存储 v2，保存家园/来信/朋友/旅程收获；`CompanionRepository.kt`、`MainViewModel.kt`：建设/布置/阅读/装扮动作；新增原子保存和 JSON 往返测试。
- [x] 4. 从原 SVG 转换身体、帽子、斗篷资源；`RabbitArtwork.kt` 共用图层；`ForestArtwork.kt` 家园和风景画面；`CompanionArtwork.kt` 接入并保持既有物品绘图可用。
- [x] 5. 重构 `CompanionScreen.kt` 为家园/旅册/行囊，加入室内、建设预览、明信片正反面、朋友、换装预览、家具位置；更新 `CompanionPrompts.kt`、归来摘要和 MainScreen 接入。
- [x] 6. `CompanionShare.kt` 支持收到的明信片与新家园分享；更新对应 UI 行为测试（不保留已移除玩法的旧断言）。运行完整测试、assembleDebug、lintDebug，模拟器查看效果。

## 执行记录

- 已阅读现有模块与 SVG；初始分支 main，建立 codex/forest-rabbit。
- Ruling: 用户最新指令覆盖旧数据迁移要求及技能分阶段确认要求，直接按已审阅设计开发。
- 验证命令：`gradlew.bat -I .gradle/verify.gradle testDebugUnitTest assembleDebug lintDebug`，必要时复用本地 JDK 回环兼容参数。

- 已完成：新 v2 状态、旅行多收获、6 地点/24 趣闻/12 明信片画面、3 朋友、22 件物品、五阶段家园、分层 SVG 兔子、换装/家具/来信/分享。
- 独立审查：颜色格式、物品绘图坐标和缺失的展示柜可视区域均已修复，复核未发现新阻断问题。
- 测试：UI 重构前 3 项交互测试失败，重构后通过；Native 书脊像素测试先失败，修正坐标后通过。
- 最近完整检查：201 项单元测试、3 项 Android 仪器测试、assembleDebug、lintDebug 通过。截图检查发现场景操作栏遮住兔子脚部，已上移兔子；离开室内到其他页时重置室内状态，最终完整回归已通过。
- 未验证：真机长时间离屏、跨厂商后台限制和整夜功耗对比。

- 最终交付记录：`docs/forest-rabbit-delivery-2026-10-09.md`；Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。
