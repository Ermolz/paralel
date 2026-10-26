package com.mathpar.NAUKMA.stud2026.GR03.yermolovych;

import java.util.Arrays;
import mpi.Group;
import mpi.Intracomm;
import mpi.MPI;
import mpi.MPIException;

public class EvenRanksCommunicator {
    public static void main(String[] args) throws MPIException {
        MPI.Init(args);
        try {
            int worldRank = MPI.COMM_WORLD.getRank();
            int worldSize = MPI.COMM_WORLD.getSize();
            int[] evenRanks = new int[(worldSize + 1) / 2];
            for (int i = 0; i < evenRanks.length; i++) {
                evenRanks[i] = 2 * i;
            }

            Group worldGroup = MPI.COMM_WORLD.getGroup();
            Group evenGroup = worldGroup.incl(evenRanks);
            Intracomm evenComm = MPI.COMM_WORLD.createGroup(evenGroup, 0);

            if (worldRank == 0) {
                System.out.println("EVEN COMMUNICATOR | world_size=" + worldSize
                        + " | members=" + Arrays.toString(evenRanks));
            }
            if (evenComm.isNull()) {
                System.out.println("world_rank=" + worldRank + " -> excluded");
            } else {
                System.out.println(String.format(
                        "world_rank=%d -> even_rank=%d, even_size=%d",
                        worldRank, evenComm.getRank(), evenComm.getSize()));
                evenComm.free();
            }

            evenGroup.free();
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
mvn -q -o -Dlab.directory=lab02 compile
mpirun --oversubscribe --bind-to none --timeout 60 -np 8 java -cp lab02/target/classes:lib/mpi.jar com.mathpar.NAUKMA.stud2026.GR03.yermolovych.EvenRanksCommunicator
Terminal output:
world_rank=1 -> excluded
world_rank=7 -> excluded
world_rank=3 -> excluded
world_rank=5 -> excluded
EVEN COMMUNICATOR | world_size=8 | members=[0, 2, 4, 6]
world_rank=6 -> even_rank=3, even_size=4
world_rank=2 -> even_rank=1, even_size=4
world_rank=4 -> even_rank=2, even_size=4
world_rank=0 -> even_rank=0, even_size=4
*/
