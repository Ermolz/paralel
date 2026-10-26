package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.nio.IntBuffer;
import mpi.MPI;
import mpi.MPIException;
import mpi.Request;

public class TestWaitFor {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            if (MPI.COMM_WORLD.getSize() < 2) {
                throw new IllegalArgumentException("Run with at least 2 MPI processes.");
            }
            IntBuffer data = MPI.newIntBuffer(1);
            if (rank == 0) {
                data.put(0, 42);
                Request request = MPI.COMM_WORLD.iSend(data, 1, MPI.INT, 1, 99);
                request.waitFor();
                System.out.println("mode=waitFor, rank=0, sent=" + data.get(0) + ", completed=true");
            } else if (rank == 1) {
                Request request = MPI.COMM_WORLD.iRecv(data, 1, MPI.INT, 0, 99);
                request.waitFor();
                System.out.println("mode=waitFor, rank=1, received=" + data.get(0) + ", completed=true");
            } else {
                System.out.println("mode=waitFor, rank=" + rank + ", role=idle");
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
mvn -q -o -Dlab.directory=lab04 compile

mpirun --oversubscribe --bind-to none --timeout 60 -np 2 java -cp lab04/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestWaitFor
Terminal output:
mode=waitFor, rank=0, sent=42, completed=true
mode=waitFor, rank=1, received=42, completed=true
*/
