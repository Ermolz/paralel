package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.Arrays;
import mpi.MPI;
import mpi.MPIException;

public class TestScatterv {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int size = MPI.COMM_WORLD.getSize();
            int count = 4;
            int[] send = new int[count * size];
            if (rank == 0) {
                for (int i = 0; i < send.length; i++) {
                    send[i] = i;
                }
                System.out.println("rank=0, send=" + Arrays.toString(send));
            }

            int[] counts = new int[size];
            int[] displacements = new int[size];
            for (int i = 0; i < size; i++) {
                counts[i] = count;
                displacements[i] = i * count;
            }
            int[] received = new int[count];
            MPI.COMM_WORLD.scatterv(send, counts, displacements, MPI.INT,
                    received, count, MPI.INT, 0);
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
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab05/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestScatterv
Terminal output:
rank=0, send=[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15]
rank=0, received=[0, 1, 2, 3]
rank=2, received=[8, 9, 10, 11]
rank=1, received=[4, 5, 6, 7]
rank=3, received=[12, 13, 14, 15]
*/
