#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
shared_dir="$(cd -- "$project_dir/.." && pwd)"
source "$shared_dir/scripts/env.sh"
processes="${1:-7}"
program="${2:-student}"
hostfile="${LAB_HOSTFILE:-$shared_dir/hostfile}"
profile="${LAB_JVM_PROFILE:-capacity}"
if [[ ! "$processes" =~ ^[1-9][0-9]{0,3}$ ]] || (( processes > 1024 )); then
    echo 'Process count must be an integer from 1 to 1024.' >&2
    exit 2
fi
case "$program" in
    student) main_class=com.mathpar.NAUKMA.stud2026.GR03.yermolovych.HelloWorld ;;
    example) main_class=com.mathpar.NAUKMA.examples.HelloWorldParallel ;;
    *) echo 'Program must be student or example.' >&2; exit 2 ;;
esac
slots="$(sed -nE 's/^localhost[[:space:]]+slots=([0-9]+)[[:space:]]*$/\1/p' "$hostfile")"
if [[ ! "$slots" =~ ^[1-9][0-9]{0,3}$ ]] || (( slots > 1024 || processes > slots )); then
    echo "Set enough slots first: ./lab slots $processes" >&2
    exit 2
fi
java_options=()
mpi_options=()
case "$profile" in
    capacity)
        java_options=(-Xms4m -Xmx16m -Xss256k -Xint -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:-UsePerfData)
        mpi_options=(--mca btl_vader_segment_size 1048576)
        ;;
    compact)
        java_options=(-Xms8m -Xmx32m -Xss256k -Xint -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:-UsePerfData)
        ;;
    default) ;;
    *) echo 'LAB_JVM_PROFILE must be capacity, compact or default.' >&2; exit 2 ;;
esac
cd -- "$project_dir"
exec mpirun --hostfile "$hostfile" -np "$processes" \
    --oversubscribe --bind-to none --mca mpi_yield_when_idle 1 \
    "${mpi_options[@]}" \
    --timeout "${LAB_TIMEOUT:-240}" \
    java "${java_options[@]}" -Djava.library.path="$OPENMPI_HOME/lib" \
    -cp "$project_dir/target/classes:$shared_dir/lib/mpi.jar" "$main_class"
