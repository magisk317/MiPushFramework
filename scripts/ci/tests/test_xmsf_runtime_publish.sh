#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
publisher="$root_dir/scripts/ci/xmsf_runtime_publish.sh"
tmp_dir="$(mktemp -d)"
state_file="$tmp_dir/mock-state.json"
expected_file="$tmp_dir/expected.json"
port_file="$tmp_dir/mock-port"
server_pid=""

cleanup() {
  if [[ -n "$server_pid" ]]; then
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
  fi
  if [[ "${KEEP_TEST_TMP:-0}" == 1 ]]; then
    printf 'TEST_TMP=%s\n' "$tmp_dir"
  else
    rm -rf -- "$tmp_dir"
  fi
}
trap cleanup EXIT

mkdir -p "$tmp_dir/apk"
printf '%s\n' 'normal-runtime-test-apk-v1' > "$tmp_dir/apk/universal_normal_xmsf_v1.1.3_release.apk"
printf '%s\n' 'vc105-runtime-test-apk-v1' > "$tmp_dir/apk/universal_vc105_xmsf_v1.1.3_release.apk"

python3 - "$tmp_dir/apk" "$expected_file" <<'PY'
import hashlib
import json
import os
import sys

apk_dir, expected_path = sys.argv[1:]
expected = {}
for flavor, version_code in (("normal", 1003003000), ("vc105", 105)):
    apk_path = os.path.join(apk_dir, f"universal_{flavor}_xmsf_v1.1.3_release.apk")
    with open(apk_path, "rb") as handle:
        apk = handle.read()
    sha = hashlib.sha256(apk).hexdigest()
    sidecar = f"{sha}  xmsf-runtime.apk\n".encode()
    manifest = {
        "schema": 1,
        "variant": "github",
        "versionName": "1.1.3",
        "versionCode": version_code,
        "minManagerVersionCode": 0,
        "sha256": sha,
        "commit": "test1234",
    }
    manifest_bytes = (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode()
    expected[flavor] = {
        "xmsf-runtime.apk": {"content": apk.decode(), "size": len(apk), "sha": sha},
        "xmsf-runtime.apk.sha256": {
            "content": sidecar.decode(),
            "size": len(sidecar),
            "sha": hashlib.sha256(sidecar).hexdigest(),
        },
        "xmsf-runtime-manifest.json": {
            "content": manifest_bytes.decode(),
            "size": len(manifest_bytes),
            "sha": hashlib.sha256(manifest_bytes).hexdigest(),
        },
    }
with open(expected_path, "w", encoding="utf-8") as handle:
    json.dump(expected, handle)
PY

cat > "$tmp_dir/mock_gitlab.py" <<'PY'
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, unquote, urlparse

state_path, port_path = sys.argv[1:]
TOKEN = "test-masked-api-token"


def read_state():
    with open(state_path, encoding="utf-8") as handle:
        return json.load(handle)


def write_state(state):
    temporary = state_path + ".tmp"
    with open(temporary, "w", encoding="utf-8") as handle:
        json.dump(state, handle)
    os.replace(temporary, state_path)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, _format, *_args):
        return

    def send_json(self, status, value, extra_headers=None):
        body = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        if extra_headers:
            for key, item in extra_headers.items():
                self.send_header(key, item)
        self.end_headers()
        self.wfile.write(body)

    def send_empty(self, status):
        self.send_response(status)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def authorized(self, state):
        state.setdefault("calls", []).append({
            "method": self.command,
            "path": self.path,
            "token_ok": self.headers.get("PRIVATE-TOKEN") == TOKEN,
        })
        if self.headers.get("PRIVATE-TOKEN") != TOKEN or state.get("force_unauthorized"):
            self.send_json(401, {"message": "unauthorized"})
            write_state(state)
            return False
        return True

    def do_GET(self):
        state = read_state()
        if not self.authorized(state):
            return
        parsed = urlparse(self.path)
        prefix = "/api/v4/projects/83955143/"
        if not parsed.path.startswith(prefix):
            self.send_json(404, {"message": "unknown route"})
            write_state(state)
            return
        route = parsed.path[len(prefix):]
        query = parse_qs(parsed.query)
        if route == "packages":
            if state.get("forbidden_list"):
                self.send_json(403, {"message": "forbidden"})
                write_state(state)
                return
            if "package_name" not in query or "package_version" not in query:
                self.send_json(400, {"message": "missing exact query filters"})
                write_state(state)
                return
            name_filter = query["package_name"][0]
            version_filter = query["package_version"][0]
            type_filter = query.get("package_type", [""])[0]
            page = int(query.get("page", ["1"])[0])
            per_page = int(query.get("per_page", ["100"])[0])
            candidates = []
            for item in state.get("packages", []):
                if type_filter and item["package_type"] != type_filter:
                    continue
                # Simulate the API's fuzzy package-name filter and a stale/misleading
                # version match; the publisher must still compare exact metadata.
                if not item["name"].startswith(name_filter):
                    continue
                if item.get("include_when_version_filter_differs") or item["version"] == version_filter:
                    candidates.append({key: item[key] for key in ("id", "name", "version", "package_type")})
            page_size = state.get("page_size", per_page)
            if page_size < per_page:
                start = (page - 1) * page_size
                page_items = candidates[start:start + page_size]
            else:
                start = (page - 1) * per_page
                page_items = candidates[start:start + per_page]
            actual_page_size = min(page_size, per_page)
            total_pages = (len(candidates) + actual_page_size - 1) // actual_page_size
            next_page = "" if page >= total_pages else str(page + 1)
            if state.get("broken_pagination") and page == 1 and total_pages > 1:
                next_page = ""
            headers = {
                "X-Page": str(page),
                "X-Per-Page": str(actual_page_size),
                "X-Next-Page": next_page,
                "X-Total-Pages": str(total_pages),
            }
            self.send_json(200, page_items, headers)
            write_state(state)
            return
        if route.startswith("packages/") and route.endswith("/package_files"):
            package_id = int(route.split("/")[1])
            item = next((x for x in state.get("packages", []) if x["id"] == package_id), None)
            if item is None:
                self.send_json(404, {"message": "not found"})
            else:
                files = []
                for index, file in enumerate(item.get("files", []), 1):
                    files.append({
                        "id": file.get("id", index),
                        "package_id": package_id,
                        "file_name": file["file_name"],
                        "size": file["size"],
                        "file_sha256": file["file_sha256"],
                    })
                page = int(query.get("page", ["1"])[0])
                per_page = int(query.get("per_page", ["100"])[0])
                page_size = min(per_page, state.get("file_page_size", per_page))
                total_pages = (len(files) + page_size - 1) // page_size
                start = (page - 1) * page_size
                next_page = "" if page >= total_pages else str(page + 1)
                self.send_json(200, files[start:start + page_size], {
                    "X-Page": str(page),
                    "X-Per-Page": str(page_size),
                    "X-Next-Page": next_page,
                    "X-Total-Pages": str(total_pages),
                })
            write_state(state)
            return
        self.send_json(404, {"message": "unknown route"})
        write_state(state)

    def do_DELETE(self):
        state = read_state()
        if not self.authorized(state):
            return
        route = urlparse(self.path).path.removeprefix("/api/v4/projects/83955143/")
        if not route.startswith("packages/"):
            self.send_json(404, {"message": "unknown route"})
            write_state(state)
            return
        package_id = int(route.split("/")[1])
        item = next((x for x in state.get("packages", []) if x["id"] == package_id), None)
        if state.get("forbidden_delete"):
            self.send_json(403, {"message": "forbidden"})
        elif item is None or state.get("delete_404_once"):
            state["delete_404_once"] = False
            state["packages"] = [x for x in state.get("packages", []) if x["id"] != package_id]
            state.setdefault("deleted", []).append({"id": package_id, "name": item["name"] if item else None, "version": item["version"] if item else None, "status": 404})
            self.send_empty(404)
        else:
            state["packages"] = [x for x in state.get("packages", []) if x["id"] != package_id]
            state.setdefault("deleted", []).append({"id": package_id, "name": item["name"], "version": item["version"], "status": 204})
            self.send_empty(204)
        write_state(state)

    def do_PUT(self):
        state = read_state()
        if not self.authorized(state):
            return
        route = urlparse(self.path).path.removeprefix("/api/v4/projects/83955143/packages/generic/")
        if route == urlparse(self.path).path:
            self.send_json(404, {"message": "unknown route"})
            write_state(state)
            return
        parts = [unquote(part) for part in route.split("/")]
        if len(parts) < 3:
            self.send_json(400, {"message": "bad upload path"})
            write_state(state)
            return
        package_name, version = parts[0], parts[1]
        file_name = "/".join(parts[2:])
        length = int(self.headers.get("Content-Length", "0"))
        content = self.rfile.read(length)
        fail_file = state.get("fail_upload_once")
        if fail_file == file_name:
            state["fail_upload_once"] = ""
            self.send_json(500, {"message": "transient upload failure"})
            write_state(state)
            return
        package = next((x for x in state.get("packages", []) if x["name"] == package_name and x["version"] == version and x["package_type"] == "generic"), None)
        if package is None:
            package = {"id": state.get("next_id", 500), "name": package_name, "version": version, "package_type": "generic", "files": []}
            state["next_id"] = package["id"] + 1
            state.setdefault("packages", []).append(package)
        digest = __import__("hashlib").sha256(content).hexdigest()
        package["files"] = [x for x in package.get("files", []) if x["file_name"] != file_name]
        package["files"].append({"file_name": file_name, "size": len(content), "file_sha256": digest, "content": content.decode(errors="replace")})
        state.setdefault("uploads", []).append({"name": package_name, "version": version, "file": file_name})
        self.send_json(201, {"message": "created"})
        write_state(state)


