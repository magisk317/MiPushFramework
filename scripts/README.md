# Scripts

This directory contains MiPushFramework-owned automation only.

- `checks/`: module, god-file, and release-specific boundary checks.
- `ci/`: MiPush-specific test shards, Zygisk, runtime publication, and asset jobs.
- `config/`: task-selection, compatibility, dual-track, release-policy, and boundary baselines.
- `release/`: MiPush release guard, tag workflow, and GitLab asset publication.

Shared behavior lives in `magisk-ci-toolkit`. The consumer repository does not keep
compatibility copies of the shared selector, dual-track checker, or submodule check.
Use the toolkit interfaces with the project configuration files:

```bash
TOOLKIT_DIR="${MAGISK_CI_TOOLKIT_DIR:-.magisk-ci-toolkit}"
python3 "$TOOLKIT_DIR/ci/select_gradle_tasks.py" \
  --root "$PWD" --toolkit "$TOOLKIT_DIR" \
  --config scripts/config/android-test-tasks.json
python3 "$TOOLKIT_DIR/checks/dual_track_check.py" \
  --root "$PWD" --config scripts/config/dual-track.json
bash "$TOOLKIT_DIR/gradle/verify_consumer_compat.sh" \
  --root "$PWD" --toolkit "$TOOLKIT_DIR" \
  --config scripts/config/compat-check.json
```

The toolkit reference must be one full 40-character commit SHA in GitLab includes,
the resolver, and GitHub action variables. Do not restore a moving tag.
