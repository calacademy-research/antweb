#!/bin/bash
# Release gate for a pull request: tools/release/gate.sh PR [--nightly-checked] [--no-review "reason"]
# (tools/release/gate.py; README.md next to it).
exec python3 "$(dirname "$0")/gate.py" "$@"
