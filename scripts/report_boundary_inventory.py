#!/usr/bin/env python3
from __future__ import annotations

import argparse
import subprocess
from collections import Counter, defaultdict
from pathlib import Path


LAYER_RULES = [
    ("product-owned", "push/src/main/java/com/xiaomi/xmsf/"),
    ("product-owned", "push/src/main/java/top/trumeet/"),
    ("product-owned", "push/src/main/java/io/github/magisk317/"),
    ("product-owned", "push/src/main/java/io/github/magisk317/"),
    ("product-owned", "push/src/main/java/com/xiaomi/push/service/"),
    ("product-owned", "push/src/main/java/com/xiaomi/mipush/sdk/"),
    ("product-owned", "push/src/main/java/com/xiaomi/push/sdk/"),
    ("legacy-runtime", "push/src/main/java/com/xiaomi/channel/"),
    ("legacy-runtime", "push/src/main/java/com/xiaomi/network/"),
    ("legacy-runtime", "push/src/main/java/com/xiaomi/smack/"),
    ("legacy-runtime", "push/src/main/java/com/xiaomi/slim/"),
    ("legacy-runtime", "push/src/main/java/com/xiaomi/clientreport/"),
    ("legacy-runtime", "push/src/main/java/com/xiaomi/stats/"),
    ("legacy-runtime", "push/src/main/java/com/xiaomi/tinyData/"),
    ("legacy-runtime", "push/src/main/java/com/xiaomi/common/logger/"),
    ("legacy-runtime", "push/src/main/java/com/xiaomi/push/mpcd/"),
    ("legacy-runtime", "legacy-runtime/src/main/java/"),
    ("frozen-protocol", "push/src/main/java/org/apache/thrift/"),
    ("frozen-protocol", "push/src/main/java/com/google/protobuf/micro/"),
    ("frozen-protocol", "push/src/main/java/com/xiaomi/xmpush/thrift/"),
    ("frozen-protocol", "push/src/main/java/com/xiaomi/push/protobuf/"),
    ("frozen-protocol", "push/src/main/java/com/xiaomi/push/thrift/"),
    ("frozen-protocol", "protocol-frozen/src/main/java/"),
]

DELETE_PREFIXES = (
    "push/src/main/java/com/xiaomi/network/usagedemo/",
)

DELETE_BASENAMES = {
    "BuildConfig.java",
    "BuildConfig.kt",
}


def git_ls_files(repo: Path) -> list[str]:
    out = subprocess.check_output(
        ["git", "-C", str(repo), "ls-files", "*.java", "*.kt"], text=True
    )
    return [line.strip() for line in out.splitlines() if line.strip()]


def classify(path: str) -> str:
    for prefix in DELETE_PREFIXES:
        if path.startswith(prefix):
            return "delete-candidate"
    if Path(path).name in DELETE_BASENAMES:
        return "delete-candidate"
    for layer, prefix in LAYER_RULES:
        if path.startswith(prefix):
            return layer
    return "manual-review"


def package_bucket(path: str) -> str:
    rel = path
    for prefix in (
        "push/src/main/java/",
        "legacy-runtime/src/main/java/",
        "protocol-frozen/src/main/java/",
    ):
        if rel.startswith(prefix):
            rel = rel.removeprefix(prefix)
            break
    parts = rel.split("/")
    return "/".join(parts[:4]) if len(parts) >= 4 else rel


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    repo = Path(args.repo).resolve()
    output = Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)

    files = git_ls_files(repo)
    counts = Counter()
    buckets = defaultdict(list)
    for path in files:
        if not (
            path.startswith("push/src/main/java/")
            or path.startswith("legacy-runtime/src/main/java/")
            or path.startswith("protocol-frozen/src/main/java/")
        ):
            continue
        layer = classify(path)
        counts[layer] += 1
        buckets[layer].append(path)

    lines: list[str] = []
    lines.append("# Boundary Inventory")
    lines.append("")
    lines.append("Generated from git-tracked `*.java`/`*.kt` under `push`, `legacy-runtime`, and `protocol-frozen` source roots.")
    lines.append("")
    lines.append("## Layer Counts")
    lines.append("")
    for layer in ["product-owned", "legacy-runtime", "frozen-protocol", "delete-candidate", "manual-review"]:
        lines.append(f"- `{layer}`: {counts[layer]}")

    lines.append("")
    lines.append("## Package Buckets")
    lines.append("")
    package_counts = Counter()
    for paths in buckets.values():
        for path in paths:
            package_counts[package_bucket(path)] += 1
    for bucket, count in package_counts.most_common(80):
        lines.append(f"- `{bucket}`: {count}")

    for layer in ["delete-candidate", "manual-review"]:
        lines.append("")
        lines.append(f"## {layer}")
        lines.append("")
        if not buckets[layer]:
            lines.append("_none_")
        else:
            for path in sorted(buckets[layer]):
                lines.append(f"- `{path}`")

    output.write_text("\n".join(lines) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
