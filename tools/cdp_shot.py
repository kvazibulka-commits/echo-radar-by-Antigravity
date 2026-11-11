#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ЭХО — инструмент визуальной проверки веб-интерфейса (tools/cdp_shot.py).

Делает скриншот страницы в настоящем мобильном вьюпорте (через Chrome DevTools
Protocol, без внешних библиотек) и печатает ошибки JavaScript из консоли.

Примеры:
    python tools/cdp_shot.py --url http://127.0.0.1:8080/app --out shot.png --mobile
    python tools/cdp_shot.py --url http://127.0.0.1:8080/app#air --out air.png --mobile
    python tools/cdp_shot.py --url http://127.0.0.1:8080/ --out dash.png --width 1440 --height 1000
    python tools/cdp_shot.py --url http://127.0.0.1:8080/app --out x.png --mobile \
        --js "document.querySelectorAll('.card').length"

Код завершения 1, если на странице были исключения JavaScript.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import socket
import struct
import subprocess
import sys
import tempfile
import time
import urllib.request

CHROME_CANDIDATES = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    os.path.join(os.environ.get("LOCALAPPDATA", ""), r"Google\Chrome\Application\chrome.exe"),
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
]


def find_chrome() -> str:
    for path in CHROME_CANDIDATES:
        if path and os.path.exists(path):
            return path
    raise SystemExit("Не найден Chrome/Edge. Укажите путь через --chrome.")


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class WebSocketClient:
    """Минимальный клиент WebSocket (text frames + ping/pong), только stdlib."""

    def __init__(self, url: str, timeout: float = 30.0):
        assert url.startswith("ws://"), url
        rest = url[len("ws://"):]
        host_port, _, path = rest.partition("/")
        host, _, port = host_port.partition(":")
        self.sock = socket.create_connection((host, int(port or "80")), timeout=timeout)
        self.sock.settimeout(timeout)
        key = base64.b64encode(os.urandom(16)).decode("ascii")
        request = (
            f"GET /{path} HTTP/1.1\r\n"
            f"Host: {host_port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n\r\n"
        )
        self.sock.sendall(request.encode("ascii"))
        response = b""
        while b"\r\n\r\n" not in response:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise RuntimeError("WebSocket handshake failed")
            response += chunk
        if b"101" not in response.split(b"\r\n", 1)[0]:
            raise RuntimeError("WebSocket handshake rejected: " + response[:200].decode("latin-1"))
        self._buffer = b""

    def _send_frame(self, payload: bytes, opcode: int = 1) -> None:
        header = bytearray([0x80 | opcode])
        length = len(payload)
        if length < 126:
            header.append(0x80 | length)
        elif length < (1 << 16):
            header.append(0x80 | 126)
            header += struct.pack("!H", length)
        else:
            header.append(0x80 | 127)
            header += struct.pack("!Q", length)
        mask = os.urandom(4)
        header += mask
        masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
        self.sock.sendall(bytes(header) + masked)

    def send_text(self, text: str) -> None:
        self._send_frame(text.encode("utf-8"), opcode=1)

    def _read_exact(self, n: int) -> bytes:
        while len(self._buffer) < n:
            chunk = self.sock.recv(65536)
            if not chunk:
                raise ConnectionError("WebSocket closed")
            self._buffer += chunk
        data, self._buffer = self._buffer[:n], self._buffer[n:]
        return data

    def _recv_frame(self):
        b1, b2 = self._read_exact(2)
        fin = b1 & 0x80
        opcode = b1 & 0x0F
        masked = b2 & 0x80
        length = b2 & 0x7F
        if length == 126:
            length = struct.unpack("!H", self._read_exact(2))[0]
        elif length == 127:
            length = struct.unpack("!Q", self._read_exact(8))[0]
        mask = self._read_exact(4) if masked else b""
        payload = self._read_exact(length) if length else b""
        if masked:
            payload = bytes(byte ^ mask[i % 4] for i, byte in enumerate(payload))
        return fin, opcode, payload

    def recv_text(self) -> str:
        parts = []
        while True:
            fin, opcode, payload = self._recv_frame()
            if opcode == 0x9:  # ping
                self._send_frame(payload, opcode=0xA)
                continue
            if opcode == 0x8:  # close
                raise ConnectionError("WebSocket closed by peer")
            if opcode in (0x1, 0x2):
                parts = [payload]
            elif opcode == 0x0:
                parts.append(payload)
            if fin:
                return b"".join(parts).decode("utf-8", errors="replace")

    def close(self) -> None:
        try:
            self._send_frame(b"", opcode=0x8)
        except Exception:
            pass
        try:
            self.sock.close()
        except Exception:
            pass


