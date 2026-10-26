#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
shared_dir="$(cd -- "$project_dir/.." && pwd)"
cd -- "$project_dir"
mkdir -p logs
source "$shared_dir/scripts/env.sh"
{
    date --iso-8601=seconds
    uname -sr
    java -version 2>&1
    mvn -version
    mpirun --version
    ompi_info | grep -i 'java bindings'
    sha256sum "$shared_dir/lib/mpi.jar" "$OPENMPI_HOME/lib/mpi.jar"
} > logs/environment.txt

for program in example student; do
    for processes in 1 7; do
        log="logs/${program}-${processes}.txt"
        timeout 60 bash scripts/run.sh "$processes" "$program" > "$log" 2>&1
        python3 - "$log" "$processes" "$program" <<'PY'
import pathlib
import re
import sys

path, count, program = sys.argv[1], int(sys.argv[2]), sys.argv[3]
output = pathlib.Path(path).read_text(encoding="utf-8")
if program == "example":
    ranks = [int(rank) for rank in re.findall(r"^Proc num (\d+) Hello World$", output, re.M)]
else:
    matches = re.findall(r"^Hello World! rank=(\d+), size=(\d+), host=\S+, student=yermolovych$", output, re.M)
    ranks = [int(rank) for rank, _ in matches]
    if any(int(size) != count for _, size in matches):
        raise SystemExit(f"FAIL: wrong communicator size in {path}\n{output}")
if sorted(ranks) != list(range(count)):
    raise SystemExit(f"FAIL: expected ranks 0..{count - 1} exactly once in {path}\n{output}")
print(f"PASS: {program}, {count} process(es), every rank appeared exactly once.")
PY
    done
done
echo 'PASS: OpenMPI Java integration verified.'
