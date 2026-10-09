package com.aizeek.phonepulse.companion

import com.aizeek.phonepulse.data.AppUsageInfo
import com.aizeek.phonepulse.data.ScreenSession

object CompanionInsights {
    private val video = setOf("com.ss.android.ugc.aweme", "com.smile.gifmaker", "com.google.android.youtube",
        "tv.danmaku.bili", "com.tencent.qqlive", "com.qiyi.video")
    private val work = setOf("com.tencent.wework", "com.alibaba.android.rimet", "com.ss.android.lark",
        "com.microsoft.teams", "com.microsoft.office.outlook")
    private val reading = setOf("com.tencent.weread", "com.dragon.read", "com.chaozh.iReaderFree", "com.amazon.kindle")

    fun describe(sessions: List<ScreenSession>, topApp: AppUsageInfo?, now: Long, hasUsageAccess: Boolean): String {
        val valid = sessions.filter { it.durationMs > 0 && it.endTime <= now }
        val on = valid.filter { it.isScreenOn }
        val quickChecks = on.count { it.startTime >= now - 3_600_000 && it.durationMs <= 120_000 }
        val longestOn = on.maxOfOrNull { it.durationMs } ?: 0L
        val longestOff = valid.filter { it.type == "SCREEN_OFF" }.maxOfOrNull { it.durationMs } ?: 0L
        val app = topApp?.takeIf { hasUsageAccess }
        return when {
            quickChecks >= 5 -> "最近一小时里，亮屏片段有些零碎。你可能在等消息？我陪你等，也陪你歇一会。"
            app != null && app.totalTimeInForegroundMs >= 20 * 60_000 && app.packageName in video ->
                "可能看了些视频？陪我看看窗外吧。"
            app != null && app.totalTimeInForegroundMs >= 20 * 60_000 && app.packageName in work ->
                "可能在忙工作？忙完了，我陪你歇一会。"
            app != null && app.totalTimeInForegroundMs >= 20 * 60_000 && app.packageName in reading ->
                "可能读得很投入？陪我歇一会吧。"
            longestOn >= 60 * 60_000 -> "今天有一段比较长的亮屏时间。可能很投入，也可能很忙，下一次休息我陪你。"
            longestOff >= 60 * 60_000 -> "今天有一段很安静的时光。可能在专心生活，我也走到了更远的地方。"
            else -> "今天的故事还在慢慢展开。无论在忙什么，都可以给自己留一点空白。"
        }
    }
}
