#!/usr/bin/env python3
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import resource
import signal
import subprocess
import time

ROOT = Path(__file__).resolve().parent.parent
HOSTFILE = ROOT.parent / "hostfile"


def meminfo():
    return {key: int(value.split()[0]) for key, value in
            (line.split(":", 1) for line in Path("/proc/meminfo").read_text().splitlines())}


def processes():
    result = {}
    for entry in Path("/proc").iterdir():
        if not entry.name.isdigit():
            continue
        try:
            fields = (entry / "stat").read_text().rsplit(")", 1)[1].split()
            result[int(entry.name)] = {
                "state": fields[0], "ppid": int(fields[1]), "pgrp": int(fields[2]),
                "threads": int(fields[17]), "start": fields[19],
            }
        except (OSError, ValueError, IndexError):
            continue
    return result


def family(snapshot, root_pid):
    chosen = {root_pid}
    while True:
        added = {pid for pid, item in snapshot.items()
                 if item["ppid"] in chosen or item["pgrp"] == root_pid}
        if added <= chosen:
            return {pid: snapshot[pid] for pid in chosen if pid in snapshot}
        chosen |= added


def stop_job(proc, tracked):
    if proc.poll() is None:
        try:
            os.killpg(proc.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            proc.wait(timeout=8)
        except subprocess.TimeoutExpired:
            pass
    current = processes()
    for pid, start in tracked.items():
        if pid in current and current[pid]["start"] == start and current[pid]["state"] != "Z":
            try:
                os.kill(pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
    if proc.poll() is None:
        proc.kill()
    proc.wait(timeout=10)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--profile", choices=["capacity", "compact", "default"], default="capacity")
    parser.add_argument("--timeout", type=int, default=240)
    parser.add_argument("--counts", type=int, nargs="+", help="Run specified counts without boundary search")
    parser.add_argument("--resume", type=Path, help="Retain prior trials with the same launch profile")
    args = parser.parse_args()
    if args.timeout < 10 or any(n < 1 or n > 1024 for n in args.counts or []):
        parser.error("Counts must be 1..1024; timeout must be at least 10 seconds.")

    stamp = dt.datetime.now().astimezone().strftime("%Y%m%d-%H%M%S")
    run_dir = ROOT / "results" / "runs" / stamp
    run_dir.mkdir(parents=True, exist_ok=False)
    cpu_rows = [line.split(",") for line in subprocess.check_output(
        ["lscpu", "-p=CORE,SOCKET"], text=True).splitlines() if not line.startswith("#")]
    cpu_model = next(line.split(":", 1)[1].strip() for line in
                     Path("/proc/cpuinfo").read_text().splitlines() if line.startswith("model name"))
    before = meminfo()
    data = {
        "started": dt.datetime.now().astimezone().isoformat(timespec="seconds"),
        "cpu": cpu_model, "physical_cores": len(set(map(tuple, cpu_rows))),
        "logical_cpus": os.cpu_count(), "available_cpus": len(os.sched_getaffinity(0)),
        "memory_mib": before["MemTotal"] // 1024, "swap_mib": before["SwapTotal"] // 1024,
        "open_files_limit": resource.getrlimit(resource.RLIMIT_NOFILE),
        "process_limit": resource.getrlimit(resource.RLIMIT_NPROC),
        "profile": args.profile, "timeout_seconds": args.timeout,
        "memory_guard_mib": 1024, "memory_guard_basis": "ram+swap", "range": [16, 1024],
        "java_options": ((["-Xms4m", "-Xmx16m"] if args.profile == "capacity" else ["-Xms8m", "-Xmx32m"]) +
                         ["-Xss256k", "-Xint", "-XX:+UseSerialGC", "-XX:ActiveProcessorCount=1", "-XX:-UsePerfData"]
                         if args.profile != "default" else []),
        "mpi_options": ["--oversubscribe", "--bind-to", "none", "--mca", "mpi_yield_when_idle", "1"],
        "trials": [], "complete": False,
    }
    if args.profile == "capacity":
        data["mpi_options"] += ["--mca", "btl_vader_segment_size", "1048576"]
    if args.resume:
        previous = json.loads(args.resume.read_text())
        for key in ("profile", "java_options", "mpi_options", "cpu", "memory_mib", "swap_mib", "timeout_seconds"):
            if previous[key] != data[key]:
                parser.error(f"Cannot resume with different {key}.")
        data["trials"] = previous["trials"]
        for row in data["trials"]:
            row.setdefault("memory_guard_basis", previous.get("memory_guard_basis", "ram"))
        data["continued_from"] = str(args.resume.resolve())
    original_hostfile = HOSTFILE.read_bytes()

    def save():
        content = json.dumps(data, indent=2) + "\n"
        (run_dir / "research.json").write_text(content)
        (ROOT / "results" / "research.json").write_text(content)

    def trial(count, phase):
        index = len(data["trials"]) + 1
        label = f"{index:02d}-{count}-{phase}"
        host = run_dir / f"{label}.hostfile"
        host.write_text(f"localhost slots={count}\n")
        HOSTFILE.write_bytes(host.read_bytes())
        log = run_dir / f"{label}.log"
        env = os.environ.copy()
        env.update(LAB_HOSTFILE=str(host), LAB_JVM_PROFILE=args.profile, LAB_TIMEOUT=str(args.timeout))
        command = ["bash", str(ROOT / "scripts/launch.sh"), str(count), "student"]
        start = time.monotonic()
        tracked = {}
        minimum_memory = meminfo()["MemAvailable"] // 1024
        peak_swap = (meminfo()["SwapTotal"] - meminfo()["SwapFree"]) // 1024
        peak_processes = peak_threads = 0
        aborted = None
        print(f"START slots={count}, processes={count}, phase={phase}", flush=True)
        with log.open("w") as output:
            proc = subprocess.Popen(command, cwd=ROOT, env=env, stdout=output,
                                    stderr=subprocess.STDOUT, start_new_session=True)
            try:
                while proc.poll() is None:
                    current = family(processes(), proc.pid)
                    tracked.update({pid: item["start"] for pid, item in current.items()})
                    peak_processes = max(peak_processes, len(current))
                    peak_threads = max(peak_threads, sum(item["threads"] for item in current.values()))
                    memory = meminfo()
                    available = memory["MemAvailable"] // 1024
                    peak_swap = max(peak_swap, (memory["SwapTotal"] - memory["SwapFree"]) // 1024)
                    minimum_memory = min(minimum_memory, available)
                    if available + memory["SwapFree"] // 1024 < 1024:
                        aborted = "MEMORY_GUARD"
                        break
                    if time.monotonic() - start > args.timeout + 15:
                        aborted = "TIMEOUT"
                        break
                    time.sleep(0.5)
            finally:
                stop_job(proc, tracked)
        elapsed = round(time.monotonic() - start, 2)
        text = log.read_text(errors="replace")
        matches = re.findall(r"^Hello World! rank=(\d+), size=(\d+), host=\S+, student=yermolovych$", text, re.M)
        ranks = [int(rank) for rank, _ in matches]
        passed = (proc.returncode == 0 and aborted is None and sorted(ranks) == list(range(count))
                  and all(int(size) == count for _, size in matches))
        reason = aborted or ("OK" if passed else "LAUNCH_FAILED")
        if not passed and aborted is None:
            for marker, kind in [
                ("too many open files", "FILE_DESCRIPTOR_LIMIT"),
                ("system limit on number", "SYSTEM_PROCESS_LIMIT"),
                ("unable to create native thread", "NATIVE_THREAD_LIMIT"),
                ("pthread_create failed", "NATIVE_THREAD_LIMIT"),
                ("exiting improperly", "ABNORMAL_MPI_EXIT"),
                ("cannot allocate memory", "MEMORY_ALLOCATION_FAILED"),
                ("outofmemory", "MEMORY_ALLOCATION_FAILED"),
                ("timeout", "TIMEOUT"),
            ]:
                if marker in text.lower():
                    reason = kind
                    break
        row = {"slots": count, "processes": count, "phase": phase,
               "status": "PASS" if passed else "FAIL", "reason": reason,
               "exit_code": proc.returncode, "seconds": elapsed, "unique_ranks": len(set(ranks)),
               "observed_processes_peak": peak_processes, "observed_threads_peak": peak_threads,
               "minimum_mem_available_mib": minimum_memory, "log": str(log.relative_to(ROOT)),
               "peak_system_swap_mib": peak_swap, "memory_guard_basis": "ram+swap",
               "hostfile": str(host.relative_to(ROOT))}
        data["trials"].append(row)
        save()
        print(f"{row['status']:4} slots={count:4} ranks={len(set(ranks)):4}/{count:<4} "
              f"time={elapsed:7.2f}s reason={reason}", flush=True)
        time.sleep(1)
        return passed

    save()
    try:
        if args.counts:
            for count in args.counts:
                trial(count, "requested")
        else:
            for count in [16, 32, 64, 128, 256, 512, 1024]:
                trial(count, "sweep")
            passes = [row["slots"] for row in data["trials"] if row["status"] == "PASS"]
            low = max(passes, default=0)
            failures = [row["slots"] for row in data["trials"]
                        if row["status"] != "PASS" and row["slots"] > low]
            high = min(failures, default=1025)
            if low and high <= 1024:
                while high - low > 1:
                    middle = (low + high) // 2
                    if trial(middle, "boundary"):
                        low = middle
                    else:
                        high = middle
            if low:
                trial(low, "confirmation-1")
                trial(low, "confirmation-2")
            if high <= 1024:
                trial(high, "failure-confirmation")
            data["boundary_candidate"] = low
            data["next_failed_candidate"] = high if high <= 1024 else None
        data["complete"] = True
    finally:
        HOSTFILE.write_bytes(original_hostfile)
        data["finished"] = dt.datetime.now().astimezone().isoformat(timespec="seconds")
        save()
    print(f"Evidence saved: {run_dir.relative_to(ROOT)}", flush=True)


if __name__ == "__main__":
    main()
