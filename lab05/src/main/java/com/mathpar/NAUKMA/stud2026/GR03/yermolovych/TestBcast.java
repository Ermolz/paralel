package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.Arrays;
import mpi.MPI;
import mpi.MPIException;

public class TestBcast {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int[] data = new int[5];
            if (rank == 0) {
                for (int i = 0; i < data.length; i++) {
                    data[i] = i;
                }
                System.out.println("rank=0, send=" + Arrays.toString(data));
            }

            MPI.COMM_WORLD.bcast(data, data.length, MPI.INT, 0);
            System.out.println("rank=" + rank + ", received=" + Arrays.toString(data));
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
mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab05/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestBcast
Terminal output:
rank=0, send=[0, 1, 2, 3, 4]
rank=0, received=[0, 1, 2, 3, 4]
rank=2, received=[0, 1, 2, 3, 4]
rank=1, received=[0, 1, 2, 3, 4]
rank=3, received=[0, 1, 2, 3, 4]
*/
