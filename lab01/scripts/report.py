#!/usr/bin/env python3
import datetime as dt
import html
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent


def print_short(data):
    print("MPI CAPACITY RESEARCH | Yermolovych | Group 3 | 2026")
    print(f"CPU: {data['physical_cores']} cores / {data['logical_cpus']} threads | Profile: {data['profile']}")
    print("Slots=np  Ranks   Time, s  Result  Note")
    print("-" * 65)
    for row in data["trials"]:
        note = row["reason"] if row["status"] != "PASS" else row["phase"]
        if note == "LAUNCH_FAILED" and "exiting improperly" in (ROOT / row["log"]).read_text(errors="replace"):
            note = "ABNORMAL_MPI_EXIT"
        print(f"{row['slots']:8}  {row['unique_ranks']:5}  {row['seconds']:8.2f}  {row['status']:6}  {note}")
    print("-" * 65)
    maximum = max((r["slots"] for r in data["trials"] if r["status"] == "PASS"), default=0)
    final = [r for r in data["trials"] if r["slots"] == maximum
             and r.get("memory_guard_basis", "ram") == data.get("memory_guard_basis", "ram")]
    print(f"Largest successful count: {maximum}. Above 1024: not tested.")
    print(f"Final checks at {maximum}: {sum(r['status'] == 'PASS' for r in final)}/{len(final)} passed.")
    if any(r["status"] != "PASS" for r in final):
        print("Completion at the maximum was not fully repeatable.")
    if any(r["reason"] == "MEMORY_GUARD" for r in data["trials"]):
        print("Early RAM guard aborted a trial; final checks allowed swap.")


