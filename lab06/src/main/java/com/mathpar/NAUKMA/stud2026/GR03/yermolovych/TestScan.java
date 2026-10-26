package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.Arrays;
import mpi.MPI;
import mpi.MPIException;

public class TestScan {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int[] send = new int[5];
            for (int i = 0; i < send.length; i++) {
                send[i] = rank * 10 + i;
            }
            System.out.println("rank=" + rank + ", send=" + Arrays.toString(send));

            int[] received = new int[send.length];
            MPI.COMM_WORLD.scan(send, received, send.length, MPI.INT, MPI.SUM);
            System.out.println("rank=" + rank + ", prefix_sum=" + Arrays.toString(received));
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
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab06/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestScan
Terminal output:
rank=0, send=[0, 1, 2, 3, 4]
rank=0, prefix_sum=[0, 1, 2, 3, 4]
rank=3, send=[30, 31, 32, 33, 34]
rank=1, send=[10, 11, 12, 13, 14]
rank=2, send=[20, 21, 22, 23, 24]
rank=2, prefix_sum=[30, 33, 36, 39, 42]
rank=3, prefix_sum=[60, 64, 68, 72, 76]
rank=1, prefix_sum=[10, 12, 14, 16, 18]
*/
