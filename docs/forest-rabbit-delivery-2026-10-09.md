# 森林兔子重构交付

## 已完成

- 使用 `thridparty/04_taro_explorer.svg` 的 33 个原始图形，按身体、脸、帽子、斗篷分层绘制。`tools/convert_rabbit.py` 可重现生成结果，SVG 三位颜色转换为 Android 颜色，不引入生产依赖。
- 家园从空地开始，依次搭营地、地基、木屋、温暖的家。确定的休息进度换成建材；先预览再建设，材料不足不可建设，同阶段重复请求不会多扣材料。
- 旅行一次保存多类收获：故事、明信片、风景、物品、朋友与建材。首发 6 个地点、24 段独立趣闻、12 种风景画面、3 位可重逢/来访的朋友、22 件物品（含初始帽和斗篷）。内容定义与纯结算规则分离，可以继续添加内容。
- 家园 / 旅册 / 行囊三页，信箱可以翻明信片读背面；室内可摆家具、展示纪念物、装框挂风景。换装先试穿，取消不写入；屋顶颜色先预览，保存后生效。
- 家园与明信片分享复用同一绘图，导出真实收到的卡片正文。分享文件保持独立，不覆盖之前接收端的图片。
- 独立 `forest_rabbit_v2` 状态，不读取旧陪伴数据。原屏幕、电量和使用记录的存储与服务逻辑没有重构。
- 每日任务领奖从用户界面和主动提示中移除，无签到、掉级、饥饿或错过惩罚。不增加后台轮询、定时投递、网络生成、服务或唤醒锁。

## 已验证

最终命令（本机附加了已存在的 `.gradle/java-loopback-patch` JDK 回环兼容参数，并通过现有 verify.gradle 使用本地 Robolectric 依赖）：

```powershell
.\gradlew.bat -I .gradle/verify.gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.aizeek.phonepulse.ForestArtworkInstrumentedTest,com.aizeek.phonepulse.CompanionShareInstrumentedTest'
```

- 201 项单元测试通过，覆盖规则、建造消费/重复请求/保存失败、装扮取消、独立状态、JSON 往返、收藏不受近期旅程裁剪影响、旧有电量与更新逻辑。
- 3 项模拟器仪器测试通过：所有物品有真实像素，书本底部书脊存在；SVG 兔子与五个家园阶段在 Android 原生 Canvas 正常绘制；分享 content URI 可实际读取。
- Debug APK 构建成功，lint 无 Error（有项目警告与绘图 KTX 风格建议，未以抑制规则隐藏）。`git diff --check` 通过。
- 独立审查并复核通过，修复 SVG 色值格式、物品右下坐标和展示柜无可见结果三项问题。
- 模拟器查看初始空地、已建木屋、室内布置、朋友记录、明信片正反面与页面切换。已建家园的检查使用临时样例状态，不注入生产默认值。
- 截图检查发现兔子被场景按钮遮住脚部，已上移角色；离开室内后切换旅册不再保留室内标题。上述修改已纳入最终完整回归。

## 未验证

未做真机数小时/整夜功耗对比、多厂商后台限制与长期内容更新测试。服务漏记的熄屏区间不会被虚构为旅行。故事和朋友是本地编写的森林世界；内容可扩展，不承诺无限联网生成。

模拟器家园页静置 15 秒，在重置 gfxinfo 后新增渲染帧为 0；这只证明没有持续重绘，不等于整机功耗基准。临时样例状态已恢复：家园为 CLEARING，来信和朋友均为空，仅保留初始帽与斗篷。

Debug 安装包：`app/build/outputs/apk/debug/app-debug.apk`。

改动留在 `codex/forest-rabbit` 分支，未提交、推送或发布。
