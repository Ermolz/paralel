package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.Arrays;
import mpi.Group;
import mpi.Intracomm;
import mpi.MPI;
import mpi.MPIException;

public class TestCreateIntracomm {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int rank = MPI.COMM_WORLD.getRank();
            if (MPI.COMM_WORLD.getSize() < 5) {
                throw new IllegalArgumentException("Run with at least 5 MPI processes.");
            }
            Group worldGroup = MPI.COMM_WORLD.getGroup();
            Group firstGroup = worldGroup.incl(new int[]{0, 1, 2});
            Group secondGroup = worldGroup.incl(new int[]{3, 4});
            Intracomm firstComm = MPI.COMM_WORLD.create(firstGroup);
            Intracomm secondComm = MPI.COMM_WORLD.create(secondGroup);
            int[] data = new int[5];
            if (rank == 0 || rank == 3) {
                for (int i = 0; i < data.length; i++) {
                    data[i] = rank * 10 + i;
                }
            }

            Intracomm selected = !firstComm.isNull() ? firstComm : secondComm;
            if (!selected.isNull()) {
                selected.bcast(data, data.length, MPI.INT, 0);
                System.out.println(String.format(
                        "world_rank=%d, group=%s, local_rank=%d, group_size=%d, data=%s",
                        rank, !firstComm.isNull() ? "A" : "B",
                        selected.getRank(), selected.getSize(), Arrays.toString(data)));
                selected.free();
            } else {
                System.out.println("world_rank=" + rank + ", group=excluded");
            }
            firstGroup.free();
            secondGroup.free();
            worldGroup.free();
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

mpirun --oversubscribe --bind-to none --timeout 60 -np 5 java -cp lab04/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestCreateIntracomm
Terminal output:
world_rank=4, group=B, local_rank=1, group_size=2, data=[30, 31, 32, 33, 34]
world_rank=0, group=A, local_rank=0, group_size=3, data=[0, 1, 2, 3, 4]
world_rank=2, group=A, local_rank=2, group_size=3, data=[0, 1, 2, 3, 4]
world_rank=1, group=A, local_rank=1, group_size=3, data=[0, 1, 2, 3, 4]
world_rank=3, group=B, local_rank=0, group_size=2, data=[30, 31, 32, 33, 34]

Run:
mpirun --oversubscribe --bind-to none --timeout 60 -np 8 java -cp lab04/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.TestCreateIntracomm
Terminal output:
world_rank=6, group=excluded
world_rank=7, group=excluded
world_rank=5, group=excluded
world_rank=3, group=B, local_rank=0, group_size=2, data=[30, 31, 32, 33, 34]
world_rank=1, group=A, local_rank=1, group_size=3, data=[0, 1, 2, 3, 4]
world_rank=0, group=A, local_rank=0, group_size=3, data=[0, 1, 2, 3, 4]
world_rank=4, group=B, local_rank=1, group_size=2, data=[30, 31, 32, 33, 34]
world_rank=2, group=A, local_rank=2, group_size=3, data=[0, 1, 2, 3, 4]
*/
