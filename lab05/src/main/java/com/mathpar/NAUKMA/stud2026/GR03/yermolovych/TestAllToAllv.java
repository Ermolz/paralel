package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.Arrays;
import mpi.MPI;
import mpi.MPIException;

public class TestAllToAllv {
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

            int[] counts = new int[size];
            int[] sendDisplacements = new int[size];
            int[] receiveDisplacements = new int[size];
            for (int i = 0; i < size; i++) {
                counts[i] = 1;
                sendDisplacements[i] = i;
                receiveDisplacements[i] = size - 1 - i;
            }
            int[] received = new int[size];
            MPI.COMM_WORLD.allToAllv(send, counts, sendDisplacements, MPI.INT,
                    received, counts, receiveDisplacements, MPI.INT);
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
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab05/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestAllToAllv
Terminal output:
rank=3, send=[30, 31, 32, 33]
rank=2, send=[20, 21, 22, 23]
rank=1, send=[10, 11, 12, 13]
rank=0, send=[0, 1, 2, 3]
rank=2, received=[32, 22, 12, 2]
rank=0, received=[30, 20, 10, 0]
rank=1, received=[31, 21, 11, 1]
rank=3, received=[33, 23, 13, 3]
*/
