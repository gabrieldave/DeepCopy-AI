#!/usr/bin/env python3
"""Capturas adb de la interfaz clara (requiere pantalla desbloqueada)."""
from __future__ import annotations

import re
import subprocess
import sys
import time
from pathlib import Path

# Prefer USB (-d) when cable+Wi-Fi both appear; else default adb device.
PKG = "com.blackeye.klipnote.debug"
ACTIVITY = f"{PKG}/com.blackeye.klipnote.MainActivity"
SHOTS = Path("/Users/davidai/Developer/deepcopy-ai/screenshots-1.6.0")
REMOTE_DIR = "/sdcard/Download/klipnote-shots"


def adb_prefix() -> list[str]:
    out = subprocess.run(["adb", "devices", "-l"], text=True, capture_output=True, check=False).stdout
    lines = [ln for ln in out.splitlines() if "\tdevice" in ln or " device " in ln]
    devices = []
    for ln in lines:
        if "\tdevice" not in ln:
            continue
        serial = ln.split()[0]
        usb = " usb:" in ln
        devices.append((serial, usb))
    if len(devices) >= 2 and any(u for _, u in devices):
        return ["adb", "-d"]
    if len(devices) == 1:
        return ["adb", "-s", devices[0][0]]
    return ["adb", "-d"]


def adb(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [*adb_prefix(), *args],
        text=True,
        capture_output=True,
        check=check,
    )


def focus() -> str:
    """Prefer the last non-null mCurrentFocus (dumpsys may emit several)."""
    out = adb("shell", "dumpsys", "window", check=False).stdout
    last_any = ""
    last_real = ""
    for line in out.splitlines():
        if "mCurrentFocus" not in line and "mFocusedApp" not in line:
            continue
        s = line.strip()
        last_any = s
        if "=null" not in s and "null}" not in s:
            last_real = s
    return last_real or last_any


def wait_unlocked(timeout: float = 120.0) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP", check=False)
        adb("shell", "wm", "dismiss-keyguard", check=False)
        foc = focus()
        print("focus:", foc)
        if not foc:
            time.sleep(1.5)
            continue
        low = foc.lower()
        if "keyguard" in low or "bouncer" in low:
            time.sleep(1.5)
            continue
        # unlocked-ish: any real focus window (incl. launcher / our app)
        if "mcurrentfocus=null" not in low and "=null" not in low:
            return
        time.sleep(1.5)
    raise SystemExit("Teléfono sigue bloqueado; desbloquéalo y reintenta.")

def dump_ui() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
    adb("pull", "/sdcard/ui.xml", "/tmp/klip-ui.xml", check=False)
    return Path("/tmp/klip-ui.xml").read_text(encoding="utf-8", errors="ignore")


def find_bounds(
    label: str,
    contains: bool = False,
    *,
    prefer_bottom: bool = False,
) -> tuple[int, int] | None:
    xml = dump_ui()
    patterns = [
        re.compile(
            r'(?:text|content-desc)="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
        ),
        re.compile(
            r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*(?:text|content-desc)="([^"]*)"'
        ),
    ]
    hits: list[tuple[int, int, int]] = []
    for pat in patterns:
        for m in pat.finditer(xml):
            groups = m.groups()
            if groups[0].isdigit():
                x1, y1, x2, y2 = map(int, groups[:4])
                text = groups[4]
            else:
                text = groups[0]
                x1, y1, x2, y2 = map(int, groups[1:])
            ok = (text == label) if not contains else (label in text)
            if ok:
                hits.append(((x1 + x2) // 2, (y1 + y2) // 2, y2))
    if not hits:
        return None
    if prefer_bottom:
        hits.sort(key=lambda h: h[2], reverse=True)
    return hits[0][0], hits[0][1]

def tap(
    label: str,
    contains: bool = False,
    required: bool = True,
    *,
    prefer_bottom: bool = False,
) -> bool:
    coords = find_bounds(label, contains=contains, prefer_bottom=prefer_bottom)
    if not coords:
        print(f"NOT FOUND: {label!r}")
        if required:
            return False
        return False
    print(f"tap {label!r} -> {coords}")
    adb("shell", "input", "tap", str(coords[0]), str(coords[1]))
    time.sleep(0.9)
    return True

def cap(name: str) -> None:
    SHOTS.mkdir(parents=True, exist_ok=True)
    adb("shell", "mkdir", "-p", REMOTE_DIR, check=False)
    remote = f"{REMOTE_DIR}/{name}.png"
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, str(SHOTS / f"{name}.png"))
    print("saved", name)


def launch(clear: bool = False) -> None:
    adb("shell", "am", "force-stop", PKG, check=False)
    if clear:
        adb("shell", "pm", "clear", PKG)
    adb("shell", "am", "start", "-n", ACTIVITY)
    time.sleep(2.5)


def main() -> None:
    wait_unlocked()
    launch(clear=True)
    # Onboarding 1
    time.sleep(1)
    cap("01-onboarding-1")
    tap("Continuar")
    cap("02-onboarding-2")
    if not tap("Continuar sin llave", required=False):
        tap("Saltar", required=False)
    # if Saltar finished wizard, page 3 may be skipped
    if find_bounds("Empezar") or find_bounds("¡Listo!", contains=True):
        cap("03-onboarding-3")
        tap("Empezar", required=False) or tap("Saltar", required=False)
    else:
        # already in app: reopen tutorial from settings later; fake page3 by restarting
        # mark done false isn't easy; capture current and continue
        cap("03-onboarding-3")

    time.sleep(1)
    cap("04-capturar-sin-llaves")

    tap("Guiones", prefer_bottom=True)
    cap("05-guiones")

    tap("Ajustes", prefer_bottom=True)
    time.sleep(1)
    cap("06-ajustes-cerrado")
    tap("Ver más", required=False)
    time.sleep(0.8)
    cap("07-servicio-ver-mas")

    adb("shell", "input", "swipe", "640", "2100", "640", "900", "350")
    time.sleep(0.5)
    tap("Videos que piden iniciar sesión", contains=True, required=False)
    time.sleep(0.8)
    cap("08-cookies-abierto")

    adb("shell", "input", "swipe", "640", "2100", "640", "900", "350")
    time.sleep(0.4)
    tap("Avanzado", required=False)
    time.sleep(0.8)
    cap("09-avanzado-abierto")

    # With keys: set groq via debug run-as SharedPreferences is encrypted EncryptedSharedPreferences - hard.
    # Use UI: Mis llaves
    tap("Mis llaves", required=False)
    time.sleep(1)
    # paste dummy key if field found - skip if complex
    # Instead seed Encrypted store not feasible; use real empty summary card skip
    # Put a key via ApiKeyStore needs crypto. Show capturar with keys by writing through UI.
    print("DONE base shots. For with-keys, open Mis llaves manually if needed.")


if __name__ == "__main__":
    main()
