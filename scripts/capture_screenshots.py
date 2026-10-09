#!/usr/bin/env python3
"""Capture PhonePulse business screens via adb into ./screenshots."""
from __future__ import annotations

import re
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "screenshots"
OUT.mkdir(parents=True, exist_ok=True)

# Emulator 1280x2856 @480dpi — bottom NavigationBar tab centers
NAV = {
    "overview": (118, 2664),
    "app_usage": (378, 2664),
    "battery": (639, 2664),
    "history": (900, 2664),
    "settings": (1161, 2664),
}

SKIP_LABELS = {
    "实时概览",
    "应用活跃",
    "电量统计",
    "状态明细",
    "系统设置",
    "今日",
    "昨日",
    "近7天",
    "近30天",
    "近 24 小时",
    "刷新",
    "应用详情",
    "返回列表",
    "搜索系统应用...",
    "搜索系统应用",
    "实时耗电",
    "电池健康",
    "充电提醒",
    "充放记录",
    "放电 / 使用",
    "充电",
    "安静陪伴",
    "家园",
    "旅册",
    "行囊",
}


def run(args: list[str], check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, check=check, text=True, capture_output=True)


def adb(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return run(["adb", *args], check=check)


def tap(x: int, y: int, wait: float = 0.9) -> None:
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def swipe_up() -> None:
    adb("shell", "input", "swipe", "640", "2100", "640", "900", "350")
    time.sleep(0.7)


def swipe_down() -> None:
    adb("shell", "input", "swipe", "640", "900", "640", "2200", "250")
    time.sleep(0.5)


def wake_display() -> None:
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP", check=False)
    adb("shell", "wm", "dismiss-keyguard", check=False)
    time.sleep(0.35)


def capture(name: str) -> None:
    """Capture via exec-out to avoid stale/blank remote screencap files on Windows."""
    path = OUT / f"{name}.png"
    wake_display()
    if not focused_on_phonepulse():
        raise RuntimeError(f"refusing to capture {name}: PhonePulse not in foreground")
    data = b""
    for _ in range(3):
        result = subprocess.run(
            ["adb", "exec-out", "screencap", "-p"],
            check=True,
            capture_output=True,
        )
        data = result.stdout
        # Some Windows adb builds corrupt LF→CRLF in the PNG stream.
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            data = data.replace(b"\r\n", b"\n")
        if data.startswith(b"\x89PNG") and len(data) >= 50_000:
            path.write_bytes(data)
            print(f"  OK  {name}  ({path.stat().st_size:,} bytes)")
            return
        time.sleep(0.6)
    raise RuntimeError(f"screenshot too small or invalid for {name} ({len(data)} bytes)")


def dump_ui() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    local = Path(OUT) / "_ui.xml"
    adb("pull", "/sdcard/ui.xml", str(local))
    raw = local.read_bytes()
    for enc in ("utf-8", "utf-8-sig", "gb18030"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def find_center_by_text(xml: str, needle: str) -> tuple[int, int] | None:
    """Match exact or substring text=... nodes (Compose often wraps CTA labels)."""
    escaped = re.escape(needle)
    pats = (
        rf'text="{escaped}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="{escaped}"',
        rf'text="[^"]*{escaped}[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="[^"]*{escaped}[^"]*"',
    )
    for pat in pats:
        m = re.search(pat, xml)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def find_first_app_row(xml: str) -> tuple[int, int, str] | None:
    rx = re.compile(
        r'clickable="true"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*>'
    )
    for m in rx.finditer(xml):
        x1, y1, x2, y2 = map(int, m.groups())
        if y1 < 400 or y1 > 2400:
            continue
        h = y2 - y1
        if h < 80 or h > 420:
            continue
        chunk = xml[m.start() : m.start() + 1500]
        tm = re.search(r'text="([^"]{2,40})"', chunk)
        if not tm:
            continue
        label = tm.group(1)
        if label in SKIP_LABELS or label[0].isdigit():
            continue
        if "搜索" in label or label.endswith("..."):
            continue
        if h < 120:
            continue
        return (x1 + x2) // 2, (y1 + y2) // 2, label
    return None


def focused_on_phonepulse() -> bool:
    focus = adb("shell", "dumpsys", "window", check=False).stdout
    return "com.aizeek.phonepulse" in focus


def ensure_app_foreground(*, relaunch: bool = False) -> None:
    # Kill noisy apps that steal focus on emulator.
    for pkg in (
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.youtube.music",
        "com.android.vending",
    ):
        adb("shell", "am", "force-stop", pkg, check=False)
    wake_display()
    if relaunch or not focused_on_phonepulse():
        adb(
            "shell",
            "am",
            "start",
            "-W",
            "-n",
            "com.aizeek.phonepulse/.MainActivity",
            check=False,
        )
        time.sleep(1.6)
    if not focused_on_phonepulse():
        raise RuntimeError("PhonePulse is not in foreground; aborting capture")


def go_tab(name: str, settle: float = 1.0) -> None:
    ensure_app_foreground()
    tap(*NAV[name], wait=settle)
    if not focused_on_phonepulse():
        ensure_app_foreground(relaunch=True)
        tap(*NAV[name], wait=settle)


def find_label_in_list(xml: str, label: str) -> tuple[int, int] | None:
    """Find a list-row label by text, ignoring the top app bar."""
    centers: list[tuple[int, int, int]] = []
    for m in re.finditer(
        rf'text="{re.escape(label)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml,
    ):
        x1, y1, x2, y2 = map(int, m.groups())
        if 480 < y1 < 2300:
            centers.append(((x1 + x2) // 2, (y1 + y2) // 2, y1))
    if not centers:
        return None
    centers.sort(key=lambda t: t[2])
    x, y, _ = centers[0]
    return x + 180, y


def clear_old_shots() -> None:
    for p in OUT.glob("*.png"):
        p.unlink()
        print(f"  removed {p.name}")
    ui_dump = OUT / "_ui.xml"
    if ui_dump.exists():
        ui_dump.unlink()


def open_companion_from_overview() -> bool:
    """Open forest companion from the overview entry card."""
    go_tab("overview", settle=0.8)
    for _ in range(2):
        swipe_down()
    time.sleep(0.8)
    xml = dump_ui()
    for needle in (
        "打个招呼",
        "认识你的伙伴",
        "看看路上的惊喜",
        "回家搭起来",
        "看看来信",
        "布置我的小窝",
        "给伙伴试穿",
        "和松松打个招呼",
    ):
        hit = find_center_by_text(xml, needle)
        if hit:
            print(f"  Opening companion via '{needle}' at {hit}")
            tap(*hit, wait=1.4)
            return focused_on_phonepulse()
    for m in re.finditer(
        r'clickable="true"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml,
    ):
        x1, y1, x2, y2 = map(int, m.groups())
        if y1 < 900 or y1 > 2100:
            continue
        h = y2 - y1
        if h < 280 or h > 700:
            continue
        chunk = xml[max(0, m.start() - 200) : m.start() + 2500]
        if "森林小窝" in chunk or "打个招呼" in chunk or "今天想去" in chunk:
            x, y = (x1 + x2) // 2, (y1 + y2) // 2
            print(f"  Opening companion via entry card at ({x},{y})")
            tap(x, y, wait=1.4)
            return focused_on_phonepulse()
    print("  WARN: companion entry not found")
    return False


def capture_companion() -> None:
    print("\n[companion] Forest home / journal / pack")
    if not open_companion_from_overview():
        return
    time.sleep(0.6)
    capture("07_forest_home")

    xml = dump_ui()
    for label, name in (("旅册", "07_forest_journal"), ("行囊", "07_forest_pack")):
        hit = find_center_by_text(xml, label)
        if hit:
            print(f"  Switching companion page: {label}")
            tap(*hit, wait=1.0)
            capture(name)
            xml = dump_ui()
        else:
            print(f"  WARN: companion tab '{label}' not found")

    adb("shell", "input", "keyevent", "4")
    time.sleep(0.8)
    ensure_app_foreground()
    go_tab("overview", settle=0.8)


def pick_app_detail_target(xml: str) -> tuple[int, int, str] | None:
    for preferred in ("有所闻", "PhonePulse", "Pixel Launcher"):
        hit = find_label_in_list(xml, preferred)
        if hit:
            return hit[0], hit[1], preferred
    app = find_first_app_row(xml)
    if app:
        return app
    return None


def main() -> None:
    print("Clearing old screenshots...")
    clear_old_shots()

    print("Launching PhonePulse...")
    ensure_app_foreground(relaunch=True)
    time.sleep(2.0)

    print("\n[1/6] Overview")
    go_tab("overview")
    for _ in range(2):
        swipe_down()
    time.sleep(0.8)
    capture("01_overview_1")
    swipe_up()
    capture("01_overview_2")

    capture_companion()

    print("\n[2/6] App usage + detail")
    go_tab("app_usage", settle=2.5)
    for _ in range(2):
        swipe_down()
    capture("02_app_usage_1")
    swipe_up()
    capture("02_app_usage_2")

    xml = dump_ui()
    detail = pick_app_detail_target(xml)
    if detail:
        x, y, label = detail
        print(f"  Opening app detail ({label}) at ({x},{y})")
        tap(x, y, wait=1.2)
        if focused_on_phonepulse():
            capture("03_app_detail_1")
            swipe_up()
            capture("03_app_detail_2")
        else:
            print("  WARN: left PhonePulse opening app detail; skipping")
            ensure_app_foreground(relaunch=True)
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.8)
        ensure_app_foreground()
        go_tab("app_usage", settle=1.0)
    else:
        print("  WARN: no app row found for detail screenshot")

    print("\n[3/6] Battery")
    go_tab("battery", settle=2.5)
    for _ in range(2):
        swipe_down()
    capture("04_battery_1")
    swipe_up()
    capture("04_battery_2")

    # Battery health / history panels are more useful than a flaky app-row tap.
    xml = dump_ui()
    for needle, name in (
        ("电池健康", "04_battery_health"),
        ("充放记录", "04_battery_history"),
    ):
        hit = find_center_by_text(xml, needle)
        if hit:
            print(f"  Opening battery panel: {needle}")
            tap(*hit, wait=1.0)
            if focused_on_phonepulse():
                capture(name)
            ensure_app_foreground()
            go_tab("battery", settle=0.8)
            xml = dump_ui()

    print("\n[4/6] History")
    go_tab("history", settle=1.2)
    for _ in range(2):
        swipe_down()
    capture("05_history_1")
    swipe_up()
    capture("05_history_2")

    print("\n[5/6] Settings")
    go_tab("settings", settle=1.2)
    for _ in range(2):
        swipe_down()
    capture("06_settings_1")
    swipe_up()
    capture("06_settings_2")

    xml = dump_ui()
    for needle in ("各品牌保活指南", "品牌保活", "展开指南", "厂商指南"):
        guide = find_center_by_text(xml, needle)
        if guide:
            print(f"  Expanding: {needle}")
            tap(*guide, wait=0.9)
            if focused_on_phonepulse():
                capture("06_settings_vendor_guide")
                swipe_up()
                capture("06_settings_vendor_guide_2")
            break

    ui_dump = OUT / "_ui.xml"
    if ui_dump.exists():
        ui_dump.unlink()

    print(f"\nDone. Files in {OUT}")
    for p in sorted(OUT.glob("*.png")):
        print(f"  {p.name:32} {p.stat().st_size:,}")


if __name__ == "__main__":
    main()
