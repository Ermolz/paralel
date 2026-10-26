package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import mpi.MPI;
import mpi.MPIException;

public final class HelloWorld {
    private HelloWorld() {
    }

    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int size = MPI.COMM_WORLD.getSize();
            String processor = MPI.getProcessorName();

            MPI.COMM_WORLD.barrier();

            String message = String.format(
                    "Hello World! rank=%d, size=%d, host=%s, student=yermolovych",
                    rank, size, processor);
            System.out.println(message);
        } finally {
            MPI.Finalize();
        }
    }
}

/*
Run:
cd ~/paralel
source scripts/env.sh
./lab lab01 build
mpirun -np 1 java -cp lab01/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.HelloWorld

Terminal output:
Hello World! rank=0, size=1, host=DESKTOP-QDOO898, student=yermolovych

Saved capacity research:
./lab results
MPI CAPACITY RESEARCH | Yermolovych | Group 3 | 2026
CPU: 8 cores / 16 threads | Profile: capacity
Slots=np  Ranks   Time, s  Result  Note
-----------------------------------------------------------------
      16     16      1.01  PASS    sweep
      32     32      1.02  PASS    sweep
      64     64      1.57  PASS    sweep
     128    128      2.93  PASS    sweep
     256    256      7.61  PASS    sweep
     512    512     23.58  PASS    sweep
    1024      0    137.99  FAIL    MEMORY_GUARD
     768    768     62.87  PASS    boundary
     896    896     99.01  PASS    boundary
    1024   1024    176.18  FAIL    ABNORMAL_MPI_EXIT
    1024   1024    174.46  PASS    requested
-----------------------------------------------------------------
Largest successful count: 1024. Above 1024: not tested.
Final checks at 1024: 1/2 passed.
*/
