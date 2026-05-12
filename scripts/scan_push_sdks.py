#!/usr/bin/env python3
"""Scan installed packages or local APKs for push SDK fingerprints.

The script is read-only by default. It prints candidate compat pipelines and
does not edit compat-profiles.json.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shlex
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


FAMILIES = {
    "XIAOMI_MIPUSH": {
        "patterns": [
            r"com\.xiaomi\.mipush",
            r"com\.xiaomi\.push\.service",
            r"MiPushClient",
        ],
        "pipeline": None,
    },
    "HUAWEI_HMS": {
        "patterns": [
            r"com\.huawei\.hms",
            r"com\.huawei\.push",
            r"com\.hihonor\.push",
            r"com\.honor",
        ],
        "pipeline": "HUAWEI_HMS",
    },
    "VIVO_PUSH": {
        "patterns": [r"com\.vivo\.push"],
        "pipeline": "VIVO_PUSH",
    },
    "OPPO_HEYTAP": {
        "patterns": [
            r"com\.heytap",
            r"com\.coloros\.mcs",
            r"com\.oppo\.push",
        ],
        "pipeline": "OPPO_HEYTAP",
    },
    "MEIZU_PUSH": {
        "patterns": [
            r"com\.meizu\.cloud",
            r"com\.meizu\.flyme",
            r"com\.meizu\.c2dm",
        ],
        "pipeline": "MEIZU_PUSH",
    },
    "XG_TPNS": {
        "patterns": [
            r"com\.tencent\.android\.tpush",
            r"com\.tencent\.tpns",
        ],
        "pipeline": "XGPUSH",
    },
    "JPUSH": {
        "patterns": [r"cn\.jpush\.android"],
        "pipeline": "JPUSH",
    },
    "ALI_AGOO_ACCS": {
        "patterns": [
            r"com\.taobao\.accs",
            r"org\.android\.agoo",
            r"com\.aliyun\.ams",
            r"com\.alibaba\.sdk\.android\.push",
        ],
        "pipeline": "ALI_AGOO_ACCS",
    },
    "UMENG_PUSH": {
        "patterns": [r"com\.umeng\.message"],
        "pipeline": "UMENG_PUSH",
    },
    "FCM_FIREBASE": {
        "patterns": [
            r"FirebaseMessagingService",
            r"com\.google\.firebase\.messaging",
        ],
        "pipeline": None,
    },
}

MI_PUSH_META_KEYS = (
    "com.xiaomi.push.api_id",
    "com.xiaomi.push.app_id",
    "mipush_app_id",
    "MIPUSH_APPID",
    "MI_PUSH_APP_ID",
    "com.xiaomi.push.api_key",
    "com.xiaomi.push.app_key",
    "mipush_app_key",
    "MIPUSH_APPKEY",
    "MI_PUSH_APP_KEY",
)


@dataclass
class ScanSource:
    name: str
    text: str


def run_command(cmd: list[str], timeout: int = 60) -> str:
    result = subprocess.run(
        cmd,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
    )
    if result.returncode != 0:
        raise RuntimeError(f"command failed: {' '.join(cmd)}\n{result.stderr.strip()}")
    return result.stdout


class AdbRunner:
    def __init__(self, adb: str, host: str | None, jump_host: str | None):
        self.adb = adb
        self.host = host
        self.jump_host = jump_host

    def run(self, args: list[str], timeout: int = 90) -> str:
        adb_command = " ".join(shlex.quote(part) for part in [self.adb, *args])
        if self.host:
            host_command = [
                "ssh",
                "-o",
                "BatchMode=yes",
                "-o",
                "ConnectTimeout=10",
                self.host,
                adb_command,
            ]
            if self.jump_host:
                outer_command = [
                    "ssh",
                    "-o",
                    "BatchMode=yes",
                    "-o",
                    "ConnectTimeout=10",
                    self.jump_host,
                    " ".join(shlex.quote(part) for part in host_command),
                ]
                return run_command(outer_command, timeout=timeout)
            return run_command(host_command, timeout=timeout)
        return run_command([self.adb, *args], timeout=timeout)

    def exec_out_to_file(self, remote_path: str, output_path: Path, timeout: int = 300) -> None:
        command = ["exec-out", "cat", remote_path]
        if self.host:
            adb_command = " ".join(shlex.quote(part) for part in [self.adb, *command])
            host_command = [
                "ssh",
                "-o",
                "BatchMode=yes",
                "-o",
                "ConnectTimeout=10",
                self.host,
                adb_command,
            ]
            if self.jump_host:
                full_command = [
                    "ssh",
                    "-o",
                    "BatchMode=yes",
                    "-o",
                    "ConnectTimeout=10",
                    self.jump_host,
                    " ".join(shlex.quote(part) for part in host_command),
                ]
            else:
                full_command = host_command
        else:
            full_command = [self.adb, *command]

        with output_path.open("wb") as fh:
            result = subprocess.run(
                full_command,
                check=False,
                stdout=fh,
                stderr=subprocess.PIPE,
                timeout=timeout,
            )
        if result.returncode != 0:
            raise RuntimeError(result.stderr.decode(errors="replace").strip())


def read_apk_manifest(apk_path: Path) -> str:
    apkanalyzer = find_executable("apkanalyzer")
    if not apkanalyzer:
        return ""
    try:
        return run_command([apkanalyzer, "manifest", "print", str(apk_path)], timeout=90)
    except Exception as exc:
        return f"apkanalyzer_error={exc}"


def read_apk_dex_strings(apk_path: Path, max_chars: int = 4_000_000) -> str:
    chunks: list[str] = []
    try:
        with zipfile.ZipFile(apk_path) as archive:
            dex_names = sorted(name for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name))
            for name in dex_names:
                data = archive.read(name)
                strings = re.findall(rb"[\x20-\x7e]{4,}", data)
                text = "\n".join(item.decode("ascii", errors="ignore") for item in strings)
                chunks.append(text)
                if sum(len(chunk) for chunk in chunks) >= max_chars:
                    break
    except zipfile.BadZipFile:
        return ""
    return "\n".join(chunks)[:max_chars]


def find_executable(name: str) -> str | None:
    for path_dir in os.environ.get("PATH", "").split(os.pathsep):
        candidate = Path(path_dir) / name
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return str(candidate)
    return None


def extract_package_name(manifest_text: str, fallback: str | None) -> str:
    match = re.search(r'\bpackage="([^"]+)"', manifest_text)
    if match:
        return match.group(1)
    match = re.search(r"Package \[([^\]]+)]", manifest_text)
    if match:
        return match.group(1)
    return fallback or "<unknown>"


def detect_families(sources: list[ScanSource]) -> dict[str, list[str]]:
    detected: dict[str, list[str]] = {}
    for family, config in FAMILIES.items():
        hits: list[str] = []
        for source in sources:
            for pattern in config["patterns"]:
                if re.search(pattern, source.text, re.IGNORECASE):
                    hits.append(source.name)
                    break
        if hits:
            detected[family] = sorted(set(hits))
    return detected


def extract_components(text: str) -> list[str]:
    result: list[str] = []
    component_line = re.compile(
        r"(activity|service|receiver|provider|meta-data|uses-permission).*"
        r"(xiaomi|mipush|huawei|hms|honor|vivo|heytap|coloros|oppo|meizu|tencent|tpns|tpush|jpush|agoo|accs|umeng|firebase)",
        re.IGNORECASE,
    )
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if component_line.search(line):
            result.append(re.sub(r"\s+", " ", line)[:240])
    return sorted(set(result))[:80]


def extract_mipush_credentials(text: str) -> dict[str, str]:
    credentials: dict[str, str] = {}
    for key in MI_PUSH_META_KEYS:
        pattern = re.compile(
            re.escape(key) + r'.{0,160}?(?:android:value=|"value"|value=)["\']?([^"\'\s<>]+)',
            re.IGNORECASE | re.DOTALL,
        )
        match = pattern.search(text)
        if match:
            credentials[key] = match.group(1)
    fallback_app_id = re.search(r"(?:xiaomi|mipush|app_id)[^\n]{0,120}?\b(2882303761\d{9,})\b", text, re.IGNORECASE)
    if fallback_app_id and not any("id" in key.lower() for key in credentials):
        credentials["fallback_app_id"] = fallback_app_id.group(1)
    return credentials


def recommended_pipelines(families: dict[str, list[str]]) -> tuple[list[str], list[str], str]:
    pipelines: list[str] = []
    reasons: list[str] = []
    has_mipush = "XIAOMI_MIPUSH" in families
    for family, config in FAMILIES.items():
        pipeline = config["pipeline"]
        if not pipeline or family not in families:
            continue
        if family in {"HUAWEI_HMS", "VIVO_PUSH", "OPPO_HEYTAP", "MEIZU_PUSH", "JPUSH", "ALI_AGOO_ACCS", "UMENG_PUSH"} and not has_mipush:
            reasons.append(f"{family} found but skipped because MiPush SDK was not detected")
            continue
        if pipeline not in pipelines:
            pipelines.append(pipeline)
            reasons.append(f"{family} detected from {','.join(families[family])}")
    confidence = "high" if has_mipush and any(p in pipelines for p in ("HUAWEI_HMS", "VIVO_PUSH", "OPPO_HEYTAP", "MEIZU_PUSH")) else "medium"
    if "FCM_FIREBASE" in families and not pipelines:
        reasons.append("FCM/Firebase detected; treat as non-MiPush split unless MiPush SDK is also present")
        confidence = "low"
    return pipelines, reasons, confidence


def scan_text(package_name: str | None, sources: list[ScanSource]) -> dict[str, object]:
    manifest_text = next((source.text for source in sources if source.name in {"manifest", "dumpsys"}), "")
    merged_text = "\n".join(source.text for source in sources)
    families = detect_families(sources)
    pipelines, reasons, confidence = recommended_pipelines(families)
    return {
        "packageName": extract_package_name(manifest_text, package_name),
        "families": families,
        "components": extract_components(merged_text),
        "mipushCredentials": extract_mipush_credentials(merged_text),
        "recommendedPipelines": pipelines,
        "reasons": reasons,
        "confidence": confidence,
    }


def scan_apk(apk_path: Path, package_name: str | None = None) -> dict[str, object]:
    manifest_text = read_apk_manifest(apk_path)
    dex_text = read_apk_dex_strings(apk_path)
    return scan_text(
        package_name,
        [
            ScanSource("manifest", manifest_text),
            ScanSource("dex", dex_text),
        ],
    )


def scan_remote_package(adb: AdbRunner, package_name: str, deep_apk: bool) -> dict[str, object]:
    dumpsys = adb.run(["shell", "dumpsys", "package", package_name], timeout=90)
    sources = [ScanSource("dumpsys", dumpsys)]
    if deep_apk:
        pm_path = adb.run(["shell", "pm", "path", package_name], timeout=30)
        base_path = next(
            (line.removeprefix("package:").strip() for line in pm_path.splitlines() if line.endswith("/base.apk")),
            None,
        )
        if base_path:
            with tempfile.TemporaryDirectory(prefix="mipush-scan-") as temp_dir:
                apk_path = Path(temp_dir) / f"{package_name}.apk"
                adb.exec_out_to_file(base_path, apk_path)
                sources.append(ScanSource("manifest", read_apk_manifest(apk_path)))
                sources.append(ScanSource("dex", read_apk_dex_strings(apk_path)))
    return scan_text(package_name, sources)


def scan_remote(args: argparse.Namespace) -> list[dict[str, object]]:
    adb = AdbRunner(args.adb, args.host, args.jump_host)
    if args.packages:
        packages = args.packages
    else:
        output = adb.run(["shell", "pm", "list", "packages", "-3"], timeout=60)
        packages = [
            line.removeprefix("package:").strip()
            for line in output.splitlines()
            if line.startswith("package:")
        ]
    if args.limit:
        packages = packages[: args.limit]
    return [scan_remote_package(adb, package, args.deep_apk) for package in packages]


def render_markdown(results: list[dict[str, object]]) -> str:
    lines = [
        "| package | families | recommended | confidence | reasons |",
        "| --- | --- | --- | --- | --- |",
    ]
    for item in results:
        families = ", ".join(sorted(item["families"].keys())) if isinstance(item["families"], dict) else ""
        recommended = ", ".join(item["recommendedPipelines"]) if isinstance(item["recommendedPipelines"], list) else ""
        reasons = "; ".join(item["reasons"]) if isinstance(item["reasons"], list) else ""
        lines.append(
            "| {package} | {families} | {recommended} | {confidence} | {reasons} |".format(
                package=item["packageName"],
                families=families,
                recommended=recommended or "-",
                confidence=item["confidence"],
                reasons=reasons.replace("|", "\\|"),
            )
        )
    return "\n".join(lines)


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--apk", action="append", type=Path, help="Local APK path. Can be passed multiple times.")
    source.add_argument("--remote", action="store_true", help="Scan installed third-party packages through adb.")
    parser.add_argument("--package", dest="packages", action="append", help="Package name to scan in remote mode.")
    parser.add_argument("--adb", default="adb", help="adb binary path. For remote host mode, this is the path on the host.")
    parser.add_argument("--host", help="SSH host that owns the adb device, for example john@100.79.25.74.")
    parser.add_argument("--jump-host", help="Optional SSH jump host, for example root@10.33.207.217.")
    parser.add_argument("--deep-apk", action="store_true", help="Pull base.apk and run manifest/dex scans in remote mode.")
    parser.add_argument("--limit", type=int, help="Limit package count in remote mode.")
    parser.add_argument("--format", choices=("markdown", "json"), default="markdown")
    return parser


def main(argv: list[str]) -> int:
    parser = build_arg_parser()
    args = parser.parse_args(argv)
    if args.apk:
        results = [scan_apk(path) for path in args.apk]
    else:
        results = scan_remote(args)

    if args.format == "json":
        print(json.dumps(results, ensure_ascii=False, indent=2))
    else:
        print(render_markdown(results))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