server = HTTPServer(("127.0.0.1", 0), Handler)
with open(port_path, "w", encoding="utf-8") as handle:
    handle.write(str(server.server_address[1]))
server.serve_forever()
PY

write_state() {
  python3 - "$state_file" "$expected_file" "$1" <<'PY'
import hashlib
import json
import sys

state_path, expected_path, mode = sys.argv[1:]
with open(expected_path, encoding="utf-8") as handle:
    expected = json.load(handle)

def package(package_id, name, version, flavor="normal", names=None, wrong=False):
    files = []
    names = list(names if names is not None else expected[flavor])
    for file_name in names:
        source = expected[flavor][file_name]
        digest = source["sha"]
        size = source["size"]
        if wrong:
            digest = "0" * 64
            size += 1
        files.append({"file_name": file_name, "size": size, "file_sha256": digest})
    return {"id": package_id, "name": name, "version": version, "package_type": "generic", "files": files}

state = {"packages": [], "page_size": 100, "next_id": 500}
if mode == "empty":
    state["packages"] = []
elif mode == "reuse":
    state["packages"] = [
        package(10, "xmsf-runtime", "1.1.3"),
        package(11, "xmsf-runtime-vc105", "1.1.3", "vc105"),
        package(12, "xmsf-runtime", "1.1.2", wrong=True),
        package(13, "xmsf-runtime-extra", "1.1.3", wrong=True),
    ]
    state["packages"][2]["include_when_version_filter_differs"] = True
    state["packages"][3]["include_when_version_filter_differs"] = True
    state["page_size"] = 1
    state["file_page_size"] = 1
elif mode == "conflict":
    state["packages"] = [
        package(20, "xmsf-runtime", "1.1.3", wrong=True),
        package(21, "xmsf-runtime", "1.1.3", names=["xmsf-runtime.apk"], wrong=True),
        package(22, "xmsf-runtime-vc105", "1.1.3", "vc105"),
        package(23, "xmsf-runtime", "1.1.2", wrong=True),
        package(24, "xmsf-runtime-extra", "1.1.3", wrong=True),
    ]
    state["packages"][3]["include_when_version_filter_differs"] = True
    state["packages"][4]["include_when_version_filter_differs"] = True
    state["page_size"] = 2
elif mode == "partial":
    state["packages"] = [package(30, "xmsf-runtime", "1.1.3", names=["xmsf-runtime.apk"])]
elif mode == "delete404":
    state["packages"] = [package(40, "xmsf-runtime", "1.1.3", wrong=True)]
    state["delete_404_once"] = True
elif mode == "unauthorized_list":
    state["packages"] = [package(49, "xmsf-runtime", "1.1.3", wrong=True)]
    state["force_unauthorized"] = True
elif mode == "forbidden_list":
    state["packages"] = [package(50, "xmsf-runtime", "1.1.3", wrong=True)]
    state["forbidden_list"] = True
elif mode == "forbidden_delete":
    state["packages"] = [package(60, "xmsf-runtime", "1.1.3", wrong=True)]
    state["forbidden_delete"] = True
elif mode == "broken_pagination":
    state["packages"] = [
        package(70, "xmsf-runtime", "1.1.2", wrong=True),
        package(71, "xmsf-runtime-extra", "1.1.3", wrong=True),
    ]
    for item in state["packages"]:
        item["include_when_version_filter_differs"] = True
    state["page_size"] = 1
    state["broken_pagination"] = True
elif mode == "upload_retry":
    state["packages"] = []
    state["fail_upload_once"] = "xmsf-runtime.apk.sha256"
else:
    raise SystemExit(f"unknown test case: {mode}")
with open(state_path, "w", encoding="utf-8") as handle:
    json.dump(state, handle)
PY
}

