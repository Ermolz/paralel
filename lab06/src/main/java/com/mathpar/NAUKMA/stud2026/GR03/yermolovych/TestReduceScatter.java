package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.Arrays;
import mpi.MPI;
import mpi.MPIException;

public class TestReduceScatter {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int[] counts = {1, 2, 3, 4};
            if (MPI.COMM_WORLD.getSize() != counts.length) {
                throw new IllegalArgumentException("Run with 4 processes.");
            }

            int[] send = new int[10];
            for (int i = 0; i < send.length; i++) {
                send[i] = rank * 10 + i;
            }
            System.out.println("rank=" + rank + ", send=" + Arrays.toString(send));

            int[] received = new int[counts[rank]];
            MPI.COMM_WORLD.reduceScatter(send, received, counts, MPI.INT, MPI.SUM);
            System.out.println("rank=" + rank + ", sum_block=" + Arrays.toString(received));
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
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab06/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestReduceScatter
Terminal output:
rank=1, send=[10, 11, 12, 13, 14, 15, 16, 17, 18, 19]
rank=0, send=[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
rank=3, send=[30, 31, 32, 33, 34, 35, 36, 37, 38, 39]
rank=2, send=[20, 21, 22, 23, 24, 25, 26, 27, 28, 29]
rank=1, sum_block=[64, 68]
rank=0, sum_block=[60]
rank=2, sum_block=[72, 76, 80]
rank=3, sum_block=[84, 88, 92, 96]
*/
