#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
from collections import defaultdict
from pathlib import Path


PRODUCT_PREFIXES = (
    "push/src/main/java/com/xiaomi/xmsf/",
    "push/src/main/java/top/trumeet/",
    "push/src/main/java/com/magisk317/",
    "push/src/main/java/io/github/magisk317/",
    "push/src/main/java/com/xiaomi/mipush/sdk/",
    "push/src/main/java/com/xiaomi/push/sdk/",
)

LEGACY_IMPORT_PREFIXES = (
    "com.xiaomi.channel.",
    "com.xiaomi.network.",
    "com.xiaomi.smack.",
    "com.xiaomi.slim.",
    "com.xiaomi.clientreport.",
    "com.xiaomi.stats.",
    "com.xiaomi.tinyData.",
    "com.xiaomi.common.logger.",
    "com.xiaomi.push.mpcd.",
)

FROZEN_IMPORT_PREFIXES = (
    "org.apache.thrift.",
    "com.google.protobuf.micro.",
    "com.xiaomi.xmpush.thrift.",
    "com.xiaomi.push.protobuf.",
    "com.xiaomi.push.thrift.",
)

IMPORT_RE = re.compile(r"^\s*import\s+([A-Za-z0-9_.]+)")


def is_product_owned(path: str) -> bool:
    return any(path.startswith(prefix) for prefix in PRODUCT_PREFIXES)


def classify_import(import_name: str) -> str | None:
    if any(import_name.startswith(prefix) for prefix in LEGACY_IMPORT_PREFIXES):
        return "legacy-runtime"
    if any(import_name.startswith(prefix) for prefix in FROZEN_IMPORT_PREFIXES):
        return "frozen-protocol"
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    repo = Path(args.repo).resolve()
    output = Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)

    findings = defaultdict(list)
    for path in repo.rglob("*.kt"):
        rel = path.relative_to(repo).as_posix()
        if not is_product_owned(rel):
            continue
        for line_no, line in enumerate(path.read_text(errors="ignore").splitlines(), start=1):
            match = IMPORT_RE.match(line)
            if not match:
                continue
            import_name = match.group(1)
            layer = classify_import(import_name)
            if layer:
                findings[layer].append((rel, line_no, import_name))

    lines: list[str] = []
    lines.append("# Boundary Direct Import Report")
    lines.append("")
    lines.append("Direct imports from product-owned code into legacy/protocol layers.")
    for layer in ["legacy-runtime", "frozen-protocol"]:
        lines.append("")
        lines.append(f"## {layer}")
        lines.append("")
        entries = findings[layer]
        if not entries:
            lines.append("_none_")
            continue
        for rel, line_no, import_name in sorted(entries):
            lines.append(f"- `{rel}:{line_no}` -> `{import_name}`")

    output.write_text("\n".join(lines) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
