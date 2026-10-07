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


def capture(name: str) -> None:
    remote = "/sdcard/pp_shot.png"
    path = OUT / f"{name}.png"
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, str(path))
    print(f"  OK  {name}  ({path.stat().st_size:,} bytes)")


def dump_ui() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    local = Path(OUT) / "_ui.xml"
    adb("pull", "/sdcard/ui.xml", str(local))
    return local.read_text(encoding="utf-8", errors="replace")


def find_center_by_text(xml: str, needle: str) -> tuple[int, int] | None:
    pat = (
        rf'text="{re.escape(needle)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    )
    m = re.search(pat, xml)
    if not m:
        pat2 = (
            rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="{re.escape(needle)}"'
        )
        m = re.search(pat2, xml)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


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
        # Prefer list rows (taller) over chips/buttons
        if h < 120:
            continue
        return (x1 + x2) // 2, (y1 + y2) // 2, label
    return None


def focused_on_phonepulse() -> bool:
    focus = adb("shell", "dumpsys", "window", check=False).stdout
    return "com.aizeek.phonepulse" in focus


def ensure_app_foreground(*, relaunch: bool = False) -> None:
    adb("shell", "am", "force-stop", "com.google.android.googlequicksearchbox", check=False)
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
        time.sleep(1.5)
    if not focused_on_phonepulse():
        raise RuntimeError("PhonePulse is not in foreground; aborting capture")


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
    # Tap slightly to the right so we hit the row, not only the text width.
    return x + 180, y


def main() -> None:
    print("Launching PhonePulse...")
    ensure_app_foreground(relaunch=True)

    print("\n[1/5] Overview")
    tap(*NAV["overview"])
    # Scroll to top first
    for _ in range(2):
        swipe_down()
    time.sleep(0.4)
    capture("01_overview_1")
    swipe_up()
    capture("01_overview_2")

    print("\n[2/5] App usage + detail")
    tap(*NAV["app_usage"])
    time.sleep(2.5)
    for _ in range(2):
        swipe_down()
    capture("02_app_usage_1")
    swipe_up()
    capture("02_app_usage_2")

    xml = dump_ui()
    # Prefer a known app row; never tap the search field.
    detail = find_label_in_list(xml, "有所闻") or find_label_in_list(xml, "PhonePulse")
    if detail is None:
        app = find_first_app_row(xml)
        detail = (app[0], app[1]) if app else None
        label = app[2] if app else None
    else:
        label = "list-app"
    if detail:
        x, y = detail
        print(f"  Opening app detail ({label}) at ({x},{y})")
        tap(x, y, wait=1.2)
        capture("03_app_detail_1")
        swipe_up()
        capture("03_app_detail_2")
        # Back to tab only — stay inside PhonePulse
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.8)
        if not focused_on_phonepulse():
            ensure_app_foreground()
        tap(*NAV["app_usage"], wait=1.0)
    else:
        print("  WARN: no app row found for detail screenshot")

    print("\n[3/5] Battery")
    tap(*NAV["battery"])
    time.sleep(2.5)
    for _ in range(2):
        swipe_down()
    capture("04_battery_1")
    swipe_up()
    capture("04_battery_2")

    xml = dump_ui()
    batt = find_label_in_list(xml, "有所闻") or find_first_app_row(xml)
    if batt:
        if isinstance(batt, tuple) and len(batt) == 3:
            x, y, label = batt
        else:
            x, y = batt  # type: ignore[misc]
            label = "battery-app"
        print(f"  Opening battery app detail: {label}")
        tap(x, y, wait=1.0)
        capture("04_battery_app_detail")
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.6)
        if not focused_on_phonepulse():
            ensure_app_foreground()

    print("\n[4/5] History")
    tap(*NAV["history"])
    time.sleep(1.0)
    for _ in range(2):
        swipe_down()
    capture("05_history_1")
    swipe_up()
    capture("05_history_2")

    print("\n[5/5] Settings")
    tap(*NAV["settings"])
    time.sleep(1.0)
    for _ in range(2):
        swipe_down()
    capture("06_settings_1")
    swipe_up()
    capture("06_settings_2")
    swipe_up()
    capture("06_settings_3")

    xml = dump_ui()
    for needle in ("各品牌保活指南", "品牌保活", "展开指南", "厂商指南"):
        guide = find_center_by_text(xml, needle)
        if guide:
            print(f"  Expanding: {needle}")
            tap(*guide, wait=0.9)
            capture("06_settings_vendor_guide")
            break

    # Remove temporary UI dump
    ui_dump = OUT / "_ui.xml"
    if ui_dump.exists():
        ui_dump.unlink()
    probe = OUT / "_probe.png"
    if probe.exists():
        probe.unlink()

    print(f"\nDone. Files in {OUT}")
    for p in sorted(OUT.glob("*.png")):
        print(f"  {p.name:32} {p.stat().st_size:,}")


if __name__ == "__main__":
    main()
