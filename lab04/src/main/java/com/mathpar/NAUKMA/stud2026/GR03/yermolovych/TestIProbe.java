package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.Arrays;
import mpi.MPI;
import mpi.MPIException;
import mpi.Status;

public class TestIProbe {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            int size = MPI.COMM_WORLD.getSize();
            if (rank != 0) {
                Status beforeSend = MPI.COMM_WORLD.iProbe(0, 1);
                System.out.println("mode=iProbe, rank=" + rank
                        + ", before_send_available=" + (beforeSend != null));
            }
            MPI.COMM_WORLD.barrier();

            if (rank == 0) {
                int[] data = {10, 20, 30, 40, 50};
                for (int destination = 1; destination < size; destination++) {
                    MPI.COMM_WORLD.send(data, data.length, MPI.INT, destination, 1);
                }
                System.out.println("mode=iProbe, rank=0, sent_to=" + (size - 1)
                        + ", data=" + Arrays.toString(data));
            } else {
                Status status;
                while ((status = MPI.COMM_WORLD.iProbe(0, 1)) == null) {
                    Thread.yield();
                }
                int count = status.getCount(MPI.INT);
                int[] data = new int[count];
                MPI.COMM_WORLD.recv(data, count, MPI.INT, status.getSource(), status.getTag());
                System.out.println(String.format(
                        "mode=iProbe, rank=%d, source=%d, tag=%d, count=%d, data=%s",
                        rank, status.getSource(), status.getTag(), count, Arrays.toString(data)));
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

mpirun --oversubscribe --bind-to none --timeout 60 -np 4 java -cp lab04/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestIProbe
Terminal output:
mode=iProbe, rank=2, before_send_available=false
mode=iProbe, rank=1, before_send_available=false
mode=iProbe, rank=3, before_send_available=false
mode=iProbe, rank=2, source=0, tag=1, count=5, data=[10, 20, 30, 40, 50]
mode=iProbe, rank=3, source=0, tag=1, count=5, data=[10, 20, 30, 40, 50]
mode=iProbe, rank=1, source=0, tag=1, count=5, data=[10, 20, 30, 40, 50]
mode=iProbe, rank=0, sent_to=3, data=[10, 20, 30, 40, 50]
*/
