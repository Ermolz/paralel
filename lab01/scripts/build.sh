#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
shared_dir="$(cd -- "$project_dir/.." && pwd)"
source "$shared_dir/scripts/env.sh"
if [[ ! -s "$OPENMPI_HOME/lib/mpi.jar" ]]; then
    echo 'OpenMPI Java bindings are missing. From the project root run: bash scripts/setup-openmpi.sh' >&2
    exit 1
fi
mkdir -p "$shared_dir/lib" "$project_dir/target/classes"
cp -- "$OPENMPI_HOME/lib/mpi.jar" "$shared_dir/lib/mpi.jar"
mapfile -d '' sources < <(find "$project_dir/src/main/java" -name '*.java' -print0)
(( ${#sources[@]} > 0 )) || { echo 'No Java sources found.' >&2; exit 1; }
javac --release 21 -encoding UTF-8 -cp "$shared_dir/lib/mpi.jar" \
    -d "$project_dir/target/classes" "${sources[@]}"
echo 'Java compilation completed.'
