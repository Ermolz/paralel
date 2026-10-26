package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.Arrays;
import mpi.MPI;
import mpi.MPIException;

public class TestGather {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int size = MPI.COMM_WORLD.getSize();
            int root = size - 1;
            int count = 5;
            int[] send = new int[count];
            for (int i = 0; i < count; i++) {
                send[i] = rank * 10 + i;
            }
            System.out.println("rank=" + rank + ", send=" + Arrays.toString(send));

            int[] received = new int[count * size];
            MPI.COMM_WORLD.gather(send, count, MPI.INT, received, count, MPI.INT, root);
            if (rank == root) {
                System.out.println("rank=" + rank + ", received=" + Arrays.toString(received));
            }
        } finally {
            MPI.Finalize();
        }
    }
}

/*
Run:
cd ~/paralel
source scripts/env.sh
mvn -q -o -Dlab.directory=lab05 compile
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab05/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestGather
Terminal output:
rank=0, send=[0, 1, 2, 3, 4]
rank=3, send=[30, 31, 32, 33, 34]
rank=1, send=[10, 11, 12, 13, 14]
rank=2, send=[20, 21, 22, 23, 24]
rank=3, received=[0, 1, 2, 3, 4, 10, 11, 12, 13, 14, 20, 21, 22, 23, 24, 30, 31, 32, 33, 34]
*/