assert_state() {
  python3 - "$state_file" "$expected_file" "$1" <<'PY'
import json
import sys

state_path, expected_path, mode = sys.argv[1:]
with open(state_path, encoding="utf-8") as handle:
    state = json.load(handle)
with open(expected_path, encoding="utf-8") as handle:
    expected = json.load(handle)
packages = state.get("packages", [])
def exact(name, version):
    return [x for x in packages if x["name"] == name and x["version"] == version and x["package_type"] == "generic"]
def fail(message):
    raise SystemExit(f"FAIL {mode}: {message}")
if any(not call.get("token_ok") for call in state.get("calls", [])):
    fail("GitLab request did not use the dedicated PRIVATE-TOKEN")
if mode == "validate":
    if state.get("calls"):
        fail("validate mode made an API request")
elif mode == "empty":
    if state.get("calls") or state.get("deleted") or state.get("uploads"):
        fail("empty fixture was unexpectedly used")
elif mode == "reuse":
    if state.get("deleted") or state.get("uploads"):
        fail("identical package was not reused")
    if len(exact("xmsf-runtime", "1.1.3")) != 1 or len(exact("xmsf-runtime-vc105", "1.1.3")) != 1:
        fail("normal/vc105 packages were not isolated")
    if not any("package_name=xmsf-runtime" in x["path"] and "package_version=1.1.3" in x["path"] for x in state.get("calls", [])):
        fail("exact name/version query parameters were not sent")
    package_list_pages = [x for x in state.get("calls", []) if x["method"] == "GET" and "/packages?" in x["path"]]
    package_file_pages = [x for x in state.get("calls", []) if x["method"] == "GET" and "/package_files?" in x["path"]]
    if len(package_list_pages) < 2:
        fail("package-list pagination was not exercised")
    if len(package_file_pages) < 3:
        fail("package-file pagination was not exercised")
elif mode == "conflict":
    deleted = {(x["id"], x["name"], x["version"]) for x in state.get("deleted", [])}
    if deleted != {(20, "xmsf-runtime", "1.1.3"), (21, "xmsf-runtime", "1.1.3")}:
        fail(f"deletion crossed exact package/version boundary: {deleted}")
    if len(exact("xmsf-runtime", "1.1.3")) != 1 or len(exact("xmsf-runtime-vc105", "1.1.3")) != 1:
        fail("replacement did not preserve flavor isolation")
    if len(exact("xmsf-runtime", "1.1.2")) != 1 or len(exact("xmsf-runtime-extra", "1.1.3")) != 1:
        fail("other version or similar package was removed")
    normal = exact("xmsf-runtime", "1.1.3")[0]
    actual = {x["file_name"]: x["file_sha256"] for x in normal["files"]}
    if actual != {k: v["sha"] for k, v in expected["normal"].items()}:
        fail("replacement package does not match the three local assets")
    upload_order = [x["file"] for x in state.get("uploads", [])]
    if upload_order[-1] != "xmsf-runtime-manifest.json":
        fail("manifest was not uploaded last")
elif mode == "partial":
    if len(state.get("deleted", [])) != 1 or len(exact("xmsf-runtime", "1.1.3")) != 1:
        fail("partial package was not replaced as a whole")
    if len(exact("xmsf-runtime", "1.1.3")[0]["files"]) != 3:
        fail("partial package was not completed")
elif mode == "delete404":
    if not state.get("deleted") or state["deleted"][0]["status"] != 404:
        fail("404 deletion scenario was not exercised")
    if len(exact("xmsf-runtime", "1.1.3")) != 1 or len(exact("xmsf-runtime", "1.1.3")[0]["files"]) != 3:
        fail("404 was not idempotently verified before full upload")
elif mode == "unauthorized_list":
    if state.get("uploads") or state.get("deleted"):
        fail("401 package query was treated as an empty package list")
elif mode == "forbidden_list":
    if state.get("uploads") or state.get("deleted"):
        fail("403 package query was treated as an empty package list")
elif mode == "forbidden_delete":
    if state.get("uploads") or state.get("deleted"):
        fail("403 deletion fell through to upload or altered packages")
elif mode == "broken_pagination":
    if state.get("uploads") or state.get("deleted"):
        fail("incomplete pagination did not fail closed")
elif mode == "upload_retry_first":
    if len(state.get("uploads", [])) != 1 or state["uploads"][0]["file"] != "xmsf-runtime.apk":
        fail("first upload did not leave exactly the intended partial package")
elif mode == "upload_retry_first":
    package = exact("xmsf-runtime-vc105", "1.1.3")
    if len(package) != 1 or len(package[0]["files"]) != 1:
        fail("failed first attempt did not leave exactly one partial asset")
elif mode == "upload_retry":
    package = exact("xmsf-runtime-vc105", "1.1.3")
    if len(package) != 1 or len(package[0]["files"]) != 3:
        fail("rerun did not restore the full package")
    if len(state.get("deleted", [])) != 1:
        fail("rerun did not remove the prior partial package")
else:
    fail("unknown assertion")
print(f"PASS {mode}")
PY
}

