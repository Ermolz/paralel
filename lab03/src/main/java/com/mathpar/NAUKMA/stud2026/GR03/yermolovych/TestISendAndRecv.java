package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Random;
import mpi.MPI;
import mpi.MPIException;
import mpi.Request;

public class TestISendAndRecv {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int size = MPI.COMM_WORLD.getSize();
            int n = 5;
            IntBuffer buffer = MPI.newIntBuffer(n);

            if (rank == 0) {
                Random random = new Random(2026);
                for (int i = 0; i < n; i++) {
                    buffer.put(i, random.nextInt(10));
                }
                Request[] requests = new Request[size - 1];
                for (int destination = 1; destination < size; destination++) {
                    requests[destination - 1] = MPI.COMM_WORLD.iSend(
                            buffer, n, MPI.INT, destination, 3000);
                }
                for (Request request : requests) {
                    request.waitFor();
                }
            } else {
                MPI.COMM_WORLD.recv(buffer, n, MPI.INT, 0, 3000);
            }

            int[] data = new int[n];
            for (int i = 0; i < n; i++) {
                data[i] = buffer.get(i);
            }
            System.out.println(String.format(
                    "mode=iSend/recv, rank=%d, size=%d, role=%s, data=%s",
                    rank, size, rank == 0 ? "source" : "receiver", Arrays.toString(data)));
        } finally {
            MPI.Finalize();
        }
    }
}

/*
Lab 3, sections 1.3.6 and 1.3.8: iSend/recv.
Run:
cd ~/paralel
source scripts/env.sh
mvn -q -o -Dlab.directory=lab03 compile

mpirun --oversubscribe --bind-to none --timeout 60 -np 8 java -cp lab03/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestISendAndRecv
Terminal output:
mode=iSend/recv, rank=0, size=8, role=source, data=[9, 0, 7, 0, 9]
mode=iSend/recv, rank=1, size=8, role=receiver, data=[9, 0, 7, 0, 9]
mode=iSend/recv, rank=2, size=8, role=receiver, data=[9, 0, 7, 0, 9]
mode=iSend/recv, rank=7, size=8, role=receiver, data=[9, 0, 7, 0, 9]
mode=iSend/recv, rank=3, size=8, role=receiver, data=[9, 0, 7, 0, 9]
mode=iSend/recv, rank=4, size=8, role=receiver, data=[9, 0, 7, 0, 9]
mode=iSend/recv, rank=6, size=8, role=receiver, data=[9, 0, 7, 0, 9]
mode=iSend/recv, rank=5, size=8, role=receiver, data=[9, 0, 7, 0, 9]
*/