class CDP:
    def __init__(self, ws: WebSocketClient):
        self.ws = ws
        self._id = 0
        self.events = []

    def call(self, method: str, params: dict | None = None, timeout: float = 60.0):
        self._id += 1
        message_id = self._id
        self.ws.send_text(json.dumps({"id": message_id, "method": method, "params": params or {}}))
        self.ws.sock.settimeout(0.25)
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                raw = self.ws.recv_text()
            except socket.timeout:
                continue
            message = json.loads(raw)
            if message.get("id") == message_id:
                if "error" in message:
                    raise RuntimeError(f"{method}: {message['error']}")
                return message.get("result", {})
            self.events.append(message)
        raise TimeoutError(method)

    def pump(self, seconds: float) -> None:
        self.ws.sock.settimeout(0.2)
        end = time.time() + seconds
        while time.time() < end:
            try:
                raw = self.ws.recv_text()
            except socket.timeout:
                continue
            except ConnectionError:
                break
            try:
                self.events.append(json.loads(raw))
            except ValueError:
                pass


def launch_chrome(chrome: str, port: int, user_data_dir: str) -> subprocess.Popen:
    args = [
        chrome,
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-extensions",
        "--mute-audio",
        f"--user-data-dir={user_data_dir}",
        f"--remote-debugging-port={port}",
        "about:blank",
    ]
    return subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def wait_for_debugger(port: int, timeout: float = 20.0) -> dict:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list", timeout=1.0) as resp:
                targets = json.loads(resp.read().decode("utf-8"))
            for target in targets:
                if target.get("type") == "page" and target.get("webSocketDebuggerUrl"):
                    return target
        except Exception:
            time.sleep(0.2)
    raise SystemExit("Chrome DevTools не поднялся на порту " + str(port))


def main() -> int:
    parser = argparse.ArgumentParser(description="Скриншот и проверка JavaScript через CDP")
    parser.add_argument("--url", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--chrome", default="")
    parser.add_argument("--width", type=int, default=390)
    parser.add_argument("--height", type=int, default=844)
    parser.add_argument("--scale", type=float, default=2.0)
    parser.add_argument("--mobile", action="store_true", help="мобильная эмуляция (touch, viewport)")
    parser.add_argument("--wait", type=int, default=3500, help="мс ожидания после загрузки")
    parser.add_argument("--js", action="append", default=[], help="выражение для выполнения (можно повторять)")
    parser.add_argument("--full-page", action="store_true")
    args = parser.parse_args()

    chrome = args.chrome or find_chrome()
    port = free_port()
    profile = tempfile.mkdtemp(prefix="echo_cdp_")
    process = launch_chrome(chrome, port, profile)
    ws = None
    exit_code = 0
    try:
        target = wait_for_debugger(port)
        ws = WebSocketClient(target["webSocketDebuggerUrl"])
        cdp = CDP(ws)
        cdp.call("Page.enable")
        cdp.call("Runtime.enable")
        cdp.call("Log.enable")
        cdp.call(
            "Emulation.setDeviceMetricsOverride",
            {
                "width": args.width,
                "height": args.height,
                "deviceScaleFactor": args.scale,
                "mobile": bool(args.mobile),
            },
        )
        if args.mobile:
            cdp.call("Emulation.setTouchEmulationEnabled", {"enabled": True, "maxTouchPoints": 5})
        cdp.call("Page.navigate", {"url": args.url})
        cdp.pump(args.wait / 1000.0)

        for expression in args.js:
            result = cdp.call(
                "Runtime.evaluate",
                {"expression": expression, "awaitPromise": True, "returnByValue": True},
                timeout=30,
            )
            value = result.get("result", {}).get("value")
            print(f"[js] {expression} => {json.dumps(value, ensure_ascii=False)}")

        screenshot = cdp.call(
            "Page.captureScreenshot",
            {"format": "png", "captureBeyondViewport": bool(args.full_page)},
            timeout=60,
        )
        os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
        with open(args.out, "wb") as fh:
            fh.write(base64.b64decode(screenshot["data"]))
        print(f"[ok] скриншот: {args.out} ({os.path.getsize(args.out)} байт)")

        errors = []
        for event in cdp.events:
            method = event.get("method")
            params = event.get("params", {})
            if method == "Runtime.exceptionThrown":
                details = params.get("exceptionDetails", {})
                text = details.get("exception", {}).get("description") or details.get("text")
                errors.append(f"exception: {text}")
            elif method == "Runtime.consoleAPICalled" and params.get("type") == "error":
                parts = []
                for arg in params.get("args", []):
                    parts.append(str(arg.get("value", arg.get("description", ""))))
                errors.append("console.error: " + " ".join(parts))
            elif method == "Log.entryAdded":
                entry = params.get("entry", {})
                if entry.get("level") == "error":
                    errors.append("log: " + str(entry.get("text")))
        if errors:
            print("[errors]")
            for error in errors:
                print("  - " + error)
            exit_code = 1
        else:
            print("[ok] ошибок JavaScript нет")
        return exit_code
    finally:
        if ws:
            ws.close()
        try:
            process.terminate()
            process.wait(timeout=5)
        except Exception:
            try:
                process.kill()
            except Exception:
                pass


if __name__ == "__main__":
    sys.exit(main())