run_publisher() {
  local flavor="$1"
  local version_code="$2"
  local apk="$3"
  env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY \
    no_proxy='*' NO_PROXY='*' \
    ANDROID_HOME="$tmp_dir/no-android-sdk" ANDROID_SDK_ROOT="$tmp_dir/no-android-sdk" \
    CI_JOB_TOKEN='must-not-be-used' GITLAB_API_TOKEN='test-masked-api-token' \
    GITLAB_API_URL="${4:-http://127.0.0.1:$server_port/api/v4}" \
    GITLAB_PROJECT_ID=83955143 \
    bash "$publisher" publish --apk "$apk" --flavor "$flavor" \
      --version 1.1.3 --version-code "$version_code" \
      --min-manager-version-code 0 --commit test1234
}

python3 - "$state_file" <<'PY'
import json
import sys
with open(sys.argv[1], "w", encoding="utf-8") as handle:
    json.dump({"packages": [], "next_id": 500}, handle)
PY
python3 "$tmp_dir/mock_gitlab.py" "$state_file" "$port_file" > "$tmp_dir/mock-server.log" &
server_pid="$!"
for _ in {1..100}; do
  [[ -s "$port_file" ]] && break
  kill -0 "$server_pid" 2>/dev/null || { printf 'Mock GitLab API failed to start\n' >&2; exit 1; }
  sleep 0.1
