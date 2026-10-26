package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import mpi.MPI;
import mpi.MPIException;

public class TestBarrier {
    public static void main(String[] args) throws MPIException, InterruptedException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int size = MPI.COMM_WORLD.getSize();
            int delayMillis = rank == size - 1 ? 500 : 0;

            MPI.COMM_WORLD.barrier();
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
            long start = System.nanoTime();
            MPI.COMM_WORLD.barrier();
            long waitedMillis = (System.nanoTime() - start) / 1_000_000;

            System.out.println("rank=" + rank + ", delay_ms=" + delayMillis
                    + ", barrier_wait_ms=" + waitedMillis);
        } finally {
            MPI.Finalize();
        }
    }
}

/*
Run:
cd ~/paralel
source scripts/env.sh
mvn -q -o -Dlab.directory=lab06 compile
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab06/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestBarrier
Terminal output:
rank=1, delay_ms=0, barrier_wait_ms=500
rank=2, delay_ms=0, barrier_wait_ms=500
rank=0, delay_ms=0, barrier_wait_ms=500
rank=3, delay_ms=500, barrier_wait_ms=0
*/
