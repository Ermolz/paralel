# Assignment 1 — measured results

```text
===========================================================================================
PARALLEL PROGRAMMING | ASSIGNMENT 1
OpenMPI + Java in Ubuntu 24.04 / WSL2
Student: Yermolovych    Group: 3    Year: 2026
Package: com.mathpar.NAUKMA.stud2026.GR03.yermolovych
Report: 2026-09-12T18:09:14+03:00
===========================================================================================
CPU AND EXECUTION ENVIRONMENT
Processor:           Intel(R) Core(TM) i7-6900K CPU @ 3.20GHz
Physical cores:      8
Logical processors:  16
WSL available CPUs:  16
WSL memory / swap:   23.43 / 6.00 GiB
OpenMPI:             4.1.6 (Java bindings enabled)
Java:                OpenJDK 21
Maven:               3.8.7

HELLO WORLD | slots=7, processes=7
Hello World! rank=6, size=7, host=DESKTOP-QDOO898, student=yermolovych
Hello World! rank=1, size=7, host=DESKTOP-QDOO898, student=yermolovych
Hello World! rank=5, size=7, host=DESKTOP-QDOO898, student=yermolovych
Hello World! rank=4, size=7, host=DESKTOP-QDOO898, student=yermolovych
Hello World! rank=3, size=7, host=DESKTOP-QDOO898, student=yermolovych
Hello World! rank=2, size=7, host=DESKTOP-QDOO898, student=yermolovych
Hello World! rank=0, size=7, host=DESKTOP-QDOO898, student=yermolovych
PASS: all 7 ranks appeared exactly once; MPI completed successfully.

CAPACITY RESEARCH | slots and process count changed together
Profile: capacity; same JVM / MPI options in every trial
JVM: -Xms4m -Xmx16m -Xss256k -Xint
     -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:-UsePerfData
MPI: --oversubscribe --bind-to none --mca mpi_yield_when_idle 1
     --mca btl_vader_segment_size 1048576 (1 MiB shared-memory segment)
Timeout: 240 s per trial; output checked for every rank.

Slots  Processes  Verified  Result  Time (s)  Phase / reason
-------------------------------------------------------------------------------------------
   16         16        16  PASS        1.01  sweep
   32         32        32  PASS        1.02  sweep
   64         64        64  PASS        1.57  sweep
  128        128       128  PASS        2.93  sweep
  256        256       256  PASS        7.61  sweep
  512        512       512  PASS       23.58  sweep
 1024       1024         0  FAIL      137.99  MEMORY_GUARD
  768        768       768  PASS       62.87  boundary
  896        896       896  PASS       99.01  boundary
 1024       1024      1024  FAIL      176.18  ABNORMAL_MPI_EXIT
 1024       1024      1024  PASS      174.46  requested
-------------------------------------------------------------------------------------------
Largest successful tested count: 1024 MPI processes.
Final validation at that count: 1/2 trials passed.
RESULT: maximum in the requested range = 1024 MPI processes.
The assignment ceiling was reached. Counts above 1024 were not tested.
The absolute machine limit has therefore NOT been established.
At 1024, completion was not fully repeatable: an abnormal MPI exit also occurred.
Earlier MEMORY_GUARD trials used a RAM-only reserve and were stopped protectively.
Final trials include existing swap in the reserve; no WSL memory settings changed.
Peak system swap during final trials: 1372 MiB.
Slots represent process capacity, not newly created physical CPU cores.
Elapsed time includes JVM startup, MPI initialization, output and shutdown.
Evidence: results/research.json and per-trial .log / .hostfile files.
===========================================================================================
```