done
[[ -s "$port_file" ]] || { printf 'Mock GitLab API did not publish its port\n' >&2; exit 1; }
server_port="$(<"$port_file")"

# Local payload validation must be usable without credentials and make no API calls.
write_state empty
env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY \
  ANDROID_HOME="$tmp_dir/no-android-sdk" ANDROID_SDK_ROOT="$tmp_dir/no-android-sdk" \
  GITLAB_API_URL="http://127.0.0.1:$server_port/api/v4" \
  bash "$publisher" validate --apk "$tmp_dir/apk/universal_normal_xmsf_v1.1.3_release.apk" \
    --flavor normal --version 1.1.3 --version-code 1003003000 --commit test1234
assert_state validate

# A CI_JOB_TOKEN alone must not substitute for the required dedicated masked token.
write_state empty
if env -u GITLAB_API_TOKEN -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY \
  CI_JOB_TOKEN='must-not-be-used' \
  ANDROID_HOME="$tmp_dir/no-android-sdk" ANDROID_SDK_ROOT="$tmp_dir/no-android-sdk" \
  GITLAB_API_URL="http://127.0.0.1:$server_port/api/v4" \
  bash "$publisher" publish --apk "$tmp_dir/apk/universal_normal_xmsf_v1.1.3_release.apk" \
    --flavor normal --version 1.1.3 --version-code 1003003000 --commit test1234; then
  printf 'FAIL missing-token: CI_JOB_TOKEN was accepted as a fallback\n' >&2
  exit 1
