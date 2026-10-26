package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.Arrays;
import mpi.MPI;
import mpi.MPIException;

public class TestAllToAll {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int size = MPI.COMM_WORLD.getSize();
            int[] send = new int[size];
            for (int i = 0; i < size; i++) {
                send[i] = rank * 10 + i;
            }
            System.out.println("rank=" + rank + ", send=" + Arrays.toString(send));

            int[] received = new int[size];
            MPI.COMM_WORLD.allToAll(send, 1, MPI.INT, received, 1, MPI.INT);
            System.out.println("rank=" + rank + ", received=" + Arrays.toString(received));
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
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab05/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestAllToAll
Terminal output:
rank=0, send=[0, 1, 2, 3]
rank=3, send=[30, 31, 32, 33]
rank=1, send=[10, 11, 12, 13]
rank=2, send=[20, 21, 22, 23]
rank=2, received=[2, 12, 22, 32]
rank=1, received=[1, 11, 21, 31]
rank=0, received=[0, 10, 20, 30]
rank=3, received=[3, 13, 23, 33]
*/