def main():
    result_file = ROOT / "results/research.json"
    if not result_file.exists():
        raise SystemExit("No measurements yet. Run ./lab research first.")
    data = json.loads(result_file.read_text())
    if not data["complete"]:
        raise SystemExit("Research is still running. Wait for completion before generating the report.")
    if sys.argv[1:] == ["--short"]:
        print_short(data)
        return
    out_dir = ROOT / "results"
    demo_host = out_dir / "demo.hostfile"
    demo_host.write_text("localhost slots=7\n")
    env = os.environ.copy()
    env.update(LAB_HOSTFILE=str(demo_host), LAB_JVM_PROFILE=data["profile"])
    demo = subprocess.run(["bash", "scripts/launch.sh", "7", "student"], cwd=ROOT,
                          env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
    output = demo.stdout.strip()
    (out_dir / "hello-7.log").write_text(output + "\n")
    ranks = [int(value) for value in re.findall(r"^Hello World! rank=(\d+), size=7,", output, re.M)]
    if demo.returncode or sorted(ranks) != list(range(7)):
        raise SystemExit("HelloWorld verification failed:\n" + output)

    border = "=" * 91
    lines = [border,
             "PARALLEL PROGRAMMING | ASSIGNMENT 1",
             "OpenMPI + Java in Ubuntu 24.04 / WSL2",
             "Student: Yermolovych    Group: 3    Year: 2026",
             "Package: com.mathpar.NAUKMA.stud2026.GR03.yermolovych",
             f"Report: {dt.datetime.now().astimezone().isoformat(timespec='seconds')}",
             border, "CPU AND EXECUTION ENVIRONMENT",
             f"Processor:           {data['cpu']}",
             f"Physical cores:      {data['physical_cores']}",
             f"Logical processors:  {data['logical_cpus']}",
             f"WSL available CPUs:  {data['available_cpus']}",
             f"WSL memory / swap:   {data['memory_mib'] / 1024:.2f} / {data['swap_mib'] / 1024:.2f} GiB",
             "OpenMPI:             4.1.6 (Java bindings enabled)",
             "Java:                OpenJDK 21", "Maven:               3.8.7",
             "", "HELLO WORLD | slots=7, processes=7"]
    lines.extend(output.splitlines())
    lines += ["PASS: all 7 ranks appeared exactly once; MPI completed successfully.",
              "", "CAPACITY RESEARCH | slots and process count changed together",
              f"Profile: {data['profile']}; same JVM / MPI options in every trial",
              "JVM: " + " ".join(data["java_options"][:4]),
              "     " + " ".join(data["java_options"][4:]),
              "MPI: --oversubscribe --bind-to none --mca mpi_yield_when_idle 1",
              *( ["     --mca btl_vader_segment_size 1048576 (1 MiB shared-memory segment)"]
                 if data["profile"] == "capacity" else [] ),
              f"Timeout: {data['timeout_seconds']} s per trial; output checked for every rank.",
              "", "Slots  Processes  Verified  Result  Time (s)  Phase / reason",
              "-" * 91]
    for row in data["trials"]:
        detail = row["phase"] if row["status"] == "PASS" else row["reason"]
        if detail == "LAUNCH_FAILED" and "exiting improperly" in (ROOT / row["log"]).read_text(errors="replace"):
            detail = "ABNORMAL_MPI_EXIT"
        lines.append(f"{row['slots']:5}  {row['processes']:9}  {row['unique_ranks']:8}  "
                     f"{row['status']:6}  {row['seconds']:8.2f}  {detail}")
    successful = [row["slots"] for row in data["trials"] if row["status"] == "PASS"]
    maximum = max(successful, default=0)
    repeats = [row for row in data["trials"] if row["slots"] == maximum
               and row.get("memory_guard_basis", "ram") == data.get("memory_guard_basis", "ram")]
    stable = all(row["status"] == "PASS" for row in repeats)
    lines += ["-" * 91,
              f"Largest successful tested count: {maximum} MPI processes.",
              f"Final validation at that count: {sum(row['status'] == 'PASS' for row in repeats)}/{len(repeats)} trials passed."]
    if maximum == 1024:
        lines += ["RESULT: maximum in the requested range = 1024 MPI processes.",
                  "The assignment ceiling was reached. Counts above 1024 were not tested.",
                  "The absolute machine limit has therefore NOT been established."]
        if not stable:
            lines.append("At 1024, completion was not fully repeatable: an abnormal MPI exit also occurred.")
    else:
        lines += ["RESULT: operational limit for this WSL / JVM configuration and timeout.",
                  "Check the adjacent failed trial and repeat results; load can change the limit."]
    if any(row["reason"] == "MEMORY_GUARD" for row in data["trials"]):
        lines += ["Earlier MEMORY_GUARD trials used a RAM-only reserve and were stopped protectively.",
                  "Final trials include existing swap in the reserve; no WSL memory settings changed."]
    lines.append(f"Peak system swap during final trials: {max((row.get('peak_system_swap_mib', 0) for row in repeats), default=0)} MiB.")
    lines += ["Slots represent process capacity, not newly created physical CPU cores.",
              "Elapsed time includes JVM startup, MPI initialization, output and shutdown.",
              "Evidence: results/research.json and per-trial .log / .hostfile files.", border]
    transcript = "\n".join(lines) + "\n"
    print(transcript, end="")
    (out_dir / "output.txt").write_text(transcript)
    (out_dir / "report.md").write_text("# Assignment 1 — measured results\n\n```text\n" + transcript + "```\n")
    page = """<!doctype html><html lang="en"><meta charset="utf-8">
<title>Assignment 1 — Yermolovych</title><style>
*{box-sizing:border-box}body{margin:0;padding:28px;background:#10141b;color:#e4eaf3}
main{width:max-content;min-width:1100px;margin:auto;background:#161e29;border:1px solid #354155;border-radius:12px;overflow:hidden}
header{font:15px system-ui;padding:15px 24px;background:#202b3a;color:#a9bad0;border-bottom:1px solid #354155}
pre{margin:0;padding:24px 30px;font:15px/1.55 Consolas,'DejaVu Sans Mono',monospace;white-space:pre}
.pass{color:#95e0ad}.result{color:#80c9ff;font-weight:bold}.fail{color:#ffc392}
</style><main><header>Ubuntu 24.04 / WSL2 &nbsp; · &nbsp; Actual output of ./lab report</header><pre>"""
    for line in lines:
        style = "result" if line.startswith(("RESULT:", "PARALLEL PROGRAMMING", "Largest successful")) else (
            "pass" if "PASS" in line else ("fail" if "FAIL" in line else ""))
        page += f'<span class="{style}">{html.escape(line)}</span>\n'
    page += "</pre></main></html>"
    (out_dir / "report.html").write_text(page)
    sweep = [row for row in data["trials"] if row["phase"] == "sweep" and row["slots"] != 1024]
    sweep += [row for row in data["trials"] if row["slots"] in (768, 896) and row["status"] == "PASS"]
    if maximum:
        sweep.append(next(row for row in reversed(data["trials"]) if row["slots"] == maximum and row["status"] == "PASS"))
    compact = """<!doctype html><html lang="en"><meta charset="utf-8"><title>Assignment 1 — Results</title>
<style>*{box-sizing:border-box}body{margin:0;background:#101824;color:#e4edf7;font:12px/1.3 system-ui,sans-serif}
main{max-width:552px;margin:auto;padding:12px 20px}h1{font-size:19px;margin:0 0 3px}p{margin:4px 0;color:#b1c3d8}
.path{font:10px monospace;color:#99aeca}.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:11px 0}
.stat{background:#1d2c40;padding:8px 10px;border:1px solid #34465e;border-radius:7px}.stat strong{display:block;font-size:23px;color:#8dcfff}
h2{font-size:12px;letter-spacing:.5px;margin:12px 0 5px;color:#a7e5b9}pre{font:11px/1.35 Consolas,monospace;background:#152236;padding:8px 10px;margin:0;border-radius:6px}
table{border-collapse:collapse;width:100%;font:11px/1.3 Consolas,monospace}td,th{text-align:right;padding:2px 5px;border-bottom:1px solid #29374b}th{color:#a4bad4}td:first-child,th:first-child{text-align:left}.ok{color:#a2e7b4}.note{font-size:11px;color:#edc493;margin-top:9px}.foot{font-size:10px;color:#9caec4;margin-top:6px}
</style><main><h1>PARALLEL PROGRAMMING · ASSIGNMENT 1</h1><p>Yermolovych · Group 3 · 2026 · Ubuntu / WSL2</p>
<div class="path">com.mathpar.NAUKMA.stud2026.GR03.yermolovych</div>"""
    compact += f'<div class="stats"><div class="stat"><strong>{data["physical_cores"]}</strong>Physical cores</div><div class="stat"><strong>{data["logical_cpus"]}</strong>CPU threads</div><div class="stat"><strong>{maximum}</strong>MPI processes reached</div></div>'
    compact += '<p>Intel Core i7-6900K · OpenMPI 4.1.6 · Java 21</p>'
    compact += '<h2>HELLO WORLD — 7 / 7 RANKS VERIFIED</h2><pre>'
    for rank in ranks:
        compact += f'Hello World! rank={rank}, size=7, student=yermolovych\n'
    compact += '</pre><h2>SUCCESSFUL CAPACITY TRIALS</h2><table><tr><th>Slots / processes</th><th>Ranks</th><th>Time, s</th><th>Status</th></tr>'
    for row in sweep:
        compact += f'<tr><td>{row["slots"]}</td><td>{row["unique_ranks"]}</td><td>{row["seconds"]:.2f}</td><td class="ok">PASS</td></tr>'
    compact += '</table>'
    limit_note = '1024 reached the assignment ceiling.' if maximum == 1024 else f'Largest successful tested count: {maximum}.'
    failure_note = ' An abnormal MPI exit was also recorded.' if not stable else ''
    compact += f'<div class="note">{limit_note} Final validation: {sum(row["status"] == "PASS" for row in repeats)}/{len(repeats)} passed.{failure_note}</div>'
    compact += f'<div class="foot">{data["physical_cores"]} cores / {data["logical_cpus"]} threads are shared by the MPI processes. WSL RAM / swap: {data["memory_mib"] / 1024:.2f} / {data["swap_mib"] / 1024:.2f} GiB. Profile: {data["profile"]}; {data["timeout_seconds"]} s timeout. Above 1024: not tested. Full output and every failed trial: output.txt / research.json.</div></main></html>'
    (out_dir / "summary.html").write_text(compact)


if __name__ == "__main__":
    main()