fi
assert_state empty

# Reuse identical normal assets while fuzzy matches, wrong versions and vc105 exist on other pages.
write_state reuse
run_publisher normal 1003003000 "$tmp_dir/apk/universal_normal_xmsf_v1.1.3_release.apk"
assert_state reuse

# Delete every exact conflicting normal package record, never adjacent package/version/flavor records.
write_state conflict
run_publisher normal 1003003000 "$tmp_dir/apk/universal_normal_xmsf_v1.1.3_release.apk"
assert_state conflict

# A partial three-file package is removed and re-created as a whole.
write_state partial
run_publisher normal 1003003000 "$tmp_dir/apk/universal_normal_xmsf_v1.1.3_release.apk"
assert_state partial

# An already-deleted exact id (HTTP 404) is accepted only after exact absence is re-queried.
write_state delete404
run_publisher normal 1003003000 "$tmp_dir/apk/universal_normal_xmsf_v1.1.3_release.apk"
assert_state delete404

# Authentication, permission and pagination failures must stop before deletion or upload.
write_state unauthorized_list
if run_publisher normal 1003003000 "$tmp_dir/apk/universal_normal_xmsf_v1.1.3_release.apk"; then
  printf 'FAIL unauthorized_list: expected failure\n' >&2
  exit 1
fi
assert_state unauthorized_list

write_state forbidden_list
if run_publisher normal 1003003000 "$tmp_dir/apk/universal_normal_xmsf_v1.1.3_release.apk"; then
  printf 'FAIL forbidden_list: expected failure\n' >&2
  exit 1
fi
assert_state forbidden_list

write_state forbidden_delete
if run_publisher normal 1003003000 "$tmp_dir/apk/universal_normal_xmsf_v1.1.3_release.apk"; then
  printf 'FAIL forbidden_delete: expected failure\n' >&2
  exit 1
fi
assert_state forbidden_delete

write_state broken_pagination
if run_publisher normal 1003003000 "$tmp_dir/apk/universal_normal_xmsf_v1.1.3_release.apk"; then
  printf 'FAIL broken_pagination: expected failure\n' >&2
  exit 1
fi
assert_state broken_pagination

write_state empty
if run_publisher normal 1003003000 "$tmp_dir/apk/universal_normal_xmsf_v1.1.3_release.apk" http://127.0.0.1:1/api/v4; then
  printf 'FAIL network_error: expected API connection failure\n' >&2
  exit 1
fi
assert_state empty

# A partial upload can be safely repaired by the next serial publish job.
write_state upload_retry
if run_publisher vc105 105 "$tmp_dir/apk/universal_vc105_xmsf_v1.1.3_release.apk"; then
  printf 'FAIL upload_retry_first: expected injected sidecar upload failure\n' >&2
  exit 1
fi
assert_state upload_retry_first
run_publisher vc105 105 "$tmp_dir/apk/universal_vc105_xmsf_v1.1.3_release.apk"
assert_state upload_retry

printf 'All xmsf runtime publisher tests passed\n'
