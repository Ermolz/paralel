#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
processes="${1:-7}"
program="${2:-student}"
bash "$project_dir/scripts/build.sh"
exec bash "$project_dir/scripts/launch.sh" "$processes" "$program"
